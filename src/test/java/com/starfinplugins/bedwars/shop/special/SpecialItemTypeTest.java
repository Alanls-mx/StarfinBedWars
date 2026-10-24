package com.starfinplugins.bedwars.shop.special;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

public class SpecialItemTypeTest {
    @Test public void findsConfiguredSpecialIds() {
        assertEquals(SpecialItemType.FIREBALL, SpecialItemType.byId("fireball"));
        assertEquals(SpecialItemType.BRIDGE_EGG, SpecialItemType.byId("bridge-egg"));
        assertEquals(SpecialItemType.BEDBUG, SpecialItemType.byId("bedbug"));
        assertEquals(SpecialItemType.DREAM_DEFENDER, SpecialItemType.byId("dream-defender"));
        assertEquals(SpecialItemType.MAGIC_MILK, SpecialItemType.byId("magic-milk"));
        assertNull(SpecialItemType.byId("tnt"));
    }

    @Test public void ignoresOrdinaryVanillaItems() {
        assertNull(SpecialItemType.identify(new ItemStack(Material.EGG)));
        assertNull(SpecialItemType.identify(new ItemStack(Material.SNOW_BALL)));
        assertNull(SpecialItemType.identify(new ItemStack(Material.PUMPKIN)));
    }

    @Test public void marksPurchasedItemsUnambiguously() {
        for (SpecialItemType type : SpecialItemType.values()) {
            ItemStack raw = new ItemStack(type.getMaterial(), 1);
            ItemStack marked = SpecialItemType.mark(type.getId(), raw);
            assertNotNull(marked);
            assertEquals(type, SpecialItemType.identify(marked));
            assertNull(SpecialItemType.identify(raw));
        }
    }

    @Test public void rejectsWrongMaterialWithCorrectId() {
        ItemStack stone = new ItemStack(Material.STONE);
        assertEquals(Material.STONE, SpecialItemType.mark("bridge-egg", stone).getType());
        assertNull(SpecialItemType.identify(stone));
    }
}
