package com.starfinplugins.bedwars.ranked;

import com.starfinplugins.bedwars.stats.StatsSettings;
import com.starfinplugins.minigames.api.ReservationSnapshot;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All writes for one match commit together; a match UUID is its idempotency key. */
public final class RankedRepository implements AutoCloseable {
    private HikariDataSource pool;

    public void open(StatsSettings database) throws SQLException {
        if (pool != null) return;
        HikariConfig config = new HikariConfig();
        config.setPoolName("StarfinBedWars-Ranked");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + database.host + ":" + database.port + "/"
            + database.schema + "?sslMode=" + database.sslMode
            + "&connectionTimeZone=UTC&characterEncoding=UTF-8");
        config.setUsername(database.user); config.setPassword(database.password);
        config.setMaximumPoolSize(database.poolSize);
        config.setMinimumIdle(0); config.setConnectionTimeout(database.timeoutMs);
        config.setInitializationFailTimeout(0);
        HikariDataSource created = new HikariDataSource(config);
        try (Connection connection = created.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_schema_migrations ("
                + "migration_id INT NOT NULL PRIMARY KEY,applied_at BIGINT NOT NULL)"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_profiles ("
                + "player_uuid CHAR(36) NOT NULL,mode VARCHAR(16) NOT NULL,context VARCHAR(32) NOT NULL,"
                + "rating INT NOT NULL,peak_rating INT NOT NULL,games INT NOT NULL DEFAULT 0,"
                + "wins INT NOT NULL DEFAULT 0,losses INT NOT NULL DEFAULT 0,draws INT NOT NULL DEFAULT 0,"
                + "placement_games INT NOT NULL DEFAULT 0,last_match_at BIGINT NOT NULL DEFAULT 0,"
                + "updated_at BIGINT NOT NULL,PRIMARY KEY(player_uuid,mode,context),"
                + "INDEX idx_sbw_ranked_mode (mode,context,rating)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_matches ("
                + "match_id CHAR(36) NOT NULL PRIMARY KEY,mode VARCHAR(16) NOT NULL,"
                + "arena_id VARCHAR(64) NOT NULL,server_id VARCHAR(64) NOT NULL,"
                + "instance_id VARCHAR(64) NOT NULL,context VARCHAR(32) NOT NULL,"
                + "winner_team VARCHAR(40) NULL,draw TINYINT(1) NOT NULL,"
                + "snapshot_hash CHAR(64) NOT NULL,started_at BIGINT NOT NULL,ended_at BIGINT NOT NULL,"
                + "processed_at BIGINT NOT NULL,status VARCHAR(16) NOT NULL,"
                + "INDEX idx_sbw_ranked_matches_time (context,ended_at))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_results ("
                + "match_id CHAR(36) NOT NULL,player_uuid CHAR(36) NOT NULL,"
                + "team_id VARCHAR(40) NOT NULL,outcome VARCHAR(8) NOT NULL,"
                + "rating_before INT NOT NULL,rating_delta INT NOT NULL,rating_after INT NOT NULL,"
                + "completed_at BIGINT NOT NULL,PRIMARY KEY(match_id,player_uuid),"
                + "INDEX idx_sbw_ranked_results_player (player_uuid,completed_at))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_policy ("
                + "server_id VARCHAR(32) NOT NULL PRIMARY KEY,instance_id CHAR(36) NOT NULL,"
                + "enabled TINYINT(1) NOT NULL,context VARCHAR(32) NOT NULL,"
                + "initial_rating INT NOT NULL,updated_at BIGINT NOT NULL)"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_abandons ("
                + "match_id CHAR(36) NOT NULL,player_uuid CHAR(36) NOT NULL,context VARCHAR(32) NOT NULL,"
                + "created_at BIGINT NOT NULL,PRIMARY KEY(match_id,player_uuid),"
                + "INDEX idx_sbw_ranked_abandon_window(player_uuid,context,created_at))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_suspensions ("
                + "player_uuid CHAR(36) NOT NULL,context VARCHAR(32) NOT NULL,until_at BIGINT NOT NULL,"
                + "reason VARCHAR(32) NOT NULL,updated_at BIGINT NOT NULL,"
                + "PRIMARY KEY(player_uuid,context)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            try (PreparedStatement mark = connection.prepareStatement(
                    "INSERT IGNORE INTO sbw_ranked_schema_migrations(migration_id,applied_at) VALUES(61,?)")) {
                mark.setLong(1, System.currentTimeMillis()); mark.executeUpdate();
            }
            pool = created;
        } catch (SQLException | RuntimeException failed) { created.close(); throw failed; }
    }

