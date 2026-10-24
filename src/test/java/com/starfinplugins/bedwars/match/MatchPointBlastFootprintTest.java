package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class MatchPointBlastFootprintTest {
    @Test public void circularExplosionIsNotAnX() {
        List<MatchPointBlastFootprint.Offset> blocks = MatchPointBlastFootprint.offsets(3);
        assertEquals(29, blocks.size());
        assertEquals(0, blocks.get(0).x);
        assertEquals(0, blocks.get(0).z);
        Set<String> cells = new HashSet<String>();
        for (MatchPointBlastFootprint.Offset block : blocks) {
            assertTrue(block.x * block.x + block.z * block.z <= 9);
            assertTrue(cells.add(block.x + ":" + block.z));
        }
        assertTrue("Include north, not just diagonal X arms", cells.contains("0:3"));
        assertTrue("Include south", cells.contains("0:-3"));
        assertTrue("Include east", cells.contains("3:0"));
        assertTrue("Include west", cells.contains("-3:0"));
        assertTrue("Include off-axis TNT positions", cells.contains("1:2"));
        assertFalse("Outside circular radius", cells.contains("3:3"));
    }

    @Test public void defaultsAreBoundedAndImmutable() {
        assertEquals(5, MatchPointBlastFootprint.offsets(-4).size());
        assertEquals(113, MatchPointBlastFootprint.offsets(6).size());
        assertEquals(113, MatchPointBlastFootprint.offsets(100).size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void cannotMutateBlastFootprint() {
        MatchPointBlastFootprint.offsets(2).clear();
    }
}
