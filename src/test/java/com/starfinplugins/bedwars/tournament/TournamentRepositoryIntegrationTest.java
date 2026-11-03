package com.starfinplugins.bedwars.tournament;

import com.starfinplugins.bedwars.stats.StatsSettings;
import com.starfinplugins.minigames.api.ReservationSnapshot;
import com.starfinplugins.minigames.api.ReservationState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/** Runs only against an explicitly supplied disposable MySQL schema. */
public class TournamentRepositoryIntegrationTest {
    @Test public void auditedWalkoverNeedsRealCheckInAndNoAllocatedGame() throws Exception {
        String port = System.getenv("SBW_TOURNAMENT_TEST_PORT");
        Assume.assumeTrue(port != null && !port.isEmpty());
        YamlConfiguration database = new YamlConfiguration();
        database.set("enabled", true); database.set("mysql.host", "127.0.0.1");
        database.set("mysql.port", Integer.parseInt(port));
        database.set("mysql.database", "sbw_tournament_test");
        database.set("mysql.username", "root");
        database.set("mysql.password", "phase71test");
        database.set("mysql.ssl-mode", "DISABLED");
        long now = System.currentTimeMillis();
        String id = "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        YamlConfiguration proposal = new YamlConfiguration();
        proposal.set("proposal.id", id); proposal.set("proposal.name", "Walkover Cup");
        proposal.set("proposal.mode", "solo"); proposal.set("proposal.team-size", 1);
        proposal.set("proposal.minimum-teams", 2); proposal.set("proposal.maximum-teams", 2);
        proposal.set("proposal.best-of", 1);
        proposal.set("proposal.registration-opens", Instant.ofEpochMilli(now - 10000).toString());
        proposal.set("proposal.registration-closes", Instant.ofEpochMilli(now + 120000).toString());
        proposal.set("proposal.starts-at", Instant.ofEpochMilli(now + 180000).toString());
        TournamentRepository repo = new TournamentRepository();
        repo.open(new StatsSettings(database));
        try {
            repo.create(new TournamentSettings(proposal), "test");
            repo.transition(id, Tournament.State.SCHEDULED, "test");
            repo.transition(id, Tournament.State.REGISTRATION_OPEN, "test");
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            TournamentTeam winner = repo.register(id, a, "A", Arrays.asList(a), "test");
            repo.register(id, b, "B", Arrays.asList(b), "test");
            repo.transition(id, Tournament.State.REGISTRATION_CLOSED, "test");
            repo.generate(id, "test");
            try (Connection c = repo.connection(); PreparedStatement update = c.prepareStatement(
                    "UPDATE sbw_tournaments SET starts_at=? WHERE tournament_id=?")) {
                update.setLong(1, now - 900000); update.setString(2, id); update.executeUpdate();
            }
            try (Connection c = repo.connection(); PreparedStatement update = c.prepareStatement(
                    "UPDATE sbw_tournament_matches SET scheduled_at=? WHERE tournament_id=?")) {
                update.setLong(1, now - 900000); update.setString(2, id); update.executeUpdate();
            }
            repo.transition(id, Tournament.State.RUNNING, "test");
            TournamentMatch match = repo.matches(id, 0, 1).get(0);
            try {
                repo.adjudicateWalkover(id, match.id, winner.id, "referee", "opponent absent");
                fail("Winner without check-in must be refused");
            } catch (IllegalStateException expected) { /* expected */ }
            repo.checkIn(id, a);
            repo.adjudicateWalkover(id, match.id, winner.id, "referee", "opponent absent");
            assertEquals(Tournament.State.FINALIZING, repo.find(id).state);
            assertEquals(winner.id, repo.find(id).champion);
            assertEquals("CANCELLED", repo.games(match.id).get(0).state);
            assertTrue(repo.auditTrail(id, 20).stream().anyMatch(x -> x.contains("WALKOVER")));
        } finally { repo.close(); }
    }

