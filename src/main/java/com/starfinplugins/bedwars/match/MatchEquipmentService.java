package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.match.EquipmentProgress;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.upgrades.TeamUpgradeService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;

public final class MatchEquipmentService implements ManagedService, Listener {
    private enum ArmorMode { CLASSIC, INVERTED }
    private final Plugin plugin;
    private final TeamUpgradeService upgrades;
    private final MatchEngineService matches;
    private final Map<UUID, ArmorMode> modes = new HashMap<UUID, ArmorMode>();
    private int swordMinimum;
    private int toolMinimum;

    public MatchEquipmentService(Plugin plugin, TeamUpgradeService upgrades, MatchEngineService matches) {
        this.plugin = plugin;
        this.upgrades = upgrades;
        this.matches = matches;
    }

    @Override public String getName() { return "match-equipment"; }
    @Override public void start() {
        swordMinimum = minimum("equipment.sword-minimum");
        toolMinimum = minimum("equipment.tool-minimum");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    @Override public void stop() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        modes.clear();
    }

    private int minimum(String path) {
        String value = plugin.getConfig().getString(path, "WOOD");
        if ("STONE".equalsIgnoreCase(value)) return 1;
        if (!"WOOD".equalsIgnoreCase(value)) plugin.getLogger().warning(path + " invalido; usando WOOD.");
        return 0;
    }

    @EventHandler public void onLifecycle(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        if (event.getType() == MatchLifecycleEvent.Type.CREATED) {
            String configured = plugin.getConfig().getString("equipment.armor-mode", "CLASSIC");
            ArmorMode mode;
            try { mode = ArmorMode.valueOf(configured.toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException invalid) { mode = ArmorMode.CLASSIC; }
            modes.put(match.getId(), mode);
        } else if (event.getType() == MatchLifecycleEvent.Type.SPAWN_OPENING) {
            for (GameParticipant participant : match.getParticipantList()) {
                if (participant.getState() != ParticipantState.CONNECTED) continue;
                Player player = Bukkit.getPlayer(participant.getPlayerId());
                participant.getEquipment().improveSword(swordMinimum);
                if (player != null) applyAll(player, match, participant);
            }
        } else if (event.getType() == MatchLifecycleEvent.Type.ENDED
                || event.getType() == MatchLifecycleEvent.Type.INSTANCE_REMOVED) {
            modes.remove(match.getId());
        }
    }

    public boolean isPermanent(Material material) {
        return armorTier(material) >= 0 || swordTier(material) >= 0
            || axeTier(material) >= 0 || pickaxeTier(material) >= 0;
    }

    public boolean canImprove(GameParticipant participant, Material material) {
        EquipmentProgress state = participant.getEquipment();
        int tier;
        if ((tier = armorTier(material)) >= 0) return tier > state.getArmor();
        if ((tier = swordTier(material)) >= 0) return tier > state.getSword();
        if ((tier = axeTier(material)) >= 0) return tier > state.getAxe();
        if ((tier = pickaxeTier(material)) >= 0) return tier > state.getPickaxe();
        return false;
    }

    public void purchased(Player player, GameMatch match, GameParticipant participant, Material material) {
        EquipmentProgress state = participant.getEquipment();
        if (armorTier(material) >= 0) state.improveArmor(armorTier(material));
        else if (swordTier(material) >= 0) state.improveSword(swordTier(material));
        else if (axeTier(material) >= 0) state.improveAxe(axeTier(material));
        else if (pickaxeTier(material) >= 0) state.improvePickaxe(pickaxeTier(material));
        applyAll(player, match, participant);
    }

    public void onValidDeath(Player player, GameParticipant participant) {
        EquipmentProgress state = participant.getEquipment();
        state.downgrade(swordMinimum, toolMinimum);
    }

    public void rebuildAfterDeath(Player player, GameMatch match, GameParticipant participant) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        applyAll(player, match, participant);
    }

    private void applyAll(Player player, GameMatch match, GameParticipant participant) {
        if (match == null || participant == null || match.getTeam(participant.getTeamId()) == null) return;
        EquipmentProgress state = participant.getEquipment();
        PlayerInventory inventory = player.getInventory();
        replaceTool(inventory, "_SWORD", swordMaterial(state.getSword()), match, participant);
        if (state.getAxe() >= 0) replaceTool(inventory, "_AXE", axeMaterial(state.getAxe()), match, participant);
        if (state.getPickaxe() >= 0) replaceTool(inventory, "_PICKAXE", pickaxeMaterial(state.getPickaxe()), match, participant);
        applyArmor(inventory, match, participant);
        player.updateInventory();
    }

