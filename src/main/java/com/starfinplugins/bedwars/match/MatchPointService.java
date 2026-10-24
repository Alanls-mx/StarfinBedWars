package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.world.ManagedMobGuard;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * After the configured match length, all remaining beds break and an
 * invulnerable, server-controlled Ender Dragon patrols the disposable arena.
 * Dragon block destruction is confined to the cloned match world. The next
 * game starts from its original immutable template, so no regeneration is
 * attempted on the live world.
 */
public final class MatchPointService implements ManagedService, Listener {
    private static final String DRAGON_TAG = "starfinbedwars-match-point-dragon";
    private final Plugin plugin;
    private final MatchEngineService matches;
    private final ManagedMobGuard mobGuard;
    private final MatchPointDragonFlightV1_8_R3 flight = new MatchPointDragonFlightV1_8_R3();
    private final MatchPointBossBarV1_8_R3 bossBars = new MatchPointBossBarV1_8_R3();
    private final Map<UUID, Active> sessions = new HashMap<UUID, Active>();
    private BukkitTask timer;
    private long ticks;

    public MatchPointService(Plugin plugin, MatchEngineService matches, ManagedMobGuard mobGuard) {
        this.plugin = plugin;
        this.matches = matches;
        this.mobGuard = mobGuard;
    }

    @Override public String getName() { return "match-point"; }

