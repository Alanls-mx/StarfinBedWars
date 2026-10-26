package com.starfinplugins.bedwars.gameplay;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressionPurchaseGatewayTest {
    @Test public void blocksWithoutCurrencyAndRejectsOutOfOrderLevels() {
        ProgressionPurchaseGateway.Request attempt = new ProgressionPurchaseGateway.Request(
            UUID.randomUUID(), UUID.randomUUID(), ScopedProgress.Kind.KIT,
            "builder", "solo", "solo", 0, 1, 1000);
        AtomicReference<ProgressionPurchaseGateway.Result> result = new AtomicReference<>();
        ProgressionPurchaseGateway.unavailable().purchase(attempt, result::set);
        assertEquals(ProgressionPurchaseGateway.Result.UNAVAILABLE, result.get());
        for (int[] invalid : new int[][] {{1, 3}, {5, 6}, {0, 0}}) {
            try {
                new ProgressionPurchaseGateway.Request(UUID.randomUUID(), UUID.randomUUID(),
                    ScopedProgress.Kind.KIT, "builder", "solo", "solo",
                    invalid[0], invalid[1], 1000);
                fail("Accepted level " + invalid[0] + " -> " + invalid[1]);
            } catch (IllegalArgumentException expected) { }
        }
    }
}
