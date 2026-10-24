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
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.ChunkSnapshot;
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
        }
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
        ArenaRegion bounds = match.getArena().getRegion(ArenaRegionType.MAIN);
        if (bounds == null) bounds = match.getArena().getBounds();
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
    }

    private void activate(Active active, World world) {
        GameMatch match = active.match;
        ArenaRegion bounds = match.getArena().getRegion(ArenaRegionType.MAIN);
        if (bounds == null) bounds = match.getArena().getBounds();
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
            plugin.getConfig().getInt("match-point.dragon-patrol-spacing", 40));
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
        active.erosion = new MatchPointErosionCursor(
            bounds.getMinimumX(), bounds.getMinimumY(), bounds.getMinimumZ(),
            bounds.getMaximumX(), bounds.getMaximumY(), bounds.getMaximumZ());
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
        // This sweep is independent of player count, view distance and the
        // dragon's current waypoint. Every generated chunk inside the arena
        // is examined, even if nobody is near it or the dragon has despawned.
        erodeEntireArena(active, world);
        EnderDragon dragon = active.dragon;
        if (dragon == null || !dragon.isValid() || dragon.isDead() || active.route == null) return;

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
     * Deliberate, rate-limited island erosion instead of uncontrolled vanilla
     * EnderDragon grief. Only disposable match instance chunks are modified.
     */
    private void destroyNearby(Active active, Location point) {
        if (ticks % 20L != 0L) return;
        ArenaRegion bounds = active.match.getArena().getRegion(ArenaRegionType.MAIN);
        if (bounds == null) bounds = active.match.getArena().getBounds();
        if (bounds == null) return;
        final int maxRemoved = Math.max(1, Math.min(75,
            plugin.getConfig().getInt("match-point.blocks-per-sweep", 28)));
        final int radius = Math.max(3, Math.min(14,
            plugin.getConfig().getInt("match-point.dragon-erode-radius", 9)));
        final int diameter = radius * 2 + 1;
        final int total = diameter * diameter;
        // Alternate the first sampled column so each sweep cuts different
        // terrain instead of drilling the same hole every second.
        final int start = (int) ((ticks / 20L * 37L) % total);
        int removed = 0;
        World world = point.getWorld();
        for (int offset = 0; offset < total && removed < maxRemoved; offset++) {
            int sample = (start + offset) % total;
            int dx = sample % diameter - radius;
            int dz = sample / diameter - radius;
            if (dx * dx + dz * dz > radius * radius) continue;
            int x = point.getBlockX() + dx;
            int z = point.getBlockZ() + dz;
            if (x < bounds.getMinimumX() || x > bounds.getMaximumX()
                    || z < bounds.getMinimumZ() || z > bounds.getMaximumZ()
                    || !world.isChunkLoaded(x >> 4, z >> 4)) continue;
            // The dragon may fly above the islands. Erode the actual terrain
            // surface at X/Z rather than the empty air around its body.
            int y = Math.min(bounds.getMaximumY(), world.getHighestBlockYAt(x, z));
            if (y < bounds.getMinimumY()) continue;
            // PaperSpigot 1.8 may return the Y of the AIR above the surface.
            // Look down a few blocks so dragon strikes genuinely hit islands.
            for (int depth = 0; depth < 8 && y >= bounds.getMinimumY(); depth++, y--) {
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() == Material.AIR) continue;
                if (eraseBlock(active, block)) removed++;
                break;
            }
        }
        if (removed > 0) active.localDestroyed += removed;
    }

    /**
     * Progressive destruction of the WHOLE disposable match map.
     * Chunk-first scanning covers every x/y/z location once, with bounded
     * main-thread work and at most one new chunk loaded per tick.
     *
     * Unlike dragon-local destruction, no players or dragon proximity are
     * required. The caller only runs this for this match's cloned world.
     */
    private void erodeEntireArena(Active active, World world) {
        MatchPointErosionCursor cursor = active.erosion;
        if (cursor == null || !cursor.hasNext()) return;
        final int checksPerTick = Math.max(250, Math.min(8000,
            plugin.getConfig().getInt("match-point.global-erosion.scan-positions-per-tick", 3000)));
        final int blocksPerTick = Math.max(1, Math.min(120,
            plugin.getConfig().getInt("match-point.global-erosion.max-blocks-per-tick", 40)));
        int changed = 0;
        int chunkLoads = 0;
        int checks = 0;
        while (cursor.hasNext() && checks < checksPerTick && changed < blocksPerTick) {
            MatchPointErosionCursor.Cell cell = cursor.peek();
            long chunkKey = (((long) cell.chunkX()) << 32) ^ (cell.chunkZ() & 0xffffffffL);
            if (!active.unavailableChunks.contains(chunkKey)
                    && !world.isChunkLoaded(cell.chunkX(), cell.chunkZ())) {
                // One previously unloaded chunk per tick prevents simultaneous
                // map-wide chunk loads, even when no players are present.
                if (chunkLoads >= 1) break;
                chunkLoads++;
                boolean loaded;
                try {
                    loaded = world.loadChunk(cell.chunkX(), cell.chunkZ(), false);
                } catch (RuntimeException error) {
                    loaded = false;
                    plugin.getLogger().warning("Match Point: falha ao carregar chunk "
                        + cell.chunkX() + "," + cell.chunkZ() + " da instancia "
                        + world.getName() + ": " + error.getMessage());
                }
                if (!loaded) {
                    active.unavailableChunks.add(chunkKey);
                    plugin.getLogger().warning("Match Point: chunk da arena indisponivel "
                        + cell.chunkX() + "," + cell.chunkZ()
                        + "; nenhum bloco externo sera gerado.");
                }
            }
            if (active.unavailableChunks.contains(chunkKey)) {
                cursor.advance();
                checks++;
                continue;
            }

            // A 280x280x256 arena has ~20 million coordinates. The heightmap
            // cannot safely skip sky: in 1.8 it ignores some transparent blocks.
            // Instead skip ONLY 16-block sections verified empty in the original
            // chunk snapshot. Glass, wool, water and other blocks are preserved
            // in the scan even when the heightmap would ignore them.
            if (active.snapshotChunk != chunkKey || active.snapshot == null) {
                active.snapshot = world.getChunkAt(cell.chunkX(), cell.chunkZ())
                    .getChunkSnapshot(false, false, false);
                active.snapshotChunk = chunkKey;
            }
            if (active.snapshot.isSectionEmpty(cell.y >> 4)) {
                int originalY = cell.y;
                cursor.skipAirAbove((cell.y & ~15) - 1);
                if (cursor.peek().y == originalY) cursor.advance();
                checks++;
                continue;
            }
            Block block = world.getBlockAt(cell.x, cell.y, cell.z);
            cursor.advance();
            checks++;
            if (eraseBlock(active, block)) changed++;
        }
        int progress = cursor.getProgressPercent();
        if (progress >= active.lastErosionReport + 25 || !cursor.hasNext()) {
            active.lastErosionReport = progress;
            plugin.getLogger().info("Match Point: destruicao progressiva da arena "
                + active.match.getArena().getId() + " " + progress + "% ("
                + cursor.getVisited() + "/" + cursor.getTotal() + " posicoes verificadas, "
                + active.localDestroyed + " blocos destruidos perto do dragao).");
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
        private MatchPointErosionCursor erosion;
        private int lastErosionReport;
        private long localDestroyed;
        private long snapshotChunk = Long.MIN_VALUE;
        private ChunkSnapshot snapshot;
        private final Set<Long> unavailableChunks = new HashSet<Long>();
        private Active(GameMatch match) { this.match = match; }
    }
}
