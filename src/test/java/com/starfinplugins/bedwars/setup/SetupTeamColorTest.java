package com.starfinplugins.bedwars.setup;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SetupTeamColorTest {
    @Test
    public void mapsAllMinecraftDyeColorsToDistinctOneEightBlockData() {
        Set<Byte> dataValues = new HashSet<Byte>();
        for (SetupTeamColor color : SetupTeamColor.values()) {
            assertTrue(color.getBlockData() >= 0 && color.getBlockData() <= 15);
            assertTrue("Duplicate dye color data: " + color, dataValues.add(color.getBlockData()));
            assertNotNull(color.getChatColor());
        }
        assertEquals(16, dataValues.size());
    }

    @Test
    public void parsesNamesCodesAndLegacyAliases() {
        assertEquals(SetupTeamColor.BROWN, SetupTeamColor.parse("brown"));
        assertEquals(SetupTeamColor.RED, SetupTeamColor.parse("&c"));
        assertEquals(SetupTeamColor.LIGHT_GRAY, SetupTeamColor.parse("silver"));
        assertEquals(12, SetupTeamColor.parse("brown").getBlockData());
        assertEquals(14, SetupTeamColor.parse("red").getBlockData());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownColors() {
        SetupTeamColor.parse("ultraviolet");
    }
}