    @Override public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        timer = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { tick(); }
        }, 1L, 1L);
    }

    @Override public void stop() {
        if (timer != null) timer.cancel();
        timer = null;
        HandlerList.unregisterAll(this);
        for (Active active : sessions.values()) removeDragon(active);
        sessions.clear();
        bossBars.clear();
    }

    @EventHandler
    public void onLifecycle(MatchLifecycleEvent event) {
        final GameMatch match = event.getMatch();
        if (event.getType() == MatchLifecycleEvent.Type.STARTED) {
            if (plugin.getConfig().getBoolean("match-point.enabled", true)) {
                sessions.put(match.getId(), new Active(match));
            }
        } else if (event.getType() == MatchLifecycleEvent.Type.ENDED
                || event.getType() == MatchLifecycleEvent.Type.INSTANCE_REMOVED) {
            Active active = sessions.remove(match.getId());
            if (active != null) removeDragon(active);
            syncBossBars();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bossBars.hide(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        // The client discards packet-only entities on world changes.
        // The next bossbar tick will spawn a fresh one if still eligible.
        bossBars.hide(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // A respawn clears client-side entities even if it stays in the
        // same world; force a fresh fake-boss spawn on the next update.
        bossBars.hide(event.getPlayer());
    }

    public enum ForceResult {
        ACTIVATED, ALREADY_ACTIVE, NOT_RUNNING, INVALID_ARENA, NO_WORLD, FAILED
    }

    /**
     * Trigger the same Match Point sequence as the 30-minute timer, instantly.
     * Only the admin command should call this, after permission checks.
     * Does not modify the timer or any other match.
     */
    public ForceResult forceMatchPoint(GameMatch match) {
        if (timer == null || match == null || match.getState() != MatchState.IN_GAME) {
            return ForceResult.NOT_RUNNING;
        }
        World world = matches.getWorld(match.getId());
        if (world == null || matches.getMatchForArena(match.getArena().getId()) != match) {
            return ForceResult.NO_WORLD;
        }
        ArenaRegion bounds = MatchPointArea.resolve(match.getArena());
        if (bounds == null) return ForceResult.INVALID_ARENA;

        Active active = sessions.get(match.getId());
        if (active != null && active.triggered) return ForceResult.ALREADY_ACTIVE;
        if (active == null) {
            active = new Active(match);
            sessions.put(match.getId(), active);
        }
        active.triggered = true;
        activate(active, world);
        if (active.dragon == null || !active.dragon.isValid()) {
            active.triggered = false;
            return ForceResult.FAILED;
        }
        return ForceResult.ACTIVATED;
    }

    private void tick() {
        ticks++;
        for (Iterator<Map.Entry<UUID, Active>> it = sessions.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Active> entry = it.next();
            Active active = entry.getValue();
            GameMatch match = active.match;
            if (match.getState() != MatchState.IN_GAME) {
                removeDragon(active);
                it.remove();
                continue;
            }
            World world = matches.getWorld(match.getId());
            if (world == null) continue;
            if (!active.triggered) {
                // The flight controller runs every tick for smooth animation;
                // Match Point timing only needs a check once per second.
                if (ticks % 20L != 0L) continue;
                long threshold = Math.max(1L, Math.min(120L,
                    plugin.getConfig().getLong("match-point.after-minutes", 30L))) * 60000L;
                if (match.getStartedAt() == null || System.currentTimeMillis()
                        - match.getStartedAt().toEpochMilli() < threshold) continue;
                ForceResult result = forceMatchPoint(match);
                if (result == ForceResult.FAILED || result == ForceResult.INVALID_ARENA) {
                    plugin.getLogger().warning("Match Point nao ativado em "
                        + match.getArena().getId() + ": " + result);
                }
            } else {
                advance(active, world);
            }
        }
        if (ticks % 10L == 0L) syncBossBars();
    }

    /** Keep a fake, client-only Wither health bar near each 1.8.8 viewer. */
    private void syncBossBars() {
        java.util.Set<UUID> eligible = new java.util.HashSet<UUID>();
        if (!plugin.getConfig().getBoolean("match-point.bossbar-enabled", true)) {
            bossBars.clear();
            return;
        }
        for (Active active : sessions.values()) {
            if (!active.triggered || active.match.getState() != MatchState.IN_GAME
                    || active.dragon == null || !active.dragon.isValid()) continue;
            World world = matches.getWorld(active.match.getId());
            if (world == null) continue;
            for (Player player : world.getPlayers()) {
                if (!player.isOnline()) continue;
                eligible.add(player.getUniqueId());
                try {
                    bossBars.update(player, ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "MATCH POINT");
                } catch (RuntimeException error) {
                    plugin.getLogger().warning("Nao foi possivel atualizar a barra Match Point de "
                        + player.getName() + ": " + error.getMessage());
                }
            }
        }
        for (UUID viewerId : bossBars.getViewerIds()) {
            if (eligible.contains(viewerId)) continue;
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null) bossBars.hide(player);
            else bossBars.forget(viewerId);
        }
    }

    private void activate(Active active, World world) {
        GameMatch match = active.match;
        ArenaRegion bounds = MatchPointArea.resolve(match.getArena());
        if (bounds == null) {
            plugin.getLogger().warning("Match Point sem limites de arena: "
                + match.getArena().getId());
            return;
        }
        int sumY = 0;
        int count = 0;
        java.util.List<Position> islands = new ArrayList<Position>();
        for (com.starfinplugins.bedwars.domain.arena.TeamDefinition team
                : match.getArena().getTeamList()) {
            Position spawn = team.getSpawn();
            if (spawn != null) {
                sumY += (int) spawn.getY();
                count++;
                islands.add(spawn);
            }
        }
        int baseY = (count == 0 ? 85 : sumY / count)
            + plugin.getConfig().getInt("match-point.dragon-height-offset", 3);
        active.route = new MatchPointPatrolRoute(bounds,
            match.getArena().getCeilingHeight(), baseY,
            plugin.getConfig().getInt("match-point.dragon-patrol-spacing", 40), islands);
        plugin.getLogger().info("Match Point: rota pos1/pos2 na arena "
            + match.getArena().getId() + " entre X=" + bounds.getMinimumX()
            + ".." + bounds.getMaximumX() + " Y=" + bounds.getMinimumY()
            + ".." + bounds.getMaximumY() + " Z=" + bounds.getMinimumZ()
            + ".." + bounds.getMaximumZ() + " (" + active.route.size()
            + " pontos de voo).");
        try {
            double[] point = active.route.at(0);
            Location location = new Location(world, point[0], point[1], point[2]);
            Entity entity = mobGuard.spawnAuthorized(location, EntityType.ENDER_DRAGON);
            if (!(entity instanceof EnderDragon)) {
                if (entity != null) entity.remove();
                plugin.getLogger().warning("Match Point: spawn nao retornou EnderDragon.");
                return;
            }
            active.dragon = (EnderDragon) entity;
            active.dragon.setMetadata(DRAGON_TAG, new FixedMetadataValue(plugin, match.getId().toString()));
            active.dragon.setCustomName(ChatColor.DARK_PURPLE + "MATCH POINT");
            active.dragon.setCustomNameVisible(true);
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Match Point: nao foi possivel iniciar dragao: "
                + failure.getMessage());
            removeDragon(active);
            return;
        }
        // Only alter the match when the Dragon actually exists. A failed
        // test command must not irreversibly break beds without a Match Point.
        if (active.dragon == null || !active.dragon.isValid()) return;
        matches.destroyAllBedsAtMatchPoint(match);
        for (GameParticipant participant : match.getParticipantList()) {
            Player player = Bukkit.getPlayer(participant.getPlayerId());
            if (player != null && player.isOnline() && player.getWorld() == world) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("match-point.message",
                        "&5&lMATCH POINT! &cTodas as camas foram destruídas! "
                        + "&dO dragão está destruindo a arena!")));
            }
        }
    }

    private void advance(Active active, World world) {
        EnderDragon dragon = active.dragon;
        if (dragon == null || !dragon.isValid() || dragon.isDead() || active.route == null) return;

        // Follow successive diagonal waypoints across MAIN, alternating the
        // two arms of an X instead of sweeping the map as a grid.
        // Move toward a sequence of waypoints using the dragon's OWN 1.8.8
        // flight AI. No periodic teleport, no forced rotation, no broken
        // history of body/head/tail positions.
        double[] point = active.route.at(active.waypointIndex);
        Location waypoint = new Location(world, point[0], point[1], point[2]);
        double reach = Math.max(8.0D, Math.min(24.0D,
            plugin.getConfig().getDouble("match-point.dragon-waypoint-reach", 14.0D)));
        if (dragon.getLocation().distanceSquared(waypoint) <= reach * reach
                || ++active.waypointTicks > 220) {
            active.waypointIndex = (active.waypointIndex + 1) % active.route.size();
            active.waypointTicks = 0;
            point = active.route.at(active.waypointIndex);
            waypoint = new Location(world, point[0], point[1], point[2]);
        }
        try {
            flight.steer(dragon, waypoint);
        } catch (RuntimeException failure) {
            // An incompatible fork must never leave a constantly teleporting,
            // visually broken dragon on screen.
            plugin.getLogger().severe("Match Point: controle de voo v1_8_R3 falhou: "
                + failure.getMessage());
            removeDragon(active);
            return;
        }

        // Use the actual dragon position, not an artificial path point.
        // This keeps erosion and damage synchronized with what players see.
        Location actual = dragon.getLocation();
        destroyNearby(active, actual);
        damageNearby(active, actual);
    }

    /**
     * Small TNT-like CIRCULAR craters only where the dragon really flies.
     * The alternating X is the DRAGON PATROL, not the blast footprint.
     * No map-wide sweeping or edits at distant player positions.
     */
    private void destroyNearby(Active active, Location point) {
        final int interval = Math.max(20, Math.min(100,
            plugin.getConfig().getInt("match-point.dragon-blast-interval-ticks", 40)));
        if (ticks - active.lastBlastTick < interval) return;
        // Never tunnel out the same place because the dragon is stuck.
        final double minMovement = Math.max(1.0D, Math.min(15.0D,
            plugin.getConfig().getDouble("match-point.dragon-blast-min-movement", 5.0D)));
        if (active.lastBlastPosition != null
                && active.lastBlastPosition.getWorld() == point.getWorld()
                && active.lastBlastPosition.distanceSquared(point) < minMovement * minMovement) {
            return;
        }
        ArenaRegion bounds = MatchPointArea.resolve(active.match.getArena());
        if (bounds == null) return;
        final int maxBlocks = Math.max(1, Math.min(24,
            plugin.getConfig().getInt("match-point.dragon-blast-blocks", 8)));
        final int radius = Math.max(1, Math.min(6,
            plugin.getConfig().getInt("match-point.dragon-blast-radius", 3)));
        final int depth = Math.max(1, Math.min(4,
            plugin.getConfig().getInt("match-point.dragon-blast-depth", 1)));

        final World world = point.getWorld();
        int centerX = point.getBlockX();
        int centerZ = point.getBlockZ();
        int removed = 0;
        Location visibleImpact = null;
        for (MatchPointBlastFootprint.Offset offset : MatchPointBlastFootprint.offsets(radius)) {
            if (removed >= maxBlocks) break;
            int x = centerX + offset.x, z = centerZ + offset.z;
            if (x < bounds.getMinimumX() || x > bounds.getMaximumX()
                    || z < bounds.getMinimumZ() || z > bounds.getMaximumZ()
                    || !world.isChunkLoaded(x >> 4, z >> 4)) continue;

            int highest = Math.min(bounds.getMaximumY(), world.getHighestBlockYAt(x, z));
            if (highest < bounds.getMinimumY()) continue;
            // Spigot 1.8 height maps may point to the AIR above the terrain.
            // Remove a few layers only, like TNT digging a small crater.
            int erasedInColumn = 0;
            for (int y = highest, searched = 0;
                    y >= bounds.getMinimumY() && searched < 12
                    && erasedInColumn < depth && removed < maxBlocks;
                    y--, searched++) {
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() == Material.AIR) continue;
                if (visibleImpact == null) visibleImpact = block.getLocation().add(0.5D, 0.5D, 0.5D);
                if (eraseBlock(active, block)) {
                    erasedInColumn++;
                    removed++;
                }
            }
        }

        active.lastBlastTick = ticks;
        active.lastBlastPosition = point.clone();
        if (removed > 0) {
            active.localDestroyed += removed;
            if (visibleImpact != null) {
                world.playEffect(visibleImpact, Effect.EXPLOSION_LARGE, 0);
                world.playSound(visibleImpact, Sound.EXPLODE, 0.85F, 0.85F);
            }
        }
    }

    /** Erase only disposable match-instance blocks, never bedrock. */
    private boolean eraseBlock(Active active, Block block) {
        Material material = block.getType();
        if (material == Material.AIR || material == Material.BEDROCK) return false;
        matches.forgetMatchPointBlock(active.match, block);
        block.setType(Material.AIR, false);
        return block.getType() == Material.AIR;
    }

    private void damageNearby(Active active, Location center) {
        if (ticks % 20L != 0L) return;
        double radius = Math.max(2.0D, Math.min(9.0D,
            plugin.getConfig().getDouble("match-point.dragon-damage-radius", 5.0D)));
        double amount = Math.max(1.0D, Math.min(12.0D,
            plugin.getConfig().getDouble("match-point.dragon-damage", 5.0D)));
        for (Player player : center.getWorld().getPlayers()) {
            GameParticipant participant = active.match.getParticipant(player.getUniqueId());
            if (participant == null || participant.getState() != ParticipantState.CONNECTED
                    || matches.getMatchForPlayer(player.getUniqueId()) != active.match
                    || matches.isSpectating(player.getUniqueId()) || player.isDead()) continue;
            if (player.getLocation().distanceSquared(center) <= radius * radius) {
                player.damage(amount);
            }
        }
    }

    private void removeDragon(Active active) {
        if (active.dragon == null) return;
        mobGuard.forget(active.dragon);
        if (active.dragon.isValid()) active.dragon.remove();
        active.dragon = null;
    }

    private boolean isOwnedDragon(Entity entity) {
        // In 1.8.8 hits can target the head, wing or tail multipart entity,
        // rather than the primary EnderDragon Bukkit entity.
        if (entity instanceof EnderDragonPart) {
            entity = ((EnderDragonPart) entity).getParent();
        }
        if (!(entity instanceof EnderDragon)) return false;
        for (Active active : sessions.values()) {
            if (active.dragon != null && active.dragon.getUniqueId().equals(entity.getUniqueId())) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDragonRandomTarget(EntityTargetEvent event) {
        if (isOwnedDragon(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDragonDamage(EntityDamageEvent event) {
        if (isOwnedDragon(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDragonMelee(EntityDamageByEntityEvent event) {
        if (isOwnedDragon(event.getDamager())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDragonNaturalGrief(EntityExplodeEvent event) {
        if (isOwnedDragon(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDragonChangeBlock(EntityChangeBlockEvent event) {
        if (isOwnedDragon(event.getEntity())) event.setCancelled(true);
    }

    private static final class Active {
        private final GameMatch match;
        private boolean triggered;
        private int waypointIndex;
        private int waypointTicks;
        private MatchPointPatrolRoute route;
        private EnderDragon dragon;
        private long localDestroyed;
        private long lastBlastTick = Long.MIN_VALUE / 2L;
        private Location lastBlastPosition;
        private Active(GameMatch match) { this.match = match; }
    }
}
