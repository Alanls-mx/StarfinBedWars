package com.starfinplugins.bedwars.domain.arena;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AutomaticArenaBoundsTest {
    @Test
    public void evenDimensionsAreCenteredWithTheExtraBlockOnThePositiveSide() {
        AutomaticArenaBounds bounds = new AutomaticArenaBounds("arena", 10, 64, -8,
            4, 2, 50, 40, 1000);

        assertEquals(8, bounds.getRegion().getMinimumX());
        assertEquals(11, bounds.getRegion().getMaximumX());
        assertEquals(-9, bounds.getRegion().getMinimumZ());
        assertEquals(-8, bounds.getRegion().getMaximumZ());
        assertEquals(24, bounds.getRegion().getMinimumY());
        assertEquals(114, bounds.getRegion().getMaximumY());
        assertEquals(8, bounds.getPos1().getX(), 0.0);
        assertEquals(24, bounds.getPos1().getY(), 0.0);
        assertEquals(-9, bounds.getPos1().getZ(), 0.0);
        assertEquals(11, bounds.getPos2().getX(), 0.0);
        assertEquals(114, bounds.getPos2().getY(), 0.0);
        assertEquals(-8, bounds.getPos2().getZ(), 0.0);
    }

    @Test
    public void oddDimensionsUseTheCenterBlockOnBothAxes() {
        AutomaticArenaBounds bounds = new AutomaticArenaBounds("arena", -3, 100, 5,
            5, 3, 10, 20, 1000);

        assertEquals(-5, bounds.getRegion().getMinimumX());
        assertEquals(-1, bounds.getRegion().getMaximumX());
        assertEquals(4, bounds.getRegion().getMinimumZ());
        assertEquals(6, bounds.getRegion().getMaximumZ());
        assertEquals(80, bounds.getRegion().getMinimumY());
        assertEquals(110, bounds.getRegion().getMaximumY());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDimensionsAboveTheConfiguredMaximum() {
        new AutomaticArenaBounds("arena", 0, 64, 0, 101, 100, 50, 50, 100);
    }

    @Test
    public void rejectsVerticalBoundsOutsideTheMinecraftWorldWithActionableLimits() {
        try {
            new AutomaticArenaBounds("arena", 0, 10, 0, 10, 10, 10, 11, 100);
        } catch (IllegalArgumentException error) {
            assertEquals("POS1/POS2 verticais calculados seriam Y=-1 e Y=20; ajuste uplow "
                + "para no maximo 10 e upheight para no maximo 245 com este centro.", error.getMessage());
            return;
        }
        throw new AssertionError("Expected invalid vertical bounds to be rejected.");
    }
}
