package com.starfinplugins.bedwars.network;

import com.starfinplugins.bedwars.stats.StatsSettings;
import com.starfinplugins.minigames.api.ReservationJdbc;
import com.starfinplugins.minigames.api.ReservationSnapshot;
import com.starfinplugins.minigames.api.ReservationState;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/** Network discovery catalog, not an API for acquiring a slot or transferring players. */
public final class MySqlNetworkRepository implements AutoCloseable {
    private static final long LEASE_MS = 30000L;
    private HikariDataSource pool;

    public void open(StatsSettings mysql, NetworkSettings network) throws SQLException {
        if (pool != null) return;
        HikariConfig config = new HikariConfig();
        config.setPoolName("StarfinBedWars-Network");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + mysql.host + ":" + mysql.port + "/" + mysql.schema
            + "?sslMode=" + mysql.sslMode + "&connectionTimeZone=UTC&characterEncoding=UTF-8");
        config.setUsername(mysql.user);
        config.setPassword(mysql.password);
        config.setMaximumPoolSize(network.poolSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(mysql.timeoutMs);
        config.setValidationTimeout(Math.min(mysql.timeoutMs, 5000));
        config.setInitializationFailTimeout(0);
        HikariDataSource next = new HikariDataSource(config);
        try (Connection connection = next.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS sbw_network_servers ("
                + "server_id VARCHAR(32) NOT NULL PRIMARY KEY, instance_id CHAR(36) NOT NULL,"
                + "online TINYINT(1) NOT NULL, online_players INT NOT NULL, max_players INT NOT NULL,"
                + "last_heartbeat BIGINT NOT NULL,"
                + "INDEX idx_sbw_server_presence (online,last_heartbeat)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.execute("CREATE TABLE IF NOT EXISTS sbw_network_arenas ("
                + "server_id VARCHAR(32) NOT NULL, arena_id VARCHAR(32) NOT NULL,"
                + "display_name VARCHAR(128) NOT NULL, source_world VARCHAR(64) NOT NULL,"
                + "configuration_yaml MEDIUMTEXT NOT NULL, config_sha256 CHAR(64) NOT NULL,"
                + "mode VARCHAR(16) NOT NULL, arena_group VARCHAR(32) NOT NULL DEFAULT 'default',"
                + "team_count INT NOT NULL, team_size INT NOT NULL,"
                + "generator_count INT NOT NULL,"
                + "min_players INT NOT NULL, max_players INT NOT NULL, players INT NOT NULL,"
                + "countdown_seconds INT NOT NULL, enabled TINYINT(1) NOT NULL,"
                + "editing TINYINT(1) NOT NULL, joinable TINYINT(1) NOT NULL,"
                + "state VARCHAR(16) NOT NULL, match_id CHAR(36) NULL,"
                + "sync_token CHAR(36) NOT NULL, updated_at BIGINT NOT NULL,"
                + "deleted_at BIGINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (server_id,arena_id),"
                + "INDEX idx_sbw_arena_selection (joinable,enabled,deleted_at),"
                + "INDEX idx_sbw_arena_match (match_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.execute("CREATE TABLE IF NOT EXISTS sbw_network_matches ("
                + "match_id CHAR(36) NOT NULL PRIMARY KEY, server_id VARCHAR(32) NOT NULL,"
                + "server_instance CHAR(36) NOT NULL, arena_id VARCHAR(32) NOT NULL,"
                + "state VARCHAR(16) NOT NULL, players INT NOT NULL, connected_players INT NOT NULL,"
                + "max_players INT NOT NULL, winner_team VARCHAR(32) NULL,"
                + "end_reason VARCHAR(32) NULL, started_at BIGINT NOT NULL,"
                + "ended_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,"
                + "sync_token CHAR(36) NOT NULL,"
                + "INDEX idx_sbw_match_server (server_id,server_instance,state),"
                + "INDEX idx_sbw_match_arena (server_id,arena_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.execute("CREATE TABLE IF NOT EXISTS sbw_network_players ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY,last_name VARCHAR(16) NOT NULL,"
                + "server_id VARCHAR(32) NOT NULL,server_instance CHAR(36) NOT NULL,"
                + "role VARCHAR(16) NOT NULL,arena_id VARCHAR(32) NULL,match_id CHAR(36) NULL,"
                + "team_id VARCHAR(32) NULL,online TINYINT(1) NOT NULL,"
                + "last_seen BIGINT NOT NULL,sync_token CHAR(36) NOT NULL,"
                + "INDEX idx_sbw_player_server (server_id,server_instance,online),"
                + "INDEX idx_sbw_player_match (match_id,online)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            ensureArenaGroup(connection);
            pool = next;
        } catch (SQLException | RuntimeException error) {
            next.close();
            throw error;
        }
    }

    private void ensureArenaGroup(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null,
                "sbw_network_arenas", "arena_group")) {
            if (columns.next()) return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE sbw_network_arenas ADD COLUMN arena_group"
                + " VARCHAR(32) NOT NULL DEFAULT 'default'");
        } catch (SQLException concurrentMigration) {
            try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null,
                    "sbw_network_arenas", "arena_group")) {
                if (!columns.next()) throw concurrentMigration;
            }
        }
    }

    /** One transaction presents a consistent generation of arenas and matches to the proxy. */
    public void synchronize(NetworkSnapshot snapshot) throws SQLException {
        String token = UUID.randomUUID().toString();
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                claimServer(connection, snapshot);
                upsertArenas(connection, snapshot, token);
                upsertMatches(connection, snapshot, token);
                upsertPlayers(connection, snapshot, token);
                try (PreparedStatement stale = connection.prepareStatement(
                        "UPDATE sbw_network_arenas SET deleted_at=?,joinable=0,state='REMOVED'"
                        + " WHERE server_id=? AND sync_token<>? AND deleted_at=0")) {
                    stale.setLong(1, snapshot.sampledAt);
                    stale.setString(2, snapshot.serverId);
                    stale.setString(3, token);
                    stale.executeUpdate();
                }
                try (PreparedStatement stale = connection.prepareStatement(
                        "UPDATE sbw_network_matches SET state='CLOSED',"
                        + " ended_at=CASE WHEN ended_at=0 THEN ? ELSE ended_at END,updated_at=?"
                        + " WHERE server_id=? AND server_instance=? AND sync_token<>? AND state<>'CLOSED'")) {
                    stale.setLong(1, snapshot.sampledAt);
                    stale.setLong(2, snapshot.sampledAt);
                    stale.setString(3, snapshot.serverId);
                    stale.setString(4, snapshot.instanceId);
                    stale.setString(5, token);
                    stale.executeUpdate();
                }
                try (PreparedStatement stale = connection.prepareStatement(
                        "UPDATE sbw_network_players SET online=0,role='OFFLINE',"
                        + "last_seen=? WHERE server_id=? AND server_instance=?"
                        + " AND sync_token<>? AND online=1")) {
                    stale.setLong(1, snapshot.sampledAt);
                    stale.setString(2, snapshot.serverId);
                    stale.setString(3, snapshot.instanceId);
                    stale.setString(4, token);
                    stale.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void claimServer(Connection connection, NetworkSnapshot snapshot) throws SQLException {
        boolean exists;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT instance_id,online,last_heartbeat FROM sbw_network_servers"
                + " WHERE server_id=? FOR UPDATE")) {
            statement.setString(1, snapshot.serverId);
            try (ResultSet row = statement.executeQuery()) {
                exists = row.next();
                if (exists && row.getBoolean("online")
                        && !snapshot.instanceId.equals(row.getString("instance_id"))
                        && row.getLong("last_heartbeat") >= snapshot.sampledAt - LEASE_MS) {
                    throw new SQLException("server-id ja esta ativo em outra instancia: " + snapshot.serverId);
                }
            }
        }
        String query = exists
            ? "UPDATE sbw_network_servers SET instance_id=?,online=1,online_players=?,"
                + "max_players=?,last_heartbeat=? WHERE server_id=?"
            : "INSERT INTO sbw_network_servers"
                + " (instance_id,online,online_players,max_players,last_heartbeat,server_id)"
                + " VALUES (?,1,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, snapshot.instanceId);
            statement.setInt(2, snapshot.onlinePlayers);
            statement.setInt(3, snapshot.maxServerPlayers);
            statement.setLong(4, snapshot.sampledAt);
            statement.setString(5, snapshot.serverId);
            statement.executeUpdate();
        }
    }

    private void upsertArenas(Connection connection, NetworkSnapshot snapshot, String token) throws SQLException {
        String query = "INSERT INTO sbw_network_arenas"
            + " (server_id,arena_id,display_name,source_world,configuration_yaml,config_sha256,"
            + "mode,arena_group,team_count,team_size,generator_count,min_players,max_players,players,countdown_seconds,"
            + "enabled,editing,joinable,state,match_id,sync_token,updated_at,deleted_at)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0) ON DUPLICATE KEY UPDATE"
            + " display_name=VALUES(display_name),source_world=VALUES(source_world),"
            + " configuration_yaml=IF(config_sha256<>VALUES(config_sha256),"
            + " VALUES(configuration_yaml),configuration_yaml),config_sha256=VALUES(config_sha256),"
            + " mode=VALUES(mode),arena_group=VALUES(arena_group),"
            + " team_count=VALUES(team_count),team_size=VALUES(team_size),"
            + " generator_count=VALUES(generator_count),"
            + " min_players=VALUES(min_players),max_players=VALUES(max_players),"
            + " players=VALUES(players),countdown_seconds=VALUES(countdown_seconds),"
            + " enabled=VALUES(enabled),editing=VALUES(editing),joinable=VALUES(joinable),"
            + " state=VALUES(state),match_id=VALUES(match_id),sync_token=VALUES(sync_token),"
            + " updated_at=VALUES(updated_at),deleted_at=0";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            for (NetworkSnapshot.Arena arena : snapshot.arenas) {
                int index = 1;
                statement.setString(index++, snapshot.serverId);
                statement.setString(index++, arena.id);
                statement.setString(index++, arena.name);
                statement.setString(index++, arena.sourceWorld);
                statement.setString(index++, arena.yaml);
                statement.setString(index++, arena.configHash);
                statement.setString(index++, arena.mode);
                statement.setString(index++, arena.group);
                statement.setInt(index++, arena.teamCount);
                statement.setInt(index++, arena.teamSize);
                statement.setInt(index++, arena.generatorCount);
                statement.setInt(index++, arena.minPlayers);
                statement.setInt(index++, arena.maxPlayers);
                statement.setInt(index++, arena.players);
                statement.setInt(index++, arena.countdownSeconds);
                statement.setBoolean(index++, arena.enabled);
                statement.setBoolean(index++, arena.editing);
                statement.setBoolean(index++, arena.joinable);
                statement.setString(index++, arena.state);
                statement.setString(index++, arena.matchId);
                statement.setString(index++, token);
                statement.setLong(index++, snapshot.sampledAt);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertPlayers(Connection connection, NetworkSnapshot snapshot, String token) throws SQLException {
        String query = "INSERT INTO sbw_network_players"
            + " (player_uuid,last_name,server_id,server_instance,role,arena_id,match_id,team_id,"
            + "online,last_seen,sync_token) VALUES (?,?,?,?,?,?,?,?,1,?,?)"
            + " ON DUPLICATE KEY UPDATE"
            + " last_name=IF(VALUES(last_seen)>=last_seen,VALUES(last_name),last_name),"
            + " server_id=IF(VALUES(last_seen)>=last_seen,VALUES(server_id),server_id),"
            + " server_instance=IF(VALUES(last_seen)>=last_seen,VALUES(server_instance),server_instance),"
            + " role=IF(VALUES(last_seen)>=last_seen,VALUES(role),role),"
            + " arena_id=IF(VALUES(last_seen)>=last_seen,VALUES(arena_id),arena_id),"
            + " match_id=IF(VALUES(last_seen)>=last_seen,VALUES(match_id),match_id),"
            + " team_id=IF(VALUES(last_seen)>=last_seen,VALUES(team_id),team_id),"
            + " online=IF(VALUES(last_seen)>=last_seen,1,online),"
            + " sync_token=IF(VALUES(last_seen)>=last_seen,VALUES(sync_token),sync_token),"
            + " last_seen=GREATEST(last_seen,VALUES(last_seen))";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            for (NetworkSnapshot.Player player : snapshot.players) {
                int index = 1;
                statement.setString(index++, player.uuid);
                statement.setString(index++, player.name);
                statement.setString(index++, snapshot.serverId);
                statement.setString(index++, snapshot.instanceId);
                statement.setString(index++, player.role);
                statement.setString(index++, player.arenaId);
                statement.setString(index++, player.matchId);
                statement.setString(index++, player.teamId);
                statement.setLong(index++, snapshot.sampledAt);
                statement.setString(index++, token);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertMatches(Connection connection, NetworkSnapshot snapshot, String token) throws SQLException {
        String query = "INSERT INTO sbw_network_matches"
            + " (match_id,server_id,server_instance,arena_id,state,players,connected_players,"
            + "max_players,winner_team,end_reason,started_at,ended_at,updated_at,sync_token)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
            + " state=VALUES(state),players=VALUES(players),connected_players=VALUES(connected_players),"
            + " winner_team=VALUES(winner_team),end_reason=VALUES(end_reason),"
            + " started_at=VALUES(started_at),ended_at=VALUES(ended_at),"
            + " updated_at=VALUES(updated_at),sync_token=VALUES(sync_token)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            for (NetworkSnapshot.Match match : snapshot.matches) {
                int index = 1;
                statement.setString(index++, match.id);
                statement.setString(index++, snapshot.serverId);
                statement.setString(index++, snapshot.instanceId);
                statement.setString(index++, match.arenaId);
                statement.setString(index++, match.state);
                statement.setInt(index++, match.players);
                statement.setInt(index++, match.connectedPlayers);
                statement.setInt(index++, match.maxPlayers);
                statement.setString(index++, match.winnerTeam);
                statement.setString(index++, match.endReason);
                statement.setLong(index++, match.startedAt);
                statement.setLong(index++, match.endedAt);
                statement.setLong(index++, snapshot.sampledAt);
                statement.setString(index++, token);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public void markOffline(String serverId, String instanceId) throws SQLException {
        if (pool == null) return;
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE sbw_network_servers SET online=0,online_players=0,last_heartbeat=?"
                 + " WHERE server_id=? AND instance_id=?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, serverId);
            statement.setString(3, instanceId);
            statement.executeUpdate();
        }
    }

    public ReservationSnapshot claimReservation(UUID playerId, String serverId, String instanceId)
            throws SQLException {
        if (pool == null) return null;
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ReservationSnapshot claimed = ReservationJdbc.claim(connection, playerId,
                    serverId, UUID.fromString(instanceId), System.currentTimeMillis(), 90000L);
                connection.commit();
                return claimed;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public boolean finishReservation(UUID reservationId, UUID playerId, boolean confirmed)
            throws SQLException {
        if (pool == null) return false;
        try (Connection connection = pool.getConnection()) {
            return ReservationJdbc.finish(connection, reservationId, playerId,
                confirmed ? ReservationState.CONFIRMED : ReservationState.FAILED,
                System.currentTimeMillis());
        }
    }

    @Override public void close() {
        if (pool != null) { pool.close(); pool = null; }
    }
}
