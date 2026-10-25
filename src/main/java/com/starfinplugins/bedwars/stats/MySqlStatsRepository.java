package com.starfinplugins.bedwars.stats;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

/** Only called on the statistics I/O executor; no Bukkit API or player objects. */
public final class MySqlStatsRepository implements AutoCloseable {
    private HikariDataSource pool;

    public void open(StatsSettings settings) throws SQLException {
        if (pool != null) return;
        HikariConfig config = new HikariConfig();
        config.setPoolName("StarfinBedWars-Statistics");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + settings.host + ":" + settings.port + "/" + settings.schema
            + "?sslMode=" + settings.sslMode + "&connectionTimeZone=UTC&characterEncoding=UTF-8");
        config.setUsername(settings.user);
        config.setPassword(settings.password);
        config.setMaximumPoolSize(settings.poolSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(settings.timeoutMs);
        config.setValidationTimeout(Math.min(settings.timeoutMs, 5000));
        config.setInitializationFailTimeout(0);
        HikariDataSource dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS sbw_profiles ("
                + "uuid CHAR(36) NOT NULL PRIMARY KEY, last_name VARCHAR(16) NOT NULL DEFAULT '',"
                + "games BIGINT NOT NULL DEFAULT 0, wins BIGINT NOT NULL DEFAULT 0,"
                + "losses BIGINT NOT NULL DEFAULT 0, draws BIGINT NOT NULL DEFAULT 0,"
                + "kills BIGINT NOT NULL DEFAULT 0, final_kills BIGINT NOT NULL DEFAULT 0,"
                + "deaths BIGINT NOT NULL DEFAULT 0, beds BIGINT NOT NULL DEFAULT 0,"
                + "total_xp BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0,"
                + "INDEX idx_sbw_name (last_name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.execute("CREATE TABLE IF NOT EXISTS sbw_match_results ("
                + "match_id CHAR(36) NOT NULL, player_uuid CHAR(36) NOT NULL,"
                + "outcome VARCHAR(8) NOT NULL, kills INT NOT NULL, final_kills INT NOT NULL,"
                + "deaths INT NOT NULL, beds INT NOT NULL, earned_xp BIGINT NOT NULL,"
                + "created_at BIGINT NOT NULL, PRIMARY KEY (match_id, player_uuid),"
                + "INDEX idx_sbw_match_player (player_uuid, created_at))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            pool = dataSource;
        } catch (SQLException | RuntimeException error) {
            dataSource.close();
            throw error;
        }
    }

    /** Transactional insert-only ledger + atomic profile increments across Bungee servers. */
    public void saveMatch(List<MatchResult> results) throws SQLException {
        if (results.isEmpty()) return;
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement match = connection.prepareStatement(
                    "INSERT IGNORE INTO sbw_match_results"
                    + " (match_id,player_uuid,outcome,kills,final_kills,deaths,beds,earned_xp,created_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?)");
                 PreparedStatement profile = connection.prepareStatement(
                    "INSERT INTO sbw_profiles"
                    + " (uuid,last_name,games,wins,losses,draws,kills,final_kills,deaths,beds,total_xp,updated_at)"
                    + " VALUES (?,?,1,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
                    + " last_name=IF(VALUES(last_name)='',last_name,VALUES(last_name)),"
                    + " games=games+1,wins=wins+VALUES(wins),losses=losses+VALUES(losses),"
                    + " draws=draws+VALUES(draws),kills=kills+VALUES(kills),"
                    + " final_kills=final_kills+VALUES(final_kills),deaths=deaths+VALUES(deaths),"
                    + " beds=beds+VALUES(beds),total_xp=total_xp+VALUES(total_xp),"
                    + " updated_at=VALUES(updated_at)")) {
                long timestamp = System.currentTimeMillis();
                for (MatchResult r : results) {
                    match.setString(1, r.matchId.toString());
                    match.setString(2, r.playerId.toString());
                    match.setString(3, r.outcome.name());
                    match.setInt(4, r.kills);
                    match.setInt(5, r.finalKills);
                    match.setInt(6, r.deaths);
                    match.setInt(7, r.beds);
                    match.setLong(8, r.xp);
                    match.setLong(9, timestamp);
                    if (match.executeUpdate() == 0) continue; // Already committed by this or another node.
                    profile.setString(1, r.playerId.toString());
                    profile.setString(2, r.name);
                    profile.setInt(3, r.outcome == MatchResult.Outcome.WIN ? 1 : 0);
                    profile.setInt(4, r.outcome == MatchResult.Outcome.LOSS ? 1 : 0);
                    profile.setInt(5, r.outcome == MatchResult.Outcome.DRAW ? 1 : 0);
                    profile.setInt(6, r.kills);
                    profile.setInt(7, r.finalKills);
                    profile.setInt(8, r.deaths);
                    profile.setInt(9, r.beds);
                    profile.setLong(10, r.xp);
                    profile.setLong(11, timestamp);
                    profile.executeUpdate();
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

    public void updateName(UUID uuid, String name) throws SQLException {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) return;
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO sbw_profiles (uuid,last_name,updated_at) VALUES (?,?,?)"
                 + " ON DUPLICATE KEY UPDATE last_name=VALUES(last_name),updated_at=VALUES(updated_at)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public StatsProfile find(String uuidOrName) throws SQLException {
        boolean uuid = false;
        try { UUID.fromString(uuidOrName); uuid = true; }
        catch (IllegalArgumentException ignored) { }
        String query = uuid
            ? "SELECT * FROM sbw_profiles WHERE uuid=?"
            : "SELECT * FROM sbw_profiles WHERE LOWER(last_name)=LOWER(?) ORDER BY updated_at DESC LIMIT 1";
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuidOrName);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                return new StatsProfile(UUID.fromString(row.getString("uuid")), row.getString("last_name"),
                    row.getLong("games"), row.getLong("wins"), row.getLong("losses"),
                    row.getLong("draws"), row.getLong("kills"), row.getLong("final_kills"),
                    row.getLong("deaths"), row.getLong("beds"), row.getLong("total_xp"));
            }
        }
    }

    @Override public void close() {
        if (pool != null) { pool.close(); pool = null; }
    }
}
