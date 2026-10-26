package com.starfinplugins.bedwars.stats;

import com.starfinplugins.bedwars.gameplay.ProgressionPurchaseGateway;
import com.starfinplugins.bedwars.gameplay.ScopedProgress;
import com.starfinplugins.bedwars.stats.MySqlStatsRepository.PurchaseState;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/** Two repositories represent two BedWars servers sharing one disposable MySQL schema. */
public class VaultPurchaseMySqlIntegrationTest {
    @Test public void serializesChargesAndCommitsOnlyOnceAcrossServers() throws Exception {
        String host = System.getenv("SBW_TEST_DB_HOST");
        String schema = System.getenv("SBW_TEST_DB_SCHEMA");
        Assume.assumeTrue(host != null && schema != null && schema.startsWith("sbw_phase2_test_"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("mysql.host", host);
        yaml.set("mysql.port", Integer.parseInt(System.getenv("SBW_TEST_DB_PORT")));
        yaml.set("mysql.database", schema);
        yaml.set("mysql.username", System.getenv("SBW_TEST_DB_USER"));
        yaml.set("mysql.password", System.getenv("SBW_TEST_DB_PASSWORD"));
        yaml.set("mysql.ssl-mode", "REQUIRED");
        StatsSettings settings = new StatsSettings(yaml);
        UUID player = UUID.randomUUID();
        ProgressionPurchaseGateway.Request solo = request(player, "solo", 0);
        ProgressionPurchaseGateway.Request duo = request(player, "duo", 0);
        try (MySqlStatsRepository first = new MySqlStatsRepository();
             MySqlStatsRepository second = new MySqlStatsRepository()) {
            first.open(settings);
            second.open(settings);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            PurchaseState a, b;
            try {
                Future<PurchaseState> one = pool.submit(() -> {
                    start.await(); return first.reservePurchase(solo, "TestEconomy");
                });
                Future<PurchaseState> two = pool.submit(() -> {
                    start.await(); return second.reservePurchase(duo, "TestEconomy");
                });
                start.countDown();
                a = one.get(); b = two.get();
            } finally { pool.shutdownNow(); }
            assertTrue((a == PurchaseState.RESERVED && b == PurchaseState.CONFLICT)
                || (a == PurchaseState.CONFLICT && b == PurchaseState.RESERVED));
            ProgressionPurchaseGateway.Request winner = a == PurchaseState.RESERVED ? solo : duo;
            ProgressionPurchaseGateway.Request loser = a == PurchaseState.RESERVED ? duo : solo;
            MySqlStatsRepository owner = a == PurchaseState.RESERVED ? first : second;
            assertEquals(PurchaseState.ALREADY_RESERVED,
                owner.reservePurchase(winner, "TestEconomy"));
            assertEquals(PurchaseState.CHARGED, owner.markPurchaseCharged(winner));
            assertEquals(PurchaseState.COMMITTED, owner.completePurchase(winner));
            assertEquals(PurchaseState.COMMITTED, owner.completePurchase(winner));
            assertEquals(1, second.loadScopedProgress(player)
                .level(ScopedProgress.Kind.KIT, "builder", winner.mode));
            assertEquals(PurchaseState.RESERVED, second.reservePurchase(loser, "TestEconomy"));
            assertEquals(PurchaseState.ABORTED, second.closePurchase(loser, PurchaseState.ABORTED));
            assertEquals(PurchaseState.ABORTED, second.purchaseStatus(loser.transactionId));
            ProgressionPurchaseGateway.Request upgrade = request(player, winner.mode, 1);
            assertEquals(PurchaseState.RESERVED, first.reservePurchase(upgrade, "TestEconomy"));
            assertEquals(PurchaseState.CHARGED, first.markPurchaseCharged(upgrade));
            assertEquals(PurchaseState.REFUND_PENDING,
                first.closePurchase(upgrade, PurchaseState.REFUND_PENDING));
            assertEquals(PurchaseState.CONFLICT, second.reservePurchase(
                request(player, winner.mode, 1), "TestEconomy"));
            assertEquals(PurchaseState.REFUNDED,
                first.closePurchase(upgrade, PurchaseState.REFUNDED));
            ProgressionPurchaseGateway.Request racingGrant = request(player, winner.mode, 1);
            assertEquals(PurchaseState.RESERVED,
                second.reservePurchase(racingGrant, "TestEconomy"));
            assertEquals(2, first.grantScoped(player, ScopedProgress.Kind.KIT,
                "builder", winner.mode, 2));
            assertEquals(PurchaseState.CHARGED, second.markPurchaseCharged(racingGrant));
            assertEquals(PurchaseState.CONFLICT, second.completePurchase(racingGrant));
            assertEquals(PurchaseState.REFUNDED,
                second.closePurchase(racingGrant, PurchaseState.REFUNDED));
            assertEquals(2, first.loadScopedProgress(player)
                .level(ScopedProgress.Kind.KIT, "builder", winner.mode));
        }
    }

    private static ProgressionPurchaseGateway.Request request(UUID player, String mode, int level) {
        return new ProgressionPurchaseGateway.Request(UUID.randomUUID(), player,
            ScopedProgress.Kind.KIT, "builder", mode, mode, level, level + 1, 100);
    }
}
