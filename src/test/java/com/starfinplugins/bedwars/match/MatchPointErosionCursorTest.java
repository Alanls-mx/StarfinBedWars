package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class MatchPointErosionCursorTest {
    @Test public void coversAllArenaCellsExactlyOnceWithoutPlayersOrDragon() {
        // Region crosses chunk boundaries, with negative coordinates.
        MatchPointErosionCursor cursor = new MatchPointErosionCursor(-17, 60, -1, 17, 63, 17);
        long expected = 35L * 4L * 19L;
        Set<String> visited = new HashSet<String>();
        while (cursor.hasNext()) {
            MatchPointErosionCursor.Cell cell = cursor.peek();
            assertNotNull(cell);
            assertTrue(cell.x >= -17 && cell.x <= 17);
            assertTrue(cell.y >= 60 && cell.y <= 63);
            assertTrue(cell.z >= -1 && cell.z <= 17);
            assertEquals(cell.x >> 4, cell.chunkX());
            assertEquals(cell.z >> 4, cell.chunkZ());
            assertTrue("Duplicate arena block",
                visited.add(cell.x + ":" + cell.y + ":" + cell.z));
            cursor.advance();
        }
        assertEquals(expected, visited.size());
        assertEquals(expected, cursor.getTotal());
        assertEquals(expected, cursor.getVisited());
        assertEquals(100, cursor.getProgressPercent());
        assertFalse(cursor.hasNext());
        assertEquals(null, cursor.peek());
    }

    @Test public void skipsKnownEmptyChunkSectionsWithoutLosingLaterTerrain() {
        MatchPointErosionCursor cursor = new MatchPointErosionCursor(
            -1, 50, -1, -1, 155, -1);
        assertEquals(155, cursor.peek().y);
        // Sections Y=144..159 and Y=128..143 are entirely empty.
        cursor.skipAirAbove(143);
        assertEquals(143, cursor.peek().y);
        assertEquals(12L, cursor.getVisited());
        cursor.skipAirAbove(127);
        assertEquals(127, cursor.peek().y);
        assertEquals(28L, cursor.getVisited());
        // The column containing terrain is still scanned, including
        // transparent blocks that heightmaps can miss.
        for (int y = 127; y >= 50; y--) {
            assertEquals(y, cursor.peek().y);
            cursor.advance();
        }
        assertEquals(106L, cursor.getTotal());
        assertEquals(106L, cursor.getVisited());
        assertFalse(cursor.hasNext());
    }

    @Test public void canResumeAfterEachBoundedTickWithoutSkippingPositions() {
        MatchPointErosionCursor cursor = new MatchPointErosionCursor(0, 1, 0, 17, 2, 1);
        int ticks = 0;
        int visited = 0;
        while (cursor.hasNext()) {
            int budget = 5; // Simulate game tick with maximum five inspections.
            while (budget-- > 0 && cursor.hasNext()) {
                cursor.advance();
                visited++;
            }
            ticks++;
        }
        assertEquals(72, visited);
        assertTrue(ticks > 1);
    }

    @Test public void descendsEachColumnAndMovesChunkByChunk() {
        MatchPointErosionCursor cursor = new MatchPointErosionCursor(15, 10, 0, 16, 11, 0);
        MatchPointErosionCursor.Cell first = cursor.peek();
        assertEquals(15, first.x);
        assertEquals(11, first.y);
        cursor.advance();
        MatchPointErosionCursor.Cell second = cursor.peek();
        assertEquals(15, second.x);
        assertEquals(10, second.y);
        cursor.advance();
        MatchPointErosionCursor.Cell third = cursor.peek();
        assertEquals(16, third.x);
        assertEquals(11, third.y);
        assertEquals(1, third.chunkX());
    }

    @Test public void includesEntireVerticalHeightWhenConfigured() {
        MatchPointErosionCursor cursor = new MatchPointErosionCursor(0, 0, 0, 0, 255, 0);
        for (int i = 255; i >= 0; i--) {
            assertEquals(i, cursor.peek().y);
            cursor.advance();
        }
        assertFalse(cursor.hasNext());
        assertEquals(256L, cursor.getTotal());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvertedBounds() {
        new MatchPointErosionCursor(10, 0, 0, 1, 255, 1);
    }
}
