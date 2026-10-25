package com.starfinplugins.bedwars.world;

import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.service.ManagedService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Applies configured world and arena environment rules on the Bukkit thread. */
public final class AlwaysService implements ManagedService, Listener {
    private final Plugin plugin;
    private final ArenaService arenas;
    private final MatchEngineService matches;
    private final Map<UUID, ArenaContext> arenaWorlds = new HashMap<UUID, ArenaContext>();
    private final Map<UUID, WorldState> original = new HashMap<UUID, WorldState>();
    private AlwaysSettings settings;
    private BukkitTask task;

    public AlwaysService(Plugin plugin, ArenaService arenas, MatchEngineService matches) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.matches = matches;
    }

    @Override public String getName() { return "always-environment"; }

    @Override public void start() throws IOException {
        settings = AlwaysSettings.load(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        enforceAll();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::enforceAll, 20L, 20L);
    }

    @Override public void stop() {
        if (task != null) task.cancel();
        HandlerList.unregisterAll(this);
        restoreAll();
        arenaWorlds.clear();
        settings = null;
    }

    /** An invalid file leaves the currently applied rules untouched. */
    public void reload() throws IOException {
        AlwaysSettings next = AlwaysSettings.load(plugin);
        restoreAll();
        settings = next;
        enforceAll();
    }

    public String describe(World world) {
        if (world == null || settings == null) return "Mundo indisponivel.";
        ArenaContext context = arenaWorlds.get(world.getUID());
        AlwaysSettings.Rule rule = context == null ? settings.forWorld(world.getName())
            : settings.forArena(context.arenaId, context.sourceWorld);
        String scope = context == null ? "mundo=" + world.getName()
            : "arena=" + context.arenaId + " instancia=" + world.getName();
        return scope + " " + rule.summary() + " | atual: time=" + world.getTime()
            + " difficulty=" + world.getDifficulty()
            + " storm=" + world.hasStorm() + " thunder=" + world.isThundering()
            + " mob-spawning=" + world.getGameRuleValue("doMobSpawning")
            + " mob-griefing=" + world.getGameRuleValue("mobGriefing")
            + " monsters=" + world.getAllowMonsters() + " animals=" + world.getAllowAnimals();
    }

    public String describeArena(String arenaId) {
        ArenaDefinition arena = arenas.getArena(arenaId);
        if (arena == null || settings == null) return "Arena inexistente ou configuracao indisponivel.";
        return "arena=" + arena.getId() + " source-world=" + arena.getSourceWorld()
            + " " + settings.forArena(arena.getId(), arena.getSourceWorld()).summary();
    }

    @EventHandler public void onWorldLoad(WorldLoadEvent event) { enforce(event.getWorld()); }

    @EventHandler public void onWorldUnload(WorldUnloadEvent event) {
        restore(event.getWorld());
        arenaWorlds.remove(event.getWorld().getUID());
    }

    @EventHandler public void onMatch(MatchLifecycleEvent event) {
        if (event.getType() != MatchLifecycleEvent.Type.CREATED) return;
        World world = matches.getWorld(event.getMatch().getId());
        if (world == null) return;
        ArenaDefinition arena = event.getMatch().getArena();
        arenaWorlds.put(world.getUID(), new ArenaContext(arena.getId(), arena.getSourceWorld()));
        enforce(world);
    }

    private void enforceAll() {
        for (World world : Bukkit.getWorlds()) enforce(world);
    }

    private void enforce(World world) {
        if (world == null || settings == null) return;
        ArenaContext context = arenaWorlds.get(world.getUID());
        AlwaysSettings.Rule rule = context == null ? settings.forWorld(world.getName())
            : settings.forArena(context.arenaId, context.sourceWorld);
        if (!rule.enabled) return;
        if (!original.containsKey(world.getUID())) original.put(world.getUID(), new WorldState(world));
        if (rule.time != null) {
            if (rule.time.mode == AlwaysSettings.TimeMode.FIXED) {
                setGameRule(world, "doDaylightCycle", false);
                if (world.getTime() != rule.time.tick) world.setTime(rule.time.tick);
            } else {
                setGameRule(world, "doDaylightCycle", true);
                long time = world.getTime();
                if (rule.time.mode == AlwaysSettings.TimeMode.DAY && time >= 12000L)
                    world.setTime(rule.time.tick);
                else if (rule.time.mode == AlwaysSettings.TimeMode.NIGHT
                        && (time < 13000L || time >= 23000L)) world.setTime(rule.time.tick);
            }
        }
        if (rule.difficulty != null && world.getDifficulty() != rule.difficulty)
            world.setDifficulty(rule.difficulty);
        if (rule.weather != null) {
            boolean storm = rule.weather != AlwaysSettings.Weather.CLEAR;
            boolean thunder = rule.weather == AlwaysSettings.Weather.THUNDER;
            if (world.hasStorm() != storm) world.setStorm(storm);
            if (world.isThundering() != thunder) world.setThundering(thunder);
        }
        if (rule.mobSpawning != null) setGameRule(world, "doMobSpawning", rule.mobSpawning);
        if (rule.mobGriefing != null) setGameRule(world, "mobGriefing", rule.mobGriefing);
        if (rule.allowMonsters != null || rule.allowAnimals != null) {
            boolean monsters = rule.allowMonsters == null ? world.getAllowMonsters() : rule.allowMonsters;
            boolean animals = rule.allowAnimals == null ? world.getAllowAnimals() : rule.allowAnimals;
            if (world.getAllowMonsters() != monsters || world.getAllowAnimals() != animals)
                world.setSpawnFlags(monsters, animals);
        }
    }

    private void setGameRule(World world, String name, boolean value) {
        String expected = Boolean.toString(value);
        if (!expected.equalsIgnoreCase(world.getGameRuleValue(name)))
            world.setGameRuleValue(name, expected);
    }

    private void restoreAll() {
        for (World world : Bukkit.getWorlds()) restore(world);
        original.clear();
    }

    private void restore(World world) {
        WorldState state = original.remove(world.getUID());
        if (state != null) state.restore(world);
    }

    private static final class ArenaContext {
        final String arenaId, sourceWorld;
        ArenaContext(String arenaId, String sourceWorld) {
            this.arenaId = arenaId;
            this.sourceWorld = sourceWorld;
        }
    }

    private static final class WorldState {
        final Difficulty difficulty;
        final boolean storm, thunder, monsters, animals;
        final String daylightCycle, mobSpawning, mobGriefing;
        WorldState(World world) {
            difficulty = world.getDifficulty();
            storm = world.hasStorm();
            thunder = world.isThundering();
            monsters = world.getAllowMonsters();
            animals = world.getAllowAnimals();
            daylightCycle = world.getGameRuleValue("doDaylightCycle");
            mobSpawning = world.getGameRuleValue("doMobSpawning");
            mobGriefing = world.getGameRuleValue("mobGriefing");
        }
        void restore(World world) {
            if (world.getDifficulty() != difficulty) world.setDifficulty(difficulty);
            if (world.hasStorm() != storm) world.setStorm(storm);
            if (world.isThundering() != thunder) world.setThundering(thunder);
            if (world.getAllowMonsters() != monsters || world.getAllowAnimals() != animals)
                world.setSpawnFlags(monsters, animals);
            if (daylightCycle != null) world.setGameRuleValue("doDaylightCycle", daylightCycle);
            if (mobSpawning != null) world.setGameRuleValue("doMobSpawning", mobSpawning);
            if (mobGriefing != null) world.setGameRuleValue("mobGriefing", mobGriefing);
        }
    }
}