    public boolean isOpen() { return pool != null && !pool.isClosed(); }

    public void publishPolicy(String serverId, String instanceId, RankedSettings settings,
                              boolean issuerAvailable)
            throws SQLException {
        try (Connection connection = pool.getConnection(); PreparedStatement update = connection.prepareStatement(
                "INSERT INTO sbw_ranked_policy(server_id,instance_id,enabled,context,initial_rating,updated_at)"
                + " VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE instance_id=VALUES(instance_id),"
                + "enabled=VALUES(enabled),context=VALUES(context),"
                + "initial_rating=VALUES(initial_rating),updated_at=VALUES(updated_at)")) {
            update.setString(1, serverId); update.setString(2, instanceId);
            update.setBoolean(3, settings.enabled && issuerAvailable);
            update.setString(4, settings.context);
            update.setInt(5, settings.initial); update.setLong(6, System.currentTimeMillis());
            update.executeUpdate();
        }
    }

    public RankedAllocation inspectAllocation(ReservationSnapshot reservation,
                                                String serverId, String instanceId)
            throws SQLException {
        if (reservation == null || !reservation.isRanked()
                || !serverId.equals(reservation.getServerId())
                || !instanceId.equals(reservation.getServerInstance().toString())) return null;
        long now = System.currentTimeMillis();
        try (Connection c = pool.getConnection(); PreparedStatement query = c.prepareStatement(
                "SELECT server_id,server_instance,arena_id,mode,context,state,expected_players,expires_at"
                + " FROM sm_ranked_allocations WHERE allocation_id=?")) {
            query.setString(1, reservation.getRankedAllocationId().toString());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next() || !serverId.equals(row.getString(1))
                        || !instanceId.equals(row.getString(2))
                        || !reservation.getArenaId().equals(row.getString(3))
                        || !reservation.getMode().equals(row.getString(4))
                        || !reservation.getCompetitiveContext().equals(row.getString(5))
                        || !("RESERVED".equals(row.getString(6))
                            || "TRANSFERRING".equals(row.getString(6)))
                        || row.getLong(8) <= now) return null;
                int expected = row.getInt(7);
                long expiry = row.getLong(8);
                Map<UUID, String> teams = new java.util.LinkedHashMap<UUID, String>();
                boolean found = false;
                try (PreparedStatement members = c.prepareStatement(
                        "SELECT m.player_uuid,m.reservation_id,m.team_id,r.state,r.ranked_allocation_id,"
                        + "r.expires_at FROM sm_ranked_allocation_members m"
                        + " JOIN sm_network_reservations r ON r.reservation_id=m.reservation_id"
                        + " WHERE m.allocation_id=?")) {
                    members.setString(1, reservation.getRankedAllocationId().toString());
                    try (ResultSet member = members.executeQuery()) {
                        while (member.next()) {
                            UUID id = UUID.fromString(member.getString(1));
                            String state = member.getString(4);
                            if (!("PENDING".equals(state) || "CLAIMED".equals(state)
                                    || "CONFIRMED".equals(state)) || member.getLong(6) <= now
                                    || !reservation.getRankedAllocationId().toString().equals(member.getString(5)))
                                return null;
                            teams.put(id, member.getString(3));
                            if (reservation.getPlayerId().equals(id))
                                found = reservation.getId().toString().equals(member.getString(2))
                                    && reservation.getTeamId().equals(member.getString(3));
                        }
                    }
                }
                if (!found || teams.size() != expected || new java.util.HashSet<String>(teams.values()).size() < 2)
                    return null;
                return new RankedAllocation(reservation.getRankedAllocationId(),
                    reservation.getArenaId(), reservation.getMode(),
                    reservation.getCompetitiveContext(), expiry, teams);
            }
        }
    }

    public boolean markAdmitted(UUID allocationId, UUID reservationId, UUID playerId)
            throws SQLException {
        try (Connection c = pool.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sm_ranked_allocation_members SET state='ADMITTED'"
                        + " WHERE allocation_id=? AND reservation_id=? AND player_uuid=?"
                        + " AND state IN ('RESERVED','ADMITTED')")) {
                    update.setString(1, allocationId.toString());
                    update.setString(2, reservationId.toString()); update.setString(3, playerId.toString());
                    if (update.executeUpdate() == 0) { c.rollback(); return false; }
                }
                int expected;
                try (PreparedStatement allocation = c.prepareStatement(
                        "SELECT expected_players,state,expires_at FROM sm_ranked_allocations"
                        + " WHERE allocation_id=? FOR UPDATE")) {
                    allocation.setString(1, allocationId.toString());
                    try (ResultSet row = allocation.executeQuery()) {
                        if (!row.next() || row.getLong(3) <= System.currentTimeMillis()
                                || !("RESERVED".equals(row.getString(2))
                                    || "TRANSFERRING".equals(row.getString(2)))) {
                            c.rollback(); return false;
                        }
                        expected = row.getInt(1);
                    }
                }
                int actual;
                try (PreparedStatement count = c.prepareStatement(
                        "SELECT COUNT(*) FROM sm_ranked_allocation_members"
                        + " WHERE allocation_id=? AND state='ADMITTED'")) {
                    count.setString(1, allocationId.toString());
                    try (ResultSet row = count.executeQuery()) { row.next(); actual = row.getInt(1); }
                }
                if (actual == expected) {
                    try (PreparedStatement done = c.prepareStatement(
                            "UPDATE sm_ranked_allocations SET state='ADMITTED' WHERE allocation_id=?")) {
                        done.setString(1, allocationId.toString()); done.executeUpdate();
                    }
                    try (PreparedStatement done = c.prepareStatement(
                            "UPDATE sm_ranked_queue_entries SET state='ADMITTED'"
                            + " WHERE allocation_id=? AND state IN ('RESERVED','TRANSFERRING')")) {
                        done.setString(1, allocationId.toString()); done.executeUpdate();
                    }
                    try (PreparedStatement release = c.prepareStatement(
                            "DELETE FROM sm_ranked_queue_members WHERE entry_id IN"
                            + " (SELECT entry_id FROM sm_ranked_queue_entries WHERE allocation_id=?)")) {
                        release.setString(1, allocationId.toString()); release.executeUpdate();
                    }
                }
                c.commit(); return actual == expected;
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }

    /** A successful pool initialization does not guarantee the database is still reachable. */
    public void ping() throws SQLException {
        try (Connection connection = pool.getConnection();
             Statement query = connection.createStatement();
             ResultSet row = query.executeQuery("SELECT 1")) {
            if (!row.next() || row.getInt(1) != 1)
                throw new SQLException("Banco competitivo nao respondeu ao health check");
        }
    }

    public RankedProfile profile(UUID player, String mode, String context, int initial) throws SQLException {
        try (Connection connection = pool.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT rating,peak_rating,games,wins,losses,draws,placement_games,last_match_at"
                + " FROM sbw_ranked_profiles WHERE player_uuid=? AND mode=? AND context=?")) {
            query.setString(1, player.toString()); query.setString(2, mode); query.setString(3, context);
            try (ResultSet row = query.executeQuery()) {
                if (row.next()) return readProfile(player, mode, context, row);
            }
        }
        return new RankedProfile(player, mode, context, initial, initial, 0, 0, 0, 0, 0, 0L);
    }

    public List<RankedResult> results(UUID matchId) throws SQLException {
        List<RankedResult> found = new ArrayList<RankedResult>();
        try (Connection connection = pool.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT player_uuid,team_id,outcome,rating_before,rating_delta,rating_after,completed_at"
                + " FROM sbw_ranked_results WHERE match_id=? ORDER BY player_uuid")) {
            query.setString(1, matchId.toString());
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) found.add(new RankedResult(matchId, UUID.fromString(row.getString(1)),
                    row.getString(2), row.getString(3), row.getInt(4), row.getInt(5),
                    row.getInt(6), row.getLong(7)));
            }
        }
        return Collections.unmodifiableList(found);
    }

    public UUID resolvePlayer(String identifier) throws SQLException {
        if (identifier == null || identifier.length() > 36) return null;
        try { return UUID.fromString(identifier); }
        catch (IllegalArgumentException name) { /* Resolve the last known name below. */ }
        if (!identifier.matches("[A-Za-z0-9_]{3,16}")) return null;
        try (Connection connection = pool.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT uuid FROM sbw_profiles WHERE last_name=? ORDER BY updated_at DESC LIMIT 1")) {
            query.setString(1, identifier);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? UUID.fromString(row.getString(1)) : null;
            }
        }
    }

    public Applied apply(RankedMatchSnapshot match, RankedSettings settings) throws SQLException {
        for (int retry = 0; ; retry++) {
            try { return applyOnce(match, settings); }
            catch (SQLException error) {
                if (retry >= 2 || !retryable(error)) throw error;
                try { Thread.sleep(30L * (retry + 1)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw error; }
            }
        }
    }

    private Applied applyOnce(RankedMatchSnapshot match, RankedSettings settings) throws SQLException {
        String fingerprint = fingerprint(match);
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO sbw_ranked_matches(match_id,mode,arena_id,server_id,instance_id,"
                        + "context,winner_team,draw,snapshot_hash,started_at,ended_at,processed_at,status)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    insert.setString(1, match.matchId.toString()); insert.setString(2, match.mode);
                    insert.setString(3, match.arenaId); insert.setString(4, match.serverId);
                    insert.setString(5, match.instanceId); insert.setString(6, match.context);
                    insert.setString(7, match.winnerTeam); insert.setBoolean(8, match.draw);
                    insert.setString(9, fingerprint); insert.setLong(10, match.startedAt);
                    insert.setLong(11, match.endedAt); insert.setLong(12, System.currentTimeMillis());
                    insert.setString(13, "PROCESSED"); insert.executeUpdate();
                } catch (SQLException duplicate) {
                    if (!"23000".equals(duplicate.getSQLState())) throw duplicate;
                    connection.rollback();
                    try (PreparedStatement existing = connection.prepareStatement(
                            "SELECT snapshot_hash FROM sbw_ranked_matches WHERE match_id=?")) {
                        existing.setString(1, match.matchId.toString());
                        try (ResultSet row = existing.executeQuery()) {
                            if (!row.next() || !fingerprint.equals(row.getString(1)))
                                throw new SQLException("Conflito de snapshot competitivo para " + match.matchId);
                        }
                    }
                    return new Applied(false, Collections.<RankedResult>emptyList());
                }
                List<RankedMatchSnapshot.Member> ordered = new ArrayList<RankedMatchSnapshot.Member>(match.members);
                ordered.sort(Comparator.comparing(m -> m.playerId.toString()));
                Map<UUID, RankedProfile> before = new HashMap<UUID, RankedProfile>();
                for (RankedMatchSnapshot.Member member : ordered) {
                    try (PreparedStatement create = connection.prepareStatement(
                            "INSERT IGNORE INTO sbw_ranked_profiles(player_uuid,mode,context,rating,"
                            + "peak_rating,updated_at) VALUES(?,?,?,?,?,?)")) {
                        create.setString(1, member.playerId.toString()); create.setString(2, match.mode);
                        create.setString(3, match.context); create.setInt(4, match.initialRating);
                        create.setInt(5, match.initialRating); create.setLong(6, System.currentTimeMillis());
                        create.executeUpdate();
                    }
                    try (PreparedStatement lock = connection.prepareStatement(
                            "SELECT rating,peak_rating,games,wins,losses,draws,placement_games,last_match_at"
                            + " FROM sbw_ranked_profiles WHERE player_uuid=? AND mode=? AND context=? FOR UPDATE")) {
                        lock.setString(1, member.playerId.toString()); lock.setString(2, match.mode);
                        lock.setString(3, match.context);
                        try (ResultSet row = lock.executeQuery()) {
                            if (!row.next()) throw new SQLException("Perfil competitivo desapareceu");
                            before.put(member.playerId, readProfile(member.playerId, match.mode, match.context, row));
                        }
                    }
                }
                Map<UUID, Integer> ratings = new HashMap<UUID, Integer>();
                for (RankedProfile profile : before.values()) ratings.put(profile.playerId, profile.rating);
                Map<UUID, Integer> deltas = RankedRatingMath.changes(match, ratings, match.kFactor);
                List<RankedResult> results = new ArrayList<RankedResult>();
                List<UUID> placed = new ArrayList<UUID>();
                for (RankedMatchSnapshot.Member member : ordered) {
                    RankedProfile old = before.get(member.playerId);
                    if (settings.placements > 0 && old.placementGames + 1 == settings.placements)
                        placed.add(member.playerId);
                    long raw = (long) old.rating + deltas.get(member.playerId);
                    int next = (int) Math.max(match.minimumRating, Math.min(Integer.MAX_VALUE, raw));
                    String outcome = member.abandoned ? "LOSS" : match.draw ? "DRAW"
                        : member.teamId.equals(match.winnerTeam) ? "WIN" : "LOSS";
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE sbw_ranked_profiles SET rating=?,peak_rating=GREATEST(peak_rating,?),"
                            + "games=games+1,wins=wins+?,losses=losses+?,draws=draws+?,"
                            + "placement_games=placement_games+1,last_match_at=?,updated_at=?"
                            + " WHERE player_uuid=? AND mode=? AND context=?")) {
                        update.setInt(1, next); update.setInt(2, next);
                        update.setInt(3, "WIN".equals(outcome) ? 1 : 0);
                        update.setInt(4, "LOSS".equals(outcome) ? 1 : 0);
                        update.setInt(5, "DRAW".equals(outcome) ? 1 : 0);
                        update.setLong(6, match.endedAt); update.setLong(7, System.currentTimeMillis());
                        update.setString(8, member.playerId.toString()); update.setString(9, match.mode);
                        update.setString(10, match.context);
                        if (update.executeUpdate() != 1) throw new SQLException("Perfil competitivo nao atualizado");
                    }
                    try (PreparedStatement result = connection.prepareStatement(
                            "INSERT INTO sbw_ranked_results(match_id,player_uuid,team_id,outcome,"
                            + "rating_before,rating_delta,rating_after,completed_at) VALUES(?,?,?,?,?,?,?,?)")) {
                        result.setString(1, match.matchId.toString()); result.setString(2, member.playerId.toString());
                        result.setString(3, member.teamId); result.setString(4, outcome);
                        result.setInt(5, old.rating); result.setInt(6, next - old.rating);
                        result.setInt(7, next); result.setLong(8, match.endedAt); result.executeUpdate();
                    }
                    results.add(new RankedResult(match.matchId, member.playerId, member.teamId,
                        outcome, old.rating, next - old.rating, next, match.endedAt));
                    if (member.abandoned) recordAbandonment(connection, match, member, settings);
                }
                connection.commit();
                return new Applied(true, Collections.unmodifiableList(results),
                    Collections.unmodifiableList(placed));
            } catch (SQLException | RuntimeException error) {
                connection.rollback(); throw error;
            } finally { connection.setAutoCommit(true); }
        }
    }

    private static void recordAbandonment(Connection connection, RankedMatchSnapshot match,
                                          RankedMatchSnapshot.Member member, RankedSettings settings)
            throws SQLException {
        try (PreparedStatement event = connection.prepareStatement(
                "INSERT INTO sbw_ranked_abandons(match_id,player_uuid,context,created_at)"
                + " VALUES(?,?,?,?)")) {
            event.setString(1, match.matchId.toString());
            event.setString(2, member.playerId.toString());
            event.setString(3, match.context); event.setLong(4, match.endedAt);
            event.executeUpdate();
        }
        if (!settings.suspensionsEnabled) return;
        int count;
        try (PreparedStatement recent = connection.prepareStatement(
                "SELECT COUNT(*) FROM sbw_ranked_abandons WHERE player_uuid=? AND context=?"
                + " AND created_at>=?")) {
            recent.setString(1, member.playerId.toString()); recent.setString(2, match.context);
            recent.setLong(3, match.endedAt - settings.suspensionWindowMinutes * 60000L);
            try (ResultSet row = recent.executeQuery()) { row.next(); count = row.getInt(1); }
        }
        if (count < settings.suspensionThreshold) return;
        long until = match.endedAt + settings.suspensionMinutes * 60000L;
        try (PreparedStatement suspension = connection.prepareStatement(
                "INSERT INTO sbw_ranked_suspensions(player_uuid,context,until_at,reason,updated_at)"
                + " VALUES(?,? ,?,'REPEATED_ABANDONMENT',?) ON DUPLICATE KEY UPDATE"
                + " until_at=GREATEST(until_at,VALUES(until_at)),reason=VALUES(reason),"
                + " updated_at=VALUES(updated_at)")) {
            suspension.setString(1, member.playerId.toString());
            suspension.setString(2, match.context);
            suspension.setLong(3, until); suspension.setLong(4, match.endedAt);
            suspension.executeUpdate();
        }
    }

    private static RankedProfile readProfile(UUID id, String mode, String context, ResultSet row)
            throws SQLException {
        return new RankedProfile(id, mode, context, row.getInt(1), row.getInt(2),
            row.getInt(3), row.getInt(4), row.getInt(5), row.getInt(6), row.getInt(7), row.getLong(8));
    }
    private static boolean retryable(SQLException error) {
        return "40001".equals(error.getSQLState()) || error.getErrorCode() == 1213
            || error.getErrorCode() == 1205;
    }
    private static String fingerprint(RankedMatchSnapshot match) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            StringBuilder value = new StringBuilder().append(match.matchId).append('|')
                .append(match.mode).append('|').append(match.context).append('|')
                .append(match.arenaId).append('|').append(match.serverId).append('|')
                .append(match.instanceId).append('|').append(match.startedAt).append('|')
                .append(match.endedAt).append('|').append(match.winnerTeam).append('|').append(match.draw)
                .append('|').append(match.initialRating).append('|').append(match.minimumRating)
                .append('|').append(match.kFactor);
            List<RankedMatchSnapshot.Member> ordered = new ArrayList<RankedMatchSnapshot.Member>(match.members);
            ordered.sort(Comparator.comparing(m -> m.playerId.toString()));
            for (RankedMatchSnapshot.Member member : ordered)
                value.append('|').append(member.playerId).append(':').append(member.teamId)
                    .append(':').append(member.abandoned);
            byte[] digest = sha.digest(value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    @Override public void close() { if (pool != null) pool.close(); pool = null; }
    public static final class Applied {
        public final boolean newResult;
        public final List<RankedResult> results;
        public final List<UUID> placementCompletions;
        Applied(boolean newResult, List<RankedResult> results) {
            this(newResult, results, Collections.<UUID>emptyList());
        }
        Applied(boolean newResult, List<RankedResult> results, List<UUID> placementCompletions) {
            this.newResult = newResult; this.results = results;
            this.placementCompletions = placementCompletions;
        }
    }
}
