package com.starfinplugins.bedwars.gameplay;

/** Bounded effect calculations shared by gameplay listeners and tests. */
public final class AbilityEffectMath {
    private AbilityEffectMath() { }

    public static int respawnTicks(int base, double percent, int minimum) {
        return Math.min(base, Math.max(minimum, (int) Math.ceil(base * (1.0 - percent / 100.0))));
    }
    public static double reduceDamage(double original, double percent, double maximumReduction) {
        if (original <= 0) return original;
        return Math.max(0, original * (1.0 - Math.min(maximumReduction, percent / 100.0)));
    }
    public static double addDamage(double original, double percent) {
        return Math.max(0, original * (1.0 + Math.min(0.20, percent / 100.0)));
    }
    public static int fireSeconds(int original, double percent) {
        if (original <= 0) return original;
        return Math.max(1, (int) Math.ceil(original * (1.0 - percent / 100.0)));
    }
    public static int shopCost(int original, double percent, int minimum) {
        if (original <= 0) return original;
        return Math.min(original, Math.max(Math.max(1, minimum),
            (int) Math.ceil(original * (1.0 - percent / 100.0))));
    }
    public static double heal(double current, double maximum, double hearts) {
        return Math.min(maximum, current + 2.0 * hearts);
    }
    public static boolean succeeds(double percent, double roll) {
        return percent > 0 && roll >= 0 && roll < percent / 100.0;
    }
}
