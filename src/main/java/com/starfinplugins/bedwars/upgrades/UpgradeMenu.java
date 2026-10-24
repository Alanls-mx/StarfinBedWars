package com.starfinplugins.bedwars.upgrades;

import com.starfinplugins.bedwars.domain.economy.TeamTrapType;
import com.starfinplugins.bedwars.domain.economy.TeamUpgradeType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class UpgradeMenu implements InventoryHolder {
    public enum Category { UPGRADES, TRAPS }
    public static final class Action {
        private final TeamUpgradeType upgrade;
        private final TeamTrapType trap;

        private Action(TeamUpgradeType upgrade, TeamTrapType trap) {
            this.upgrade = upgrade;
            this.trap = trap;
        }
        public TeamUpgradeType getUpgrade() { return upgrade; }
        public TeamTrapType getTrap() { return trap; }
    }

    private final Inventory inventory;
    private final Category category;
    private final Map<Integer, Action> actions = new LinkedHashMap<Integer, Action>();

    public UpgradeMenu(Map<TeamUpgradeType, Integer> levels, Map<TeamUpgradeType, Integer> nextCosts,
                       Map<TeamTrapType, Integer> trapCosts, int queuedTraps, int maximumTraps,
                       Category category) {
        this.category = category;
        inventory = Bukkit.createInventory(this, 54, ChatColor.BLUE + "Melhorias: "
            + (category == Category.UPGRADES ? "Equipe" : "Armadilhas"));
        inventory.setItem(0, icon(Material.DIAMOND_SWORD, ChatColor.YELLOW + "Melhorias"));
        inventory.setItem(1, icon(Material.TRIPWIRE_HOOK, ChatColor.YELLOW + "Armadilhas"));
        for (int slot = 9; slot <= 17; slot++) {
            ItemStack pane = new ItemStack(Material.STAINED_GLASS_PANE, 1,
                (short) (slot == (category == Category.UPGRADES ? 9 : 10) ? 5 : 7));
            ItemMeta paneMeta = pane.getItemMeta();
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
            inventory.setItem(slot, pane);
        }
        if (category == Category.UPGRADES) {
            addUpgrade(19, TeamUpgradeType.SHARPNESS, Material.ENCHANTED_BOOK, "Afiacao",
                levels, nextCosts);
            addUpgrade(21, TeamUpgradeType.PROTECTION, Material.IRON_CHESTPLATE, "Protecao",
                levels, nextCosts);
            addUpgrade(23, TeamUpgradeType.FORGE, Material.FURNACE, "Forja",
                levels, nextCosts);
            addUpgrade(25, TeamUpgradeType.HEAL_POOL, Material.BEACON, "Regeneracao",
                levels, nextCosts);
        } else {
            addTrap(20, TeamTrapType.ALARM, Material.REDSTONE_TORCH_ON, trapCosts,
                queuedTraps, maximumTraps);
            addTrap(22, TeamTrapType.BLINDNESS, Material.ENDER_PEARL, trapCosts,
                queuedTraps, maximumTraps);
            addTrap(24, TeamTrapType.MINING_FATIGUE, Material.IRON_PICKAXE, trapCosts,
                queuedTraps, maximumTraps);
        }
        inventory.setItem(49, icon(Material.BARRIER, ChatColor.RED + "Fechar"));
    }

    private ItemStack icon(Material material, String title) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        item.setItemMeta(meta);
        return item;
    }

    private void addUpgrade(int slot, TeamUpgradeType type, Material icon, String name,
                            Map<TeamUpgradeType, Integer> levels, Map<TeamUpgradeType, Integer> costs) {
        int level = levels.containsKey(type) ? levels.get(type) : 0;
        Integer cost = costs.get(type);
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name + " " + level);
        meta.setLore(Collections.singletonList(cost == null ? ChatColor.RED + "Nivel maximo"
            : ChatColor.GRAY + "Custo: " + cost + " diamantes"));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
        actions.put(slot, new Action(type, null));
    }

    private void addTrap(int slot, TeamTrapType type, Material icon, Map<TeamTrapType, Integer> costs,
                         int queuedTraps, int maximumTraps) {
        Integer cost = costs.get(type);
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + type.name().replace('_', ' '));
        meta.setLore(Collections.singletonList(queuedTraps >= maximumTraps
            ? ChatColor.RED + "Fila de armadilhas cheia"
            : ChatColor.GRAY + "Custo: " + cost + " diamantes"));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
        actions.put(slot, new Action(null, type));
    }

    public Action getAction(int slot) { return actions.get(slot); }
    public Category getCategory() { return category; }
    @Override public Inventory getInventory() { return inventory; }
}
