package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.Position;
import java.util.Arrays;
import org.junit.Test;

public class MatchPointPathTest {
    @Test public void flightStaysInBoundsAndVisitsIslandLocations() {
        ArenaRegion bounds = new ArenaRegion("world", -140, 0, -140, 140, 180, 140);
        MatchPointPath path = new MatchPointPath(bounds, 180, 105, Arrays.asList(
            new Position("world", -90, 100, 0, 0, 0),
            new Position("world", 90, 100, 0, 0, 0),
            new Position("world", 0, 100, -90, 0, 0),
            new Position("world", 0, 100, 90, 0, 0)));
        for (int step = 0; step < 360; step++) {
            double[] position = path.at(Math.toRadians(step));
            assertTrue(position[0] >= -140 && position[0] <= 140);
            assertTrue(position[2] >= -140 && position[2] <= 140);
            assertTrue(position[1] > 0 && position[1] < 180);
        }
        double[] eastern = path.at(0);
        assertEquals(90.0D, eastern[0], 0.001D);
        assertEquals(105.0D, eastern[1], 0.001D);
    }

    @Test public void twoAlignedIslandsGetAFullLoopRatherThanFourBlockStrip() {
        ArenaRegion bounds = new ArenaRegion("world", -140, 0, -140, 140, 180, 140);
        MatchPointPath path = new MatchPointPath(bounds, 180, 108, Arrays.asList(
            new Position("world", -90, 105, 0, 0, 0),
            new Position("world", 90, 105, 0, 0, 0)));
        // Old implementation had a radius of 4 blocks on the other axis,
        // causing the dragon to writhe in a narrow turnaround.
        assertTrue("Dragon must sweep across the central island area",
            path.at(Math.PI / 2.0D)[2] > 35.0D);
        assertTrue(path.at(3.0D * Math.PI / 2.0D)[2] < -35.0D);
        assertTrue(path.at(0)[0] > 80.0D);
        assertTrue(path.at(Math.PI)[0] < -80.0D);
        for (int step = 0; step < 360; step++) {
            double[] p = path.at(Math.toRadians(step));
            assertTrue(p[0] > bounds.getMinimumX());
            assertTrue(p[0] < bounds.getMaximumX());
            assertTrue(p[2] > bounds.getMinimumZ());
            assertTrue(p[2] < bounds.getMaximumZ());
            assertTrue(p[1] < 180);
        }
    }

    @Test public void fourCornerIslandsFallNearTheDragonsPatrol() {
        ArenaRegion bounds = new ArenaRegion("world", -140, 0, -140, 140, 180, 140);
        MatchPointPath path = new MatchPointPath(bounds, 180, 108, Arrays.asList(
            new Position("world", -90, 105, -90, 0, 0),
            new Position("world", 90, 105, -90, 0, 0),
            new Position("world", 90, 105, 90, 0, 0),
            new Position("world", -90, 105, 90, 0, 0)));
        // At 45 degrees the old ellipse was at (63,63), far from the island
        // at (90,90). The expanded loop now passes near both coordinates.
        double[] northEast = path.at(Math.PI / 4.0D);
        assertTrue(northEast[0] > 85.0D);
        assertTrue(northEast[2] > 85.0D);
        assertTrue(northEast[0] < 129.0D);
        assertTrue(northEast[2] < 129.0D);
    }

    @Test public void twoIslandsAlongZStillHaveHorizontalBreathingRoom() {
        ArenaRegion bounds = new ArenaRegion("world", -140, 0, -140, 140, 180, 140);
        MatchPointPath path = new MatchPointPath(bounds, 180, 108, Arrays.asList(
            new Position("world", 0, 105, -75, 0, 0),
            new Position("world", 0, 105, 75, 0, 0)));
        assertTrue(path.at(0.0D)[0] > 30.0D);
        assertTrue(path.at(Math.PI)[0] < -30.0D);
    }

    @Test public void supportsTinyArenasWithoutEscapingBounds() {
        ArenaRegion bounds = new ArenaRegion("world", 0, 0, 0, 5, 100, 5);
        MatchPointPath path = new MatchPointPath(bounds, 100, 90);
        for (int i = 0; i < 40; i++) {
            double[] p = path.at(i);
            assertTrue(p[0] >= 0 && p[0] <= 6);
            assertTrue(p[2] >= 0 && p[2] <= 6);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void requiresArenaBounds() {
        new MatchPointPath(null, 255, 90);
    }
}
