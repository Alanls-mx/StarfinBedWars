package com.starfinplugins.bedwars.domain.economy;

import org.bukkit.ChatColor;

/** Shared colors for resource prices in shop and team upgrade menus. */
public final class ResourcePriceText {
    private ResourcePriceText() { }

    public static String amount(ResourceType resource, int count) {
        if (resource == null || count < 0) throw new IllegalArgumentException("Preco invalido.");
        switch (resource) {
            case IRON:
                return ChatColor.WHITE + Integer.toString(count) + (count == 1 ? " Ferro" : " Ferros");
            case GOLD:
                return ChatColor.GOLD + Integer.toString(count) + (count == 1 ? " Ouro" : " Ouros");
            case DIAMOND:
                return ChatColor.AQUA + Integer.toString(count)
                    + (count == 1 ? " Diamante" : " Diamantes");
            case EMERALD:
                return ChatColor.GREEN + Integer.toString(count)
                    + (count == 1 ? " Esmeralda" : " Esmeraldas");
            default: throw new IllegalArgumentException("Recurso invalido.");
        }
    }

    public static String price(ResourceType resource, int count) {
        return ChatColor.GRAY + "Preço: " + amount(resource, count);
    }
}