    private void replaceTool(PlayerInventory inventory, String suffix, Material material,
                             GameMatch match, GameParticipant participant) {
        int chosen = -1;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || !existing.getType().name().endsWith(suffix)) continue;
            if (chosen < 0) chosen = slot;
            inventory.setItem(slot, null);
        }
        if (chosen < 0) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                if (inventory.getItem(slot) == null) { chosen = slot; break; }
            }
        }
        if (chosen < 0) return;
        ItemStack item = new ItemStack(material);
        if ((suffix.equals("_AXE") || suffix.equals("_PICKAXE"))
                && (material.name().startsWith("IRON_")
                    || material.name().startsWith("DIAMOND_"))) {
            item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DIG_SPEED,
                material.name().startsWith("DIAMOND_") ? 2 : 1);
        }
        upgrades.applyItemUpgrade(match, participant, item);
        inventory.setItem(chosen, item);
    }

    private void applyArmor(PlayerInventory inventory, GameMatch match, GameParticipant participant) {
        EquipmentProgress state = participant.getEquipment();
        TeamDefinition team = match.getTeam(participant.getTeamId()).getDefinition();
        DyeColor dye = DyeColor.getByWoolData(team.getColorData());
        ArmorMode mode = modes.get(match.getId());
        if (mode == null) mode = ArmorMode.CLASSIC;
        int upper = mode == ArmorMode.CLASSIC ? 0 : state.getArmor();
        int lower = mode == ArmorMode.CLASSIC ? state.getArmor() : 0;
        inventory.setHelmet(armor(upper, "HELMET", dye, match, participant));
        inventory.setChestplate(armor(upper, "CHESTPLATE", dye, match, participant));
        inventory.setLeggings(armor(lower, "LEGGINGS", dye, match, participant));
        inventory.setBoots(armor(lower, "BOOTS", dye, match, participant));
    }

    private ItemStack armor(int tier, String part, DyeColor color, GameMatch match,
                            GameParticipant participant) {
        String prefix = tier == 0 ? "LEATHER" : tier == 1 ? "CHAINMAIL"
            : tier == 2 ? "IRON" : "DIAMOND";
        ItemStack item = new ItemStack(Material.valueOf(prefix + "_" + part));
        if (tier == 0 && color != null && item.getItemMeta() instanceof LeatherArmorMeta) {
            LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
            meta.setColor(color.getColor());
            item.setItemMeta(meta);
        }
        upgrades.applyItemUpgrade(match, participant, item);
        return item;
    }

    private int armorTier(Material material) {
        String name = material.name();
        if (!(name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS"))) return -1;
        if (name.startsWith("LEATHER_")) return 0;
        if (name.startsWith("CHAINMAIL_")) return 1;
        if (name.startsWith("IRON_")) return 2;
        if (name.startsWith("DIAMOND_")) return 3;
        return -1;
    }
    private int swordTier(Material material) { return tier(material, "_SWORD"); }
    private int axeTier(Material material) { return tier(material, "_AXE"); }
    private int pickaxeTier(Material material) { return tier(material, "_PICKAXE"); }
    private int tier(Material material, String suffix) {
        String name = material.name();
        if (!name.endsWith(suffix)) return -1;
        if (name.startsWith("WOOD_") || name.startsWith("GOLD_")) return 0;
        if (name.startsWith("STONE_")) return 1;
        if (name.startsWith("IRON_")) return 2;
        if (name.startsWith("DIAMOND_")) return 3;
        return -1;
    }
    private Material swordMaterial(int tier) { return Material.valueOf(prefix(tier) + "_SWORD"); }
    private Material axeMaterial(int tier) { return Material.valueOf(prefix(tier) + "_AXE"); }
    private Material pickaxeMaterial(int tier) { return Material.valueOf(prefix(tier) + "_PICKAXE"); }
    private String prefix(int tier) { return tier <= 0 ? "WOOD" : tier == 1 ? "STONE" : tier == 2 ? "IRON" : "DIAMOND"; }

    private boolean protectedPlayer(Player player) {
        if (player == null) return false;
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        GameParticipant participant = match == null ? null : match.getParticipant(player.getUniqueId());
        return match != null && match.getState() == com.starfinplugins.bedwars.domain.match.MatchState.IN_GAME
            && participant != null && participant.getState() == ParticipantState.CONNECTED
            && matches.getWorld(match.getId()) == player.getWorld();
    }

    @EventHandler public void onArmorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)
                || !protectedPlayer((Player) event.getWhoClicked())) return;
        Player player = (Player) event.getWhoClicked();
        boolean armorSlot = event.getClickedInventory() == player.getInventory()
            && event.getSlot() >= 36 && event.getSlot() <= 39;
        boolean armorShift = event.isShiftClick() && event.getCurrentItem() != null
            && armorTier(event.getCurrentItem().getType()) >= 0;
        boolean armorHotbar = event.getClick() == ClickType.NUMBER_KEY
            && event.getHotbarButton() >= 0
            && armorTier(player.getInventory().getItem(event.getHotbarButton()) == null
                ? Material.AIR : player.getInventory().getItem(event.getHotbarButton()).getType()) >= 0;
        if (armorSlot || armorShift || armorHotbar) event.setCancelled(true);
    }

    @EventHandler public void onArmorDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)
                || !protectedPlayer((Player) event.getWhoClicked())) return;
        Player player = (Player) event.getWhoClicked();
        for (int raw : event.getRawSlots()) {
            if (raw < event.getView().getTopInventory().getSize()) continue;
            int slot = event.getView().convertSlot(raw);
            if (slot >= 36 && slot <= 39) { event.setCancelled(true); return; }
        }
    }

    @EventHandler public void onArmorDrop(PlayerDropItemEvent event) {
        if (protectedPlayer(event.getPlayer())
                && armorTier(event.getItemDrop().getItemStack().getType()) >= 0) event.setCancelled(true);
    }
}
