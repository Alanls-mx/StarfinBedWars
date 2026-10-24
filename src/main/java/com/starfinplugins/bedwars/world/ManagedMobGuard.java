package com.starfinplugins.bedwars.world;

import com.starfinplugins.bedwars.arena.ArenaEditingService;
import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

/** Blocks creature creation only inside worlds or regions owned by BedWars. */
public final class ManagedMobGuard implements ManagedService, Listener {
    private final Plugin plugin;
    private final ArenaService arenas;
    private final ArenaEditingService editing;
    private final MatchEngineService matches;
    private final WorldManagementService worlds;
    private final Set<UUID> authorized = new HashSet<UUID>();
    private final ThreadLocal<SpawnPermit> permit = new ThreadLocal<SpawnPermit>();

    public ManagedMobGuard(Plugin plugin, ArenaService arenas, ArenaEditingService editing,
                           MatchEngineService matches, WorldManagementService worlds) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.editing = editing;
        this.matches = matches;
        this.worlds = worlds;
    }

    @Override public String getName() { return "managed-mob-guard"; }

    @Override public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override public void stop() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        authorized.clear();
        permit.remove();
    }

    public Entity spawnAuthorized(Location location, EntityType type) {
        return spawnAuthorized(location, type, new Supplier<Entity>() {
            @Override public Entity get() { return location.getWorld().spawnEntity(location, type); }
        });
    }

    public Entity spawnAuthorized(Location location, EntityType type, Supplier<? extends Entity> factory) {
        if (location == null || location.getWorld() == null || type == null) {
            throw new IllegalArgumentException("Spawn autorizado invalido.");
        }
        if (factory == null) throw new IllegalArgumentException("Fabrica de entidade ausente.");
        if (permit.get() != null) throw new IllegalStateException("Spawn autorizado aninhado.");
        permit.set(new SpawnPermit(location.getWorld(), type));
        try {
            Entity entity = factory.get();
            if (entity == null || !entity.isValid() || entity.getWorld() != location.getWorld()
                    || entity.getType() != type) {
                throw new IllegalStateException("Fabrica de entidade autorizada retornou entidade invalida.");
            }
            authorized.add(entity.getUniqueId());
            return entity;
        } finally {
            permit.remove();
        }
    }

    public void forget(Entity entity) {
        if (entity != null) authorized.remove(entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!controlled(event.getLocation())) return;
        SpawnPermit active = permit.get();
        if (active == null || active.world != event.getLocation().getWorld()
                || active.type != event.getEntityType()) event.setCancelled(true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!matches.isMatchWorld(event.getWorld())) return;
        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof Creature) || authorized.contains(entity.getUniqueId())) continue;
            entity.remove();
        }
    }

    @EventHandler
    public void onMatchStarted(MatchLifecycleEvent event) {
        if (event.getType() != MatchLifecycleEvent.Type.STARTED) return;
        World world = matches.getWorld(event.getMatch().getId());
        if (world == null) return;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Creature && !authorized.contains(entity.getUniqueId())) entity.remove();
        }
    }

    private boolean controlled(Location location) {
        World world = location.getWorld();
        if (matches.isMatchWorld(world) || editing.isEditingWorld(world)) return true;
        for (String name : worlds.getManagedWorlds().keySet()) {
            if (name.equalsIgnoreCase(world.getName())) return true;
        }
        for (ArenaDefinition arena : arenas.getArenas()) {
            if (!arena.getSourceWorld().equals(world.getName())) continue;
            ArenaRegion region = arena.getRegion(ArenaRegionType.MAIN);
            if (region != null && location.getY() >= 0 && location.getY() <= 255
                    && region.contains(new Position(world.getName(), location.getX(),
                        location.getY(), location.getZ(), location.getYaw(), location.getPitch()))) return true;
        }
        return false;
    }

    private static final class SpawnPermit {
        private final World world;
        private final EntityType type;
        private SpawnPermit(World world, EntityType type) {
            this.world = world;
            this.type = type;
        }
    }
}
