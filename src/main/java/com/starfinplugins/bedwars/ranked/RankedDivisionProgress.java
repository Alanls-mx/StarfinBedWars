package com.starfinplugins.bedwars.ranked;

/** Division thresholds captured for the queried competitive context. */
public final class RankedDivisionProgress {
    public final String current, next;
    public final int pointsToNext;
    public RankedDivisionProgress(String current, String next, int pointsToNext) {
        this.current = current; this.next = next; this.pointsToNext = pointsToNext;
    }
}
