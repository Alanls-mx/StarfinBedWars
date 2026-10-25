package com.starfinplugins.bedwars.stats;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/** Run against an explicitly disposable MariaDB schema; never touches the live player database. */
public class Phase2MySqlIntegrationTest {
    @Test public void migratesAndSerializesConcurrentResultsAndRewards() throws Exception {
        String host = System.getenv("SBW_TEST_DB_HOST");
        String database = System.getenv("SBW_TEST_DB_SCHEMA");
        Assume.assumeTrue(host != null && database != null && database.startsWith("sbw_phase2_test_"));
        String user = System.getenv("SBW_TEST_DB_USER");
        String password = System.getenv("SBW_TEST_DB_PASSWORD");
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("enabled", true);
        yml.set("mysql.host", host);
        yml.set("mysql.port", 3306);
        yml.set("mysql.database", database);
        yml.set("mysql.username", user);
        yml.set("mysql.password", password);
        yml.set("mysql.ssl-mode", "DISABLED");
        yml.set("levels.base-xp", 100);
        yml.set("levels.increase-per-level", 0);
        StatsSettings settings = new StatsSettings(yml);
        String url = "jdbc:mysql://" + host + ":3306/" + database + "?sslMode=DISABLED";
        UUID player = UUID.randomUUID();
        UUID oldMatch = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement old = connection.createStatement()) {
            old.execute("DROP TABLE IF EXISTS sbw_reward_unlocks");
            old.execute("DROP TABLE IF EXISTS sbw_player_preferences");
            old.execute("DROP TABLE IF EXISTS sbw_match_results");
            old.execute("DROP TABLE IF EXISTS sbw_profiles");
            old.execute("CREATE TABLE sbw_profiles (uuid CHAR(36) PRIMARY KEY,last_name VARCHAR(16),"
                + "games BIGINT DEFAULT 0,wins BIGINT DEFAULT 0,losses BIGINT DEFAULT 0,draws BIGINT DEFAULT 0,"
                + "kills BIGINT DEFAULT 0,final_kills BIGINT DEFAULT 0,deaths BIGINT DEFAULT 0,beds BIGINT DEFAULT 0,"
                + "total_xp BIGINT DEFAULT 0,updated_at BIGINT DEFAULT 0)");
            old.execute("CREATE TABLE sbw_match_results (match_id CHAR(36),player_uuid CHAR(36),"
                + "outcome VARCHAR(8),kills INT,final_kills INT,deaths INT,beds INT,earned_xp BIGINT,"
                + "created_at BIGINT,PRIMARY KEY(match_id,player_uuid))");
            old.execute("CREATE TABLE sbw_player_preferences (uuid CHAR(36) PRIMARY KEY,"
                + "language VARCHAR(5) DEFAULT 'pt_BR',quick_buy TEXT,updated_at BIGINT DEFAULT 0)");
            old.execute("INSERT INTO sbw_profiles (uuid,last_name,games,wins,total_xp) VALUES ('"
                + player + "','Legacy',1,1,50)");
            old.execute("INSERT INTO sbw_match_results"
                + " (match_id,player_uuid,outcome,kills,final_kills,deaths,beds,earned_xp,created_at)"
                + " VALUES ('" + oldMatch + "','" + player + "','WIN',0,0,0,0,50,"
                + Instant.now().minus(100, ChronoUnit.DAYS).toEpochMilli() + ")");
        }
        XpProgression progression = settings.progression;
        YamlConfiguration rewardYaml = YamlConfiguration.loadConfiguration(new StringReader(
            "levels:\n  2:\n    rewards:\n      - type: TITLE_UNLOCK\n        id: novice\n"
            + "        titles: {pt_BR: Novato, en_US: Novice, es_ES: Novato}\n"));
        RewardsCatalog rewards = RewardsCatalog.parse(rewardYaml, progression);
        try (MySqlStatsRepository first = new MySqlStatsRepository();
             MySqlStatsRepository second = new MySqlStatsRepository()) {
            first.open(settings);
            second.open(settings);
            assertFalse(first.selectTitle(player, "novice"));
            assertNull(first.loadPreferences(player, null).selectedTitle);
            MatchResult solo = new MatchResult(UUID.randomUUID(), player, "Legacy", 1, 0, 0, 0,
                0, MatchResult.Outcome.WIN, 120, "solo", System.currentTimeMillis());
            MatchResult duo = new MatchResult(UUID.randomUUID(), player, "Legacy", 1, 0, 0, 0,
                0, MatchResult.Outcome.LOSS, 120, "duo", System.currentTimeMillis());
            ExecutorService parallel = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<?> a = parallel.submit(() -> {
                    start.await(); first.saveMatch(Collections.singletonList(solo), rewards, progression); return null;
                });
                Future<?> b = parallel.submit(() -> {
                    start.await(); second.saveMatch(Collections.singletonList(duo), rewards, progression); return null;
                });
                start.countDown();
                a.get(); b.get();
            } finally { parallel.shutdownNow(); }
            StatsProfile profile = first.find(player.toString());
            assertEquals(3, profile.games);
            assertEquals(290, profile.totalXp);
            assertEquals(120, first.latestMatchXp(player));
            assertEquals(1, first.ensureRewards(player, rewards).size());
            assertTrue(first.selectTitle(player, "novice"));
            assertTrue(second.selectTitle(player, "novice"));
            assertEquals("novice", second.loadPreferences(player, null).selectedTitle);
            assertTrue(first.saveMatch(Collections.singletonList(solo), rewards, progression).isEmpty());
            assertEquals(3, second.find(player.toString()).games);
            RankingSpec global = new RankingSpec(RankingSpec.Metric.WINS, RankingSpec.Period.ALL,
                null, 1, 10, 50, Instant.now());
            assertEquals(2.0, first.ranking(global, player).viewer.value, 0.0001);
            UUID rival = UUID.randomUUID();
            for (int i = 0; i < 3; i++) {
                MatchResult win = new MatchResult(UUID.randomUUID(), rival, "Rival", 0, 0, 0, 0,
                    0, MatchResult.Outcome.WIN, 10, "solo", System.currentTimeMillis());
                first.saveMatch(Collections.singletonList(win), rewards, progression);
            }
            RankingSpec firstPage = new RankingSpec(RankingSpec.Metric.WINS, RankingSpec.Period.ALL,
                null, 1, 1, 50, Instant.now());
            RankingPage top = first.ranking(firstPage, player);
            assertEquals(2, top.totalPlayers);
            assertEquals(rival, top.entries.get(0).uuid);
            assertEquals(2, top.viewer.position);
            RankingSpec secondPage = new RankingSpec(RankingSpec.Metric.WINS, RankingSpec.Period.ALL,
                null, 2, 1, 50, Instant.now());
            assertEquals(player, first.ranking(secondPage, player).entries.get(0).uuid);
            RankingSpec soloRank = new RankingSpec(RankingSpec.Metric.GAMES, RankingSpec.Period.ALL,
                "solo", 1, 10, 50, Instant.now());
            assertEquals(2, first.ranking(soloRank, player).totalPlayers);
            assertEquals(1.0, first.ranking(soloRank, player).viewer.value, 0.0001);
            RankingSpec weekly = new RankingSpec(RankingSpec.Metric.GAMES, RankingSpec.Period.WEEKLY,
                null, 1, 10, 50, Instant.now());
            assertTrue(first.ranking(weekly, player).viewer.value >= 1);
            RankingSpec monthly = new RankingSpec(RankingSpec.Metric.GAMES, RankingSpec.Period.MONTHLY,
                null, 1, 10, 50, Instant.now());
            assertEquals(2.0, first.ranking(monthly, player).viewer.value, 0.0001);
            for (RankingSpec.Metric metric : RankingSpec.Metric.values()) {
                RankingSpec allTimeMetric = new RankingSpec(metric, RankingSpec.Period.ALL,
                    null, 1, 10, 50, Instant.now());
                assertNotNull(metric.name(), first.ranking(allTimeMetric, player).viewer);
                RankingSpec scopedMetric = new RankingSpec(metric, RankingSpec.Period.WEEKLY,
                    "solo", 1, 10, 50, Instant.now());
                assertNotNull(metric.name(), second.ranking(scopedMetric, player).viewer);
            }
            UUID historical = UUID.randomUUID();
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO sbw_profiles (uuid,last_name,total_xp) VALUES (?,?,?)")) {
                insert.setString(1, historical.toString());
                insert.setString(2, "OldLevel");
                insert.setLong(3, 500);
                insert.executeUpdate();
            }
            assertTrue(first.ensureRewards(historical, rewards).contains("novice"));
            assertEquals(1, second.ensureRewards(historical, rewards).size());
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 PreparedStatement query = connection.prepareStatement(
                     "SELECT mode FROM sbw_match_results WHERE match_id=?")) {
                query.setString(1, oldMatch.toString());
                try (ResultSet row = query.executeQuery()) {
                    assertTrue(row.next());
                    assertNull(row.getString(1));
                }
            }
        }
    }
}
