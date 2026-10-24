package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class MatchPointPatrolRouteTest {
    @Test public void visitsEntireManualPos1Pos2NotJustTwoTeamSpawns() {
        ArenaRegion main = new ArenaRegion("world", -140, 50, -140, 140, 155, 140);
        MatchPointPatrolRoute route = new MatchPointPatrolRoute(main, 155, 106, 40);
        assertTrue("A full sized map needs waypoints across multiple sectors",
            route.size() >= 40);
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        boolean centerVisited = false;
        Set<String> locations = new HashSet<String>();
        for (int i = 0; i < route.size(); i++) {
            double[] xyz = route.at(i);
            minX = Math.min(minX, xyz[0]);
            maxX = Math.max(maxX, xyz[0]);
            minZ = Math.min(minZ, xyz[2]);
            maxZ = Math.max(maxZ, xyz[2]);
            assertTrue(xyz[0] >= main.getMinimumX() && xyz[0] <= main.getMaximumX() + 1);
            assertTrue(xyz[2] >= main.getMinimumZ() && xyz[2] <= main.getMaximumZ() + 1);
            assertTrue(xyz[1] >= 50 && xyz[1] <= 155);
            if (Math.abs(xyz[0]) < 25 && Math.abs(xyz[2]) < 25) centerVisited = true;
            assertTrue("The route should not visit duplicate grid waypoints",
                locations.add(xyz[0] + ":" + xyz[2]));
        }
        assertTrue("Dragon must reach far-west and east sectors", minX < -100 && maxX > 100);
        assertTrue("Dragon must reach far-north and south sectors", minZ < -100 && maxZ > 100);
        assertTrue("Dragon must also visit the center of the map", centerVisited);
        assertEquals(route.at(0)[0], route.at(route.size())[0], 0.0001D);
        assertEquals(route.at(0)[2], route.at(route.size())[2], 0.0001D);
    }

    @Test public void serpentineRouteDoesNotTeleportBetweenEachRow() {
        ArenaRegion main = new ArenaRegion("world", 0, 30, 0, 90, 140, 90);
        MatchPointPatrolRoute route = new MatchPointPatrolRoute(main, 140, 90, 24);
        for (int i = 1; i < route.size(); i++) {
            double[] a = route.at(i - 1), b = route.at(i);
            double dx = a[0] - b[0], dz = a[2] - b[2];
            assertTrue("Consecutive waypoints need short manageable turns",
                dx * dx + dz * dz <= 24.1D * 24.1D);
        }
    }

    @Test public void respectsTinyRegionsAndVerticalCeiling() {
        ArenaRegion main = new ArenaRegion("world", 10, 40, 10, 15, 70, 15);
        MatchPointPatrolRoute route = new MatchPointPatrolRoute(main, 65, 500, 40);
        assertTrue(route.size() >= 1);
        for (int i = 0; i < route.size(); i++) {
            double[] point = route.at(i);
            assertTrue(point[0] >= 10 && point[0] <= 16);
            assertTrue(point[2] >= 10 && point[2] <= 16);
            assertTrue(point[1] < 65);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void requiresMainRegion() {
        new MatchPointPatrolRoute(null, 250, 100, 40);
    }
}
