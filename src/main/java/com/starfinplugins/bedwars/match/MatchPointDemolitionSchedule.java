package com.starfinplugins.bedwars.match;

/**
 * Fraction of the originally indexed destructible arena blocks which must
 * have disappeared as Match Point progresses. Configurable two-milestone
 * piecewise-linear budget: default 80% at 5 minutes, 100% at 8 minutes.
 */
public final class MatchPointDemolitionSchedule {
    private final long partialMillis;
    private final long completeMillis;
    private final int partialPercent;

    public MatchPointDemolitionSchedule(int partialMinutes, int completeMinutes, int partialPercent) {
        if (partialMinutes < 1 || completeMinutes <= partialMinutes
                || partialPercent < 1 || partialPercent >= 100) {
            throw new IllegalArgumentException("Metas de destruicao do Match Point invalidas.");
        }
        this.partialMillis = partialMinutes * 60000L;
        this.completeMillis = completeMinutes * 60000L;
        this.partialPercent = partialPercent;
    }

    public long target(long elapsedMillis, long initialBlocks) {
        if (initialBlocks <= 0 || elapsedMillis <= 0) return 0L;
        if (elapsedMillis >= completeMillis) return initialBlocks;
        final double fraction;
        if (elapsedMillis <= partialMillis) {
            fraction = (partialPercent / 100.0D) * elapsedMillis / partialMillis;
        } else {
            fraction = partialPercent / 100.0D
                + ((100.0D - partialPercent) / 100.0D)
                * (elapsedMillis - partialMillis) / (completeMillis - partialMillis);
        }
        return Math.min(initialBlocks, (long) Math.ceil(fraction * initialBlocks));
    }

    public long getPartialMillis() { return partialMillis; }
    public long getCompleteMillis() { return completeMillis; }
}