    @Test public void authorizedSubstitutionRequiresIncomingConfirmation() throws Exception {
        String port = System.getenv("SBW_TOURNAMENT_TEST_PORT");
        Assume.assumeTrue(port != null && !port.isEmpty());
        YamlConfiguration database = new YamlConfiguration();
        database.set("enabled", true); database.set("mysql.host", "127.0.0.1");
        database.set("mysql.port", Integer.parseInt(port));
        database.set("mysql.database", "sbw_tournament_test");
        database.set("mysql.username", "root");
        database.set("mysql.password", "phase71test");
        database.set("mysql.ssl-mode", "DISABLED");
        long now = System.currentTimeMillis();
        String id = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        YamlConfiguration proposal = new YamlConfiguration();
        proposal.set("proposal.id", id); proposal.set("proposal.name", "Substitution Cup");
        proposal.set("proposal.mode", "duo"); proposal.set("proposal.team-size", 2);
        proposal.set("proposal.minimum-teams", 2); proposal.set("proposal.maximum-teams", 2);
        proposal.set("proposal.allow-substitutions", true);
        proposal.set("proposal.registration-opens", Instant.ofEpochMilli(now - 10000).toString());
        proposal.set("proposal.registration-closes", Instant.ofEpochMilli(now + 120000).toString());
        proposal.set("proposal.starts-at", Instant.ofEpochMilli(now + 180000).toString());
        TournamentRepository repo = new TournamentRepository();
        repo.open(new StatsSettings(database));
        try {
            repo.create(new TournamentSettings(proposal), "test");
            repo.transition(id, Tournament.State.SCHEDULED, "test");
            repo.transition(id, Tournament.State.REGISTRATION_OPEN, "test");
            UUID captain = UUID.randomUUID(), outgoing = UUID.randomUUID();
            UUID incoming = UUID.randomUUID();
            TournamentTeam team = repo.register(id, captain, "A",
                Arrays.asList(captain, outgoing), "test");
            repo.confirm(id, outgoing);
            assertEquals("CONFIRMED", repo.team(team.id).state);
            repo.substitute(id, team.id, outgoing, incoming, "referee", "replacement approved");
            assertEquals("PENDING", repo.team(team.id).state);
            assertFalse(repo.team(team.id).members.contains(outgoing));
            assertTrue(repo.team(team.id).members.contains(incoming));
            repo.confirm(id, incoming);
            assertEquals("CONFIRMED", repo.team(team.id).state);
            try {
                repo.substitute(id, team.id, captain, outgoing, "referee", "captain swap");
                fail("Captain substitution must be refused");
            } catch (IllegalStateException expected) { /* expected */ }
        } finally { repo.close(); }
    }

