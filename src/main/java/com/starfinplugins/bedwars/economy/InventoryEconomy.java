package com.starfinplugins.bedwars.economy;

import com.starfinplugins.bedwars.domain.economy.ResourceType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class InventoryEconomy {
    private InventoryEconomy() { }

    public static ItemStack[] snapshot(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null ? null : contents[index].clone();
        }
        return copy;
    }

    public static boolean charge(Player player, ResourceType resource, int amount) {
        if (player == null || resource == null || amount < 1) return false;
        PlayerInventory inventory = player.getInventory();
        if (count(inventory.getContents(), resource) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != resource.getMaterial()) continue;
            ItemStack updated = stack.clone();
            int removed = Math.min(updated.getAmount(), remaining);
            updated.setAmount(updated.getAmount() - removed);
            inventory.setItem(slot, updated.getAmount() == 0 ? null : updated);
            remaining -= removed;
            if (remaining == 0) return true;
        }
        throw new IllegalStateException("Saldo de recursos alterado durante a cobranca.");
    }

    public static void restore(Player player, ItemStack[] snapshot) {
        if (player == null || snapshot == null) throw new IllegalArgumentException("Jogador/snapshot ausente.");
        ItemStack[] copy = new ItemStack[snapshot.length];
        for (int index = 0; index < snapshot.length; index++) {
            copy[index] = snapshot[index] == null ? null : snapshot[index].clone();
        }
        player.getInventory().setContents(copy);
    }

    private static int count(ItemStack[] contents, ResourceType resource) {
        int amount = 0;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() == resource.getMaterial()) amount += stack.getAmount();
        }
        return amount;
    }
}
