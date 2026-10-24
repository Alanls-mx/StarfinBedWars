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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderDragon;
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
        }, 20L, 10L);
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
        ArenaRegion bounds = match.getArena().getBounds();
        if (bounds == null) bounds = match.getArena().getRegion(ArenaRegionType.MAIN);
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
        ticks += 10L;
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
        matches.destroyAllBedsAtMatchPoint(match);
        for (GameParticipant participant : match.getParticipantList()) {
            Player player = Bukkit.getPlayer(participant.getPlayerId());
            if (player != null && player.isOnline() && player.getWorld() == world) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("match-point.message",
                        "&5&lMATCH POINT! &cTodas as camas foram destruidas! "
                        + "&dUm dragao esta destruindo a arena!")));
            }
        }
        ArenaRegion bounds = match.getArena().getBounds();
        if (bounds == null) bounds = match.getArena().getRegion(ArenaRegionType.MAIN);
        if (bounds == null) {
            plugin.getLogger().warning("Match Point sem limites de arena: "
                + match.getArena().getId());
            return;
        }
        int baseY = 80;
        int count = 0;
        for (com.starfinplugins.bedwars.domain.arena.TeamDefinition team
                : match.getArena().getTeamList()) {
            Position spawn = team.getSpawn();
            if (spawn != null) { baseY += (int) spawn.getY(); count++; }
        }
        baseY = count == 0 ? 85 : (baseY - 80) / count
            + plugin.getConfig().getInt("match-point.dragon-height-offset", 3);
        active.path = new MatchPointPath(bounds, match.getArena().getCeilingHeight(), baseY);
        try {
            double[] point = active.path.at(0.0D);
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
        }
    }

    private void advance(Active active, World world) {
        EnderDragon dragon = active.dragon;
        if (dragon == null || !dragon.isValid() || dragon.isDead() || active.path == null) return;
        active.angle += Math.max(0.01D, Math.min(0.20D,
            plugin.getConfig().getDouble("match-point.dragon-angular-speed", 0.065D)));
        double[] p = active.path.at(active.angle);
        Location target = new Location(world, p[0], p[1], p[2]);
        target.setYaw((float) Math.toDegrees(-active.angle));
        dragon.teleport(target);
        destroyNearby(active, target);
        damageNearby(active, target);
    }

    /**
     * Deliberate, rate-limited island erosion instead of uncontrolled vanilla
     * EnderDragon grief. Only disposable match instance chunks are modified.
     */
    private void destroyNearby(Active active, Location point) {
        if (ticks % 20L != 0L) return;
        ArenaRegion bounds = active.match.getArena().getBounds();
        if (bounds == null) bounds = active.match.getArena().getRegion(ArenaRegionType.MAIN);
        if (bounds == null) return;
        int limit = Math.max(1, Math.min(35,
            plugin.getConfig().getInt("match-point.blocks-per-sweep", 18)));
        int removed = 0;
        World world = point.getWorld();
        for (int dy = -6; dy <= 2 && removed < limit; dy++) {
            for (int dx = -3; dx <= 3 && removed < limit; dx++) {
                for (int dz = -3; dz <= 3 && removed < limit; dz++) {
                    int x = point.getBlockX() + dx;
                    int y = point.getBlockY() + dy;
                    int z = point.getBlockZ() + dz;
                    if (y < 1 || y > 254 || !world.isChunkLoaded(x >> 4, z >> 4)
                            || x < bounds.getMinimumX() || x > bounds.getMaximumX()
                            || z < bounds.getMinimumZ() || z > bounds.getMaximumZ()
                            || y < bounds.getMinimumY() || y > bounds.getMaximumY()) continue;
                    Block block = world.getBlockAt(x, y, z);
                    Material material = block.getType();
                    if (material == Material.AIR || material == Material.BEDROCK
                            || material == Material.BED_BLOCK || !material.isSolid()) continue;
                    matches.forgetMatchPointBlock(active.match, block);
                    block.setType(Material.AIR, false);
                    removed++;
                }
            }
        }
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
        if (!(entity instanceof EnderDragon)) return false;
        for (Active active : sessions.values()) {
            if (active.dragon != null && active.dragon.getUniqueId().equals(entity.getUniqueId())) return true;
        }
        return false;
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
        private double angle;
        private MatchPointPath path;
        private EnderDragon dragon;
        private Active(GameMatch match) { this.match = match; }
    }
}