    @Test public void drawReplaySeriesFinalAndIdempotency() throws Exception {
        String port = System.getenv("SBW_TOURNAMENT_TEST_PORT");
        Assume.assumeTrue(port != null && !port.isEmpty());
        YamlConfiguration database = new YamlConfiguration();
        database.set("enabled", true); database.set("mysql.host", "127.0.0.1");
        database.set("mysql.port", Integer.parseInt(port));
        database.set("mysql.database", "sbw_tournament_test");
        database.set("mysql.username", "root");
        database.set("mysql.password", "phase71test");
        database.set("mysql.ssl-mode", "DISABLED");
        long now = System.currentTimeMillis();
        String id = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        YamlConfiguration proposal = new YamlConfiguration();
        proposal.set("proposal.id", id); proposal.set("proposal.name", "Integration Cup");
        proposal.set("proposal.mode", "solo"); proposal.set("proposal.team-size", 1);
        proposal.set("proposal.minimum-teams", 2); proposal.set("proposal.maximum-teams", 2);
        proposal.set("proposal.best-of", 3); proposal.set("proposal.draw", "SEEDED");
        proposal.set("proposal.registration-opens", Instant.ofEpochMilli(now - 10000).toString());
        proposal.set("proposal.registration-closes", Instant.ofEpochMilli(now + 120000).toString());
        proposal.set("proposal.starts-at", Instant.ofEpochMilli(now + 180000).toString());
        proposal.set("proposal.champion-title", "Champion");
        proposal.set("proposal.runner-up-title", "Finalist");
        proposal.set("proposal.participant-title", "Participant");
        TournamentRepository repo = new TournamentRepository();
        repo.open(new StatsSettings(database));
        try {
            repo.create(new TournamentSettings(proposal), "test");
            repo.transition(id, Tournament.State.SCHEDULED, "test");
            repo.transition(id, Tournament.State.REGISTRATION_OPEN, "test");
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            TournamentTeam teamA = repo.register(id, a, "A", Arrays.asList(a), "test");
            TournamentTeam teamB = repo.register(id, b, "B", Arrays.asList(b), "test");
            repo.transition(id, Tournament.State.REGISTRATION_CLOSED, "test");
            assertTrue(repo.matches(id, 0, 1).isEmpty());
            repo.generate(id, "test");
            assertNotNull(repo.checkIn(id, a));
            assertNotNull(repo.checkIn(id, b));
            try (Connection c = repo.connection(); PreparedStatement update = c.prepareStatement(
                    "UPDATE sbw_tournaments SET starts_at=? WHERE tournament_id=?")) {
                update.setLong(1, System.currentTimeMillis() - 1); update.setString(2, id);
                update.executeUpdate();
            }
            try (Connection c = repo.connection(); PreparedStatement update = c.prepareStatement(
                    "UPDATE sbw_tournament_matches SET scheduled_at=? WHERE tournament_id=?")) {
                update.setLong(1, System.currentTimeMillis() - 1); update.setString(2, id);
                update.executeUpdate();
            }
            repo.transition(id, Tournament.State.RUNNING, "test");
            TournamentGame game = repo.requestedGames(10).stream()
                .filter(row -> id.equals(row.tournamentId)).findFirst().get();
            UUID allocation = UUID.randomUUID(), instance = UUID.randomUUID(), mc = UUID.randomUUID();
            game = repo.allocate(game.id, allocation, "bedwars-01", instance.toString(),
                "cup_arena", "red", "blue", System.currentTimeMillis() + 60000);
            TournamentMatch match = repo.matches(id, 0, 1).get(0);
            UUID first = match.teamA.equals(teamA.id) ? a : b;
            UUID second = match.teamB.equals(teamB.id) ? b : a;
            ReservationSnapshot reservation = new ReservationSnapshot(UUID.randomUUID(), first,
                "bedwars", "solo", "bedwars-01", instance, "cup_arena",
                ReservationState.CLAIMED, now, now + 60000, match.teamA, null, null, 0L,
                1, "red", null, null, game.id, allocation);
            assertNotNull(repo.inspect(reservation, "bedwars-01", instance.toString()));
            assertNull(repo.inspect(reservation, "bedwars-02", instance.toString()));
            assertEquals(1, repo.admit(game.id, allocation, first, reservation.getId(), mc,
                "bedwars-01", instance.toString(), "cup_arena"));
            assertEquals(1, repo.admit(game.id, allocation, first, reservation.getId(), mc,
                "bedwars-01", instance.toString(), "cup_arena"));
            assertEquals(2, repo.admit(game.id, allocation, second, UUID.randomUUID(), mc,
                "bedwars-01", instance.toString(), "cup_arena"));
            repo.startGame(game.id, mc);
            assertTrue(repo.recordResult(game.id, mc, "DRAW", null));
            assertFalse(repo.recordResult(game.id, mc, "DRAW", null));
            assertEquals("REPLAY", repo.game(game.id).state);
            for (int n = 0; n < 2; n++) {
                TournamentGame next = repo.requestedGames(10).stream()
                    .filter(row -> id.equals(row.tournamentId)).findFirst().get();
                UUID nextAllocation = UUID.randomUUID(), nextMc = UUID.randomUUID();
                repo.allocate(next.id, nextAllocation, "bedwars-01", instance.toString(),
                    "cup_arena", "red", "blue", System.currentTimeMillis() + 60000);
                repo.admit(next.id, nextAllocation, first, UUID.randomUUID(), nextMc,
                    "bedwars-01", instance.toString(), "cup_arena");
                repo.admit(next.id, nextAllocation, second, UUID.randomUUID(), nextMc,
                    "bedwars-01", instance.toString(), "cup_arena");
                repo.startGame(next.id, nextMc);
                assertTrue(repo.recordResult(next.id, nextMc, "VICTORY", "red"));
                assertFalse(repo.recordResult(next.id, nextMc, "VICTORY", "red"));
            }
            assertEquals(Tournament.State.FINALIZING, repo.find(id).state);
            assertEquals(match.teamA, repo.find(id).champion);
            repo.finalizeTournament(id, "test");
            assertEquals(Tournament.State.FINISHED, repo.find(id).state);
            assertEquals(2, repo.claimRewards(first));
            assertEquals(0, repo.claimRewards(first));
            assertEquals(2, repo.rewards(second).size());
        } finally { repo.close(); }
    }
}
