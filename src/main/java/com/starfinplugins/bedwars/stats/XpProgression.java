package com.starfinplugins.bedwars.stats;

/** Level is derived from total XP, never maintained as a second mutable counter. */
public final class XpProgression {
    private final long base;
    private final long increment;

    public XpProgression(long base, long increment) {
        if (base < 1 || base > 1000000000L || increment < 0 || increment > 10000000L) {
            throw new IllegalArgumentException("Curva de XP invalida");
        }
        this.base = base;
        this.increment = increment;
    }

    public Progress progress(long totalXp) {
        long remaining = Math.max(0L, totalXp);
        int level = 1;
        long required = base;
        // Cap protects against overflow and implausible levels in corrupted data.
        while (remaining >= required && level < 10000) {
            remaining -= required;
            level++;
            required = increment > (Long.MAX_VALUE - base) / (level - 1L)
                ? Long.MAX_VALUE : base + increment * (level - 1L);
        }
        return new Progress(level, remaining, required);
    }

    public static final class Progress {
        public final int level;
        public final long current;
        public final long required;

        Progress(int level, long current, long required) {
            this.level = level;
            this.current = current;
            this.required = required;
        }
    }
}
