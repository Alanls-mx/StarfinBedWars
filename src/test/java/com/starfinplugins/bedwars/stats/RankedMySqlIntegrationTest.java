package com.starfinplugins.bedwars.stats;

import com.starfinplugins.bedwars.ranked.RankedMatchSnapshot;
import com.starfinplugins.bedwars.ranked.RankedProfile;
import com.starfinplugins.bedwars.ranked.RankedRepository;
import com.starfinplugins.bedwars.ranked.RankedSettings;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/** Explicit disposable schema only; no destructive query ever reaches production. */
public class RankedMySqlIntegrationTest {
    private static RankedMatchSnapshot match(UUID id, UUID winner, UUID loser,
                                             String mode, boolean abandoned) {
        return new RankedMatchSnapshot(id, mode, "arena", "server", "instance", "preseason-1",
            "RED", System.currentTimeMillis() - 1000L, System.currentTimeMillis(), false, Arrays.asList(
                new RankedMatchSnapshot.Member(winner, "Winner", "RED", abandoned),
                new RankedMatchSnapshot.Member(loser, "Loser", "BLUE", false)), 1000, 0, 24);
    }
    @Test public void concurrentIdempotentTransactionalRatingsAndPlacements() throws Exception {
        String host = System.getenv("SBW_RANKED_TEST_DB_HOST");
        String schema = System.getenv("SBW_RANKED_TEST_DB_SCHEMA");
        Assume.assumeTrue(host != null && schema != null && schema.startsWith("sbw_ranked_test_"));
        String user = System.getenv("SBW_RANKED_TEST_DB_USER");
        String password = System.getenv("SBW_RANKED_TEST_DB_PASSWORD");
        String port = System.getenv("SBW_RANKED_TEST_DB_PORT");
        if (port == null) port = "3306";
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true); yaml.set("mysql.host", host);
        yaml.set("mysql.port", Integer.parseInt(port)); yaml.set("mysql.database", schema);
        yaml.set("mysql.username", user); yaml.set("mysql.password", password);
        yaml.set("mysql.ssl-mode", "DISABLED");
        StatsSettings database = new StatsSettings(yaml);
        RankedSettings rules = new RankedSettings(YamlConfiguration.loadConfiguration(
            new File("src/main/resources/ranked.yml")));
        String url = "jdbc:mysql://" + host + ":" + port + "/" + schema + "?sslMode=DISABLED";
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement sql = connection.createStatement()) {
            for (String table : Arrays.asList("sbw_ranked_results", "sbw_ranked_matches",
                    "sbw_ranked_profiles", "sbw_ranked_schema_migrations", "sbw_profiles"))
                sql.execute("DROP TABLE IF EXISTS " + table);
            sql.execute("CREATE TABLE sbw_profiles(uuid CHAR(36) PRIMARY KEY,last_name VARCHAR(16),"
                + "updated_at BIGINT) ENGINE=InnoDB");
            sql.execute("INSERT INTO sbw_profiles VALUES('00000000-0000-0000-0000-000000000001','Legacy',1)");
        }
        UUID winner = UUID.randomUUID(), loser = UUID.randomUUID();
        try (RankedRepository first = new RankedRepository(); RankedRepository second = new RankedRepository()) {
            first.open(database); second.open(database);
            assertEquals(1000, first.profile(winner, "solo", rules.context, rules.initial).rating);
            for (int i = 1; i <= 5; i++) {
                RankedMatchSnapshot current = match(UUID.randomUUID(), winner, loser, "solo", false);
                assertTrue(first.apply(current, rules).newResult);
                assertFalse(second.apply(current, rules).newResult);
                assertEquals(i, first.profile(winner, "solo", rules.context, rules.initial).placementGames);
            }
            RankedProfile placed = first.profile(winner, "solo", rules.context, rules.initial);
            assertTrue(placed.isPlaced(rules.placements));
            assertEquals(5, placed.wins);
            assertEquals(0, placed.losses);
            assertEquals(1000, first.profile(winner, "duo", rules.context, rules.initial).rating);
            assertTrue(first.apply(match(UUID.randomUUID(), winner, loser, "duo", false), rules).newResult);
            assertEquals(1, first.profile(winner, "duo", rules.context, rules.initial).games);
            ExecutorService threads = Executors.newFixedThreadPool(2);
            try {
                Future<?> a = threads.submit(() -> {
                    try { first.apply(match(UUID.randomUUID(), winner, loser, "solo", false), rules); }
                    catch (Exception error) { throw new RuntimeException(error); }
                });
                Future<?> b = threads.submit(() -> {
                    try { second.apply(match(UUID.randomUUID(), winner, loser, "solo", false), rules); }
                    catch (Exception error) { throw new RuntimeException(error); }
                });
                a.get(); b.get();
            } finally { threads.shutdownNow(); }
            assertEquals(7, first.profile(winner, "solo", rules.context, rules.initial).games);
            RankedMatchSnapshot abandoned = match(UUID.randomUUID(), winner, loser, "solo", true);
            RankedRepository.Applied abandonment = first.apply(abandoned, rules);
            assertEquals("LOSS", abandonment.results.stream()
                .filter(result -> result.playerId.equals(winner)).findFirst().get().outcome);
            assertEquals(8, first.profile(winner, "solo", rules.context, rules.initial).games);
            RankedMatchSnapshot secondAbandon = match(UUID.randomUUID(), winner, loser, "solo", true);
            RankedMatchSnapshot thirdAbandon = match(UUID.randomUUID(), winner, loser, "solo", true);
            assertTrue(first.apply(secondAbandon, rules).newResult);
            assertTrue(first.apply(thirdAbandon, rules).newResult);
            assertFalse(second.apply(thirdAbandon, rules).newResult);
            assertEquals(10, first.profile(winner, "solo", rules.context, rules.initial).games);
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 java.sql.PreparedStatement query = connection.prepareStatement(
                    "SELECT until_at FROM sbw_ranked_suspensions WHERE player_uuid=? AND context=?")) {
                query.setString(1, winner.toString()); query.setString(2, rules.context);
                try (ResultSet row = query.executeQuery()) {
                    assertTrue(row.next()); assertTrue(row.getLong(1) > System.currentTimeMillis());
                }
            }
        }
        RankedMatchSnapshot failed = match(UUID.randomUUID(), winner, loser, "solo", false);
        try (RankedRepository broken = new RankedRepository()) {
            broken.open(database); // Recreates the table; remove it after opening to inject failure.
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement sql = connection.createStatement()) { sql.execute("DROP TABLE sbw_ranked_results"); }
            try { broken.apply(failed, rules); fail("Expected SQL failure"); }
            catch (java.sql.SQLException expected) { /* Entire match transaction rolled back. */ }
            assertEquals(10, broken.profile(winner, "solo", rules.context, rules.initial).games);
        }
        try (RankedRepository recovered = new RankedRepository()) {
            recovered.open(database);
            assertTrue(recovered.apply(failed, rules).newResult);
            assertFalse(recovered.apply(failed, rules).newResult);
            assertEquals(11, recovered.profile(winner, "solo", rules.context, rules.initial).games);
        }
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement sql = connection.createStatement();
             ResultSet row = sql.executeQuery("SELECT COUNT(*) FROM sbw_profiles")) {
            assertTrue(row.next()); assertEquals(1, row.getInt(1));
        }
    }
}
