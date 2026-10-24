package com.starfinplugins.bedwars.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Matches a currently visible shop by its stable holder rather than comparing
 * Bukkit Inventory wrapper instances. A 1.8 player's own window has 45 slots,
 * while the Starfin classic shop has 54 slots.
 */
public final class ShopInventoryGuard {
    private ShopInventoryGuard() { }

    public static boolean isSameShop(Inventory visible, InventoryHolder menu) {
        return visible != null && menu != null && visible.getSize() == 54
            && visible.getHolder() == menu;
    }
}
