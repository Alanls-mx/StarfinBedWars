package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public final class MatchChestService implements ManagedService, Listener {
    private enum Mode { MATCH_SHARED, TEAM_SHARED, PLAYER_PRIVATE }
    private final Plugin plugin;
    private final MatchEngineService matches;
    private final Map<UUID, Map<String, SharedChest>> enderInventories =
        new HashMap<UUID, Map<String, SharedChest>>();
    private final Map<UUID, Set<String>> initializedNormalChests =
        new HashMap<UUID, Set<String>>();
    private final Map<UUID, Mode> matchModes = new HashMap<UUID, Mode>();

    public MatchChestService(Plugin plugin, MatchEngineService matches) {
        this.plugin = plugin;
        this.matches = matches;
    }

    @Override public String getName() { return "match-chests"; }
    @Override public void start() { Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public void stop() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        for (UUID id : new HashSet<UUID>(enderInventories.keySet())) clear(id);
        initializedNormalChests.clear();
        matchModes.clear();
    }

    @EventHandler public void onLifecycle(MatchLifecycleEvent event) {
        UUID id = event.getMatch().getId();
        if (event.getType() == MatchLifecycleEvent.Type.STARTED) {
            Mode mode;
            try {
                mode = Mode.valueOf(plugin.getConfig().getString("chests.ender-mode", "MATCH_SHARED"));
            } catch (IllegalArgumentException invalid) {
                mode = Mode.MATCH_SHARED;
                plugin.getLogger().warning("chests.ender-mode invalido; usando MATCH_SHARED.");
            }
            matchModes.put(id, mode);
            initializedNormalChests.put(id, new HashSet<String>());
            enderInventories.put(id, new HashMap<String, SharedChest>());
            World world = matches.getWorld(id);
            if (world != null) for (Chunk chunk : world.getLoadedChunks()) initializeNormalChests(id, chunk);
        } else if (event.getType() == MatchLifecycleEvent.Type.ENDED
                || event.getType() == MatchLifecycleEvent.Type.INSTANCE_REMOVED) {
            clear(id);
        }
    }

    @EventHandler public void onChunkLoad(ChunkLoadEvent event) {
        GameMatch match = matches.getMatchInWorld(event.getWorld());
        if (match != null && initializedNormalChests.containsKey(match.getId())) {
            initializeNormalChests(match.getId(), event.getChunk());
        }
    }

    private void initializeNormalChests(UUID matchId, Chunk chunk) {
        Set<String> initialized = initializedNormalChests.get(matchId);
        if (initialized == null) return;
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof Chest)) continue;
            String key = state.getX() + ":" + state.getY() + ":" + state.getZ();
            if (initialized.add(key)) ((Chest) state).getBlockInventory().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;
        Player player = event.getPlayer();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        GameParticipant participant = match == null ? null : match.getParticipant(player.getUniqueId());
        if (match == null || match.getState() != MatchState.IN_GAME || participant == null
                || participant.getState() != ParticipantState.CONNECTED
                || matches.getWorld(match.getId()) != player.getWorld()) return;
        event.setCancelled(true);
        Map<String, SharedChest> inventories = enderInventories.get(match.getId());
        if (inventories == null) return;
        Mode mode = matchModes.get(match.getId());
        String key = mode == Mode.TEAM_SHARED ? participant.getTeamId()
            : mode == Mode.PLAYER_PRIVATE ? player.getUniqueId().toString() : "match";
        SharedChest chest = inventories.get(key);
        if (chest == null) {
            chest = new SharedChest(match.getId(), key);
            inventories.put(key, chest);
        }
        player.openInventory(chest.getInventory());
    }

    private void clear(UUID id) {
        Map<String, SharedChest> inventories = enderInventories.remove(id);
        if (inventories != null) {
            for (SharedChest chest : inventories.values()) {
                for (org.bukkit.entity.HumanEntity viewer : new java.util.ArrayList<org.bukkit.entity.HumanEntity>(
                        chest.getInventory().getViewers())) viewer.closeInventory();
                chest.getInventory().clear();
            }
        }
        initializedNormalChests.remove(id);
        matchModes.remove(id);
    }

    private static final class SharedChest implements InventoryHolder {
        private final UUID matchId;
        private final String key;
        private final Inventory inventory;
        private SharedChest(UUID matchId, String key) {
            this.matchId = matchId;
            this.key = key;
            this.inventory = Bukkit.createInventory(this, 27, ChatColor.DARK_PURPLE + "Bau do Fim");
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
