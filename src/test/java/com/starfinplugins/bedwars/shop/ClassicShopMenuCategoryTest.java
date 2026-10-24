package com.starfinplugins.bedwars.shop;

import static org.junit.Assert.assertEquals;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.domain.economy.ShopItemDefinition;
import org.bukkit.Material;
import org.junit.Test;

public class ClassicShopMenuCategoryTest {
    private static ShopItemDefinition item(String id, Material material) {
        return new ShopItemDefinition(id, id, material, (short) 0, 1,
            ResourceType.IRON, 10, 0, false);
    }

    @Test public void separatesFireballFromOtherSpecials() {
        assertEquals(ClassicShopMenu.Category.UTILITIES,
            ClassicShopMenu.categoryOf(item("fireball", Material.FIREBALL)));
        assertEquals(ClassicShopMenu.Category.SPECIAL,
            ClassicShopMenu.categoryOf(item("bridge-egg", Material.EGG)));
        assertEquals(ClassicShopMenu.Category.SPECIAL,
            ClassicShopMenu.categoryOf(item("bedbug", Material.SNOW_BALL)));
        assertEquals(ClassicShopMenu.Category.SPECIAL,
            ClassicShopMenu.categoryOf(item("dream-defender", Material.PUMPKIN)));
        assertEquals(ClassicShopMenu.Category.SPECIAL,
            ClassicShopMenu.categoryOf(item("magic-milk", Material.MILK_BUCKET)));
    }

    @Test public void preservesNormalCategories() {
        assertEquals(ClassicShopMenu.Category.BLOCKS,
            ClassicShopMenu.categoryOf(item("wool", Material.WOOL)));
        assertEquals(ClassicShopMenu.Category.SWORDS,
            ClassicShopMenu.categoryOf(item("stone-sword", Material.STONE_SWORD)));
        assertEquals(ClassicShopMenu.Category.TOOLS,
            ClassicShopMenu.categoryOf(item("shears", Material.SHEARS)));
    }
}
