package com.starfinplugins.bedwars.match;

import java.util.function.IntPredicate;

/**
 * Minecraft 1.8 floating arenas can have hundreds of air blocks above each
 * island. Vanilla heightmaps may also ignore glass, wool or floating builds.
 * Search the ENTIRE configured Y-column instead of the previous 12 blocks.
 */
public final class MatchPointSurfaceSearch {
    private MatchPointSurfaceSearch() { }

    public static int highest(int minimumY, int maximumY, IntPredicate present) {
        if (present == null || minimumY < 0 || maximumY > 255 || minimumY > maximumY) {
            throw new IllegalArgumentException("Coluna da arena invalida.");
        }
        for (int y = maximumY; y >= minimumY; y--) {
            if (present.test(y)) return y;
        }
        return -1;
    }
}
