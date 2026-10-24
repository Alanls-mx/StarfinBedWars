package com.starfinplugins.bedwars.shop.special;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SpecialItemBalanceTest {
    @Test public void usesModerateGolemKnockbackReduction() {
        assertEquals(0.75D, SpecialItemBalance.knockbackMultiplier(0.75D, 0.75D), 0.0D);
        assertEquals(0.85D, SpecialItemBalance.knockbackMultiplier(0.85D, 0.85D), 0.0D);
    }

    @Test public void oneRestoresVanillaBehavior() {
        assertEquals(1.0D, SpecialItemBalance.knockbackMultiplier(1.0D, 0.75D), 0.0D);
    }

    @Test public void clampsOutOfBounds() {
        assertEquals(1.0D, SpecialItemBalance.knockbackMultiplier(3.0D, 0.75D), 0.0D);
        assertEquals(0.0D, SpecialItemBalance.knockbackMultiplier(-1.0D, 0.75D), 0.0D);
    }

    @Test public void nonFiniteValuesUseDefaults() {
        assertEquals(0.75D, SpecialItemBalance.knockbackMultiplier(
            Double.NaN, 0.75D), 0.0D);
        assertEquals(0.85D, SpecialItemBalance.knockbackMultiplier(
            Double.POSITIVE_INFINITY, 0.85D), 0.0D);
    }
}
