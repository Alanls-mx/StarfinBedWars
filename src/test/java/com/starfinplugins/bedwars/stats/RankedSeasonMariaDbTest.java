package com.starfinplugins.bedwars.stats;

import com.starfinplugins.bedwars.ranked.RankedRepository;
import com.starfinplugins.bedwars.ranked.RankedSeason;
import com.starfinplugins.bedwars.ranked.RankedSeasonRepository;
import com.starfinplugins.bedwars.ranked.RankedSeasonReward;
import com.starfinplugins.bedwars.ranked.RankedSeasonSettings;
import com.starfinplugins.bedwars.ranked.RankedSettings;
import com.starfinplugins.bedwars.ranked.RankedStanding;
import com.starfinplugins.bedwars.ranked.RankedMatchSnapshot;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/** Runs only against a disposable schema explicitly supplied by the test invocation. */
public class RankedSeasonMariaDbTest {
    @Test public void lifecycleRankingRewardsAndIdempotentFinalize() throws Exception {
        String host = System.getenv("SBW_RANKED_TEST_HOST");
        Assume.assumeTrue("Disposable MariaDB required", host != null && !host.isEmpty());
        int port = Integer.parseInt(System.getenv("SBW_RANKED_TEST_PORT"));
        String schema = System.getenv("SBW_RANKED_TEST_SCHEMA");
        String password = System.getenv("SBW_RANKED_TEST_PASSWORD");
        Assume.assumeTrue("Only a dedicated disposable schema is allowed",
            schema != null && schema.startsWith("ranked63"));
        YamlConfiguration database = new YamlConfiguration();
        database.set("enabled", true); database.set("mysql.host", host);
        database.set("mysql.port", port); database.set("mysql.database", schema);
        database.set("mysql.username", "root"); database.set("mysql.password", password);
        database.set("mysql.ssl-mode", "DISABLED");
        StatsSettings stats = new StatsSettings(database);
        String url = "jdbc:mysql://" + host + ":" + port + "/" + schema + "?sslMode=DISABLED";
        try (RankedRepository ranked = new RankedRepository()) {
            ranked.open(stats);
            try (Connection c = DriverManager.getConnection(url, "root", password);
                 Statement sql = c.createStatement()) {
                sql.execute("CREATE TABLE IF NOT EXISTS sm_ranked_allocations"
                    + " (allocation_id CHAR(36) PRIMARY KEY,state VARCHAR(16),expires_at BIGINT)");
                sql.execute("CREATE TABLE IF NOT EXISTS sbw_profiles"
                    + " (uuid CHAR(36) PRIMARY KEY,last_name VARCHAR(32))");
            }
            RankedSeasonRepository seasons = new RankedSeasonRepository(ranked);
            String id = "test_" + UUID.randomUUID().toString().substring(0, 8);
            String reward = "title_" + id;
            YamlConfiguration proposed = new YamlConfiguration();
            long now = System.currentTimeMillis();
            proposed.set("enabled", true); proposed.set("current.id", id);
            proposed.set("current.name", "Integration Test");
            proposed.set("current.starts-at", Instant.ofEpochMilli(now + 2000).toString());
            proposed.set("current.ends-at", Instant.ofEpochMilli(now + 120000).toString());
            proposed.set("current.modes.duo", false); proposed.set("current.modes.trio", false);
            proposed.set("current.modes.squad", false);
            proposed.set("current.placement-required", 5);
            proposed.set("current.rewards.enabled", true);
            proposed.set("current.rewards.minimum-games", 5);
            Map<String, Object> title = new HashMap<String, Object>();
            title.put("id", reward); title.put("pt_BR", "Ouro Teste");
            title.put("en_US", "Test Gold"); title.put("es_ES", "Oro Prueba");
            title.put("minimum-rating", 1000); title.put("maximum-position", 2);
            title.put("minimum-games", 5);
            proposed.set("current.rewards.titles", Arrays.asList(title));
            RankedSeasonSettings proposal = new RankedSeasonSettings(proposed);
            RankedSettings rules = new RankedSettings(YamlConfiguration.loadConfiguration(
                new File("src/main/resources/ranked.yml")));
            RankedSeason draft = seasons.create(proposal, rules, "junit");
            assertEquals(RankedSeason.State.DRAFT, draft.state);
            assertEquals(RankedSeason.State.SCHEDULED,
                seasons.transition(id, RankedSeason.State.SCHEDULED, "junit").state);
            try (Connection c = DriverManager.getConnection(url, "root", password);
                 PreparedStatement policy = c.prepareStatement(
                     "INSERT INTO sbw_ranked_policy(server_id,instance_id,enabled,context,"
                     + "initial_rating,updated_at,season_version,pending_results,active_matches)"
                     + " VALUES('test','00000000-0000-0000-0000-000000000001',0,'preseason-1',"
                     + "1000,?,0,0,0) ON DUPLICATE KEY UPDATE updated_at=VALUES(updated_at),"
                     + "pending_results=0,active_matches=0")) {
                policy.setLong(1, System.currentTimeMillis()); policy.executeUpdate();
            }
            Thread.sleep(2200);
            RankedSeason active = seasons.transition(id, RankedSeason.State.ACTIVE, "junit");
            assertEquals(RankedSeason.State.ACTIVE, active.state);
            UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
            try (Connection c = DriverManager.getConnection(url, "root", password);
                 PreparedStatement profile = c.prepareStatement(
                     "INSERT INTO sbw_ranked_profiles(player_uuid,mode,context,rating,peak_rating,"
                     + "games,wins,losses,draws,placement_games,last_match_at,updated_at)"
                     + " VALUES(?,'solo',?,?,?,10,6,4,0,5,?,?)");
                 PreparedStatement name = c.prepareStatement("INSERT INTO sbw_profiles VALUES(?,?)")) {
                int i = 0;
                for (UUID player : Arrays.asList(first, second, third)) {
                    profile.setString(1, player.toString()); profile.setString(2, id);
                    profile.setInt(3, i == 2 ? 900 : 1200);
                    profile.setInt(4, i == 2 ? 900 : 1250);
                    profile.setLong(5, now); profile.setLong(6, now); profile.executeUpdate();
                    name.setString(1, player.toString()); name.setString(2, "Player" + i);
                    name.executeUpdate(); i++;
                }
            }
            assertEquals(2, seasons.leaderboard(id, "solo", 2, 1).size());
            assertEquals(1, seasons.leaderboard(id, "solo", 2, 2).size());
            assertNotNull(seasons.position(first, id, "solo"));
            YamlConfiguration competing = new YamlConfiguration();
            String competingId = "wait_" + UUID.randomUUID().toString().substring(0, 8);
            competing.set("current.id", competingId);
            competing.set("current.starts-at", Instant.ofEpochMilli(now + 5000).toString());
            competing.set("current.ends-at", Instant.ofEpochMilli(now + 180000).toString());
            seasons.create(new RankedSeasonSettings(competing), rules, "junit");
            seasons.transition(competingId, RankedSeason.State.SCHEDULED, "junit");
            Thread.sleep(Math.max(0L, now + 5050 - System.currentTimeMillis()));
            try {
                seasons.transition(competingId, RankedSeason.State.ACTIVE, "junit");
                fail("A second season must not become active");
            } catch (IllegalStateException expected) { /* Central control is occupied. */ }
            seasons.transition(id, RankedSeason.State.CLOSING, "junit");
            UUID lateA = UUID.randomUUID(), lateB = UUID.randomUUID();
            RankedMatchSnapshot late = new RankedMatchSnapshot(UUID.randomUUID(), "solo",
                "test-arena", "test", "00000000-0000-0000-0000-000000000001", id, "red",
                now + 2000, System.currentTimeMillis(), false, Arrays.asList(
                    new RankedMatchSnapshot.Member(lateA, "LateA", "red", false),
                    new RankedMatchSnapshot.Member(lateB, "LateB", "blue", false)),
                rules.initial, rules.minimum, rules.kFactor);
            assertTrue(ranked.apply(late, rules).newResult);
            assertFalse(ranked.apply(late, rules).newResult);
            assertEquals(1, ranked.profile(lateA, "solo", id, rules.initial).games);
            try (Connection c = DriverManager.getConnection(url, "root", password);
                 PreparedStatement policy = c.prepareStatement(
                     "UPDATE sbw_ranked_policy SET context=?,season_version=?,enabled=0,"
                     + "pending_results=0,active_matches=0,updated_at=? WHERE server_id='test'")) {
                policy.setString(1, id); policy.setLong(2, seasons.find(id).version);
                policy.setLong(3, System.currentTimeMillis()); policy.executeUpdate();
            }
            try (Connection c = DriverManager.getConnection(url, "root", password);
                 Statement pending = c.createStatement()) {
                pending.executeUpdate("UPDATE sbw_ranked_policy SET pending_results=1 WHERE server_id='test'");
            }
            try {
                seasons.transition(id, RankedSeason.State.FINALIZING, "junit");
                fail("Pending outbox must block finalization");
            } catch (IllegalStateException expected) { /* Fail closed. */ }
            try (Connection c = DriverManager.getConnection(url, "root", password);
                 Statement cleared = c.createStatement()) {
                cleared.executeUpdate("UPDATE sbw_ranked_policy SET pending_results=0 WHERE server_id='test'");
            }
            seasons.transition(id, RankedSeason.State.FINALIZING, "junit");
            try (Connection c = DriverManager.getConnection(url, "root", password);
                 PreparedStatement policy = c.prepareStatement(
                     "UPDATE sbw_ranked_policy SET season_version=?,updated_at=? WHERE server_id='test'")) {
                policy.setLong(1, seasons.find(id).version);
                policy.setLong(2, System.currentTimeMillis()); policy.executeUpdate();
            }
            assertEquals(RankedSeason.State.FINISHED, seasons.finalizeSeason(id, "junit").state);
            assertEquals(RankedSeason.State.FINISHED, seasons.finalizeSeason(id, "junit").state);
            java.util.List<RankedStanding> finalTop = seasons.leaderboard(id, "solo", 10, 1);
            assertEquals(3, finalTop.size());
            assertEquals(2, seasons.leaderboard(id, "solo", finalTop.get(0).divisionId,
                10, 1).size());
            assertEquals(1, seasons.history(lateA, 10, 1).size());
            assertEquals(0, seasons.history(lateA, 10, 1).get(0).position);
            assertNull(seasons.position(lateA, id, "solo"));
            assertTrue(finalTop.get(0).playerId.toString().compareTo(finalTop.get(1).playerId.toString()) < 0);
            assertEquals(1, seasons.history(first, 10, 1).size());
            java.util.List<RankedSeasonReward> claims = seasons.rewards(first, id);
            assertEquals(1, claims.size());
            assertEquals("CLAIMABLE", claims.get(0).state);
            assertTrue(seasons.claim(first, id, "solo", reward));
            assertFalse(seasons.claim(first, id, "solo", reward));
            assertEquals("CLAIMED", seasons.rewards(first, id).get(0).state);
            try (Connection c = DriverManager.getConnection(url, "root", password);
                 PreparedStatement changed = c.prepareStatement(
                     "UPDATE sbw_ranked_profiles SET rating=1500 WHERE player_uuid=?"
                     + " AND mode='solo' AND context=?")) {
                changed.setString(1, finalTop.get(0).playerId.toString());
                changed.setString(2, id); changed.executeUpdate();
            }
            assertEquals(finalTop.get(0).rating,
                seasons.leaderboard(id, "solo", 10, 1).get(0).rating);
            String nextId = "next_" + UUID.randomUUID().toString().substring(0, 8);
            YamlConfiguration next = new YamlConfiguration();
            next.set("current.id", nextId);
            next.set("current.starts-at", Instant.ofEpochMilli(now + 60000).toString());
            next.set("current.ends-at", Instant.ofEpochMilli(now + 180000).toString());
            next.set("current.reset.type", "SOFT_RESET");
            next.set("current.reset.source-season", id);
            next.set("current.reset.factor", 0.25D);
            RankedSeasonSettings nextProposal = new RankedSeasonSettings(next);
            seasons.create(nextProposal, rules, "junit");
            assertEquals(RankedSeason.State.SCHEDULED,
                seasons.transition(nextId, RankedSeason.State.SCHEDULED, "junit").state);
            int expectedSeed = (int) Math.round(rules.initial
                + (finalTop.get(0).rating - rules.initial) * 0.25D);
            assertEquals(expectedSeed,
                ranked.profile(finalTop.get(0).playerId, "solo", nextId, rules.initial).rating);
            assertEquals(rules.initial,
                ranked.profile(UUID.randomUUID(), "solo", nextId, rules.initial).rating);
            assertEquals(1500,
                ranked.profile(finalTop.get(0).playerId, "solo", id, rules.initial).rating);
        }
    }
}
