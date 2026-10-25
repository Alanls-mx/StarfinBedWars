package com.starfinplugins.bedwars.match;

/** Small testable rule used before scheduling a TNT placement for priming. */
public final class TntPlacementPolicy {
    private TntPlacementPolicy() { }

    public static boolean shouldPrime(boolean inGame, boolean ownsMatch, boolean connected,
                                      boolean notSpectating, boolean canBuild) {
        return inGame && ownsMatch && connected && notSpectating && canBuild;
    }
}
