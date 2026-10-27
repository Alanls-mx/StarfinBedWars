package com.starfinplugins.bedwars.domain;

import com.starfinplugins.bedwars.domain.economy.ResourcePriceText;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import org.bukkit.ChatColor;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ResourcePriceTextTest {
    @Test public void priceUsesEachResourcesColorAndCorrectSingular() {
        assertEquals(ChatColor.GRAY + "Preço: " + ChatColor.WHITE + "12 Ferros",
            ResourcePriceText.price(ResourceType.IRON, 12));
        assertEquals(ChatColor.GRAY + "Preço: " + ChatColor.GOLD + "4 Ouros",
            ResourcePriceText.price(ResourceType.GOLD, 4));
        assertEquals(ChatColor.GRAY + "Preço: " + ChatColor.AQUA + "4 Diamantes",
            ResourcePriceText.price(ResourceType.DIAMOND, 4));
        assertEquals(ChatColor.GRAY + "Preço: " + ChatColor.GREEN + "1 Esmeralda",
            ResourcePriceText.price(ResourceType.EMERALD, 1));
    }
}
