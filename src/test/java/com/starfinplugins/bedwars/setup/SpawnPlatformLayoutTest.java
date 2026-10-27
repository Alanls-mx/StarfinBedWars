package com.starfinplugins.bedwars.setup;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SpawnPlatformLayoutTest {
    @Test
    public void soloLayoutHasThreeByThreeFootprintAndOneByOneInterior() {
        assertLayout(true, 3, 3, 1, 1, 9, 9, 24, 42);
    }

    @Test
    public void soloLayoutRemainsThreeByThreeWhenFacingAlongX() {
        SpawnPlatformLayout layout = new SpawnPlatformLayout(20, 63, -10, true, 90.0F);
        assertEquals(3, layout.getMaximumX() - layout.getMinimumX() + 1);
        assertEquals(3, layout.getMaximumZ() - layout.getMinimumZ() + 1);
        assertInteriorSize(layout, 1, 1);
    }

    @Test
    public void teamLayoutHasFiveByFiveFootprintAndThreeByThreeInterior() {
        assertLayout(false, 5, 5, 3, 3, 25, 25, 48, 98);
    }

    @Test
    public void spawnRoomsGrowWithActualTeamCapacity() {
        assertCapacity(1, 3, 1);
        assertCapacity(2, 5, 3);
        assertCapacity(3, 7, 5);
        assertCapacity(4, 7, 5);
        assertCapacity(5, 9, 7);
        assertCapacity(8, 9, 7);
    }

    private void assertCapacity(int capacity, int width, int interior) {
        SpawnPlatformLayout layout = SpawnPlatformLayout.forTeamCapacity(20, 63, -10, capacity);
        assertEquals(width, layout.getMaximumX() - layout.getMinimumX() + 1);
        assertEquals(width, layout.getMaximumZ() - layout.getMinimumZ() + 1);
        assertInteriorSize(layout, interior, interior);
    }

    private void assertLayout(boolean solo, int width, int depth, int interiorWidth,
                              int interiorDepth, int baseCount,
                              int roofCount, int wallCount, int total) {
        SpawnPlatformLayout layout = new SpawnPlatformLayout(20, 63, -10, solo);
        int base = 0;
        int roof = 0;
        int walls = 0;
        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (SpawnPlatformLayout.BlockCoordinate block : layout.getBlocks()) {
            if (block.getPart() == SpawnPlatformLayout.Part.BASE) base++;
            else if (block.getPart() == SpawnPlatformLayout.Part.ROOF) roof++;
            else walls++;
            minimumX = Math.min(minimumX, block.getX());
            maximumX = Math.max(maximumX, block.getX());
            minimumZ = Math.min(minimumZ, block.getZ());
            maximumZ = Math.max(maximumZ, block.getZ());
        }
        assertEquals(baseCount, base);
        assertEquals(roofCount, roof);
        assertEquals(wallCount, walls);
        assertEquals(total, layout.getBlocks().size());
        assertEquals(width, maximumX - minimumX + 1);
        assertEquals(depth, maximumZ - minimumZ + 1);
        assertEquals(minimumX, layout.getMinimumX());
        assertEquals(maximumX, layout.getMaximumX());
        assertEquals(minimumZ, layout.getMinimumZ());
        assertEquals(maximumZ, layout.getMaximumZ());
        assertEquals(width * depth, base);
        assertEquals(interiorWidth * interiorDepth, openInteriorBlocks(layout));
    }

    private boolean hasWall(SpawnPlatformLayout layout, int x, int y, int z) {
        for (SpawnPlatformLayout.BlockCoordinate block : layout.getBlocks()) {
            if (block.getPart() == SpawnPlatformLayout.Part.WALL
                    && block.getX() == x && block.getY() == y && block.getZ() == z) return true;
        }
        return false;
    }

    private void assertInteriorSize(SpawnPlatformLayout layout, int expectedWidth, int expectedDepth) {
        assertEquals(expectedWidth * expectedDepth, openInteriorBlocks(layout));
    }

    private int openInteriorBlocks(SpawnPlatformLayout layout) {
        int open = 0;
        for (int x = layout.getMinimumX() + 1; x < layout.getMaximumX(); x++) {
            for (int z = layout.getMinimumZ() + 1; z < layout.getMaximumZ(); z++) {
                boolean hasWall = false;
                for (int y = 64; y <= 66; y++) {
                    if (hasWall(layout, x, y, z)) {
                        hasWall = true;
                        break;
                    }
                }
                if (!hasWall) open++;
            }
        }
        return open;
    }

    @Test
    public void rejectsPlatformThatWouldCrossWorldHeight() {
        try {
            new SpawnPlatformLayout(0, 252, 0, false);
            fail("Expected invalid vertical layout to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertEquals("A plataforma ultrapassa os limites do mundo.", expected.getMessage());
        }
    }
}
