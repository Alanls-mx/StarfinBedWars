package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.config.BedWarsSettings;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Converts valid TNT placement into primed TNT on the next server tick.
 * Placement is accepted and consumed by vanilla first; the block is removed
 * only if it still belongs to a live match. Explosions are protected by
 * MatchEngineService rather than by this visual/priming service.
 */
public final class TntAutoIgniteService implements ManagedService, Listener {
    private final Plugin plugin;
    private final MatchEngineService matches;
    private final BedWarsSettings settings;
    private final Map<UUID, Map<String, BukkitTask>> pending =
        new HashMap<UUID, Map<String, BukkitTask>>();

    public TntAutoIgniteService(Plugin plugin, MatchEngineService matches, BedWarsSettings settings) {
        if (plugin == null || matches == null || settings == null) {
            throw new IllegalArgumentException("Dependencias da TNT ausentes.");
        }
        this.plugin = plugin;
        this.matches = matches;
        this.settings = settings;
    }

    @Override public String getName() { return "match-tnt-auto-ignite"; }

    @Override public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override public void stop() {
        HandlerList.unregisterAll(this);
        for (Map<String, BukkitTask> tasks : pending.values()) {
            for (BukkitTask task : tasks.values()) task.cancel();
        }
        pending.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntPlaced(BlockPlaceEvent event) {
        if (!settings.isTntAutoPrime()) return;
        final Block block = event.getBlockPlaced();
        if (block.getType() != Material.TNT) return;
        final GameMatch match = matches.getMatchInWorld(block.getWorld());
        if (match == null) return;
        final GameParticipant participant = match.getParticipant(event.getPlayer().getUniqueId());
        if (!TntPlacementPolicy.shouldPrime(match.getState() == MatchState.IN_GAME,
                matches.getMatchForPlayer(event.getPlayer().getUniqueId()) == match,
                participant != null && participant.getState() == ParticipantState.CONNECTED,
                !matches.isSpectating(event.getPlayer().getUniqueId()), event.canBuild())) {
            return;
        }

        final UUID matchId = match.getId();
        final World world = block.getWorld();
        final Location location = block.getLocation();
        final String key = blockKey(block);
        Map<String, BukkitTask> tasks = pending.get(matchId);
        if (tasks == null) {
            tasks = new HashMap<String, BukkitTask>();
            pending.put(matchId, tasks);
        }
        if (tasks.containsKey(key)) return;

        // Delay one tick so Bukkit consumes the placed item and all other
        // BlockPlaceEvent listeners can finish before replacing the block.
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                removePending(matchId, key);
                if (matches.getWorld(matchId) != world
                        || match.getState() != MatchState.IN_GAME) return;
                Block placed = world.getBlockAt(location);
                if (placed.getType() != Material.TNT) return;
                placed.setType(Material.AIR, false);
                try {
                    Location spawnAt = location.clone().add(0.5D, 0.0D, 0.5D);
                    TNTPrimed primed = (TNTPrimed) world.spawnEntity(spawnAt, EntityType.PRIMED_TNT);
                    primed.setFuseTicks(settings.getTntFuseTicks());
                    matches.forgetPlacedBlock(match, placed);
                } catch (RuntimeException failure) {
                    // If priming fails, preserve the purchased/placed TNT.
                    if (placed.getType() == Material.AIR) placed.setType(Material.TNT, false);
                    plugin.getLogger().warning("Falha ao ativar TNT na partida " + matchId
                        + ": " + failure.getMessage());
                }
            }
        });
        tasks.put(key, task);
    }

    @EventHandler
    public void onMatchLifecycle(MatchLifecycleEvent event) {
        if (event.getType() != MatchLifecycleEvent.Type.ENDED
                && event.getType() != MatchLifecycleEvent.Type.INSTANCE_REMOVED) return;
        Map<String, BukkitTask> tasks = pending.remove(event.getMatch().getId());
        if (tasks != null) for (BukkitTask task : tasks.values()) task.cancel();
    }

    private void removePending(UUID matchId, String key) {
        Map<String, BukkitTask> tasks = pending.get(matchId);
        if (tasks == null) return;
        tasks.remove(key);
        if (tasks.isEmpty()) pending.remove(matchId);
    }

    private static String blockKey(Block block) {
        return block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
