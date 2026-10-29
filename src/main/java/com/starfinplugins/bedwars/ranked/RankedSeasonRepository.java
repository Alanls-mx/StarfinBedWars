package com.starfinplugins.bedwars.ranked;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** All mutations lock the single network control row before the season row. */
public final class RankedSeasonRepository {
    private final RankedRepository ranked;
    public RankedSeasonRepository(RankedRepository ranked) { this.ranked = ranked; }

    static void ensureSchema(Connection c) throws SQLException {
        try (Statement sql = c.createStatement()) {
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_season_control ("
                + "control_id TINYINT NOT NULL PRIMARY KEY,active_id VARCHAR(32) NULL,"
                + "policy_version BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("INSERT IGNORE INTO sbw_ranked_season_control VALUES(1,NULL,0)");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_seasons ("
                + "season_id VARCHAR(32) NOT NULL PRIMARY KEY,name VARCHAR(64) NOT NULL,"
                + "description VARCHAR(256) NOT NULL,state VARCHAR(16) NOT NULL,"
                + "starts_at BIGINT NOT NULL,ends_at BIGINT NOT NULL,modes VARCHAR(96) NOT NULL,"
                + "initial_rating INT NOT NULL,placement_required INT NOT NULL,"
                + "reset_type VARCHAR(16) NOT NULL,reset_source VARCHAR(32) NULL,"
                + "reset_factor DOUBLE NOT NULL,rewards_enabled TINYINT(1) NOT NULL,"
                + "reward_minimum_games INT NOT NULL,policy_version BIGINT NOT NULL,"
                + "created_at BIGINT NOT NULL,updated_at BIGINT NOT NULL,"
                + "INDEX idx_sbw_season_state(state,starts_at))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_season_divisions ("
                + "season_id VARCHAR(32) NOT NULL,division_id VARCHAR(32) NOT NULL,"
                + "name VARCHAR(32) NOT NULL,color CHAR(2) NOT NULL,"
                + "minimum_rating INT NOT NULL,display_order INT NOT NULL,"
                + "PRIMARY KEY(season_id,division_id),"
                + "INDEX idx_sbw_season_division_rating(season_id,minimum_rating))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_season_snapshots ("
                + "season_id VARCHAR(32) NOT NULL,mode VARCHAR(16) NOT NULL,"
                + "player_uuid CHAR(36) NOT NULL,position INT NOT NULL,"
                + "rating INT NOT NULL,peak_rating INT NOT NULL,"
                + "division_id VARCHAR(32) NOT NULL,peak_division_id VARCHAR(32) NOT NULL,"
                + "games INT NOT NULL,wins INT NOT NULL,losses INT NOT NULL,draws INT NOT NULL,"
                + "placement_games INT NOT NULL,eligible TINYINT(1) NOT NULL,"
                + "finalized_at BIGINT NOT NULL,PRIMARY KEY(season_id,mode,player_uuid),"
                + "INDEX idx_sbw_season_rank(season_id,mode,position),"
                + "INDEX idx_sbw_season_player(player_uuid,season_id))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_season_rewards ("
                + "season_id VARCHAR(32) NOT NULL,reward_id VARCHAR(32) NOT NULL,"
                + "mode VARCHAR(16) NULL,title_pt VARCHAR(24) NOT NULL,"
                + "title_en VARCHAR(24) NOT NULL,title_es VARCHAR(24) NOT NULL,"
                + "minimum_rating INT NOT NULL,maximum_position INT NOT NULL,"
                + "minimum_games INT NOT NULL,source_rule VARCHAR(8) NOT NULL,"
                + "PRIMARY KEY(season_id,reward_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_reward_grants ("
                + "player_uuid CHAR(36) NOT NULL,season_id VARCHAR(32) NOT NULL,"
                + "mode VARCHAR(16) NOT NULL,reward_id VARCHAR(32) NOT NULL,"
                + "state VARCHAR(16) NOT NULL,created_at BIGINT NOT NULL,claimed_at BIGINT NOT NULL,"
                + "PRIMARY KEY(player_uuid,season_id,mode,reward_id),"
                + "INDEX idx_sbw_season_grants(season_id,state))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_ranked_season_operations ("
                + "operation_id CHAR(36) NOT NULL PRIMARY KEY,season_id VARCHAR(32) NOT NULL,"
                + "action VARCHAR(24) NOT NULL,actor VARCHAR(64) NOT NULL,"
                + "created_at BIGINT NOT NULL,details VARCHAR(256) NOT NULL,"
                + "INDEX idx_sbw_season_operation(season_id,created_at))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
        ensureColumn(c, "sbw_ranked_policy", "season_version", "BIGINT NOT NULL DEFAULT 0");
        ensureColumn(c, "sbw_ranked_policy", "pending_results", "INT NOT NULL DEFAULT 0");
        ensureColumn(c, "sbw_ranked_policy", "active_matches", "INT NOT NULL DEFAULT 0");
        try (PreparedStatement mark = c.prepareStatement(
                "INSERT IGNORE INTO sbw_ranked_schema_migrations(migration_id,applied_at) VALUES(63,?)")) {
            mark.setLong(1, System.currentTimeMillis()); mark.executeUpdate();
        }
    }
    private static void ensureColumn(Connection c, String table, String name, String definition)
            throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet columns = meta.getColumns(c.getCatalog(), null, table, name)) {
            if (columns.next()) return;
        }
        try (Statement sql = c.createStatement()) {
            sql.execute("ALTER TABLE " + table + " ADD COLUMN " + name + " " + definition);
        } catch (SQLException concurrent) {
            try (ResultSet columns = meta.getColumns(c.getCatalog(), null, table, name)) {
                if (!columns.next()) throw concurrent;
            }
        }
    }

