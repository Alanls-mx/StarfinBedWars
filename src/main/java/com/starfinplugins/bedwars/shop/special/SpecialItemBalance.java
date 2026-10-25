package com.starfinplugins.bedwars.shop.special;

/** Pure validation of configurable PvP/ability balance values. */
public final class SpecialItemBalance {
    private SpecialItemBalance() { }

    /**
     * Never amplify vanilla knockback: 1.0 preserves it and 0.0 cancels it.
     * Invalid or nonfinite settings fall back to a conservative default.
     */
    public static double knockbackMultiplier(double configured, double defaultValue) {
        if (Double.isNaN(configured) || Double.isInfinite(configured)) return defaultValue;
        return Math.max(0.0D, Math.min(1.0D, configured));
    }
}
