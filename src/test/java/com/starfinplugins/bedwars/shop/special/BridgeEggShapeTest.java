package com.starfinplugins.bedwars.shop.special;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class BridgeEggShapeTest {
    @Test public void defaultBridgeIsThreeWideAndTwoThick() {
        java.util.List<BridgeEggShape.Offset> blocks =
            BridgeEggShape.footprint(3, 2, 1.0D, 0.0D);
        assertEquals(6, blocks.size());
        Set<String> cells = new HashSet<String>();
        for (BridgeEggShape.Offset block : blocks) {
            assertEquals(0, block.getX());
            assertTrue(block.getZ() >= -1 && block.getZ() <= 1);
            assertTrue(block.getY() == 0 || block.getY() == -1);
            cells.add(block.getX() + ":" + block.getY() + ":" + block.getZ());
        }
        assertEquals(6, cells.size());
        assertEquals("0:0:0", blocks.get(0).getX() + ":"
            + blocks.get(0).getY() + ":" + blocks.get(0).getZ());
    }

    @Test public void widthRotatesAcrossTravelDirection() {
        java.util.List<BridgeEggShape.Offset> blocks =
            BridgeEggShape.footprint(3, 1, 0.0D, -1.0D);
        assertEquals(3, blocks.size());
        Set<Integer> xs = new HashSet<Integer>();
        for (BridgeEggShape.Offset cell : blocks) {
            assertEquals(0, cell.getZ());
            xs.add(cell.getX());
        }
        assertTrue(xs.contains(-1));
        assertTrue(xs.contains(0));
        assertTrue(xs.contains(1));
    }

    @Test public void supportsTheFiveBlockWidthLimit() {
        assertEquals(10, BridgeEggShape.footprint(5, 2, 1, 1).size());
        assertEquals(1, BridgeEggShape.footprint(1, 1, 0, 0).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEvenWidth() {
        BridgeEggShape.footprint(2, 1, 1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsExcessiveThickness() {
        BridgeEggShape.footprint(3, 3, 1, 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void returnsImmutableFootprint() {
        BridgeEggShape.footprint(3, 1, 1, 0).clear();
    }
}
