package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class MatchPointSurfaceSearchTest {
    @Test public void findsFloatingIslandFarBelowSkyNotJustTwelveBlocks() {
        // A 1.8 heightmap can report Y=180 despite island platform at Y=90.
        assertEquals(90, MatchPointSurfaceSearch.highest(30, 180, y -> y == 90));
    }

    @Test public void honorsTransparentBuildsAndChangesAfterBlast() {
        final boolean[] present = new boolean[256];
        present[100] = true; // e.g. a glass/wool bridge
        present[98] = true;  // island base
        assertEquals(100, MatchPointSurfaceSearch.highest(30, 180, y -> present[y]));
        present[100] = false;
        assertEquals(98, MatchPointSurfaceSearch.highest(30, 180, y -> present[y]));
    }

    @Test public void emptyColumnNeverCreatesAnExplosiveVisual() {
        assertEquals(-1, MatchPointSurfaceSearch.highest(30, 180, y -> false));
    }

    @Test public void neverTouchesBlocksOutsideSelectedArenaHeight() {
        assertEquals(-1, MatchPointSurfaceSearch.highest(70, 105,
            y -> y == 60 || y == 130));
    }

    @Test public void includesMinAndMaxLayers() {
        assertEquals(110, MatchPointSurfaceSearch.highest(70, 110, y -> y == 110));
        assertEquals(70, MatchPointSurfaceSearch.highest(70, 110, y -> y == 70));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBadBounds() {
        MatchPointSurfaceSearch.highest(140, 70, y -> true);
    }
}
