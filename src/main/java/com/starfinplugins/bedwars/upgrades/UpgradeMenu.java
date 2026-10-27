package com.starfinplugins.bedwars.upgrades;

import com.starfinplugins.bedwars.domain.economy.TeamTrapType;
import com.starfinplugins.bedwars.domain.economy.TeamUpgradeType;
import com.starfinplugins.bedwars.domain.economy.ResourcePriceText;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.domain.match.GameTeam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Compact three-row team menu with the seven reference choices. */
public final class UpgradeMenu implements InventoryHolder {
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
    private final Map<Integer, Action> actions = new HashMap<Integer, Action>();

    public UpgradeMenu(Player player, GameTeam team, TeamUpgradeSettings settings,
                       boolean forgeAvailable) {
        inventory = Bukkit.createInventory(this, 27, "Melhorias do Time");
        refresh(player, team, settings, forgeAvailable);
    }

    public void refresh(Player player, GameTeam team, TeamUpgradeSettings settings,
                        boolean forgeAvailable) {
        inventory.clear();
        actions.clear();
        addUpgrade(10, TeamUpgradeType.SHARPNESS, Material.IRON_SWORD,
            "Afiação nas Espadas", "O seu time ganha Afiação permanente em todas as espadas.",
            "Afiação", player, team, settings, true);
        addUpgrade(11, TeamUpgradeType.PROTECTION, Material.IRON_CHESTPLATE,
            "Reforço na Armadura", "O seu time ganha Proteção em todas as peças de armadura.",
            "Proteção", player, team, settings, true);
        addUpgrade(12, TeamUpgradeType.HASTE, Material.GOLD_PICKAXE,
            "Sem tempo", "O seu time ganha o efeito de Pressa permanente.",
            "Pressa", player, team, settings, true);
        addUpgrade(13, TeamUpgradeType.FORGE, Material.FURNACE,
            "Forja da Ilha", "Melhore os geradores de Ferro e Ouro da sua ilha.",
            "Forja", player, team, settings, forgeAvailable);
        addUpgrade(14, TeamUpgradeType.HEAL_POOL, Material.BEACON,
            "Regeneração na Ilha", "Crie um campo de Regeneração em volta de sua ilha.",
            "Regeneração", player, team, settings, true);
        addTrap(15, TeamTrapType.ALARM, Material.REDSTONE_TORCH_ON,
            "É uma Cilada!", "Cause Cegueira e Lentidão por "
                + (settings.getTrapDurationTicks(TeamTrapType.ALARM, 1) / 20)
                + " segundos em invasores.",
            player, team, settings);
        addTrap(16, TeamTrapType.MINING_FATIGUE, Material.DIODE,
            "Fadiga", "Cause Fadiga em invasores.", player, team, settings);
    }

    private void addUpgrade(int slot, TeamUpgradeType type, Material icon,
                            String title, String description, String effect,
                            Player player, GameTeam team, TeamUpgradeSettings settings,
                            boolean available) {
        int level = team.getUpgradeLevel(type);
        List<Integer> costs = settings.getCosts(type);
        int next = level + 1;
        List<String> lore = new ArrayList<String>();
        wrap(lore, description);
        lore.add(" ");
        for (int index = 0; index < costs.size(); index++) {
            boolean owned = index < level;
            lore.add((owned ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✗ ")
                + ChatColor.GRAY + "Nível " + (index + 1) + ": " + effect + " "
                + roman(index + 1) + ", "
                + ResourcePriceText.amount(ResourceType.DIAMOND, costs.get(index)));
        }
        lore.add(" ");
        if (level >= costs.size() || !available) {
            lore.add(ChatColor.GREEN + "Nível máximo nesta partida.");
        } else if (diamonds(player) < costs.get(level)) {
            lore.add(ChatColor.RED + "Você não tem Diamantes o suficiente.");
        } else {
            lore.add(ChatColor.GREEN + "Clique para comprar.");
        }
        inventory.setItem(slot, item(icon, (level >= costs.size() ? ChatColor.GREEN : ChatColor.RED)
            + title + " " + roman(Math.min(next, costs.size())), lore));
        actions.put(slot, new Action(type, null));
    }

    private void addTrap(int slot, TeamTrapType type, Material icon, String title,
                         String description, Player player, GameTeam team,
                         TeamUpgradeSettings settings) {
        int level = team.getQueuedTrapLevel(type);
        int maximum = settings.getTrapMaxLevel(type);
        int next = Math.min(level + 1, maximum);
        int cost = settings.getTrapCost(type, next);
        List<String> lore = new ArrayList<String>();
        wrap(lore, description);
        lore.add(" ");
        if (maximum > 1) {
            for (int index = 1; index <= maximum; index++) {
                lore.add((index <= level ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✗ ")
                    + ChatColor.GRAY + "Nível " + index + ": " + title + " "
                    + roman(index) + " por " + (settings.getTrapDurationTicks(type, index) / 20)
                    + " segundos, " + ResourcePriceText.amount(ResourceType.DIAMOND,
                        settings.getTrapCost(type, index)));
            }
        } else {
            lore.add(ResourcePriceText.price(ResourceType.DIAMOND, cost));
        }
        lore.add(" ");
        if (level >= maximum && maximum > 1) {
            lore.add(ChatColor.GREEN + "Armadilha no nível máximo.");
        } else if (team.getTraps().size() >= settings.getMaximumQueuedTraps()
                && level == 0) {
            lore.add(ChatColor.RED + "Fila de armadilhas cheia.");
        } else if (diamonds(player) < cost) {
            lore.add(ChatColor.RED + "Você não tem Diamantes o suficiente.");
        } else {
            lore.add(ChatColor.GREEN + "Clique para comprar.");
        }
        inventory.setItem(slot, item(icon, ChatColor.RED + title
            + (maximum > 1 ? " " + roman(next) : ""), lore));
        actions.put(slot, new Action(null, type));
    }

    private static ItemStack item(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static int diamonds(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.DIAMOND) total += stack.getAmount();
        }
        return total;
    }

    private static void wrap(List<String> lines, String text) {
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > 34) {
                lines.add(ChatColor.GRAY + line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(ChatColor.GRAY + line.toString());
    }

    private static String roman(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            default: return Integer.toString(level);
        }
    }

    public Action getAction(int slot) { return actions.get(slot); }
    @Override public Inventory getInventory() { return inventory; }
}
