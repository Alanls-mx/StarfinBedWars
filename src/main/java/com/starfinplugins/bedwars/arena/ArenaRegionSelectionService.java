package com.starfinplugins.bedwars.arena;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;
import com.starfinplugins.bedwars.service.ManagedService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class ArenaRegionSelectionService implements ManagedService, Listener {
    private static final String WAND_NAME = ChatColor.AQUA + "StarfinBedWars Region Wand";
    private final Plugin plugin;
    private final ArenaService arenas;
    private final ArenaEditingService editing;
    private final Map<UUID, Selection> selections = new HashMap<UUID, Selection>();

    public ArenaRegionSelectionService(Plugin plugin, ArenaService arenas, ArenaEditingService editing) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.editing = editing;
    }

    @Override
    public String getName() { return "arena-region-selection"; }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void stop() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        selections.clear();
    }

    public void giveWand(Player player, ArenaDefinition arena, ArenaRegionType type) {
        if (!editing.isEditWorld(arena, player.getWorld())) {
            throw new IllegalStateException("Entre no modo de edicao isolado antes de selecionar uma regiao.");
        }
        clearSelection(player);
        if (player.getInventory().firstEmpty() < 0) {
            throw new IllegalStateException("Libere um espaco no inventario para receber a ferramenta.");
        }
        String token = UUID.randomUUID().toString();
        ItemStack wand = new ItemStack(Material.WOOD_AXE);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(WAND_NAME);
        java.util.List<String> lore = new java.util.ArrayList<String>();
        lore.add(ChatColor.DARK_GRAY + token);
        lore.add(ChatColor.GRAY + "Clique esquerdo: ponto 1");
        lore.add(ChatColor.GRAY + "Clique direito: ponto 2");
        meta.setLore(lore);
        wand.setItemMeta(meta);
        selections.put(player.getUniqueId(), new Selection(arena.getId(), type, token));
        player.getInventory().addItem(wand);
        player.sendMessage(ChatColor.AQUA + "Selecao " + type.name().toLowerCase(java.util.Locale.ROOT)
            + ": clique esquerdo e direito nos blocos de limite.");
    }

    public void clearSelection(Player player) {
        if (player == null) return;
        Selection selection = selections.remove(player.getUniqueId());
        if (selection == null) return;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isWand(contents[slot], selection.token)) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    public void setRegion(ArenaDefinition arena, ArenaRegionType type,
                          int minX, int minY, int minZ, int maxX, int maxY, int maxZ)
            throws IOException {
        arenas.setRegion(arena, type, new ArenaRegion(arena.getSourceWorld(),
            minX, minY, minZ, maxX, maxY, maxZ));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isNamedWand(event.getItem())) return;
        event.setCancelled(true);
        Selection selection = selections.get(event.getPlayer().getUniqueId());
        if (selection == null || !isWand(event.getItem(), selection.token)) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ArenaDefinition arena = arenas.getArena(selection.arenaId);
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (arena == null || block == null || !editing.isEditWorld(arena, block.getWorld())) {
            player.sendMessage(ChatColor.RED + "A selecao deve ser feita na instancia de edicao correta.");
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.first = block.getLocation();
            player.sendMessage(ChatColor.GREEN + "Ponto 1: " + block.getX() + ", "
                + block.getY() + ", " + block.getZ());
        } else {
            selection.second = block.getLocation();
            player.sendMessage(ChatColor.GREEN + "Ponto 2: " + block.getX() + ", "
                + block.getY() + ", " + block.getZ());
        }
        if (selection.first == null || selection.second == null) return;
        try {
            setRegion(arena, selection.type,
                selection.first.getBlockX(), selection.first.getBlockY(), selection.first.getBlockZ(),
                selection.second.getBlockX(), selection.second.getBlockY(), selection.second.getBlockZ());
            clearSelection(player);
            player.sendMessage(ChatColor.GREEN + "Regiao " + selection.type.name().toLowerCase(
                java.util.Locale.ROOT) + " salva e persistida.");
        } catch (Exception error) {
            player.sendMessage(ChatColor.RED + "Falha ao salvar regiao: " + error.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearSelection(event.getPlayer());
    }

    private boolean isWand(ItemStack item, String token) {
        return isNamedWand(item) && item.getItemMeta().hasLore()
            && item.getItemMeta().getLore().contains(ChatColor.DARK_GRAY + token);
    }

    private boolean isNamedWand(ItemStack item) {
        if (item == null || item.getType() != Material.WOOD_AXE || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return WAND_NAME.equals(meta.getDisplayName());
    }

    private static final class Selection {
        private final String arenaId;
        private final ArenaRegionType type;
        private final String token;
        private org.bukkit.Location first;
        private org.bukkit.Location second;

        private Selection(String arenaId, ArenaRegionType type, String token) {
            this.arenaId = arenaId;
            this.type = type;
            this.token = token;
        }
    }
}
