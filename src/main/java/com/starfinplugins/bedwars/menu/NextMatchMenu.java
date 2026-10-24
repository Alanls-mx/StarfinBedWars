package com.starfinplugins.bedwars.menu;

import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Result-lobby choice shown after the disposable match world is removed. */
public final class NextMatchMenu implements InventoryHolder {
    public static final int PLAY_AGAIN = 11;
    public static final int CHOOSE_ARENA = 15;
    private final String sourceArenaId;
    private final Inventory inventory;

    public NextMatchMenu(String sourceArenaId) {
        this.sourceArenaId = sourceArenaId;
        inventory = Bukkit.createInventory(this, 27, "Jogar novamente?");
        inventory.setItem(4, item(Material.STAINED_GLASS_PANE, " ", (short) 7));
        inventory.setItem(PLAY_AGAIN, item(Material.EYE_OF_ENDER,
            ChatColor.GREEN + "Jogar novamente",
            ChatColor.GRAY + "Entrar na proxima partida do mesmo modo."));
        inventory.setItem(CHOOSE_ARENA, item(Material.PAPER,
            ChatColor.AQUA + "Escolher arena",
            ChatColor.GRAY + "Ver as arenas disponiveis."));
    }

    private static ItemStack item(Material material, String title, String... lore) {
        return item(material, title, (short) 0, lore);
    }

    private static ItemStack item(Material material, String title, short data, String... lore) {
        ItemStack stack = new ItemStack(material, 1, data);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(Arrays.asList(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    public String getSourceArenaId() { return sourceArenaId; }
    @Override public Inventory getInventory() { return inventory; }
}
