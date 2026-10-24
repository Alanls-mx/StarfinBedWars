package com.starfinplugins.bedwars.match;

import java.util.Set;

/**
 * Pure predicate for match-owned, player-placed blocks.
 * The match engine performs additional checks for protected beds.
 */
public final class ExplosionBlockPolicy {
    private ExplosionBlockPolicy() { }

    public static boolean canDestroy(boolean matchPlaying, Set<String> playerPlacedKeys,
                                     String blockKey) {
        return matchPlaying && playerPlacedKeys != null && blockKey != null
            && playerPlacedKeys.contains(blockKey);
    }
}
