package com.starfinplugins.bedwars.stats;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;

/** Only called on the statistics I/O executor; no Bukkit API or player objects. */
public final class MySqlStatsRepository implements AutoCloseable {
    private HikariDataSource pool;
    private XpProgression progression;

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
                + "deaths BIGINT NOT NULL DEFAULT 0, final_deaths BIGINT NOT NULL DEFAULT 0,"
                + "beds BIGINT NOT NULL DEFAULT 0,"
                + "total_xp BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0,"
                + "first_play BIGINT NOT NULL DEFAULT 0, last_play BIGINT NOT NULL DEFAULT 0,"
                + "INDEX idx_sbw_name (last_name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.execute("CREATE TABLE IF NOT EXISTS sbw_match_results ("
                + "match_id CHAR(36) NOT NULL, player_uuid CHAR(36) NOT NULL,"
                + "outcome VARCHAR(8) NOT NULL, kills INT NOT NULL, final_kills INT NOT NULL,"
                + "deaths INT NOT NULL, final_deaths INT NOT NULL DEFAULT 0,"
                + "beds INT NOT NULL, earned_xp BIGINT NOT NULL,"
                + "created_at BIGINT NOT NULL, mode VARCHAR(16) NULL,"
                + "PRIMARY KEY (match_id, player_uuid),"
                + "INDEX idx_sbw_match_player (player_uuid, created_at),"
                + "INDEX idx_sbw_result_period (created_at,mode,player_uuid),"
                + "INDEX idx_sbw_result_mode_period (mode,created_at,player_uuid))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.execute("CREATE TABLE IF NOT EXISTS sbw_player_preferences ("
                + "uuid CHAR(36) NOT NULL PRIMARY KEY, language VARCHAR(5) NOT NULL DEFAULT 'pt_BR',"
                + "quick_buy TEXT NULL, selected_title VARCHAR(32) NULL,"
                + "selected_kit VARCHAR(32) NOT NULL DEFAULT 'default',"
                + "selected_ability VARCHAR(32) NOT NULL DEFAULT 'none',"
                + "updated_at BIGINT NOT NULL DEFAULT 0)"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.execute("CREATE TABLE IF NOT EXISTS sbw_reward_unlocks ("
                + "player_uuid CHAR(36) NOT NULL,reward_id VARCHAR(32) NOT NULL,"
                + "required_level INT NOT NULL,unlocked_at BIGINT NOT NULL,"
                + "PRIMARY KEY(player_uuid,reward_id),"
                + "INDEX idx_sbw_reward_id (reward_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            // Upgrade databases created by earlier BedWars versions without deleting user data.
            ensureColumn(connection, "sbw_profiles", "final_deaths", "BIGINT NOT NULL DEFAULT 0");
            ensureColumn(connection, "sbw_profiles", "first_play", "BIGINT NOT NULL DEFAULT 0");
            ensureColumn(connection, "sbw_profiles", "last_play", "BIGINT NOT NULL DEFAULT 0");
            ensureColumn(connection, "sbw_match_results", "final_deaths", "INT NOT NULL DEFAULT 0");
            ensureColumn(connection, "sbw_match_results", "mode", "VARCHAR(16) NULL");
            ensureColumn(connection, "sbw_player_preferences", "selected_title", "VARCHAR(32) NULL");
            ensureColumn(connection, "sbw_player_preferences", "selected_kit",
                "VARCHAR(32) NOT NULL DEFAULT 'default'");
            ensureColumn(connection, "sbw_player_preferences", "selected_ability",
                "VARCHAR(32) NOT NULL DEFAULT 'none'");
            ensureIndex(connection, "sbw_match_results", "idx_sbw_result_period",
                "(created_at,mode,player_uuid)");
            ensureIndex(connection, "sbw_match_results", "idx_sbw_result_mode_period",
                "(mode,created_at,player_uuid)");
            pool = dataSource;
            progression = settings.progression;
        } catch (SQLException | RuntimeException error) {
            dataSource.close();
            throw error;
        }
    }

    /** Transactional insert-only ledger + atomic profile increments across Bungee servers. */
    public List<ProgressionUpdate> saveMatch(List<MatchResult> results,
                                             RewardsCatalog rewards,
                                             XpProgression progression) throws SQLException {
        List<ProgressionUpdate> updates = new ArrayList<ProgressionUpdate>();
        if (results.isEmpty()) return updates;
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement match = connection.prepareStatement(
                    "INSERT IGNORE INTO sbw_match_results"
                    + " (match_id,player_uuid,outcome,kills,final_kills,deaths,final_deaths,beds,earned_xp,created_at,mode)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?)");
                 PreparedStatement profile = connection.prepareStatement(
                    "INSERT INTO sbw_profiles"
                    + " (uuid,last_name,games,wins,losses,draws,kills,final_kills,deaths,final_deaths,"
                    + "beds,total_xp,first_play,last_play,updated_at)"
                    + " VALUES (?,?,1,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
                    + " last_name=IF(VALUES(last_name)='',last_name,VALUES(last_name)),"
                    + " games=games+1,wins=wins+VALUES(wins),losses=losses+VALUES(losses),"
                    + " draws=draws+VALUES(draws),kills=kills+VALUES(kills),"
                    + " final_kills=final_kills+VALUES(final_kills),deaths=deaths+VALUES(deaths),"
                    + " final_deaths=final_deaths+VALUES(final_deaths),"
                    + " beds=beds+VALUES(beds),total_xp=total_xp+VALUES(total_xp),"
                    + " first_play=IF(first_play=0,VALUES(first_play),LEAST(first_play,VALUES(first_play))),"
                    + " last_play=GREATEST(last_play,VALUES(last_play)),"
                    + " updated_at=GREATEST(updated_at,VALUES(updated_at))");
                 PreparedStatement current = connection.prepareStatement(
                    "SELECT total_xp FROM sbw_profiles WHERE uuid=? FOR UPDATE");
                 PreparedStatement unlock = connection.prepareStatement(
                    "INSERT IGNORE INTO sbw_reward_unlocks"
                    + " (player_uuid,reward_id,required_level,unlocked_at)"
                    + " SELECT ?,?,?,? FROM sbw_profiles WHERE uuid=? AND total_xp>=?")) {
                long timestamp = System.currentTimeMillis();
                for (MatchResult r : results) {
                    match.setString(1, r.matchId.toString());
                    match.setString(2, r.playerId.toString());
                    match.setString(3, r.outcome.name());
                    match.setInt(4, r.kills);
                    match.setInt(5, r.finalKills);
                    match.setInt(6, r.deaths);
                    match.setInt(7, r.finalDeaths);
                    match.setInt(8, r.beds);
                    match.setLong(9, r.xp);
                    match.setLong(10, r.completedAtMillis > 0 ? r.completedAtMillis : timestamp);
                    match.setString(11, r.mode);
                    if (match.executeUpdate() == 0) continue; // Already committed by this or another node.
                    profile.setString(1, r.playerId.toString());
                    profile.setString(2, r.name);
                    profile.setInt(3, r.outcome == MatchResult.Outcome.WIN ? 1 : 0);
                    profile.setInt(4, r.outcome == MatchResult.Outcome.LOSS ? 1 : 0);
                    profile.setInt(5, r.outcome == MatchResult.Outcome.DRAW ? 1 : 0);
                    profile.setInt(6, r.kills);
                    profile.setInt(7, r.finalKills);
                    profile.setInt(8, r.deaths);
                    profile.setInt(9, r.finalDeaths);
                    profile.setInt(10, r.beds);
                    profile.setLong(11, r.xp);
                    profile.setLong(12, timestamp);
                    profile.setLong(13, timestamp);
                    profile.setLong(14, timestamp);
                    profile.executeUpdate();
                    current.setString(1, r.playerId.toString());
                    long totalXp;
                    try (ResultSet row = current.executeQuery()) {
                        if (!row.next()) throw new SQLException("Perfil nao foi persistido");
                        totalXp = row.getLong(1);
                    }
                    long previousXp = Math.max(0, totalXp - r.xp);
                    List<String> granted = new ArrayList<String>();
                    for (RewardsCatalog.TitleReward title : rewards.titles()) {
                        if (totalXp < title.requiredTotalXp) break;
                        unlock.setString(1, r.playerId.toString());
                        unlock.setString(2, title.id);
                        unlock.setInt(3, title.level);
                        unlock.setLong(4, timestamp);
                        unlock.setString(5, r.playerId.toString());
                        unlock.setLong(6, title.requiredTotalXp);
                        if (unlock.executeUpdate() == 1) granted.add(title.id);
                    }
                    updates.add(new ProgressionUpdate(r.playerId, r.xp, totalXp,
                        progression.progress(previousXp).level,
                        progression.progress(totalXp).level, granted));
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return updates;
    }

    public void updateName(UUID uuid, String name) throws SQLException {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) return;
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO sbw_profiles (uuid,last_name,first_play,last_play,updated_at)"
                 + " VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE"
                 + " last_name=VALUES(last_name),"
                 + " first_play=IF(first_play=0,VALUES(first_play),LEAST(first_play,VALUES(first_play))),"
                 + " last_play=GREATEST(last_play,VALUES(last_play)),"
                 + " updated_at=GREATEST(updated_at,VALUES(updated_at))")) {
            long now = System.currentTimeMillis();
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.setLong(5, now);
            statement.executeUpdate();
        }
    }

    public PlayerPreferences loadPreferences(UUID uuid, List<String> legacyQuickBuy) throws SQLException {
        try (Connection connection = pool.getConnection()) {
            try (PreparedStatement create = connection.prepareStatement(
                    "INSERT IGNORE INTO sbw_player_preferences (uuid,quick_buy,updated_at) VALUES (?,?,?)")) {
                create.setString(1, uuid.toString());
                create.setString(2, PlayerPreferences.encode(legacyQuickBuy));
                create.setLong(3, System.currentTimeMillis());
                create.executeUpdate();
            }
            if (legacyQuickBuy != null) {
                // A player might have a language-only row from an older node.
                // Never overwrite an explicit Quick Buy list (including an empty one).
                try (PreparedStatement seed = connection.prepareStatement(
                        "UPDATE sbw_player_preferences SET quick_buy=? WHERE uuid=? AND quick_buy IS NULL")) {
                    seed.setString(1, PlayerPreferences.encode(legacyQuickBuy));
                    seed.setString(2, uuid.toString());
                    seed.executeUpdate();
                }
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT language,quick_buy,selected_title,selected_kit,selected_ability"
                    + " FROM sbw_player_preferences WHERE uuid=?")) {
                select.setString(1, uuid.toString());
                try (ResultSet row = select.executeQuery()) {
                    if (row.next()) return new PlayerPreferences(row.getString(1),
                        PlayerPreferences.decode(row.getString(2)), row.getString(3),
                        row.getString(4), row.getString(5));
                }
            }
        }
        return new PlayerPreferences("pt_BR", legacyQuickBuy);
    }

    public void updateLanguage(UUID uuid, String language) throws SQLException {
        if (!PlayerPreferences.supportedLanguage(language)) throw new IllegalArgumentException("Idioma invalido");
        try (Connection connection = pool.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sbw_player_preferences (uuid,language,updated_at) VALUES (?,?,?)"
                + " ON DUPLICATE KEY UPDATE language=VALUES(language),updated_at=VALUES(updated_at)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, PlayerPreferences.languageOrDefault(language));
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public void updateQuickBuy(UUID uuid, List<String> quickBuy) throws SQLException {
        try (Connection connection = pool.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sbw_player_preferences (uuid,quick_buy,updated_at) VALUES (?,?,?)"
                + " ON DUPLICATE KEY UPDATE quick_buy=VALUES(quick_buy),updated_at=VALUES(updated_at)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, PlayerPreferences.encode(quickBuy));
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public void updateGameplaySelection(UUID uuid, PreferencesOutbox.Kind kind, String id)
            throws SQLException {
        if ((kind != PreferencesOutbox.Kind.KIT && kind != PreferencesOutbox.Kind.ABILITY)
                || !PlayerPreferences.validSelectionId(id)) throw new IllegalArgumentException("Selecao invalida");
        String column = kind == PreferencesOutbox.Kind.KIT ? "selected_kit" : "selected_ability";
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO sbw_player_preferences (uuid," + column + ",updated_at) VALUES (?,?,?)"
                 + " ON DUPLICATE KEY UPDATE " + column + "=VALUES(" + column
                 + "),updated_at=GREATEST(updated_at+1,VALUES(updated_at))")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, id);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /** Backfill after configuration changes or older results; safe across Spigot nodes. */
    public Set<String> ensureRewards(UUID uuid, RewardsCatalog rewards) throws SQLException {
        Set<String> unlocked = new LinkedHashSet<String>();
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement profile = connection.prepareStatement(
                    "SELECT total_xp FROM sbw_profiles WHERE uuid=? FOR UPDATE");
                 PreparedStatement grant = connection.prepareStatement(
                    "INSERT IGNORE INTO sbw_reward_unlocks"
                    + " (player_uuid,reward_id,required_level,unlocked_at)"
                    + " SELECT ?,?,?,? FROM sbw_profiles WHERE uuid=? AND total_xp>=?")) {
                profile.setString(1, uuid.toString());
                long xp = 0;
                try (ResultSet row = profile.executeQuery()) { if (row.next()) xp = row.getLong(1); }
                long now = System.currentTimeMillis();
                for (RewardsCatalog.TitleReward title : rewards.titles()) {
                    if (xp < title.requiredTotalXp) break;
                    grant.setString(1, uuid.toString());
                    grant.setString(2, title.id);
                    grant.setInt(3, title.level);
                    grant.setLong(4, now);
                    grant.setString(5, uuid.toString());
                    grant.setLong(6, title.requiredTotalXp);
                    grant.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        }
        try (Connection connection = pool.getConnection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT reward_id FROM sbw_reward_unlocks WHERE player_uuid=?")) {
            query.setString(1, uuid.toString());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) unlocked.add(rows.getString(1));
            }
        }
        return unlocked;
    }

    /** A locked title cannot be selected even if the client invents an item click. */
    public boolean selectTitle(UUID uuid, String titleId) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE sbw_player_preferences SET selected_title=?,updated_at=GREATEST(updated_at+1,?)"
                 + " WHERE uuid=? AND EXISTS (SELECT 1 FROM sbw_reward_unlocks"
                 + " WHERE player_uuid=? AND reward_id=?)")) {
            statement.setString(1, titleId);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, uuid.toString());
            statement.setString(4, uuid.toString());
            statement.setString(5, titleId);
            return statement.executeUpdate() == 1;
        }
    }

    public RankingPage ranking(RankingSpec spec, UUID viewerId) throws SQLException {
        String base = rankingBase(spec);
        List<RankingPage.Entry> entries = new ArrayList<RankingPage.Entry>();
        long total;
        RankingPage.Entry viewer = null;
        try (Connection connection = pool.getConnection()) {
            try (PreparedStatement count = connection.prepareStatement(
                    "SELECT COUNT(*) FROM (" + base + ") ranked")) {
                bindRankingFilters(count, spec);
                count.setQueryTimeout(5);
                try (ResultSet row = count.executeQuery()) { row.next(); total = row.getLong(1); }
            }
            try (PreparedStatement page = connection.prepareStatement(
                    "SELECT uuid,name,global_xp,value FROM (" + base + ") ranked"
                    + " ORDER BY value DESC,uuid ASC LIMIT ? OFFSET ?")) {
                int index = bindRankingFilters(page, spec);
                page.setInt(index++, spec.pageSize);
                page.setLong(index, (long) (spec.page - 1) * spec.pageSize);
                page.setQueryTimeout(5);
                long position = (long) (spec.page - 1) * spec.pageSize + 1;
                try (ResultSet rows = page.executeQuery()) {
                    while (rows.next()) entries.add(readRankingEntry(rows, position++, spec.metric));
                }
            }
            if (viewerId != null) {
                try (PreparedStatement position = connection.prepareStatement(
                        "SELECT uuid,name,global_xp,value,rank_pos FROM (SELECT ranked.*,"
                        + " ROW_NUMBER() OVER (ORDER BY value DESC,uuid ASC) AS rank_pos"
                        + " FROM (" + base + ") ranked) numbered WHERE uuid=?")) {
                    int index = bindRankingFilters(position, spec);
                    position.setString(index, viewerId.toString());
                    position.setQueryTimeout(5);
                    try (ResultSet row = position.executeQuery()) {
                        if (row.next()) viewer = readRankingEntry(row, row.getLong("rank_pos"), spec.metric);
                    }
                }
            }
        }
        return new RankingPage(spec, entries, viewer, total);
    }

    public long globalWinPosition(UUID playerId) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT CASE WHEN target.games=0 THEN 0 ELSE 1+(SELECT COUNT(*)"
                 + " FROM sbw_profiles p WHERE p.games>0 AND (p.wins>target.wins"
                 + " OR (p.wins=target.wins AND p.uuid<target.uuid))) END"
                 + " FROM sbw_profiles target WHERE target.uuid=?")) {
            query.setString(1, playerId.toString());
            query.setQueryTimeout(5);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? row.getLong(1) : 0L;
            }
        }
    }

    public long latestMatchXp(UUID playerId) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT earned_xp FROM sbw_match_results WHERE player_uuid=?"
                 + " ORDER BY created_at DESC,match_id DESC LIMIT 1")) {
            query.setString(1, playerId.toString());
            query.setQueryTimeout(5);
            try (ResultSet row = query.executeQuery()) { return row.next() ? row.getLong(1) : 0L; }
        }
    }

    private RankingPage.Entry readRankingEntry(ResultSet row, long position,
                                               RankingSpec.Metric metric) throws SQLException {
        double value = row.getDouble("value");
        if (metric == RankingSpec.Metric.LEVEL)
            value = progression.progress(Math.max(0L, row.getLong("value"))).level;
        return new RankingPage.Entry(UUID.fromString(row.getString("uuid")),
            row.getString("name"), position, row.getLong("global_xp"), value);
    }

    private static int bindRankingFilters(PreparedStatement statement, RankingSpec spec)
            throws SQLException {
        int index = 1;
        if (!spec.isGlobalAllTime()) {
            if (spec.mode != null) statement.setString(index++, spec.mode);
            if (spec.period != RankingSpec.Period.ALL) {
                statement.setLong(index++, spec.fromMillis);
                statement.setLong(index++, spec.untilMillis);
            }
        }
        return index;
    }

    private static String rankingBase(RankingSpec spec) {
        if (spec.isGlobalAllTime()) return "SELECT p.uuid AS uuid,"
            + " COALESCE(NULLIF(p.last_name,''),p.uuid) AS name,"
            + " p.total_xp AS global_xp," + globalMetric(spec.metric) + " AS value"
            + " FROM sbw_profiles p WHERE p.games>0";
        StringBuilder query = new StringBuilder("SELECT r.player_uuid AS uuid,")
            .append(" COALESCE(NULLIF(MAX(p.last_name),''),r.player_uuid) AS name,")
            .append(" COALESCE(MAX(p.total_xp),0) AS global_xp,")
            .append(scopedMetric(spec.metric)).append(" AS value")
            .append(" FROM sbw_match_results r LEFT JOIN sbw_profiles p ON p.uuid=r.player_uuid")
            .append(" WHERE 1=1");
        if (spec.mode != null) query.append(" AND r.mode=?");
        if (spec.period != RankingSpec.Period.ALL)
            query.append(" AND r.created_at>=? AND r.created_at<?");
        query.append(" GROUP BY r.player_uuid");
        return query.toString();
    }

    private static String globalMetric(RankingSpec.Metric metric) {
        switch (metric) {
            case WINS: return "p.wins";
            case GAMES: return "p.games";
            case KILLS: return "p.kills";
            case FINAL_KILLS: return "p.final_kills";
            case DEATHS: return "p.deaths";
            case FINAL_DEATHS: return "p.final_deaths";
            case BEDS: return "p.beds";
            case XP: case LEVEL: return "p.total_xp";
            case WIN_RATE: return "p.wins / GREATEST(p.games,1)";
            case KD: return "p.kills / GREATEST(p.deaths,1)";
            default: throw new IllegalArgumentException("Metrica invalida");
        }
    }

    private static String scopedMetric(RankingSpec.Metric metric) {
        switch (metric) {
            case WINS: return "SUM(CASE WHEN r.outcome='WIN' THEN 1 ELSE 0 END)";
            case GAMES: return "COUNT(*)";
            case KILLS: return "SUM(r.kills)";
            case FINAL_KILLS: return "SUM(r.final_kills)";
            case DEATHS: return "SUM(r.deaths)";
            case FINAL_DEATHS: return "SUM(r.final_deaths)";
            case BEDS: return "SUM(r.beds)";
            case XP: case LEVEL: return "SUM(r.earned_xp)";
            case WIN_RATE: return "SUM(CASE WHEN r.outcome='WIN' THEN 1 ELSE 0 END) / COUNT(*)";
            case KD: return "SUM(r.kills) / GREATEST(SUM(r.deaths),1)";
            default: throw new IllegalArgumentException("Metrica invalida");
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
                    row.getLong("deaths"), row.getLong("final_deaths"), row.getLong("beds"),
                    row.getLong("total_xp"), row.getLong("first_play"), row.getLong("last_play"));
            }
        }
    }

    private void ensureColumn(Connection connection, String table, String name, String definition)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet existing = metadata.getColumns(connection.getCatalog(), null, table, name)) {
            if (existing.next()) return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + name + " " + definition);
        } catch (SQLException race) {
            // Two Spigot nodes may migrate the same schema simultaneously.
            try (ResultSet existing = metadata.getColumns(connection.getCatalog(), null, table, name)) {
                if (!existing.next()) throw race;
            }
        }
    }

    private void ensureIndex(Connection connection, String table, String name, String columns)
            throws SQLException {
        if (hasIndex(connection, table, name)) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX " + name + " ON " + table + " " + columns);
        } catch (SQLException race) {
            if (!hasIndex(connection, table, name)) throw race;
        }
    }

    private boolean hasIndex(Connection connection, String table, String name) throws SQLException {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) if (name.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return true;
        }
        return false;
    }

    @Override public void close() {
        if (pool != null) { pool.close(); pool = null; }
    }
}
