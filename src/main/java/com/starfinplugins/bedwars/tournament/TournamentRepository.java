package com.starfinplugins.bedwars.tournament;

import com.starfinplugins.bedwars.stats.StatsSettings;
import com.starfinplugins.minigames.api.ReservationSnapshot;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

/** All tournament writes use InnoDB transactions and lock the parent tournament first. */
public final class TournamentRepository implements AutoCloseable {
    private HikariDataSource pool;

    public void open(StatsSettings database) throws SQLException {
        if (pool != null) return;
        HikariConfig config = new HikariConfig();
        config.setPoolName("StarfinBedWars-Tournaments");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + database.host + ":" + database.port + "/"
            + database.schema + "?sslMode=" + database.sslMode
            + "&connectionTimeZone=UTC&characterEncoding=UTF-8");
        config.setUsername(database.user); config.setPassword(database.password);
        config.setMaximumPoolSize(Math.min(database.poolSize, 4));
        config.setMinimumIdle(0); config.setConnectionTimeout(database.timeoutMs);
        config.setInitializationFailTimeout(0);
        HikariDataSource created = new HikariDataSource(config);
        try (Connection c = created.getConnection()) {
            ensureSchema(c); pool = created;
        } catch (SQLException | RuntimeException error) { created.close(); throw error; }
    }
    public boolean isOpen() { return pool != null && !pool.isClosed(); }
    public Connection connection() throws SQLException {
        if (!isOpen()) throw new SQLException("Torneios indisponiveis");
        return pool.getConnection();
    }
    @Override public void close() { if (pool != null) pool.close(); pool = null; }

    public static void ensureSchema(Connection c) throws SQLException {
        try (Statement sql = c.createStatement()) {
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_tournament_schema_migrations"
                + " (version INT PRIMARY KEY,applied_at BIGINT NOT NULL) ENGINE=InnoDB");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_tournaments ("
                + "tournament_id VARCHAR(32) PRIMARY KEY,name VARCHAR(64) NOT NULL,"
                + "description VARCHAR(256) NOT NULL,mode VARCHAR(16) NOT NULL,"
                + "arena_group VARCHAR(32) NOT NULL,format VARCHAR(32) NOT NULL,"
                + "state VARCHAR(24) NOT NULL,team_size INT NOT NULL,minimum_teams INT NOT NULL,"
                + "maximum_teams INT NOT NULL,best_of INT NOT NULL,spectator_limit INT NOT NULL,"
                + "public_spectators TINYINT(1) NOT NULL,allow_substitutions TINYINT(1) NOT NULL,"
                + "registration_opens BIGINT NOT NULL,registration_closes BIGINT NOT NULL,"
                + "starts_at BIGINT NOT NULL,draw_mode VARCHAR(16) NOT NULL,random_seed BIGINT NOT NULL,"
                + "champion_team CHAR(36) NULL,runner_up_team CHAR(36) NULL,"
                + "champion_title VARCHAR(48) NOT NULL,runner_up_title VARCHAR(48) NOT NULL,"
                + "participant_title VARCHAR(48) NOT NULL,creator VARCHAR(64) NOT NULL,"
                + "created_at BIGINT NOT NULL,updated_at BIGINT NOT NULL,"
                + "INDEX idx_sbw_tournaments_public(state,starts_at))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_tournament_teams ("
                + "team_id CHAR(36) PRIMARY KEY,tournament_id VARCHAR(32) NOT NULL,"
                + "team_name VARCHAR(32) NOT NULL,captain_uuid CHAR(36) NOT NULL,"
                + "seed_no INT NOT NULL,state VARCHAR(20) NOT NULL,registered_at BIGINT NOT NULL,"
                + "INDEX idx_sbw_tournament_teams(tournament_id,state,seed_no))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_tournament_members ("
                + "tournament_id VARCHAR(32) NOT NULL,player_uuid CHAR(36) NOT NULL,"
                + "team_id CHAR(36) NOT NULL,confirmed TINYINT(1) NOT NULL,joined_at BIGINT NOT NULL,"
                + "PRIMARY KEY(tournament_id,player_uuid),"
                + "INDEX idx_sbw_tournament_members(team_id,confirmed))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_tournament_matches ("
                + "match_id CHAR(36) PRIMARY KEY,tournament_id VARCHAR(32) NOT NULL,"
                + "round_no INT NOT NULL,slot_no INT NOT NULL,team_a CHAR(36) NULL,"
                + "team_b CHAR(36) NULL,wins_a INT NOT NULL DEFAULT 0,"
                + "wins_b INT NOT NULL DEFAULT 0,best_of INT NOT NULL,"
                + "state VARCHAR(24) NOT NULL,winner_team CHAR(36) NULL,"
                + "scheduled_at BIGINT NOT NULL,updated_at BIGINT NOT NULL,"
                + "UNIQUE KEY uq_sbw_tournament_slot(tournament_id,round_no,slot_no),"
                + "INDEX idx_sbw_tournament_matches(tournament_id,state,scheduled_at))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_tournament_games ("
                + "game_id CHAR(36) PRIMARY KEY,tournament_id VARCHAR(32) NOT NULL,"
                + "match_id CHAR(36) NOT NULL,game_no INT NOT NULL,state VARCHAR(24) NOT NULL,"
                + "allocation_id CHAR(36) NULL,server_id VARCHAR(32) NULL,"
                + "server_instance CHAR(36) NULL,arena_id VARCHAR(32) NULL,"
                + "arena_team_a VARCHAR(32) NULL,arena_team_b VARCHAR(32) NULL,"
                + "minecraft_match_id CHAR(36) NULL,winner_team CHAR(36) NULL,"
                + "result_reason VARCHAR(24) NULL,lease_expires BIGINT NOT NULL DEFAULT 0,"
                + "created_at BIGINT NOT NULL,started_at BIGINT NOT NULL DEFAULT 0,"
                + "ended_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL,"
                + "UNIQUE KEY uq_sbw_tournament_game_no(match_id,game_no),"
                + "UNIQUE KEY uq_sbw_tournament_allocation(allocation_id),"
                + "UNIQUE KEY uq_sbw_tournament_mc_match(minecraft_match_id),"
                + "INDEX idx_sbw_tournament_game_state(state,lease_expires))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_tournament_reward_grants ("
                + "tournament_id VARCHAR(32) NOT NULL,team_id CHAR(36) NOT NULL,"
                + "player_uuid CHAR(36) NOT NULL,reward_id VARCHAR(32) NOT NULL,"
                + "title_name VARCHAR(48) NOT NULL,state VARCHAR(16) NOT NULL,"
                + "created_at BIGINT NOT NULL,claimed_at BIGINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(tournament_id,team_id,player_uuid,reward_id),"
                + "INDEX idx_sbw_tournament_grants(player_uuid,state))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_tournament_game_admissions ("
                + "game_id CHAR(36) NOT NULL,player_uuid CHAR(36) NOT NULL,"
                + "reservation_id CHAR(36) NOT NULL,admitted_at BIGINT NOT NULL,"
                + "PRIMARY KEY(game_id,player_uuid),UNIQUE KEY uq_sbw_tournament_admission_reservation(reservation_id))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_tournament_checkin_policy ("
                + "tournament_id VARCHAR(32) PRIMARY KEY,required TINYINT(1) NOT NULL,"
                + "grace_minutes INT NOT NULL,automatic_walkover TINYINT(1) NOT NULL DEFAULT 0)"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_tournament_checkins ("
                + "match_id CHAR(36) NOT NULL,player_uuid CHAR(36) NOT NULL,"
                + "confirmed_at BIGINT NOT NULL,PRIMARY KEY(match_id,player_uuid))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("CREATE TABLE IF NOT EXISTS sbw_tournament_operations ("
                + "operation_id CHAR(36) PRIMARY KEY,tournament_id VARCHAR(32) NOT NULL,"
                + "action VARCHAR(32) NOT NULL,actor VARCHAR(64) NOT NULL,"
                + "details VARCHAR(256) NOT NULL,created_at BIGINT NOT NULL,"
                + "INDEX idx_sbw_tournament_operations(tournament_id,created_at))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            sql.execute("INSERT IGNORE INTO sbw_tournament_schema_migrations(version,applied_at)"
                + " VALUES(71," + System.currentTimeMillis() + ")");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static UUID uuid(String raw) { return raw == null ? null : UUID.fromString(raw); }
    private static String value(UUID id) { return id == null ? null : id.toString(); }
    private static Tournament readTournament(ResultSet row) throws SQLException {
        return new Tournament(row.getString("tournament_id"), row.getString("name"),
            row.getString("description"), row.getString("mode"), row.getString("arena_group"),
            Tournament.State.valueOf(row.getString("state")), row.getInt("team_size"),
            row.getInt("minimum_teams"), row.getInt("maximum_teams"), row.getInt("best_of"),
            row.getInt("spectator_limit"), row.getLong("registration_opens"),
            row.getLong("registration_closes"), row.getLong("starts_at"),
            row.getLong("random_seed"), TournamentBracket.Draw.valueOf(row.getString("draw_mode")),
            row.getBoolean("public_spectators"), row.getBoolean("allow_substitutions"),
            uuid(row.getString("champion_team")), uuid(row.getString("runner_up_team")));
    }
    private static TournamentMatch readMatch(ResultSet row) throws SQLException {
        return new TournamentMatch(UUID.fromString(row.getString("match_id")),
            row.getString("tournament_id"), row.getInt("round_no"), row.getInt("slot_no"),
            uuid(row.getString("team_a")), uuid(row.getString("team_b")),
            row.getInt("wins_a"), row.getInt("wins_b"), row.getInt("best_of"),
            row.getString("state"), uuid(row.getString("winner_team")),
            row.getLong("scheduled_at"));
    }
    private static TournamentGame readGame(ResultSet row) throws SQLException {
        return new TournamentGame(UUID.fromString(row.getString("game_id")),
            row.getString("tournament_id"), UUID.fromString(row.getString("match_id")),
            row.getInt("game_no"), row.getString("state"), uuid(row.getString("allocation_id")),
            row.getString("server_id"), row.getString("server_instance"),
            row.getString("arena_id"), row.getString("arena_team_a"),
            row.getString("arena_team_b"), uuid(row.getString("minecraft_match_id")),
            uuid(row.getString("winner_team")), row.getString("result_reason"),
            row.getLong("lease_expires"));
    }
    private static Tournament lockTournament(Connection c, String id) throws SQLException {
        try (PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_tournaments WHERE tournament_id=? FOR UPDATE")) {
            query.setString(1, id);
            try (ResultSet row = query.executeQuery()) { return row.next() ? readTournament(row) : null; }
        }
    }
    private static TournamentMatch lockMatch(Connection c, UUID id) throws SQLException {
        try (PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_tournament_matches WHERE match_id=? FOR UPDATE")) {
            query.setString(1, id.toString());
            try (ResultSet row = query.executeQuery()) { return row.next() ? readMatch(row) : null; }
        }
    }
    private static void audit(Connection c, String tournament, String action,
            String actor, String details) throws SQLException {
        try (PreparedStatement insert = c.prepareStatement(
                "INSERT INTO sbw_tournament_operations VALUES(?,?,?,?,?,?)")) {
            insert.setString(1, UUID.randomUUID().toString()); insert.setString(2, tournament);
            insert.setString(3, action); insert.setString(4, actor);
            insert.setString(5, details); insert.setLong(6, System.currentTimeMillis());
            insert.executeUpdate();
        }
    }
    public Tournament find(String id) throws SQLException {
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_tournaments WHERE tournament_id=?")) {
            query.setString(1, id);
            try (ResultSet row = query.executeQuery()) { return row.next() ? readTournament(row) : null; }
        }
    }
    public List<Tournament> list(int limit, int page) throws SQLException {
        List<Tournament> result = new ArrayList<Tournament>();
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_tournaments WHERE state NOT IN ('DRAFT','CANCELLED')"
                + " ORDER BY starts_at DESC,tournament_id LIMIT ? OFFSET ?")) {
            int size = Math.max(1, Math.min(50, limit));
            query.setInt(1, size); query.setInt(2, Math.max(0, page - 1) * size);
            try (ResultSet row = query.executeQuery())
                { while (row.next()) result.add(readTournament(row)); }
        }
        return Collections.unmodifiableList(result);
    }
    public List<Tournament> history(UUID player, int limit) throws SQLException {
        List<Tournament> result = new ArrayList<Tournament>();
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT t.* FROM sbw_tournaments t JOIN sbw_tournament_members m"
                + " ON m.tournament_id=t.tournament_id WHERE m.player_uuid=?"
                + " ORDER BY t.starts_at DESC LIMIT ?")) {
            query.setString(1, player.toString());
            query.setInt(2, Math.max(1, Math.min(50, limit)));
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) result.add(readTournament(row));
            }
        }
        return result;
    }
    public List<TournamentMatch> matches(String tournament, int round, int page)
            throws SQLException {
        List<TournamentMatch> result = new ArrayList<TournamentMatch>();
        String sql = "SELECT * FROM sbw_tournament_matches WHERE tournament_id=?"
            + (round > 0 ? " AND round_no=?" : "")
            + " ORDER BY round_no,slot_no LIMIT 36 OFFSET ?";
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(sql)) {
            query.setString(1, tournament);
            if (round > 0) query.setInt(2, round);
            query.setInt(round > 0 ? 3 : 2, Math.max(0, page - 1) * 36);
            try (ResultSet row = query.executeQuery())
                { while (row.next()) result.add(readMatch(row)); }
        }
        return Collections.unmodifiableList(result);
    }
    private static int countTeams(Connection c, String id, String state) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sbw_tournament_teams WHERE tournament_id=?"
            + " AND state<>'WITHDRAWN'"
            + (state == null ? "" : " AND state=?");
        try (PreparedStatement query = c.prepareStatement(sql)) {
            query.setString(1, id);
            if (state != null) query.setString(2, state);
            try (ResultSet row = query.executeQuery()) { row.next(); return row.getInt(1); }
        }
    }

    public Tournament create(TournamentSettings proposal, String actor) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO sbw_tournaments VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                int i = 1;
                insert.setString(i++, proposal.id); insert.setString(i++, proposal.name);
                insert.setString(i++, proposal.description); insert.setString(i++, proposal.mode);
                insert.setString(i++, proposal.arenaGroup);
                insert.setString(i++, "SINGLE_ELIMINATION"); insert.setString(i++, "DRAFT");
                insert.setInt(i++, proposal.teamSize); insert.setInt(i++, proposal.minimumTeams);
                insert.setInt(i++, proposal.maximumTeams); insert.setInt(i++, proposal.bestOf);
                insert.setInt(i++, proposal.spectatorLimit);
                insert.setBoolean(i++, proposal.publicSpectators);
                insert.setBoolean(i++, proposal.allowSubstitutions);
                insert.setLong(i++, proposal.registrationOpens);
                insert.setLong(i++, proposal.registrationCloses);
                insert.setLong(i++, proposal.startsAt);
                insert.setString(i++, proposal.draw.name()); insert.setLong(i++, proposal.randomSeed);
                insert.setString(i++, null); insert.setString(i++, null);
                insert.setString(i++, proposal.championTitle);
                insert.setString(i++, proposal.runnerUpTitle);
                insert.setString(i++, proposal.participantTitle);
                insert.setString(i++, actor); insert.setLong(i++, now); insert.setLong(i, now);
                insert.executeUpdate(); audit(c, proposal.id, "CREATE", actor, "Proposta validada");
                try (PreparedStatement policy = c.prepareStatement(
                        "INSERT INTO sbw_tournament_checkin_policy VALUES(?,?,?,0)")) {
                    policy.setString(1, proposal.id); policy.setBoolean(2, proposal.checkInEnabled);
                    policy.setInt(3, proposal.checkInGraceMinutes); policy.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
        return find(proposal.id);
    }

    public Tournament editDraft(String id, String field, String value, String actor)
            throws SQLException {
        String column;
        switch (field) {
            case "name": column = "name"; break;
            case "description": column = "description"; break;
            case "mode": column = "mode"; break;
            case "team-size": column = "team_size"; break;
            case "minimum-teams": column = "minimum_teams"; break;
            case "maximum-teams": column = "maximum_teams"; break;
            case "best-of": column = "best_of"; break;
            case "registration-opens": column = "registration_opens"; break;
            case "registration-closes": column = "registration_closes"; break;
            case "starts-at": column = "starts_at"; break;
            case "draw": column = "draw_mode"; break;
            case "random-seed": column = "random_seed"; break;
            case "arena-group": column = "arena_group"; break;
            case "spectator-limit": column = "spectator_limit"; break;
            case "public-spectators": column = "public_spectators"; break;
            case "champion-title": column = "champion_title"; break;
            case "runner-up-title": column = "runner_up_title"; break;
            case "participant-title": column = "participant_title"; break;
            default: throw new IllegalArgumentException("Campo de rascunho desconhecido");
        }
        Object parsed = value;
        if (column.endsWith("_at") || column.startsWith("registration_"))
            parsed = java.time.Instant.parse(value).toEpochMilli();
        else if (column.equals("team_size") || column.equals("minimum_teams")
                || column.equals("maximum_teams") || column.equals("best_of")
                || column.equals("spectator_limit")) parsed = Integer.parseInt(value);
        else if (column.equals("random_seed")) parsed = Long.parseLong(value);
        else if (column.equals("public_spectators"))
            parsed = "true".equalsIgnoreCase(value) ? 1 : "false".equalsIgnoreCase(value)
                ? 0 : -1;
        require(value != null && value.length() <= 256, "Valor longo ou ausente");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament current = lockTournament(c, id);
                require(current != null && current.state == Tournament.State.DRAFT,
                    "Somente rascunhos podem ser editados");
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournaments SET " + column + "=?,updated_at=?"
                        + " WHERE tournament_id=? AND state='DRAFT'")) {
                    update.setObject(1, parsed); update.setLong(2, System.currentTimeMillis());
                    update.setString(3, id); require(update.executeUpdate() == 1,
                        "Rascunho mudou de estado");
                }
                try (PreparedStatement query = c.prepareStatement(
                        "SELECT * FROM sbw_tournaments WHERE tournament_id=?")) {
                    query.setString(1, id);
                    try (ResultSet row = query.executeQuery()) {
                        row.next();
                        int size = row.getInt("team_size"), minimum = row.getInt("minimum_teams"),
                            maximum = row.getInt("maximum_teams"), best = row.getInt("best_of");
                        long opens = row.getLong("registration_opens"),
                            closes = row.getLong("registration_closes"),
                            starts = row.getLong("starts_at");
                        String mode = row.getString("mode"), group = row.getString("arena_group");
                        require(!row.getString("name").trim().isEmpty()
                            && row.getString("name").length() <= 64
                            && row.getString("description").length() <= 256
                            && size >= 1 && size <= 16 && minimum >= 2
                            && maximum >= minimum && maximum <= 64
                            && (best == 1 || best == 3 || best == 5)
                            && opens > 0 && closes > opens && starts >= closes
                            && ("custom".equals(mode) || ("solo".equals(mode) && size == 1)
                                || ("duo".equals(mode) && size == 2)
                                || ("trio".equals(mode) && size == 3)
                                || ("squad".equals(mode) && size == 4))
                            && group.matches("[a-z0-9_-]{1,32}")
                            && ("RANDOM".equals(row.getString("draw_mode"))
                                || "SEEDED".equals(row.getString("draw_mode")))
                            && row.getString("champion_title").length() <= 48
                            && row.getString("runner_up_title").length() <= 48
                            && row.getString("participant_title").length() <= 48
                            && row.getInt("spectator_limit") >= 0
                            && row.getInt("spectator_limit") <= 100
                            && row.getInt("public_spectators") >= 0,
                            "Alteracao viola as regras do torneio");
                    }
                }
                audit(c, id, "EDIT", actor, field + "=" + value);
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
        return find(id);
    }

    public Tournament transition(String id, Tournament.State target, String actor)
            throws SQLException {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament current = lockTournament(c, id);
                require(current != null, "Torneio inexistente");
                if (current.state == target) { c.commit(); return current; }
                long now = System.currentTimeMillis();
                switch (target) {
                    case SCHEDULED:
                        require(current.state == Tournament.State.DRAFT
                            && current.registrationCloses > now, "Rascunho ou datas invalidas"); break;
                    case REGISTRATION_OPEN:
                        require(current.state == Tournament.State.SCHEDULED
                            && now >= current.registrationOpens && now < current.registrationCloses,
                            "Inscricoes fora da janela"); break;
                    case REGISTRATION_CLOSED:
                        require(current.state == Tournament.State.REGISTRATION_OPEN,
                            "Inscricoes nao estao abertas"); break;
                    case RUNNING:
                        require(current.state == Tournament.State.READY && now >= current.startsAt,
                            "Chave ou data de inicio indisponivel");
                        require(countTeams(c, id, "CONFIRMED") >= current.minimumTeams,
                            "Equipes confirmadas insuficientes"); break;
                    case PAUSED:
                        require(current.state == Tournament.State.RUNNING,
                            "Somente torneio em andamento pode pausar"); break;
                    case CANCELLED:
                        require(current.state == Tournament.State.DRAFT
                            || current.state == Tournament.State.SCHEDULED
                            || current.state == Tournament.State.REGISTRATION_OPEN
                            || current.state == Tournament.State.REGISTRATION_CLOSED,
                            "Torneio com chave publicada nao pode ser cancelado automaticamente"); break;
                    default: throw new IllegalStateException("Transicao nao permitida");
                }
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournaments SET state=?,updated_at=? WHERE tournament_id=?")) {
                    update.setString(1, target.name()); update.setLong(2, now);
                    update.setString(3, id); update.executeUpdate();
                }
                if (target == Tournament.State.RUNNING) requestReadyGames(c, id, now);
                audit(c, id, target.name(), actor, "Transicao confirmada");
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
        return find(id);
    }

    public Tournament resume(String id, String actor) throws SQLException {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament current = lockTournament(c, id);
                require(current != null && current.state == Tournament.State.PAUSED,
                    "Torneio nao esta pausado");
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournaments SET state='RUNNING',updated_at=?"
                        + " WHERE tournament_id=?")) {
                    update.setLong(1, System.currentTimeMillis()); update.setString(2, id);
                    update.executeUpdate();
                }
                requestReadyGames(c, id, System.currentTimeMillis());
                audit(c, id, "RESUME", actor, "Retomado"); c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
        return find(id);
    }

    public void tick() throws SQLException {
        List<Tournament> scheduled = list(50, 1);
        long now = System.currentTimeMillis();
        for (Tournament tournament : scheduled) {
            try {
                if (tournament.state == Tournament.State.SCHEDULED
                        && now >= tournament.registrationOpens
                        && now < tournament.registrationCloses)
                    transition(tournament.id, Tournament.State.REGISTRATION_OPEN, "scheduler");
                else if (tournament.state == Tournament.State.REGISTRATION_OPEN
                        && now >= tournament.registrationCloses)
                    transition(tournament.id, Tournament.State.REGISTRATION_CLOSED, "scheduler");
            } catch (IllegalStateException raced) { /* Another backend completed the transition. */ }
        }
    }

    public TournamentTeam register(String tournamentId, UUID captain, String name,
            List<UUID> roster, String actor) throws SQLException {
        require(captain != null && roster != null && !roster.isEmpty()
            && roster.contains(captain) && !roster.contains(null)
            && new HashSet<UUID>(roster).size() == roster.size(), "Elenco invalido");
        require(name != null && !name.trim().isEmpty() && name.length() <= 32,
            "Nome da equipe invalido");
        UUID teamId = UUID.randomUUID();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament tournament = lockTournament(c, tournamentId);
                long now = System.currentTimeMillis();
                require(tournament != null && tournament.state == Tournament.State.REGISTRATION_OPEN
                    && now >= tournament.registrationOpens && now < tournament.registrationCloses,
                    "Inscricoes fechadas");
                require(roster.size() == tournament.teamSize, "Tamanho de equipe incorreto");
                int count = countTeams(c, tournamentId, null);
                require(count < tournament.maximumTeams, "Limite de equipes atingido");
                try (PreparedStatement team = c.prepareStatement(
                        "INSERT INTO sbw_tournament_teams VALUES(?,?,?,?,?,?,?)")) {
                    team.setString(1, teamId.toString()); team.setString(2, tournamentId);
                    team.setString(3, name); team.setString(4, captain.toString());
                    team.setInt(5, count + 1);
                    team.setString(6, roster.size() == 1 ? "CONFIRMED" : "PENDING");
                    team.setLong(7, now); team.executeUpdate();
                }
                try (PreparedStatement member = c.prepareStatement(
                        "INSERT INTO sbw_tournament_members VALUES(?,?,?,?,?)")) {
                    for (UUID player : roster) {
                        member.setString(1, tournamentId); member.setString(2, player.toString());
                        member.setString(3, teamId.toString());
                        member.setBoolean(4, player.equals(captain)); member.setLong(5, now);
                        member.executeUpdate();
                    }
                }
                audit(c, tournamentId, "REGISTER", actor, teamId.toString()); c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
        return team(teamId);
    }

    public TournamentTeam confirm(String tournamentId, UUID player) throws SQLException {
        UUID confirmedTeam;
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament tournament = lockTournament(c, tournamentId);
                require(tournament != null && ((tournament.state == Tournament.State.REGISTRATION_OPEN
                    && System.currentTimeMillis() < tournament.registrationCloses)
                    || (tournament.allowSubstitutions && (tournament.state == Tournament.State.REGISTRATION_CLOSED
                        || tournament.state == Tournament.State.READY
                        || tournament.state == Tournament.State.RUNNING
                        || tournament.state == Tournament.State.PAUSED))),
                    "Confirmacao fechada");
                UUID team;
                try (PreparedStatement query = c.prepareStatement(
                        "SELECT team_id FROM sbw_tournament_members"
                        + " WHERE tournament_id=? AND player_uuid=? FOR UPDATE")) {
                    query.setString(1, tournamentId); query.setString(2, player.toString());
                    try (ResultSet row = query.executeQuery()) {
                        require(row.next(), "Jogador nao inscrito"); team = UUID.fromString(row.getString(1));
                    }
                }
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_members SET confirmed=1"
                        + " WHERE tournament_id=? AND player_uuid=?")) {
                    update.setString(1, tournamentId); update.setString(2, player.toString());
                    update.executeUpdate();
                }
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_teams SET state='CONFIRMED' WHERE team_id=?"
                        + " AND NOT EXISTS (SELECT 1 FROM sbw_tournament_members"
                        + " WHERE team_id=? AND confirmed=0)")) {
                    update.setString(1, team.toString()); update.setString(2, team.toString());
                    update.executeUpdate();
                }
                audit(c, tournamentId, "CONFIRM", player.toString(), team.toString());
                c.commit(); confirmedTeam = team;
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
        return team(confirmedTeam);
    }

    public void withdraw(String tournamentId, UUID captain) throws SQLException {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament tournament = lockTournament(c, tournamentId);
                require(tournament != null && tournament.state == Tournament.State.REGISTRATION_OPEN,
                    "Inscricoes fechadas");
                UUID team;
                try (PreparedStatement query = c.prepareStatement(
                        "SELECT team_id FROM sbw_tournament_teams"
                        + " WHERE tournament_id=? AND captain_uuid=?"
                        + " AND state<>'WITHDRAWN' FOR UPDATE")) {
                    query.setString(1, tournamentId); query.setString(2, captain.toString());
                    try (ResultSet row = query.executeQuery()) {
                        require(row.next(), "Apenas o capitao pode cancelar a inscricao");
                        team = UUID.fromString(row.getString(1));
                    }
                }
                try (PreparedStatement remove = c.prepareStatement(
                        "DELETE FROM sbw_tournament_members WHERE team_id=?")) {
                    remove.setString(1, team.toString()); remove.executeUpdate();
                }
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_teams SET state='WITHDRAWN' WHERE team_id=?")) {
                    update.setString(1, team.toString()); update.executeUpdate();
                }
                audit(c, tournamentId, "WITHDRAW", captain.toString(), team.toString());
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }

    /** Admin-authorized substitution, pending consent from the incoming player. */
    public void substitute(String tournamentId, UUID teamId, UUID outgoing, UUID incoming,
            String actor, String reason) throws SQLException {
        require(teamId != null && outgoing != null && incoming != null && !outgoing.equals(incoming),
            "Substituicao invalida");
        require(reason != null && reason.trim().length() >= 8 && reason.length() <= 128,
            "Justificativa obrigatoria (8-128 caracteres)");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament tournament = lockTournament(c, tournamentId);
                require(tournament != null && tournament.allowSubstitutions
                    && (tournament.state == Tournament.State.REGISTRATION_OPEN
                        || tournament.state == Tournament.State.REGISTRATION_CLOSED
                        || tournament.state == Tournament.State.READY
                        || tournament.state == Tournament.State.RUNNING
                        || tournament.state == Tournament.State.PAUSED),
                    "Substituicoes indisponiveis");
                String captain;
                try (PreparedStatement query = c.prepareStatement(
                        "SELECT captain_uuid FROM sbw_tournament_teams"
                        + " WHERE tournament_id=? AND team_id=? AND state='CONFIRMED' FOR UPDATE")) {
                    query.setString(1, tournamentId); query.setString(2, teamId.toString());
                    try (ResultSet row = query.executeQuery()) {
                        require(row.next(), "Equipe inexistente"); captain = row.getString(1);
                    }
                }
                require(!captain.equals(outgoing.toString()), "Capitao nao pode ser substituido");
                try (PreparedStatement query = c.prepareStatement(
                        "SELECT 1 FROM sbw_tournament_matches WHERE tournament_id=?"
                        + " AND (team_a=? OR team_b=?) AND (state IN ('IN_PROGRESS','REVIEW')"
                        + " OR (state='READY' AND (wins_a>0 OR wins_b>0))) LIMIT 1")) {
                    query.setString(1, tournamentId); query.setString(2, teamId.toString());
                    query.setString(3, teamId.toString());
                    try (ResultSet row = query.executeQuery()) {
                        require(!row.next(), "Equipe possui serie ativa ou em revisao");
                    }
                }
                try (PreparedStatement query = c.prepareStatement(
                        "SELECT 1 FROM sbw_tournament_games g JOIN sbw_tournament_matches m"
                        + " ON m.match_id=g.match_id WHERE g.tournament_id=?"
                        + " AND (m.team_a=? OR m.team_b=?)"
                        + " AND g.state IN ('ALLOCATED','RUNNING') LIMIT 1")) {
                    query.setString(1, tournamentId); query.setString(2, teamId.toString());
                    query.setString(3, teamId.toString());
                    try (ResultSet row = query.executeQuery()) {
                        require(!row.next(), "Equipe possui jogo ativo");
                    }
                }
                try (PreparedStatement remove = c.prepareStatement(
                        "DELETE FROM sbw_tournament_members WHERE tournament_id=?"
                        + " AND team_id=? AND player_uuid=?")) {
                    remove.setString(1, tournamentId); remove.setString(2, teamId.toString());
                    remove.setString(3, outgoing.toString());
                    require(remove.executeUpdate() == 1, "Integrante original nao pertence a equipe");
                }
                try (PreparedStatement add = c.prepareStatement(
                        "INSERT INTO sbw_tournament_members"
                        + " (tournament_id,player_uuid,team_id,confirmed,joined_at)"
                        + " VALUES(?,?,?,0,?)")) {
                    add.setString(1, tournamentId); add.setString(2, incoming.toString());
                    add.setString(3, teamId.toString()); add.setLong(4, System.currentTimeMillis());
                    add.executeUpdate();
                }
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_teams SET state='PENDING' WHERE team_id=?")) {
                    update.setString(1, teamId.toString()); update.executeUpdate();
                }
                audit(c, tournamentId, "SUBSTITUTE", actor,
                    teamId + ":" + outgoing + ":" + incoming + ":" + reason.trim());
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }

    public TournamentTeam team(UUID id) throws SQLException {
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_tournament_teams WHERE team_id=?")) {
            query.setString(1, id.toString());
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? readTeam(c, row) : null;
            }
        }
    }
    public TournamentTeam teamFor(String tournament, UUID player) throws SQLException {
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT t.* FROM sbw_tournament_teams t JOIN sbw_tournament_members m"
                + " ON m.team_id=t.team_id WHERE m.tournament_id=? AND m.player_uuid=?")) {
            query.setString(1, tournament); query.setString(2, player.toString());
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? readTeam(c, row) : null;
            }
        }
    }
    public List<TournamentTeam> teams(String tournament) throws SQLException {
        List<TournamentTeam> result = new ArrayList<TournamentTeam>();
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_tournament_teams WHERE tournament_id=?"
                + " AND state<>'WITHDRAWN' ORDER BY seed_no,registered_at")) {
            query.setString(1, tournament);
            try (ResultSet row = query.executeQuery())
                { while (row.next()) result.add(readTeam(c, row)); }
        }
        return Collections.unmodifiableList(result);
    }
    private static TournamentTeam readTeam(Connection c, ResultSet row) throws SQLException {
        UUID id = UUID.fromString(row.getString("team_id"));
        List<UUID> members = new ArrayList<UUID>();
        try (PreparedStatement query = c.prepareStatement(
                "SELECT player_uuid FROM sbw_tournament_members WHERE team_id=?"
                + " ORDER BY player_uuid")) {
            query.setString(1, id.toString());
            try (ResultSet players = query.executeQuery())
                { while (players.next()) members.add(UUID.fromString(players.getString(1))); }
        }
        return new TournamentTeam(id, UUID.fromString(row.getString("captain_uuid")),
            row.getString("tournament_id"), row.getString("team_name"),
            row.getString("state"), row.getInt("seed_no"), members);
    }

    public void seed(String tournament, UUID teamId, int seed, String actor) throws SQLException {
        require(seed > 0 && seed <= 64, "Seed invalida");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament row = lockTournament(c, tournament);
                require(row != null && row.state == Tournament.State.REGISTRATION_CLOSED,
                    "Seeds somente antes de publicar a chave");
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_teams SET seed_no=?"
                        + " WHERE tournament_id=? AND team_id=? AND state='CONFIRMED'")) {
                    update.setInt(1, seed); update.setString(2, tournament);
                    update.setString(3, teamId.toString());
                    require(update.executeUpdate() == 1, "Equipe nao confirmada");
                }
                audit(c, tournament, "SEED", actor, teamId + "=" + seed); c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }

    public Tournament generate(String id, String actor) throws SQLException {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament tournament = lockTournament(c, id);
                require(tournament != null && tournament.state == Tournament.State.REGISTRATION_CLOSED,
                    "Feche as inscricoes antes de gerar a chave");
                List<TournamentBracket.Entry> entries = new ArrayList<TournamentBracket.Entry>();
                try (PreparedStatement query = c.prepareStatement(
                        "SELECT team_id,seed_no,state FROM sbw_tournament_teams"
                        + " WHERE tournament_id=? AND state<>'WITHDRAWN' ORDER BY registered_at")) {
                    query.setString(1, id);
                    try (ResultSet row = query.executeQuery()) {
                        while (row.next()) {
                            require("CONFIRMED".equals(row.getString(3)),
                                "Todas as equipes devem confirmar o elenco");
                            entries.add(new TournamentBracket.Entry(
                                UUID.fromString(row.getString(1)), row.getInt(2)));
                        }
                    }
                }
                require(entries.size() >= tournament.minimumTeams
                    && entries.size() <= tournament.maximumTeams, "Quantidade de equipes invalida");
                Set<Integer> seeds = new HashSet<Integer>();
                if (tournament.draw == TournamentBracket.Draw.SEEDED)
                    for (TournamentBracket.Entry entry : entries)
                        require(seeds.add(entry.seed), "Seeds duplicadas");
                List<TournamentBracket.Pair> plan = TournamentBracket.plan(entries,
                    tournament.draw, tournament.randomSeed);
                long now = System.currentTimeMillis();
                try (PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO sbw_tournament_matches"
                        + " (match_id,tournament_id,round_no,slot_no,team_a,team_b,"
                        + "wins_a,wins_b,best_of,state,winner_team,scheduled_at,updated_at)"
                        + " VALUES(?,?,?,?,?,?,0,0,?,'PENDING',NULL,?,?)")) {
                    for (TournamentBracket.Pair pair : plan) {
                        insert.setString(1, UUID.randomUUID().toString());
                        insert.setString(2, id); insert.setInt(3, pair.round);
                        insert.setInt(4, pair.slot); insert.setString(5, value(pair.first));
                        insert.setString(6, value(pair.second));
                        insert.setInt(7, tournament.bestOf);
                        insert.setLong(8, tournament.startsAt); insert.setLong(9, now);
                        insert.executeUpdate();
                    }
                }
                for (TournamentBracket.Pair pair : plan) if (pair.round == 1 && pair.bye()) {
                    UUID winner = pair.first == null ? pair.second : pair.first;
                    try (PreparedStatement update = c.prepareStatement(
                            "UPDATE sbw_tournament_matches SET state='COMPLETED',"
                            + "winner_team=?,updated_at=? WHERE tournament_id=?"
                            + " AND round_no=1 AND slot_no=?")) {
                        update.setString(1, winner.toString()); update.setLong(2, now);
                        update.setString(3, id); update.setInt(4, pair.slot); update.executeUpdate();
                    }
                    advance(c, id, 1, pair.slot, winner, now);
                }
                try (PreparedStatement ready = c.prepareStatement(
                        "UPDATE sbw_tournament_matches SET state='READY'"
                        + " WHERE tournament_id=? AND state='PENDING'"
                        + " AND team_a IS NOT NULL AND team_b IS NOT NULL")) {
                    ready.setString(1, id); ready.executeUpdate();
                }
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournaments SET state='READY',updated_at=?"
                        + " WHERE tournament_id=?")) {
                    update.setLong(1, now); update.setString(2, id); update.executeUpdate();
                }
                audit(c, id, "GENERATE", actor, "teams=" + entries.size()
                    + ";draw=" + tournament.draw + ";seed=" + tournament.randomSeed);
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
        return find(id);
    }

    private static void advance(Connection c, String tournament, int round, int slot,
            UUID winner, long now) throws SQLException {
        String side = slot % 2 == 0 ? "team_a" : "team_b";
        String sql = "UPDATE sbw_tournament_matches SET " + side + "=?,updated_at=?"
            + " WHERE tournament_id=? AND round_no=? AND slot_no=? AND " + side + " IS NULL";
        try (PreparedStatement update = c.prepareStatement(sql)) {
            update.setString(1, winner.toString()); update.setLong(2, now);
            update.setString(3, tournament); update.setInt(4, round + 1);
            update.setInt(5, slot / 2);
            if (update.executeUpdate() == 0) {
                try (PreparedStatement finalCheck = c.prepareStatement(
                        "SELECT COUNT(*) FROM sbw_tournament_matches WHERE tournament_id=?"
                        + " AND round_no=?")) {
                    finalCheck.setString(1, tournament); finalCheck.setInt(2, round + 1);
                    try (ResultSet row = finalCheck.executeQuery()) {
                        row.next(); require(row.getInt(1) == 0,
                            "Avanco duplicado ou confronto seguinte ocupado");
                    }
                }
                return;
            }
        }
        try (PreparedStatement ready = c.prepareStatement(
                "UPDATE sbw_tournament_matches SET state='READY'"
                + " WHERE tournament_id=? AND round_no=? AND slot_no=?"
                + " AND state='PENDING' AND team_a IS NOT NULL AND team_b IS NOT NULL")) {
            ready.setString(1, tournament); ready.setInt(2, round + 1);
            ready.setInt(3, slot / 2); ready.executeUpdate();
        }
    }

    private static void requestReadyGames(Connection c, String tournament, long now)
            throws SQLException {
        try (PreparedStatement ready = c.prepareStatement(
                "SELECT match_id FROM sbw_tournament_matches WHERE tournament_id=?"
                + " AND state='READY' AND scheduled_at<=? ORDER BY round_no,slot_no")) {
            ready.setString(1, tournament); ready.setLong(2, now);
            try (ResultSet row = ready.executeQuery()) {
                while (row.next()) requestGame(c, tournament,
                    UUID.fromString(row.getString(1)), now);
            }
        }
    }
    private static void requestGame(Connection c, String tournament, UUID match, long now)
            throws SQLException {
        int next = 1;
        try (PreparedStatement query = c.prepareStatement(
                "SELECT COALESCE(MAX(game_no),0)+1 FROM sbw_tournament_games WHERE match_id=?")) {
            query.setString(1, match.toString());
            try (ResultSet row = query.executeQuery()) { row.next(); next = row.getInt(1); }
        }
        try (PreparedStatement insert = c.prepareStatement(
                "INSERT INTO sbw_tournament_games"
                + " (game_id,tournament_id,match_id,game_no,state,created_at,updated_at)"
                + " VALUES(?,?,?,?,'REQUESTED',?,?)")) {
            insert.setString(1, UUID.randomUUID().toString()); insert.setString(2, tournament);
            insert.setString(3, match.toString()); insert.setInt(4, next);
            insert.setLong(5, now); insert.setLong(6, now); insert.executeUpdate();
        }
        try (PreparedStatement update = c.prepareStatement(
                "UPDATE sbw_tournament_matches SET state='IN_PROGRESS',updated_at=?"
                + " WHERE match_id=? AND state='READY'")) {
            update.setLong(1, now); update.setString(2, match.toString());
            require(update.executeUpdate() == 1, "Confronto mudou de estado");
        }
    }

    public void scheduleMatch(String tournament, UUID match, long when,
            String actor) throws SQLException {
        require(when > 0, "Horario invalido");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament owner = lockTournament(c, tournament);
                require(owner != null && (owner.state == Tournament.State.READY
                    || owner.state == Tournament.State.RUNNING
                    || owner.state == Tournament.State.PAUSED), "Torneio indisponivel");
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_matches SET scheduled_at=?,updated_at=?"
                        + " WHERE tournament_id=? AND match_id=?"
                        + " AND state IN ('PENDING','READY')")) {
                    update.setLong(1, when); update.setLong(2, System.currentTimeMillis());
                    update.setString(3, tournament); update.setString(4, match.toString());
                    require(update.executeUpdate() == 1, "Confronto nao pode ser reagendado");
                }
                if (owner.state == Tournament.State.RUNNING && when <= System.currentTimeMillis())
                    requestReadyGames(c, tournament, System.currentTimeMillis());
                audit(c, tournament, "SCHEDULE_MATCH", actor, match + "=" + when);
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }

    public UUID checkIn(String tournament, UUID player) throws SQLException {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament owner = lockTournament(c, tournament);
                require(owner != null && (owner.state == Tournament.State.READY
                    || owner.state == Tournament.State.RUNNING), "Check-in indisponivel");
                UUID match = null;
                try (PreparedStatement query = c.prepareStatement(
                        "SELECT m.match_id,m.scheduled_at,p.grace_minutes"
                        + " FROM sbw_tournament_matches m"
                        + " JOIN sbw_tournament_members r ON r.tournament_id=m.tournament_id"
                        + " AND r.team_id IN (m.team_a,m.team_b)"
                        + " JOIN sbw_tournament_checkin_policy p"
                        + " ON p.tournament_id=m.tournament_id"
                        + " WHERE m.tournament_id=? AND r.player_uuid=?"
                        + " AND r.confirmed=1 AND m.state IN ('READY','IN_PROGRESS')"
                        + " ORDER BY m.round_no,m.slot_no LIMIT 1")) {
                    query.setString(1, tournament); query.setString(2, player.toString());
                    try (ResultSet row = query.executeQuery()) {
                        require(row.next(), "Nenhum confronto pendente para este jogador");
                        require(System.currentTimeMillis() >= row.getLong(2)
                            - row.getInt(3) * 60000L, "Check-in ainda nao abriu");
                        match = UUID.fromString(row.getString(1));
                    }
                }
                try (PreparedStatement insert = c.prepareStatement(
                        "INSERT IGNORE INTO sbw_tournament_checkins VALUES(?,?,?)")) {
                    insert.setString(1, match.toString());
                    insert.setString(2, player.toString());
                    insert.setLong(3, System.currentTimeMillis()); insert.executeUpdate();
                }
                audit(c, tournament, "CHECK_IN", player.toString(), match.toString());
                c.commit(); return match;
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }

    public TournamentGame game(UUID id) throws SQLException {
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_tournament_games WHERE game_id=?")) {
            query.setString(1, id.toString());
            try (ResultSet row = query.executeQuery()) { return row.next() ? readGame(row) : null; }
        }
    }

    public TournamentGame runningGame(String tournamentId) throws SQLException {
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_tournament_games WHERE tournament_id=?"
                + " AND state='RUNNING' ORDER BY started_at DESC LIMIT 1")) {
            query.setString(1, tournamentId);
            try (ResultSet row = query.executeQuery()) { return row.next() ? readGame(row) : null; }
        }
    }

    public Set<String> arenaGroups() throws SQLException {
        Set<String> groups = new HashSet<String>();
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT DISTINCT arena_group FROM sbw_tournaments")) {
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) groups.add(rows.getString(1));
            }
        }
        return Collections.unmodifiableSet(groups);
    }

    public List<TournamentGame> games(UUID matchId) throws SQLException {
        List<TournamentGame> result = new ArrayList<TournamentGame>();
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_tournament_games WHERE match_id=? ORDER BY game_no")) {
            query.setString(1, matchId.toString());
            try (ResultSet row = query.executeQuery())
                { while (row.next()) result.add(readGame(row)); }
        }
        return Collections.unmodifiableList(result);
    }

    public List<TournamentGame> requestedGames(int limit) throws SQLException {
        List<TournamentGame> result = new ArrayList<TournamentGame>();
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT g.* FROM sbw_tournament_games g JOIN sbw_tournaments t"
                + " ON t.tournament_id=g.tournament_id WHERE g.state='REQUESTED'"
                + " AND t.state='RUNNING' ORDER BY g.created_at LIMIT ?")) {
            query.setInt(1, Math.max(1, Math.min(64, limit)));
            try (ResultSet row = query.executeQuery())
                { while (row.next()) result.add(readGame(row)); }
        }
        return Collections.unmodifiableList(result);
    }

    /** Proxy allocation is compare-and-set; a second worker cannot claim the game. */
    public TournamentGame allocate(UUID gameId, UUID allocationId, String serverId,
            String serverInstance, String arenaId, String arenaTeamA, String arenaTeamB,
            long leaseExpires) throws SQLException {
        require(gameId != null && allocationId != null && serverId != null
            && serverInstance != null && arenaId != null && arenaTeamA != null
            && arenaTeamB != null && !arenaTeamA.equals(arenaTeamB)
            && leaseExpires > System.currentTimeMillis(), "Alocacao invalida");
        TournamentGame snapshot = game(gameId);
        require(snapshot != null, "Jogo inexistente");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament tournament = lockTournament(c, snapshot.tournamentId);
                TournamentGame game = lockGame(c, gameId);
                require(game != null && "REQUESTED".equals(game.state),
                    "Jogo ja alocado ou indisponivel");
                require(tournament != null && tournament.state == Tournament.State.RUNNING,
                    "Torneio pausado ou encerrado");
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_games SET state='ALLOCATED',allocation_id=?,"
                        + "server_id=?,server_instance=?,arena_id=?,arena_team_a=?,"
                        + "arena_team_b=?,lease_expires=?,updated_at=? WHERE game_id=?"
                        + " AND state='REQUESTED'")) {
                    update.setString(1, allocationId.toString()); update.setString(2, serverId);
                    update.setString(3, serverInstance); update.setString(4, arenaId);
                    update.setString(5, arenaTeamA); update.setString(6, arenaTeamB);
                    update.setLong(7, leaseExpires); update.setLong(8, System.currentTimeMillis());
                    update.setString(9, gameId.toString());
                    require(update.executeUpdate() == 1, "Jogo ja alocado");
                }
                audit(c, game.tournamentId, "ALLOCATE", "proxy", gameId + ":" + arenaId);
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
        return game(gameId);
    }

    private static TournamentGame lockGame(Connection c, UUID id) throws SQLException {
        try (PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_tournament_games WHERE game_id=? FOR UPDATE")) {
            query.setString(1, id.toString());
            try (ResultSet row = query.executeQuery()) { return row.next() ? readGame(row) : null; }
        }
    }

    public boolean memberOfGame(UUID gameId, UUID player) throws SQLException {
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT 1 FROM sbw_tournament_games g JOIN sbw_tournament_matches m"
                + " ON m.match_id=g.match_id JOIN sbw_tournament_members p"
                + " ON p.tournament_id=g.tournament_id AND p.team_id IN (m.team_a,m.team_b)"
                + " WHERE g.game_id=? AND p.player_uuid=? AND p.confirmed=1")) {
            query.setString(1, gameId.toString()); query.setString(2, player.toString());
            try (ResultSet row = query.executeQuery()) { return row.next(); }
        }
    }

    public TournamentAdmission inspect(ReservationSnapshot reservation, String serverId,
            String serverInstance) throws SQLException {
        if (reservation == null || !reservation.isTournament()) return null;
        try (Connection c = connection()) {
            TournamentGame game;
            try (PreparedStatement query = c.prepareStatement(
                    "SELECT * FROM sbw_tournament_games WHERE game_id=?")) {
                query.setString(1, reservation.getTournamentGameId().toString());
                try (ResultSet row = query.executeQuery()) {
                    if (!row.next()) return null;
                    game = readGame(row);
                }
            }
            if (!"ALLOCATED".equals(game.state)
                    || !reservation.getTournamentAllocationId().equals(game.allocationId)
                    || !reservation.getServerId().equals(serverId)
                    || !serverId.equals(game.serverId)
                    || !serverInstance.equals(game.serverInstance)
                    || !reservation.getArenaId().equals(game.arenaId)
                    || game.leaseExpires <= System.currentTimeMillis()) return null;
            String mode, group, name; int size;
            try (PreparedStatement query = c.prepareStatement(
                    "SELECT mode,arena_group,team_size,name FROM sbw_tournaments"
                    + " WHERE tournament_id=? AND state='RUNNING'")) {
                query.setString(1, game.tournamentId);
                try (ResultSet row = query.executeQuery()) {
                    if (!row.next()) return null;
                    mode = row.getString(1); group = row.getString(2);
                    size = row.getInt(3); name = row.getString(4);
                }
            }
            if (!mode.equals(reservation.getMode())) return null;
            int round, winsA, winsB; String teamAName, teamBName;
            try (PreparedStatement query = c.prepareStatement(
                    "SELECT m.round_no,m.wins_a,m.wins_b,a.team_name,b.team_name"
                    + " FROM sbw_tournament_matches m"
                    + " JOIN sbw_tournament_teams a ON a.team_id=m.team_a"
                    + " JOIN sbw_tournament_teams b ON b.team_id=m.team_b"
                    + " WHERE m.match_id=? AND m.state='IN_PROGRESS'")) {
                query.setString(1, game.matchId.toString());
                try (ResultSet row = query.executeQuery()) {
                    if (!row.next()) return null;
                    round = row.getInt(1); winsA = row.getInt(2); winsB = row.getInt(3);
                    teamAName = row.getString(4); teamBName = row.getString(5);
                }
            }
            Map<UUID, String> roster = new LinkedHashMap<UUID, String>();
            try (PreparedStatement query = c.prepareStatement(
                    "SELECT p.player_uuid,p.team_id,m.team_a,m.team_b"
                    + " FROM sbw_tournament_matches m JOIN sbw_tournament_members p"
                    + " ON p.tournament_id=m.tournament_id"
                    + " AND p.team_id IN (m.team_a,m.team_b)"
                    + " WHERE m.match_id=? AND p.confirmed=1")) {
                query.setString(1, game.matchId.toString());
                try (ResultSet row = query.executeQuery()) {
                    while (row.next()) roster.put(UUID.fromString(row.getString(1)),
                        row.getString(2).equals(row.getString(3))
                            ? game.arenaTeamA : game.arenaTeamB);
                }
            }
            if (roster.size() != size * 2
                    || !roster.containsKey(reservation.getPlayerId())
                    || !roster.get(reservation.getPlayerId()).equals(reservation.getTeamId()))
                return null;
            return new TournamentAdmission(game, game.tournamentId, mode, group, size,
                name, teamAName, teamBName, round, winsA, winsB, roster);
        }
    }

    /** Called after the reservation is joined on the Bukkit thread. */
    public int admit(UUID gameId, UUID allocationId, UUID player, UUID reservationId,
            UUID minecraftMatch, String serverId, String serverInstance, String arenaId)
            throws SQLException {
        TournamentGame snapshot = game(gameId);
        require(snapshot != null, "Jogo inexistente");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament tournament = lockTournament(c, snapshot.tournamentId);
                TournamentGame game = lockGame(c, gameId);
                require(game != null && "ALLOCATED".equals(game.state)
                    && allocationId.equals(game.allocationId)
                    && serverId.equals(game.serverId) && serverInstance.equals(game.serverInstance)
                    && arenaId.equals(game.arenaId)
                    && game.leaseExpires > System.currentTimeMillis(),
                    "Alocacao expirada ou divergente");
                require(game.minecraftMatchId == null
                    || game.minecraftMatchId.equals(minecraftMatch),
                    "Instancia de partida divergente");
                require(tournament != null && tournament.state == Tournament.State.RUNNING,
                    "Torneio indisponivel");
                try (PreparedStatement check = c.prepareStatement(
                        "SELECT 1 FROM sbw_tournament_matches m JOIN sbw_tournament_members p"
                        + " ON p.tournament_id=m.tournament_id"
                        + " AND p.team_id IN (m.team_a,m.team_b)"
                        + " WHERE m.match_id=? AND p.player_uuid=? AND p.confirmed=1")) {
                    check.setString(1, game.matchId.toString()); check.setString(2, player.toString());
                    try (ResultSet row = check.executeQuery())
                        { require(row.next(), "Jogador fora do elenco"); }
                }
                String existingReservation = null;
                try (PreparedStatement query = c.prepareStatement(
                        "SELECT reservation_id FROM sbw_tournament_game_admissions"
                        + " WHERE game_id=? AND player_uuid=? FOR UPDATE")) {
                    query.setString(1, gameId.toString()); query.setString(2, player.toString());
                    try (ResultSet row = query.executeQuery()) {
                        if (row.next()) existingReservation = row.getString(1);
                    }
                }
                require(existingReservation == null || existingReservation.equals(reservationId.toString()),
                    "Reserva conflitante para jogador ja admitido");
                if (existingReservation == null) {
                    try (PreparedStatement insert = c.prepareStatement(
                            "INSERT INTO sbw_tournament_game_admissions"
                            + " (game_id,player_uuid,reservation_id,admitted_at) VALUES(?,?,?,?)")) {
                        insert.setString(1, gameId.toString()); insert.setString(2, player.toString());
                        insert.setString(3, reservationId.toString());
                        insert.setLong(4, System.currentTimeMillis()); insert.executeUpdate();
                    }
                }
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_games SET minecraft_match_id=?,updated_at=?"
                        + " WHERE game_id=? AND minecraft_match_id IS NULL")) {
                    update.setString(1, minecraftMatch.toString());
                    update.setLong(2, System.currentTimeMillis());
                    update.setString(3, gameId.toString()); update.executeUpdate();
                }
                int admitted;
                try (PreparedStatement query = c.prepareStatement(
                        "SELECT COUNT(*) FROM sbw_tournament_game_admissions WHERE game_id=?")) {
                    query.setString(1, gameId.toString());
                    try (ResultSet row = query.executeQuery())
                        { row.next(); admitted = row.getInt(1); }
                }
                c.commit(); return admitted;
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }

    public void startGame(UUID gameId, UUID minecraftMatch) throws SQLException {
        TournamentGame snapshot = game(gameId);
        require(snapshot != null, "Jogo inexistente");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament tournament = lockTournament(c, snapshot.tournamentId);
                TournamentGame game = lockGame(c, gameId);
                require(game != null && "ALLOCATED".equals(game.state)
                    && minecraftMatch.equals(game.minecraftMatchId), "Jogo nao autorizado");
                require(tournament != null && tournament.state == Tournament.State.RUNNING,
                    "Torneio indisponivel");
                try (PreparedStatement query = c.prepareStatement(
                        "SELECT COUNT(*) FROM sbw_tournament_game_admissions WHERE game_id=?")) {
                    query.setString(1, gameId.toString());
                    try (ResultSet row = query.executeQuery()) {
                        row.next(); require(row.getInt(1) == tournament.teamSize * 2,
                            "Elenco incompleto");
                    }
                }
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_games SET state='RUNNING',started_at=?,updated_at=?"
                        + " WHERE game_id=? AND state='ALLOCATED'")) {
                    update.setLong(1, System.currentTimeMillis());
                    update.setLong(2, System.currentTimeMillis());
                    update.setString(3, gameId.toString());
                    require(update.executeUpdate() == 1, "Jogo mudou de estado");
                }
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }

    /** Quarantines an uncertain game; only an audited admin replay can release its series. */
    public void reviewGame(UUID gameId, String reason) throws SQLException {
        TournamentGame snapshot = game(gameId);
        require(snapshot != null, "Jogo inexistente");
        require(reason != null && reason.matches("[A-Z_]{1,24}"), "Motivo invalido");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament tournament = lockTournament(c, snapshot.tournamentId);
                TournamentGame game = lockGame(c, gameId);
                require(tournament != null && game != null, "Jogo indisponivel");
                if ("REVIEW".equals(game.state) || "COMPLETED".equals(game.state)
                        || "REPLAY".equals(game.state)) { c.commit(); return; }
                require("ALLOCATED".equals(game.state) || "RUNNING".equals(game.state),
                    "Jogo fora de alocacao");
                TournamentMatch match = lockMatch(c, game.matchId);
                require(match != null && "IN_PROGRESS".equals(match.state),
                    "Confronto fora de serie");
                long now = System.currentTimeMillis();
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_games SET state='REVIEW',result_reason=?,"
                        + "ended_at=?,updated_at=? WHERE game_id=?")) {
                    update.setString(1, reason); update.setLong(2, now);
                    update.setLong(3, now); update.setString(4, gameId.toString());
                    update.executeUpdate();
                }
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_matches SET state='REVIEW',updated_at=?"
                        + " WHERE match_id=?")) {
                    update.setLong(1, now); update.setString(2, match.id.toString());
                    update.executeUpdate();
                }
                audit(c, game.tournamentId, "GAME_REVIEW", "backend", gameId + ":" + reason);
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }

    /** Returns false for a duplicate result; never fabricates a winner on draw/error. */
    public boolean recordResult(UUID gameId, UUID minecraftMatch, String reason,
            String winningArenaTeam) throws SQLException {
        require(reason != null && (reason.equals("VICTORY") || reason.equals("DRAW")
            || reason.equals("CANCELLED") || reason.equals("ERROR")), "Resultado invalido");
        TournamentGame snapshot = game(gameId);
        require(snapshot != null, "Jogo inexistente");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament tournament = lockTournament(c, snapshot.tournamentId);
                TournamentGame game = lockGame(c, gameId);
                require(game != null && minecraftMatch.equals(game.minecraftMatchId),
                    "Resultado de partida divergente");
                boolean lostServerReview = "REVIEW".equals(game.state)
                    && "SERVER_LOST".equals(game.resultReason);
                if (!"RUNNING".equals(game.state) && !lostServerReview) {
                    UUID claimedWinner = null;
                    if ("VICTORY".equals(reason)) {
                        TournamentMatch previous = lockMatch(c, game.matchId);
                        if (winningArenaTeam != null && previous != null)
                            claimedWinner = winningArenaTeam.equals(game.arenaTeamA)
                                ? previous.teamA : winningArenaTeam.equals(game.arenaTeamB)
                                    ? previous.teamB : null;
                    }
                    require(reason.equals(game.resultReason)
                        && (claimedWinner == null ? game.winner == null
                            : claimedWinner.equals(game.winner)), "Resultado conflitante");
                    c.commit(); return false;
                }
                require(tournament != null && (tournament.state == Tournament.State.RUNNING
                    || tournament.state == Tournament.State.PAUSED), "Torneio indisponivel");
                TournamentMatch match = lockMatch(c, game.matchId);
                require(match != null && ("IN_PROGRESS".equals(match.state)
                    || (lostServerReview && "REVIEW".equals(match.state))),
                    "Confronto fora da serie");
                UUID winner = null;
                if (reason.equals("VICTORY")) {
                    require(winningArenaTeam != null, "Vencedor ausente");
                    if (winningArenaTeam.equals(game.arenaTeamA)) winner = match.teamA;
                    else if (winningArenaTeam.equals(game.arenaTeamB)) winner = match.teamB;
                    require(winner != null, "Time vencedor desconhecido");
                }
                long now = System.currentTimeMillis();
                String outcome = reason.equals("VICTORY") ? "COMPLETED"
                    : reason.equals("DRAW") ? "REPLAY" : "REVIEW";
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_games SET state=?,winner_team=?,"
                        + "result_reason=?,ended_at=?,updated_at=? WHERE game_id=?")) {
                    update.setString(1, outcome); update.setString(2, value(winner));
                    update.setString(3, reason); update.setLong(4, now);
                    update.setLong(5, now); update.setString(6, gameId.toString());
                    update.executeUpdate();
                }
                if (reason.equals("VICTORY")) {
                    int winsA = match.winsA + (winner.equals(match.teamA) ? 1 : 0);
                    int winsB = match.winsB + (winner.equals(match.teamB) ? 1 : 0);
                    int target = match.bestOf / 2 + 1;
                    boolean seriesOver = winsA >= target || winsB >= target;
                    try (PreparedStatement update = c.prepareStatement(
                            "UPDATE sbw_tournament_matches SET wins_a=?,wins_b=?,state=?,"
                            + "winner_team=?,updated_at=? WHERE match_id=?")) {
                        update.setInt(1, winsA); update.setInt(2, winsB);
                        update.setString(3, seriesOver ? "COMPLETED" : "READY");
                        update.setString(4, seriesOver ? winner.toString() : null);
                        update.setLong(5, now); update.setString(6, match.id.toString());
                        update.executeUpdate();
                    }
                    if (seriesOver) {
                        advance(c, game.tournamentId, match.round, match.slot, winner, now);
                        if (finalRound(c, game.tournamentId, match.round)) {
                            UUID runner = winner.equals(match.teamA) ? match.teamB : match.teamA;
                            try (PreparedStatement update = c.prepareStatement(
                                    "UPDATE sbw_tournaments SET state='FINALIZING',"
                                    + "champion_team=?,runner_up_team=?,updated_at=?"
                                    + " WHERE tournament_id=?")) {
                                update.setString(1, winner.toString());
                                update.setString(2, runner.toString());
                                update.setLong(3, now); update.setString(4, game.tournamentId);
                                update.executeUpdate();
                            }
                        }
                    } else if (tournament.state == Tournament.State.RUNNING)
                        requestGame(c, game.tournamentId, match.id, now);
                    if (seriesOver && tournament.state == Tournament.State.RUNNING)
                        requestReadyGames(c, game.tournamentId, now);
                } else {
                    try (PreparedStatement update = c.prepareStatement(
                            "UPDATE sbw_tournament_matches SET state=?,updated_at=?"
                            + " WHERE match_id=?")) {
                        update.setString(1, reason.equals("DRAW") ? "READY" : "REVIEW");
                        update.setLong(2, now); update.setString(3, match.id.toString());
                        update.executeUpdate();
                    }
                    if (reason.equals("DRAW") && tournament.state == Tournament.State.RUNNING)
                        requestGame(c, game.tournamentId, match.id, now);
                }
                audit(c, game.tournamentId, "RESULT", "backend",
                    gameId + ":" + reason + ":" + value(winner));
                c.commit(); return true;
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }

    private static boolean finalRound(Connection c, String tournament, int round)
            throws SQLException {
        try (PreparedStatement query = c.prepareStatement(
                "SELECT 1 FROM sbw_tournament_matches WHERE tournament_id=?"
                + " AND round_no>? LIMIT 1")) {
            query.setString(1, tournament); query.setInt(2, round);
            try (ResultSet row = query.executeQuery()) { return !row.next(); }
        }
    }

    /** Explicit no-show ruling; never records a synthetic Minecraft victory. */
    public void adjudicateWalkover(String tournamentId, UUID matchId, UUID winner,
            String actor, String reason) throws SQLException {
        require(winner != null && reason != null && reason.trim().length() >= 8
            && reason.length() <= 128, "Justificativa obrigatoria (8-128 caracteres)");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament tournament = lockTournament(c, tournamentId);
                require(tournament != null && (tournament.state == Tournament.State.RUNNING
                    || tournament.state == Tournament.State.PAUSED), "Torneio indisponivel");
                TournamentMatch match = lockMatch(c, matchId);
                require(match != null && tournamentId.equals(match.tournamentId)
                    && "IN_PROGRESS".equals(match.state)
                    && match.teamA != null && match.teamB != null
                    && (winner.equals(match.teamA) || winner.equals(match.teamB))
                    && match.winsA == 0 && match.winsB == 0,
                    "Confronto nao aceita decisao por ausencia");
                int grace = 0;
                try (PreparedStatement policy = c.prepareStatement(
                        "SELECT grace_minutes FROM sbw_tournament_checkin_policy"
                        + " WHERE tournament_id=?")) {
                    policy.setString(1, tournamentId);
                    try (ResultSet row = policy.executeQuery()) {
                        require(row.next(), "Politica de check-in ausente");
                        grace = row.getInt(1);
                    }
                }
                long now = System.currentTimeMillis();
                require(now >= match.scheduledAt + grace * 60000L,
                    "Prazo de comparecimento ainda aberto");
                UUID loser = winner.equals(match.teamA) ? match.teamB : match.teamA;
                require(checkedInMembers(c, matchId, winner) == tournament.teamSize
                    && checkedInMembers(c, matchId, loser) < tournament.teamSize,
                    "Ausencia nao comprovada pelo check-in");
                int requested = 0;
                try (PreparedStatement games = c.prepareStatement(
                        "SELECT state FROM sbw_tournament_games"
                        + " WHERE match_id=? FOR UPDATE")) {
                    games.setString(1, matchId.toString());
                    try (ResultSet row = games.executeQuery()) {
                        while (row.next()) {
                            require("REQUESTED".equals(row.getString(1)),
                                "Jogo alocado, disputado ou em revisao");
                            requested++;
                        }
                    }
                }
                require(requested == 1, "Solicitacao de jogo divergente");
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_games SET state='CANCELLED',"
                        + "result_reason='ADMIN_WALKOVER',ended_at=?,updated_at=?"
                        + " WHERE match_id=? AND state='REQUESTED'")) {
                    update.setLong(1, now); update.setLong(2, now);
                    update.setString(3, matchId.toString());
                    require(update.executeUpdate() == 1, "Jogo mudou de estado");
                }
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_matches SET state='COMPLETED',"
                        + "winner_team=?,updated_at=? WHERE match_id=? AND state='IN_PROGRESS'")) {
                    update.setString(1, winner.toString()); update.setLong(2, now);
                    update.setString(3, matchId.toString());
                    require(update.executeUpdate() == 1, "Confronto mudou de estado");
                }
                advance(c, tournamentId, match.round, match.slot, winner, now);
                if (finalRound(c, tournamentId, match.round)) {
                    try (PreparedStatement update = c.prepareStatement(
                            "UPDATE sbw_tournaments SET state='FINALIZING',"
                            + "champion_team=?,runner_up_team=?,updated_at=?"
                            + " WHERE tournament_id=?")) {
                        update.setString(1, winner.toString()); update.setString(2, loser.toString());
                        update.setLong(3, now); update.setString(4, tournamentId);
                        update.executeUpdate();
                    }
                } else if (tournament.state == Tournament.State.RUNNING)
                    requestReadyGames(c, tournamentId, now);
                audit(c, tournamentId, "WALKOVER", actor,
                    matchId + ":" + winner + ":" + reason.trim());
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }
    private static int checkedInMembers(Connection c, UUID match, UUID team)
            throws SQLException {
        try (PreparedStatement query = c.prepareStatement(
                "SELECT COUNT(*) FROM sbw_tournament_members p"
                + " JOIN sbw_tournament_checkins i ON i.player_uuid=p.player_uuid"
                + " AND i.match_id=? WHERE p.team_id=? AND p.confirmed=1")) {
            query.setString(1, match.toString()); query.setString(2, team.toString());
            try (ResultSet row = query.executeQuery()) { row.next(); return row.getInt(1); }
        }
    }

    public void replayReviewed(String tournament, UUID matchId, String actor) throws SQLException {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament owner = lockTournament(c, tournament);
                require(owner != null && owner.state == Tournament.State.RUNNING,
                    "Torneio precisa estar em andamento");
                TournamentMatch match = lockMatch(c, matchId);
                require(match != null && tournament.equals(match.tournamentId)
                    && "REVIEW".equals(match.state), "Confronto nao esta em revisao");
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournament_matches SET state='READY',updated_at=?"
                        + " WHERE match_id=?")) {
                    update.setLong(1, System.currentTimeMillis());
                    update.setString(2, matchId.toString()); update.executeUpdate();
                }
                requestGame(c, tournament, matchId, System.currentTimeMillis());
                audit(c, tournament, "REPLAY_REVIEW", actor, matchId.toString());
                c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
    }

    /** Finalization only follows a real final game result, never an admin-selected winner. */
    public Tournament finalizeTournament(String id, String actor) throws SQLException {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                Tournament owner = lockTournament(c, id);
                require(owner != null && owner.state == Tournament.State.FINALIZING
                    && owner.champion != null && owner.runnerUp != null,
                    "Final sem resultado validado");
                long now = System.currentTimeMillis();
                String participantTitle = titleName(c, id, "participant_title");
                String championTitle = titleName(c, id, "champion_title");
                String runnerUpTitle = titleName(c, id, "runner_up_title");
                try (PreparedStatement rows = c.prepareStatement(
                        "SELECT m.player_uuid,m.team_id FROM sbw_tournament_members m"
                        + " JOIN sbw_tournament_teams t ON t.team_id=m.team_id"
                        + " WHERE m.tournament_id=? AND t.state='CONFIRMED'")) {
                    rows.setString(1, id);
                    try (ResultSet row = rows.executeQuery()) {
                        while (row.next()) {
                            String player = row.getString(1), team = row.getString(2);
                            grant(c, id, team, player, "participant", participantTitle, now);
                            if (team.equals(owner.champion.toString()))
                                grant(c, id, team, player, "champion", championTitle, now);
                            else if (team.equals(owner.runnerUp.toString()))
                                grant(c, id, team, player, "runner_up", runnerUpTitle, now);
                        }
                    }
                }
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE sbw_tournaments SET state='FINISHED',updated_at=?"
                        + " WHERE tournament_id=? AND state='FINALIZING'")) {
                    update.setLong(1, now); update.setString(2, id);
                    require(update.executeUpdate() == 1, "Finalizacao duplicada");
                }
                audit(c, id, "FINALIZE", actor, owner.champion.toString()); c.commit();
            } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
            finally { c.setAutoCommit(true); }
        }
        return find(id);
    }
    private static String titleName(Connection c, String id, String column) throws SQLException {
        try (PreparedStatement query = c.prepareStatement(
                "SELECT " + column + " FROM sbw_tournaments WHERE tournament_id=?")) {
            query.setString(1, id);
            try (ResultSet row = query.executeQuery()) { row.next(); return row.getString(1); }
        }
    }
    private static String rewardId(String tournament, String role) {
        String key = tournament + ":" + role;
        UUID value = UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "t_" + value.toString().replace("-", "").substring(0, 24);
    }
    private static void grant(Connection c, String tournament, String team, String player,
            String role, String title, long now) throws SQLException {
        if (title == null || title.trim().isEmpty()) return;
        try (PreparedStatement insert = c.prepareStatement(
                "INSERT IGNORE INTO sbw_tournament_reward_grants"
                + " (tournament_id,team_id,player_uuid,reward_id,title_name,state,created_at)"
                + " VALUES(?,?,?,?,?,'PENDING',?)")) {
            insert.setString(1, tournament); insert.setString(2, team);
            insert.setString(3, player); insert.setString(4, rewardId(tournament, role));
            insert.setString(5, title); insert.setLong(6, now); insert.executeUpdate();
        }
    }
    public List<TournamentReward> rewards(UUID player) throws SQLException {
        List<TournamentReward> result = new ArrayList<TournamentReward>();
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT * FROM sbw_tournament_reward_grants WHERE player_uuid=?"
                + " ORDER BY created_at DESC LIMIT 50")) {
            query.setString(1, player.toString());
            try (ResultSet row = query.executeQuery()) { while (row.next()) {
                result.add(new TournamentReward(row.getString("tournament_id"),
                    UUID.fromString(row.getString("team_id")), player,
                    row.getString("reward_id"), row.getString("title_name"),
                    row.getString("state")));
            } }
        }
        return result;
    }
    public int claimRewards(UUID player) throws SQLException {
        try (Connection c = connection(); PreparedStatement update = c.prepareStatement(
                "UPDATE sbw_tournament_reward_grants SET state='CLAIMED',claimed_at=?"
                + " WHERE player_uuid=? AND state='PENDING'")) {
            update.setLong(1, System.currentTimeMillis());
            update.setString(2, player.toString()); return update.executeUpdate();
        }
    }
    public List<String> auditTrail(String tournament, int limit) throws SQLException {
        List<String> result = new ArrayList<String>();
        try (Connection c = connection(); PreparedStatement query = c.prepareStatement(
                "SELECT action,actor,details,created_at FROM sbw_tournament_operations"
                + " WHERE tournament_id=? ORDER BY created_at DESC LIMIT ?")) {
            query.setString(1, tournament); query.setInt(2, Math.max(1, Math.min(100, limit)));
            try (ResultSet row = query.executeQuery()) { while (row.next())
                result.add(row.getLong(4) + " " + row.getString(1) + " "
                    + row.getString(2) + " " + row.getString(3));
            }
        }
        return result;
    }
}
