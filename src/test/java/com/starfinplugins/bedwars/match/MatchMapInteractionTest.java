package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.bukkit.Material;
import org.junit.Test;

public class MatchMapInteractionTest {
    @Test public void blocksVanillaStationsAndBedButKeepsMatchChests() {
        assertTrue(MatchEngineService.protectedMapInteraction(Material.BED_BLOCK));
        assertTrue(MatchEngineService.protectedMapInteraction(Material.ENCHANTMENT_TABLE));
        assertTrue(MatchEngineService.protectedMapInteraction(Material.ANVIL));
        assertTrue(MatchEngineService.protectedMapInteraction(Material.WORKBENCH));
        assertTrue(MatchEngineService.protectedMapInteraction(Material.LEVER));
        assertFalse(MatchEngineService.protectedMapInteraction(Material.CHEST));
        assertFalse(MatchEngineService.protectedMapInteraction(Material.ENDER_CHEST));
    }
}