    public RankedSeason active() throws SQLException {
        try (Connection c = ranked.connection(); PreparedStatement query = c.prepareStatement(
                "SELECT s.* FROM sbw_ranked_season_control control"
                + " JOIN sbw_ranked_seasons s ON s.season_id=control.active_id"
                + " WHERE control.control_id=1")) {
            try (ResultSet row = query.executeQuery()) { return row.next() ? read(row) : null; }
        }
    }
    public long controlVersion() throws SQLException {
        try (Connection c = ranked.connection(); PreparedStatement query = c.prepareStatement(
                "SELECT policy_version FROM sbw_ranked_season_control WHERE control_id=1");
             ResultSet row = query.executeQuery()) {
            if (!row.next()) throw new SQLException("Controle sazonal ausente");
            return row.getLong(1);
        }
    }
    public RankedSeason find(String id) throws SQLException {
        try (Connection c = ranked.connection(); PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_ranked_seasons WHERE season_id=?")) {
            query.setString(1, id);
            try (ResultSet row = query.executeQuery()) { return row.next() ? read(row) : null; }
        }
    }
    public List<RankedSeason> list(int limit, int offset) throws SQLException {
        List<RankedSeason> result = new ArrayList<RankedSeason>();
        try (Connection c = ranked.connection(); PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_ranked_seasons ORDER BY starts_at DESC,season_id LIMIT ? OFFSET ?")) {
            query.setInt(1, limit); query.setInt(2, offset);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) result.add(read(row));
            }
        }
        return result;
    }

    /** The file is a proposal only; creating a season never enables it. */
    public RankedSeason create(RankedSeasonSettings proposal, RankedSettings rules, String actor)
            throws SQLException {
        require(rules.modes.containsAll(proposal.modes), "Modalidade sazonal desabilitada em ranked.yml");
        try (Connection c = ranked.connection()) {
            c.setAutoCommit(false);
            try {
                lockControl(c);
                long now = System.currentTimeMillis();
                try (PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO sbw_ranked_seasons(season_id,name,description,state,starts_at,ends_at,"
                        + "modes,initial_rating,placement_required,reset_type,reset_source,reset_factor,"
                        + "rewards_enabled,reward_minimum_games,policy_version,created_at,updated_at)"
                        + " VALUES(?,?,?,'DRAFT',?,?,?,?,?,?,?,?,?,?,0,?,?)")) {
                    insert.setString(1, proposal.id); insert.setString(2, proposal.name);
                    insert.setString(3, proposal.description); insert.setLong(4, proposal.startsAt);
                    insert.setLong(5, proposal.endsAt);
                    insert.setString(6, String.join(",", new java.util.TreeSet<String>(proposal.modes)));
                    insert.setInt(7, rules.initial); insert.setInt(8, proposal.placementRequired);
                    insert.setString(9, proposal.resetType);
                    insert.setString(10, proposal.sourceSeason.isEmpty() ? null : proposal.sourceSeason);
                    insert.setDouble(11, proposal.resetFactor);
                    insert.setBoolean(12, proposal.rewardsEnabled);
                    insert.setInt(13, proposal.rewardMinimumGames);
                    insert.setLong(14, now); insert.setLong(15, now); insert.executeUpdate();
                }
                for (RankedSettings.Division division : rules.divisions) {
                    try (PreparedStatement add = c.prepareStatement(
                            "INSERT INTO sbw_ranked_season_divisions VALUES(?,?,?,?,?,?)")) {
                        add.setString(1, proposal.id); add.setString(2, division.id);
                        add.setString(3, division.name); add.setString(4, division.color);
                        add.setInt(5, division.minimum); add.setInt(6, division.order);
                        add.executeUpdate();
                    }
                }
                for (RankedSeasonSettings.Title title : proposal.titles) {
                    try (PreparedStatement duplicate = c.prepareStatement(
                            "SELECT 1 FROM sbw_ranked_season_rewards WHERE reward_id=? LIMIT 1")) {
                        duplicate.setString(1, title.id);
                        try (ResultSet row = duplicate.executeQuery()) {
                            require(!row.next(), "ID de titulo sazonal ja utilizado: " + title.id);
                        }
                    }
                    try (PreparedStatement insert = c.prepareStatement(
                            "INSERT INTO sbw_ranked_season_rewards VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                        insert.setString(1, proposal.id); insert.setString(2, title.id);
                        insert.setString(3, title.mode.isEmpty() ? null : title.mode);
                        insert.setString(4, title.pt); insert.setString(5, title.en);
                        insert.setString(6, title.es); insert.setInt(7, title.minimumRating);
                        insert.setInt(8, title.maximumPosition); insert.setInt(9, title.minimumGames);
                        insert.setString(10, title.sourceRule); insert.executeUpdate();
                    }
                }
                operation(c, proposal.id, "CREATE", actor, "Proposta local validada");
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
        return find(proposal.id);
    }

    public RankedSeason transition(String id, RankedSeason.State target, String actor)
            throws SQLException {
        try (Connection c = ranked.connection()) {
            c.setAutoCommit(false);
            try {
                Control control = lockControl(c);
                RankedSeason source = lockSeason(c, id);
                if (source == null) throw new IllegalStateException("Temporada inexistente");
                if (source.state == target) { c.commit(); return source; }
                long now = System.currentTimeMillis();
                if (target == RankedSeason.State.SCHEDULED) {
                    require(source.state == RankedSeason.State.DRAFT && source.startsAt > now,
                        "Agendamento exige DRAFT e inicio futuro");
                    if ("SOFT_RESET".equals(source.resetType)) {
                        RankedSeason previous = lockSeason(c, source.resetSource);
                        require(previous != null && previous.state == RankedSeason.State.FINISHED,
                            "SOFT_RESET exige temporada anterior finalizada");
                    }
                } else if (target == RankedSeason.State.ACTIVE) {
                    require(source.state == RankedSeason.State.SCHEDULED,
                        "Ativacao exige SCHEDULED");
                    require(control.activeId == null && now >= source.startsAt && now < source.endsAt,
                        "Existe temporada ativa ou data fora da janela");
                    require(noCompetingAllocations(c), "Ainda ha alocacoes da politica anterior");
                    require(backendsDrained(c), "Backends ausentes ou com resultados/partidas pendentes");
                    updateControl(c, id, control.version + 1);
                } else if (target == RankedSeason.State.CLOSING) {
                    require(source.state == RankedSeason.State.ACTIVE && id.equals(control.activeId),
                        "Encerramento exige temporada ativa");
                    updateControl(c, id, control.version + 1);
                } else if (target == RankedSeason.State.CANCELLED) {
                    require(source.state == RankedSeason.State.DRAFT
                        || source.state == RankedSeason.State.SCHEDULED,
                        "Somente DRAFT ou SCHEDULED podem ser canceladas");
                } else if (target == RankedSeason.State.FINALIZING) {
                    require(source.state == RankedSeason.State.CLOSING && id.equals(control.activeId),
                        "Finalizacao exige CLOSING");
                    require(noCompetingAllocations(c), "Alocacoes ainda pendentes");
                    require(backendsConsistent(c, id, control.version),
                        "Backend ausente, divergente ou com resultados/partidas pendentes");
                    updateControl(c, id, control.version + 1);
                } else throw new IllegalStateException("Transicao invalida");
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_ranked_seasons SET state=?,policy_version=?,updated_at=?"
                        + " WHERE season_id=?")) {
                    update.setString(1, target.name());
                    update.setLong(2, target == RankedSeason.State.ACTIVE
                        || target == RankedSeason.State.CLOSING
                        || target == RankedSeason.State.FINALIZING ? control.version + 1 : source.version);
                    update.setLong(3, now); update.setString(4, id); update.executeUpdate();
                }
                operation(c, id, target.name(), actor, "Transicao confirmada");
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
        return find(id);
    }

    /** Called only after all backend journals and matches have drained. Safe to retry. */
    public RankedSeason finalizeSeason(String id, String actor) throws SQLException {
        try (Connection c = ranked.connection()) {
            c.setAutoCommit(false);
            try {
                Control control = lockControl(c);
                RankedSeason season = lockSeason(c, id);
                if (season == null) throw new IllegalStateException("Temporada inexistente");
                if (season.state == RankedSeason.State.FINISHED) { c.commit(); return season; }
                require(season.state == RankedSeason.State.FINALIZING && id.equals(control.activeId),
                    "Temporada nao esta em FINALIZING");
                require(noCompetingAllocations(c), "Alocacoes ainda pendentes");
                require(backendsConsistent(c, id, control.version),
                    "Backend ausente, divergente ou com resultados/partidas pendentes");
                long now = System.currentTimeMillis();
                try (PreparedStatement snapshot = c.prepareStatement(
                        "INSERT INTO sbw_ranked_season_snapshots(season_id,mode,player_uuid,position,"
                        + "rating,peak_rating,division_id,peak_division_id,games,wins,losses,draws,"
                        + "placement_games,eligible,finalized_at)"
                        + " SELECT ?,r.mode,r.player_uuid,r.position,r.rating,r.peak_rating,"
                        + "(SELECT d.division_id FROM sbw_ranked_season_divisions d"
                        + " WHERE d.season_id=? AND d.minimum_rating<=r.rating"
                        + " ORDER BY d.minimum_rating DESC LIMIT 1),"
                        + "(SELECT d.division_id FROM sbw_ranked_season_divisions d"
                        + " WHERE d.season_id=? AND d.minimum_rating<=r.peak_rating"
                        + " ORDER BY d.minimum_rating DESC LIMIT 1),"
                        + "r.games,r.wins,r.losses,r.draws,r.placement_games,1,? FROM"
                        + " (SELECT p.*,ROW_NUMBER() OVER (PARTITION BY mode ORDER BY rating DESC,"
                        + "peak_rating DESC,wins DESC,(wins/GREATEST(games,1)) DESC,games DESC,"
                        + "player_uuid ASC) AS position FROM sbw_ranked_profiles p"
                        + " WHERE p.context=? AND p.placement_games>=?) r")) {
                    snapshot.setString(1, id); snapshot.setString(2, id); snapshot.setString(3, id);
                    snapshot.setLong(4, now); snapshot.setString(5, id);
                    snapshot.setInt(6, season.placementRequired); snapshot.executeUpdate();
                }
                try (PreparedStatement unplaced = c.prepareStatement(
                        "INSERT INTO sbw_ranked_season_snapshots(season_id,mode,player_uuid,position,"
                        + "rating,peak_rating,division_id,peak_division_id,games,wins,losses,draws,"
                        + "placement_games,eligible,finalized_at) SELECT ?,p.mode,p.player_uuid,0,"
                        + "p.rating,p.peak_rating,"
                        + "(SELECT d.division_id FROM sbw_ranked_season_divisions d"
                        + " WHERE d.season_id=? AND d.minimum_rating<=p.rating"
                        + " ORDER BY d.minimum_rating DESC LIMIT 1),"
                        + "(SELECT d.division_id FROM sbw_ranked_season_divisions d"
                        + " WHERE d.season_id=? AND d.minimum_rating<=p.peak_rating"
                        + " ORDER BY d.minimum_rating DESC LIMIT 1),"
                        + "p.games,p.wins,p.losses,p.draws,p.placement_games,0,?"
                        + " FROM sbw_ranked_profiles p WHERE p.context=? AND p.placement_games<?")) {
                    unplaced.setString(1, id); unplaced.setString(2, id); unplaced.setString(3, id);
                    unplaced.setLong(4, now); unplaced.setString(5, id);
                    unplaced.setInt(6, season.placementRequired); unplaced.executeUpdate();
                }
                try (PreparedStatement rewards = c.prepareStatement(
                        "INSERT IGNORE INTO sbw_ranked_reward_grants(player_uuid,season_id,mode,reward_id,"
                        + "state,created_at,claimed_at)"
                        + " SELECT s.player_uuid,s.season_id,s.mode,r.reward_id,'CLAIMABLE',?,0"
                        + " FROM sbw_ranked_season_snapshots s JOIN sbw_ranked_season_rewards r"
                        + " ON r.season_id=s.season_id AND (r.mode IS NULL OR r.mode=s.mode)"
                        + " JOIN sbw_ranked_seasons season ON season.season_id=s.season_id"
                        + " WHERE s.season_id=? AND season.rewards_enabled=1 AND s.eligible=1"
                        + " AND s.games>=GREATEST(r.minimum_games,season.reward_minimum_games)"
                        + " AND (CASE r.source_rule WHEN 'PEAK' THEN s.peak_rating"
                        + " ELSE s.rating END)>=r.minimum_rating AND"
                        + " (r.maximum_position=0 OR s.position<=r.maximum_position)")) {
                    rewards.setLong(1, now); rewards.setString(2, id); rewards.executeUpdate();
                }
                updateControl(c, null, control.version + 1);
                try (PreparedStatement finish = c.prepareStatement(
                        "UPDATE sbw_ranked_seasons SET state='FINISHED',policy_version=?,updated_at=?"
                        + " WHERE season_id=?")) {
                    finish.setLong(1, control.version + 1); finish.setLong(2, now);
                    finish.setString(3, id); finish.executeUpdate();
                }
                operation(c, id, "FINISH", actor, "Snapshot e grants confirmados");
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
        return find(id);
    }

    public void tick(RankedSeasonSettings settings) throws SQLException {
        if (!settings.enabled) return;
        RankedSeason current = active();
        long now = System.currentTimeMillis();
        if (current != null && current.state == RankedSeason.State.ACTIVE && now >= current.endsAt) {
            transition(current.id, RankedSeason.State.CLOSING, "scheduler"); return;
        }
        if (current != null) return;
        try (Connection c = ranked.connection(); PreparedStatement query = c.prepareStatement(
                "SELECT season_id FROM sbw_ranked_seasons WHERE state='SCHEDULED'"
                + " AND starts_at<=? AND ends_at>? ORDER BY starts_at,season_id LIMIT 1")) {
            query.setLong(1, now); query.setLong(2, now);
            try (ResultSet row = query.executeQuery()) {
                if (row.next()) transition(row.getString(1), RankedSeason.State.ACTIVE, "scheduler");
            }
        }
    }

    public List<RankedStanding> leaderboard(String seasonId, String mode, int limit, int page)
            throws SQLException {
        return leaderboard(seasonId, mode, null, limit, page);
    }
    public List<RankedStanding> leaderboard(String seasonId, String mode, String divisionId,
            int limit, int page) throws SQLException {
        RankedSeason season = find(seasonId);
        if (season == null || !season.modes.contains(mode)
                || season.state == RankedSeason.State.DRAFT
                || season.state == RankedSeason.State.SCHEDULED
                || season.state == RankedSeason.State.CANCELLED)
            return java.util.Collections.emptyList();
        int count = Math.max(1, Math.min(100, limit));
        int offset = Math.max(0, page - 1) * count;
        String base = season.state == RankedSeason.State.FINISHED
            ? "SELECT s.player_uuid,s.mode,s.position,s.rating,s.peak_rating,s.division_id,"
                + "s.peak_division_id,s.games,s.wins,s.losses,s.draws,s.placement_games"
                + " FROM sbw_ranked_season_snapshots s WHERE s.season_id=? AND s.mode=?"
                + " AND s.eligible=1"
            : "SELECT p.player_uuid,p.mode,ROW_NUMBER() OVER (ORDER BY p.rating DESC,"
                + "p.peak_rating DESC,p.wins DESC,(p.wins/GREATEST(p.games,1)) DESC,"
                + "p.games DESC,p.player_uuid ASC) AS position,p.rating,p.peak_rating,"
                + "(SELECT d.division_id FROM sbw_ranked_season_divisions d WHERE d.season_id=?"
                + " AND d.minimum_rating<=p.rating ORDER BY d.minimum_rating DESC LIMIT 1) AS division_id,"
                + "(SELECT d.division_id FROM sbw_ranked_season_divisions d WHERE d.season_id=?"
                + " AND d.minimum_rating<=p.peak_rating ORDER BY d.minimum_rating DESC LIMIT 1)"
                + " AS peak_division_id,p.games,p.wins,p.losses,p.draws,p.placement_games"
                + " FROM sbw_ranked_profiles p WHERE p.context=? AND p.mode=?"
                + " AND p.placement_games>=?";
        List<RankedStanding> results = new ArrayList<RankedStanding>();
        try (Connection c = ranked.connection(); PreparedStatement query = c.prepareStatement(
                "SELECT ranked.*,COALESCE(player.last_name,ranked.player_uuid) AS player_name"
                + " FROM (" + base + ") ranked LEFT JOIN sbw_profiles player"
                + " ON player.uuid=ranked.player_uuid"
                + " WHERE (?='' OR ranked.division_id=?)"
                + " ORDER BY ranked.position LIMIT ? OFFSET ?")) {
            int i = 1;
            if (season.state != RankedSeason.State.FINISHED) {
                query.setString(i++, seasonId); query.setString(i++, seasonId);
            }
            query.setString(i++, seasonId); query.setString(i++, mode);
            if (season.state != RankedSeason.State.FINISHED)
                query.setInt(i++, season.placementRequired);
            query.setString(i++, divisionId == null ? "" : divisionId);
            query.setString(i++, divisionId == null ? "" : divisionId);
            query.setInt(i++, count); query.setInt(i, offset);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) results.add(standing(seasonId, row));
            }
        }
        return java.util.Collections.unmodifiableList(results);
    }
    public RankedStanding position(UUID player, String seasonId, String mode) throws SQLException {
        RankedSeason season = find(seasonId);
        if (season == null || !season.modes.contains(mode)
                || season.state == RankedSeason.State.DRAFT
                || season.state == RankedSeason.State.SCHEDULED
                || season.state == RankedSeason.State.CANCELLED) return null;
        if (season.state == RankedSeason.State.FINISHED) {
            try (Connection c = ranked.connection(); PreparedStatement query = c.prepareStatement(
                    "SELECT s.*,COALESCE(p.last_name,s.player_uuid) AS player_name"
                    + " FROM sbw_ranked_season_snapshots s LEFT JOIN sbw_profiles p"
                    + " ON p.uuid=s.player_uuid WHERE s.season_id=? AND s.mode=?"
                    + " AND s.player_uuid=? AND s.eligible=1")) {
                query.setString(1, seasonId); query.setString(2, mode);
                query.setString(3, player.toString());
                try (ResultSet row = query.executeQuery()) {
                    return row.next() ? standing(seasonId, row) : null;
                }
            }
        }
        RankedProfile profile = ranked.profile(player, mode, seasonId, season.initialRating);
        if (profile.placementGames < season.placementRequired) return null;
        // Ranking is calculated by SQL; no complete profile list enters JVM memory.
        String order = "q.rating>p.rating OR (q.rating=p.rating AND q.peak_rating>p.peak_rating)"
            + " OR (q.rating=p.rating AND q.peak_rating=p.peak_rating AND q.wins>p.wins)"
            + " OR (q.rating=p.rating AND q.peak_rating=p.peak_rating AND q.wins=p.wins"
            + " AND q.wins/GREATEST(q.games,1)>p.wins/GREATEST(p.games,1))"
            + " OR (q.rating=p.rating AND q.peak_rating=p.peak_rating AND q.wins=p.wins"
            + " AND q.wins/GREATEST(q.games,1)=p.wins/GREATEST(p.games,1)"
            + " AND q.games>p.games) OR (q.rating=p.rating AND q.peak_rating=p.peak_rating"
            + " AND q.wins=p.wins AND q.wins/GREATEST(q.games,1)="
            + "p.wins/GREATEST(p.games,1) AND q.games=p.games AND q.player_uuid<p.player_uuid)";
        try (Connection c = ranked.connection(); PreparedStatement query = c.prepareStatement(
                "SELECT p.player_uuid,p.mode,1+(SELECT COUNT(*) FROM sbw_ranked_profiles q"
                + " WHERE q.context=p.context AND q.mode=p.mode AND q.placement_games>=?"
                + " AND (" + order + ")) AS position,p.rating,p.peak_rating,"
                + "(SELECT d.division_id FROM sbw_ranked_season_divisions d"
                + " WHERE d.season_id=? AND d.minimum_rating<=p.rating"
                + " ORDER BY d.minimum_rating DESC LIMIT 1) AS division_id,"
                + "(SELECT d.division_id FROM sbw_ranked_season_divisions d"
                + " WHERE d.season_id=? AND d.minimum_rating<=p.peak_rating"
                + " ORDER BY d.minimum_rating DESC LIMIT 1) AS peak_division_id,"
                + "p.games,p.wins,p.losses,p.draws,p.placement_games,"
                + "COALESCE(player.last_name,p.player_uuid) AS player_name"
                + " FROM sbw_ranked_profiles p LEFT JOIN sbw_profiles player"
                + " ON player.uuid=p.player_uuid WHERE p.context=? AND p.mode=? AND p.player_uuid=?")) {
            query.setInt(1, season.placementRequired); query.setString(2, seasonId);
            query.setString(3, seasonId); query.setString(4, seasonId);
            query.setString(5, mode); query.setString(6, player.toString());
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? standing(seasonId, row) : null;
            }
        }
    }
    public List<RankedStanding> history(UUID player, int limit, int page) throws SQLException {
        List<RankedStanding> found = new ArrayList<RankedStanding>();
        try (Connection c = ranked.connection(); PreparedStatement query = c.prepareStatement(
                "SELECT s.*,COALESCE(p.last_name,s.player_uuid) AS player_name"
                + " FROM sbw_ranked_season_snapshots s JOIN sbw_ranked_seasons season"
                + " ON season.season_id=s.season_id LEFT JOIN sbw_profiles p"
                + " ON p.uuid=s.player_uuid WHERE s.player_uuid=? AND season.state='FINISHED'"
                + " ORDER BY season.ends_at DESC,s.mode LIMIT ? OFFSET ?")) {
            query.setString(1, player.toString()); query.setInt(2, Math.min(50, Math.max(1, limit)));
            query.setInt(3, Math.max(0, page - 1) * Math.min(50, Math.max(1, limit)));
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) found.add(standing(row.getString("season_id"), row));
            }
        }
        return java.util.Collections.unmodifiableList(found);
    }
    private static RankedStanding standing(String seasonId, ResultSet row) throws SQLException {
        return new RankedStanding(UUID.fromString(row.getString("player_uuid")),
            row.getString("player_name"), seasonId, row.getString("mode"),
            row.getString("division_id"), row.getString("peak_division_id"),
            row.getLong("position"), row.getInt("rating"), row.getInt("peak_rating"),
            row.getInt("games"), row.getInt("wins"), row.getInt("losses"),
            row.getInt("draws"), row.getInt("placement_games"));
    }

    public List<RankedSeasonReward> rewards(UUID player, String seasonId) throws SQLException {
        List<RankedSeasonReward> found = new ArrayList<RankedSeasonReward>();
        try (Connection c = ranked.connection(); PreparedStatement query = c.prepareStatement(
                "SELECT r.*,COALESCE(g.state,'LOCKED') AS claim_state,"
                + "COALESCE(g.mode,r.mode) AS grant_mode FROM sbw_ranked_season_rewards r"
                + " LEFT JOIN sbw_ranked_reward_grants g ON g.season_id=r.season_id"
                + " AND g.reward_id=r.reward_id AND g.player_uuid=?"
                + " WHERE r.season_id=? ORDER BY r.reward_id,g.mode")) {
            query.setString(1, player.toString()); query.setString(2, seasonId);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) found.add(new RankedSeasonReward(seasonId,
                    row.getString("reward_id"), row.getString("grant_mode"),
                    row.getString("title_pt"), row.getString("title_en"), row.getString("title_es"),
                    row.getString("claim_state"), row.getInt("minimum_rating"),
                    row.getInt("maximum_position"), row.getInt("minimum_games")));
            }
        }
        return java.util.Collections.unmodifiableList(found);
    }
    public boolean claim(UUID player, String seasonId, String mode, String rewardId) throws SQLException {
        try (Connection c = ranked.connection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement season = c.prepareStatement(
                        "SELECT state FROM sbw_ranked_seasons WHERE season_id=?")) {
                    season.setString(1, seasonId);
                    try (ResultSet row = season.executeQuery()) {
                        require(row.next() && "FINISHED".equals(row.getString(1)),
                            "Recompensas ainda nao publicadas");
                    }
                }
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_ranked_reward_grants SET state='CLAIMED',claimed_at=?"
                        + " WHERE player_uuid=? AND season_id=? AND mode=? AND reward_id=?"
                        + " AND state='CLAIMABLE'")) {
                    update.setLong(1, System.currentTimeMillis()); update.setString(2, player.toString());
                    update.setString(3, seasonId); update.setString(4, mode);
                    update.setString(5, rewardId);
                    int changed = update.executeUpdate();
                    if (changed == 1) operation(c, seasonId, "CLAIM", player.toString(), rewardId + "/" + mode);
                    c.commit(); return changed == 1;
                }
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }
    public List<String> diagnostics(int closingGraceMinutes) throws SQLException {
        List<String> lines = new ArrayList<String>();
        RankedSeason current = active();
        lines.add("Temporada: " + (current == null ? "nenhuma" : current.id + " / " + current.state));
        if (current != null && current.state == RankedSeason.State.CLOSING) {
            try (Connection c = ranked.connection(); PreparedStatement query = c.prepareStatement(
                    "SELECT updated_at FROM sbw_ranked_seasons WHERE season_id=?")) {
                query.setString(1, current.id);
                try (ResultSet row = query.executeQuery()) {
                    if (row.next()) {
                        long elapsed = Math.max(0L, System.currentTimeMillis() - row.getLong(1)) / 60000L;
                        lines.add("Fechamento: " + elapsed + " min / janela operacional: "
                            + closingGraceMinutes + " min"
                            + (elapsed >= closingGraceMinutes ? " | INVESTIGAR PENDENCIAS" : ""));
                    }
                }
            }
        }
        try (Connection c = ranked.connection(); Statement query = c.createStatement();
             ResultSet row = query.executeQuery(
                 "SELECT COUNT(*),COALESCE(SUM(pending_results),0),"
                 + "COALESCE(SUM(active_matches),0),COALESCE(MIN(updated_at),0)"
                 + " FROM sbw_ranked_policy")) {
            row.next(); lines.add("Backends: " + row.getInt(1) + " | resultados pendentes: "
                + row.getInt(2) + " | partidas ativas: " + row.getInt(3)
                + " | heartbeat mais antigo: " + row.getLong(4));
        }
        try (Connection c = ranked.connection(); Statement query = c.createStatement();
             ResultSet row = query.executeQuery(
                 "SELECT COUNT(*) FROM sm_ranked_allocations WHERE state IN"
                 + " ('RESERVED','TRANSFERRING','ADMITTED') AND expires_at>UNIX_TIMESTAMP()*1000")) {
            row.next(); lines.add("Alocacoes em formacao: " + row.getInt(1));
        }
        return lines;
    }

    public List<String> auditRewards(String playerNameOrUuid) throws SQLException {
        UUID player = ranked.resolvePlayer(playerNameOrUuid);
        if (player == null) return java.util.Collections.singletonList("Jogador nao encontrado.");
        List<String> lines = new ArrayList<String>();
        lines.add("Recompensas de " + player + ":");
        try (Connection c = ranked.connection(); PreparedStatement query = c.prepareStatement(
                "SELECT season_id,mode,reward_id,state,created_at,claimed_at"
                + " FROM sbw_ranked_reward_grants WHERE player_uuid=?"
                + " ORDER BY created_at DESC,season_id,mode,reward_id LIMIT 50")) {
            query.setString(1, player.toString());
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) lines.add(row.getString(1) + "/" + row.getString(2)
                    + " " + row.getString(3) + " " + row.getString(4)
                    + " grant=" + row.getLong(5) + " claim=" + row.getLong(6));
            }
        }
        if (lines.size() == 1) lines.add("Nenhuma concessao sazonal.");
        return lines;
    }

    private static Control lockControl(Connection c) throws SQLException {
        try (PreparedStatement lock = c.prepareStatement(
                "SELECT active_id,policy_version FROM sbw_ranked_season_control WHERE control_id=1 FOR UPDATE");
             ResultSet row = lock.executeQuery()) {
            if (!row.next()) throw new SQLException("Controle de temporada ausente");
            return new Control(row.getString(1), row.getLong(2));
        }
    }
    private static RankedSeason lockSeason(Connection c, String id) throws SQLException {
        if (id == null) return null;
        try (PreparedStatement lock = c.prepareStatement(
                "SELECT * FROM sbw_ranked_seasons WHERE season_id=? FOR UPDATE")) {
            lock.setString(1, id);
            try (ResultSet row = lock.executeQuery()) { return row.next() ? read(row) : null; }
        }
    }
    static RankedSeason lockContext(Connection c, String id) throws SQLException {
        return lockSeason(c, id);
    }
    static int startingRating(Connection c, RankedSeason season, UUID player, String mode)
            throws SQLException {
        if (!"SOFT_RESET".equals(season.resetType)) return season.initialRating;
        try (PreparedStatement source = c.prepareStatement(
                "SELECT rating FROM sbw_ranked_season_snapshots"
                + " WHERE season_id=? AND mode=? AND player_uuid=?")) {
            source.setString(1, season.resetSource); source.setString(2, mode);
            source.setString(3, player.toString());
            try (ResultSet row = source.executeQuery()) {
                if (!row.next()) return season.initialRating;
                return Math.max(0, (int) Math.round(season.initialRating
                    + (row.getInt(1) - season.initialRating) * season.resetFactor));
            }
        }
    }
    static RankedSettings.Division division(Connection c, String seasonId, int rating,
            RankedSettings fallback) throws SQLException {
        try (PreparedStatement query = c.prepareStatement(
                "SELECT division_id,name,color,minimum_rating,display_order"
                + " FROM sbw_ranked_season_divisions WHERE season_id=? AND minimum_rating<=?"
                + " ORDER BY minimum_rating DESC LIMIT 1")) {
            query.setString(1, seasonId); query.setInt(2, rating);
            try (ResultSet row = query.executeQuery()) {
                if (row.next()) return new RankedSettings.Division(row.getString(1),
                    row.getString(2), row.getString(3), "PAPER", row.getInt(4), row.getInt(5));
            }
        }
        return fallback.division(rating);
    }
    public RankedSettings.Division division(String seasonId, int rating, RankedSettings fallback)
            throws SQLException {
        try (Connection c = ranked.connection()) { return division(c, seasonId, rating, fallback); }
    }
    public RankedDivisionProgress progress(String context, int rating, RankedSettings fallback)
            throws SQLException {
        try (Connection c = ranked.connection()) {
            RankedSettings.Division current = division(c, context, rating, fallback);
            try (PreparedStatement next = c.prepareStatement(
                    "SELECT name,minimum_rating FROM sbw_ranked_season_divisions"
                    + " WHERE season_id=? AND minimum_rating>? ORDER BY minimum_rating LIMIT 1")) {
                next.setString(1, context); next.setInt(2, rating);
                try (ResultSet row = next.executeQuery()) {
                    if (row.next()) return new RankedDivisionProgress(current.name,
                        row.getString(1), row.getInt(2) - rating);
                }
            }
            try (PreparedStatement exists = c.prepareStatement(
                    "SELECT 1 FROM sbw_ranked_seasons WHERE season_id=?")) {
                exists.setString(1, context);
                try (ResultSet row = exists.executeQuery()) {
                    if (!row.next()) for (RankedSettings.Division division : fallback.divisions)
                        if (division.minimum > rating)
                            return new RankedDivisionProgress(current.name, division.name,
                                division.minimum - rating);
                }
            }
            return new RankedDivisionProgress(current.name, "-", 0);
        }
    }
    private static void updateControl(Connection c, String id, long version) throws SQLException {
        try (PreparedStatement update = c.prepareStatement(
                "UPDATE sbw_ranked_season_control SET active_id=?,policy_version=? WHERE control_id=1")) {
            update.setString(1, id); update.setLong(2, version); update.executeUpdate();
        }
    }
    private static boolean noCompetingAllocations(Connection c) throws SQLException {
        try (PreparedStatement query = c.prepareStatement(
                "SELECT COUNT(*) FROM sm_ranked_allocations WHERE state IN"
                + " ('RESERVED','TRANSFERRING','ADMITTED') AND expires_at>?")) {
            query.setLong(1, System.currentTimeMillis());
            try (ResultSet row = query.executeQuery()) { row.next(); return row.getInt(1) == 0; }
        }
    }
    private static boolean backendsDrained(Connection c) throws SQLException {
        try (PreparedStatement query = c.prepareStatement(
                "SELECT COUNT(*),COALESCE(SUM(pending_results),0),COALESCE(SUM(active_matches),0),"
                + "COALESCE(MIN(updated_at),0) FROM sbw_ranked_policy")) {
            try (ResultSet row = query.executeQuery()) {
                row.next(); return row.getInt(1) > 0 && row.getInt(2) == 0 && row.getInt(3) == 0
                    && row.getLong(4) > System.currentTimeMillis() - 120000L;
            }
        }
    }
    private static boolean backendsConsistent(Connection c, String context, long version)
            throws SQLException {
        try (PreparedStatement query = c.prepareStatement(
                "SELECT COUNT(*),COALESCE(SUM(pending_results),0),COALESCE(SUM(active_matches),0),"
                + "COALESCE(MIN(updated_at),0),COALESCE(SUM(CASE WHEN context=? AND season_version=?"
                + " THEN 0 ELSE 1 END),0) FROM sbw_ranked_policy")) {
            query.setString(1, context); query.setLong(2, version);
            try (ResultSet row = query.executeQuery()) {
                row.next(); return row.getInt(1) > 0 && row.getInt(2) == 0 && row.getInt(3) == 0
                    && row.getLong(4) > System.currentTimeMillis() - 120000L && row.getInt(5) == 0;
            }
        }
    }
    private static void operation(Connection c, String id, String action, String actor, String details)
            throws SQLException {
        try (PreparedStatement insert = c.prepareStatement(
                "INSERT INTO sbw_ranked_season_operations VALUES(?,?,?,?,?,?)")) {
            insert.setString(1, UUID.randomUUID().toString()); insert.setString(2, id);
            insert.setString(3, action); insert.setString(4, actor);
            insert.setLong(5, System.currentTimeMillis()); insert.setString(6, details);
            insert.executeUpdate();
        }
    }
    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
    private static final class Control {
        final String activeId; final long version;
        Control(String activeId, long version) { this.activeId = activeId; this.version = version; }
    }
    private static RankedSeason read(ResultSet row) throws SQLException {
        String modes = row.getString("modes");
        return new RankedSeason(row.getString("season_id"), row.getString("name"),
            row.getString("description"), RankedSeason.State.valueOf(row.getString("state")),
            row.getLong("starts_at"), row.getLong("ends_at"), row.getLong("policy_version"),
            row.getInt("initial_rating"), row.getInt("placement_required"),
            row.getString("reset_type"), row.getString("reset_source"),
            row.getDouble("reset_factor"), new HashSet<String>(Arrays.asList(modes.split(","))));
    }
}
