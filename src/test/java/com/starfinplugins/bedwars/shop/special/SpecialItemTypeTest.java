package com.starfinplugins.bedwars.shop.special;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.bukkit.Material;
import org.junit.Test;

public class SpecialItemTypeTest {
    @Test public void recognizesConfiguredSpecialIds() {
        assertEquals(SpecialItemType.FIREBALL, SpecialItemType.byId("fireball"));
        assertEquals(SpecialItemType.BRIDGE_EGG, SpecialItemType.byId("bridge-egg"));
        assertEquals(SpecialItemType.BEDBUG, SpecialItemType.byId("bedbug"));
        assertEquals(SpecialItemType.DREAM_DEFENDER, SpecialItemType.byId("dream-defender"));
        assertEquals(SpecialItemType.MAGIC_MILK, SpecialItemType.byId("magic-milk"));
        assertNull(SpecialItemType.byId("tnt"));
    }

    @Test public void mapsMaterialsCorrectlyForMinecraft188() {
        assertEquals(Material.FIREBALL, SpecialItemType.FIREBALL.getMaterial());
        assertEquals(Material.EGG, SpecialItemType.BRIDGE_EGG.getMaterial());
        assertEquals(Material.SNOW_BALL, SpecialItemType.BEDBUG.getMaterial());
        assertEquals(Material.PUMPKIN, SpecialItemType.DREAM_DEFENDER.getMaterial());
        assertEquals(Material.MILK_BUCKET, SpecialItemType.MAGIC_MILK.getMaterial());
    }

    @Test public void rejectsAbsentMarkersWithoutBukkitServer() {
        assertNull(SpecialItemType.byId(null));
        assertNull(SpecialItemType.identify(null));
    }
}
