package com.starfinplugins.bedwars.economy.generator;

import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ResourceGeneratorDefinition;
import com.starfinplugins.bedwars.domain.economy.GeneratorInstance;
import com.starfinplugins.bedwars.domain.economy.GeneratorLevel;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.GeneratorEvolvedEvent;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.event.ResourceGeneratedEvent;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.world.ManagedMobGuard;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.util.EulerAngle;

public final class ResourceGeneratorService implements ManagedService, Listener {
    private static final String ITEM_TAG = "starfinbedwars-generator";
    // Visual centers are relative to the physical drop anchor, never to a previous teleport.
    private static final double VISUAL_CENTER_RAISE = 2.35D;
    private static final double NORMAL_HELMET_OFFSET = 1.5D;
    private static final double HAND_ITEM_OFFSET = 1.85D;
    private final Plugin plugin;
    private final ArenaService arenas;
    private final MatchEngineService matchEngine;
    private final Map<UUID, List<GeneratorInstance>> instances =
        new HashMap<UUID, List<GeneratorInstance>>();
    private final Map<UUID, GameMatch> matches = new HashMap<UUID, GameMatch>();
    private final Map<UUID, Set<Item>> spawnedItems = new HashMap<UUID, Set<Item>>();
    private final Map<UUID, Map<String, GeneratorHologram>> holograms =
        new HashMap<UUID, Map<String, GeneratorHologram>>();
    private GeneratorSettings settings;
    private GeneratorHologramSettings hologramSettings;
    private GeneratorEventSchedule eventSchedule;
    private final Map<UUID, Integer> nextEventIndexes = new HashMap<UUID, Integer>();
    private BukkitTask task;
    private long currentTick;
    private final Random random = new Random();
    private final Set<String> warnedLocations = new HashSet<String>();
    private ManagedMobGuard mobGuard;

    public void setMobGuard(ManagedMobGuard mobGuard) { this.mobGuard = mobGuard; }

    public ResourceGeneratorService(Plugin plugin, ArenaService arenas, MatchEngineService matchEngine) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.matchEngine = matchEngine;
    }

    @Override public String getName() { return "resource-generators"; }

    @Override
    public void start() throws Exception {
        settings = GeneratorSettings.load(plugin);
        hologramSettings = new GeneratorHologramSettings(plugin.getConfig());
        eventSchedule = GeneratorEventSchedule.load(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { tick(); }
        }, 1L, 1L);
    }

    @Override
    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (UUID matchId : new ArrayList<UUID>(instances.keySet())) {
            stopMatch(matchId);
        }
        instances.clear();
        matches.clear();
        spawnedItems.clear();
        holograms.clear();
        warnedLocations.clear();
        nextEventIndexes.clear();
    }

    @EventHandler
    public void onMatchLifecycle(MatchLifecycleEvent event) {
        if (event.getType() == MatchLifecycleEvent.Type.STARTED) {
            startMatch(event.getMatch());
        } else if (event.getType() == MatchLifecycleEvent.Type.ENDED
                || event.getType() == MatchLifecycleEvent.Type.INSTANCE_REMOVED) {
            stopMatch(event.getMatch().getId());
        }
    }

    public boolean upgrade(UUID matchId, String generatorId, int expectedLevel) {
        GameMatch match = matches.get(matchId);
        List<GeneratorInstance> matchGenerators = instances.get(matchId);
        if (match == null || match.getState() != MatchState.IN_GAME || matchGenerators == null) return false;
        for (GeneratorInstance instance : matchGenerators) {
            if (!instance.getDefinition().getId().equals(generatorId)
                    || instance.getLevel() != expectedLevel) continue;
            int oldLevel = instance.getLevel();
            if (!instance.upgrade(settings.getLevels(instance.getDefinition().getResource()))) return false;
            Bukkit.getPluginManager().callEvent(new GeneratorEvolvedEvent(
                match, instance, oldLevel, instance.getLevel()));
            return true;
        }
        return false;
    }

    public int upgradeTeam(UUID matchId, String teamId, int levels) {
        GameMatch match = matches.get(matchId);
        List<GeneratorInstance> matchGenerators = instances.get(matchId);
        if (match == null || match.getState() != MatchState.IN_GAME
                || matchGenerators == null || teamId == null || levels < 1) return 0;
        int upgraded = 0;
        for (GeneratorInstance instance : matchGenerators) {
            ResourceGeneratorDefinition definition = instance.getDefinition();
            if (teamId.equals(definition.getTeamId())
                    && (definition.getResource() == ResourceType.IRON
                        || definition.getResource() == ResourceType.GOLD)) {
                for (int index = 0; index < levels; index++) {
                    int oldLevel = instance.getLevel();
                    if (!instance.upgrade(settings.getLevels(definition.getResource()))) break;
                    Bukkit.getPluginManager().callEvent(new GeneratorEvolvedEvent(
                        match, instance, oldLevel, instance.getLevel()));
                    upgraded++;
                }
            } else if (teamId.equals(definition.getTeamId())
                    && definition.getResource() == ResourceType.EMERALD) {
                if (instance.activate(currentTick, settings.getLevels(ResourceType.EMERALD))) upgraded++;
            }
        }
        return upgraded;
    }

    public boolean canUpgradeTeam(UUID matchId, String teamId) {
        GameMatch match = matches.get(matchId);
        List<GeneratorInstance> matchGenerators = instances.get(matchId);
        if (match == null || match.getState() != MatchState.IN_GAME
                || matchGenerators == null || teamId == null) return false;
        for (GeneratorInstance instance : matchGenerators) {
            ResourceGeneratorDefinition definition = instance.getDefinition();
            if (teamId.equals(definition.getTeamId())
                    && (definition.getResource() == ResourceType.IRON
                        || definition.getResource() == ResourceType.GOLD)
                    && instance.getLevel() < settings.getLevels(definition.getResource()).size()) {
                return true;
            }
            if (teamId.equals(definition.getTeamId())
                    && definition.getResource() == ResourceType.EMERALD && !instance.isActive()) {
                return true;
            }
        }
        return false;
    }

    public List<GeneratorInstance> getGenerators(UUID matchId) {
        List<GeneratorInstance> result = instances.get(matchId);
        return result == null ? java.util.Collections.<GeneratorInstance>emptyList()
            : java.util.Collections.unmodifiableList(result);
    }

    public String getCentralGeneratorStatus(UUID matchId, ResourceType resource) {
        if (resource == null || (resource != ResourceType.DIAMOND && resource != ResourceType.EMERALD)) {
            throw new IllegalArgumentException("Recurso central invalido.");
        }
        List<GeneratorInstance> matchGenerators = instances.get(matchId);
        if (matchGenerators == null || settings == null) return null;
        for (GeneratorInstance instance : matchGenerators) {
            ResourceGeneratorDefinition definition = instance.getDefinition();
            if (definition.getTeamId() != null || definition.getResource() != resource
                    || !instance.isActive()) continue;
            List<GeneratorLevel> levels = settings.getLevels(resource);
            if (instance.getLevel() < 1 || instance.getLevel() > levels.size()) continue;
            int seconds = (int) Math.ceil(instance.getTicksUntilProduction(currentTick) / 20.0D);
            String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
            String level = instance.getLevel() <= numerals.length
                ? numerals[instance.getLevel() - 1] : Integer.toString(instance.getLevel());
            return (resource == ResourceType.DIAMOND ? "Diamante " : "Esmeralda ") + level
                + " em " + (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
        }
        return null;
    }

    public NextUpgrade getNextUpgrade(UUID matchId) {
        GameMatch match = matches.get(matchId);
        if (match == null || match.getState() != MatchState.IN_GAME
                || eventSchedule == null || !eventSchedule.isEnabled()
                || match.getStartedAt() == null) return null;
        int index = nextEventIndexes.containsKey(matchId) ? nextEventIndexes.get(matchId) : 0;
        if (index >= eventSchedule.getEntries().size()) return null;
        GeneratorEventSchedule.Entry entry = eventSchedule.getEntries().get(index);
        long elapsed = Math.max(0L,
            (System.currentTimeMillis() - match.getStartedAt().toEpochMilli()) / 1000L);
        return new NextUpgrade(entry.getResource(), entry.getLevel(),
            Math.max(0L, entry.getTimeSeconds() - elapsed));
    }

    public static final class NextUpgrade {
        private final ResourceType resource;
        private final int level;
        private final long secondsRemaining;
        private NextUpgrade(ResourceType resource, int level, long secondsRemaining) {
            this.resource = resource; this.level = level; this.secondsRemaining = secondsRemaining;
        }
        public ResourceType getResource() { return resource; }
        public int getLevel() { return level; }
        public long getSecondsRemaining() { return secondsRemaining; }
    }

    private void startMatch(GameMatch match) {
        if (matches.containsKey(match.getId())) return;
        ArenaDefinition arena = match.getArena();
        World world = matchEngine.getWorld(match.getId());
        if (world == null) {
            plugin.getLogger().severe("Geradores nao iniciados: mundo da partida ausente para " + match.getId());
            return;
        }
        List<GeneratorInstance> created = new ArrayList<GeneratorInstance>();
        for (ResourceGeneratorDefinition definition : arena.getGeneratorList()) {
            if (!settings.isEnabled(definition.getResource())
                    || settings.getLevels(definition.getResource()).size() < definition.getStartingLevel()) {
                plugin.getLogger().warning("Gerador " + definition.getId() + " indisponivel para partida "
                    + match.getId() + "; configuracao desabilitada ou nivel inicial ausente.");
                continue;
            }
            boolean startsDisabled = definition.getTeamId() != null
                && definition.getResource() == ResourceType.EMERALD;
            GeneratorInstance instance = new GeneratorInstance(match, definition, currentTick, !startsDisabled);
            instance.scheduleFirstProduction(currentTick, settings.getLevels(definition.getResource()));
            created.add(instance);
        }
        matches.put(match.getId(), match);
        nextEventIndexes.put(match.getId(), 0);
        instances.put(match.getId(), created);
        spawnedItems.put(match.getId(), new HashSet<Item>());
        Map<String, GeneratorHologram> matchHolograms = new HashMap<String, GeneratorHologram>();
        for (GeneratorInstance instance : created) {
            ResourceGeneratorDefinition definition = instance.getDefinition();
            if (hologramSettings.enabled && definition.getTeamId() == null
                    && (definition.getResource() == ResourceType.DIAMOND
                        || definition.getResource() == ResourceType.EMERALD)) {
                matchHolograms.put(definition.getId(), createHologram(definition, world));
            }
        }
        holograms.put(match.getId(), matchHolograms);
        for (GeneratorInstance instance : created) updateHologram(match.getId(), instance);
    }

    private void tick() {
        currentTick++;
        if (instances.isEmpty()) return;
        for (Map.Entry<UUID, List<GeneratorInstance>> entry : instances.entrySet()) {
            GameMatch match = matches.get(entry.getKey());
            if (match == null || match.getState() != MatchState.IN_GAME) continue;
            World world = worldFor(match);
            if (world == null) continue;
            if (currentTick % 20L == 0L) applyTimedUpgrades(match, entry.getValue());
            if (currentTick % 20L == 0L) confineItems(entry.getKey(), entry.getValue(), world);
            if (currentTick % hologramSettings.orbitInterval == 0L) animateHolograms(entry.getKey());
            for (GeneratorInstance instance : entry.getValue()) {
                if (currentTick % hologramSettings.textInterval == 0L) updateHologram(entry.getKey(), instance);
                if (!instance.isDue(currentTick)) continue;
                ResourceType type = instance.getDefinition().getResource();
                List<GeneratorLevel> levels = settings.getLevels(type);
                if (levels.isEmpty()) continue;
                Location location = generatorLocation(instance.getDefinition(), world);
                int currentDropped = countTaggedItems(instance, match.getId());
                if (location == null) {
                    logInvalidLocationOnce(match, instance);
                    instance.advance(currentTick, levels, levels.get(instance.getLevel() - 1).getMaximumDropped());
                    continue;
                }
                warnedLocations.remove(match.getId() + ":" + instance.getDefinition().getId());
                if (!instance.advance(currentTick, levels, currentDropped)) continue;
                GeneratorLevel level = levels.get(instance.getLevel() - 1);
                Item item = world.dropItem(location, new ItemStack(type.getMaterial(), level.getAmount()));
                item.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
                item.setMetadata(ITEM_TAG, new FixedMetadataValue(plugin, tag(match.getId(), instance)));
                spawnedItems.get(match.getId()).add(item);
                Bukkit.getPluginManager().callEvent(new ResourceGeneratedEvent(match, instance, level.getAmount()));
                updateHologram(entry.getKey(), instance);
            }

        }
    }

    private void applyTimedUpgrades(GameMatch match, List<GeneratorInstance> generators) {
        if (eventSchedule == null || !eventSchedule.isEnabled() || match.getStartedAt() == null) return;
        long elapsed = Math.max(0L,
            (System.currentTimeMillis() - match.getStartedAt().toEpochMilli()) / 1000L);
        int index = nextEventIndexes.containsKey(match.getId()) ? nextEventIndexes.get(match.getId()) : 0;
        List<GeneratorEventSchedule.Entry> entries = eventSchedule.getEntries();
        while (index < entries.size() && elapsed >= entries.get(index).getTimeSeconds()) {
            GeneratorEventSchedule.Entry scheduled = entries.get(index++);
            ResourceType resource = scheduled.getResource();
            for (GeneratorInstance generator : generators) {
                ResourceGeneratorDefinition definition = generator.getDefinition();
                if (definition.getTeamId() != null || definition.getResource() != resource) continue;
                List<GeneratorLevel> levels = settings.getLevels(resource);
                while (generator.getLevel() < scheduled.getLevel()) {
                    int previous = generator.getLevel();
                    if (!generator.upgradeAt(currentTick, levels)) break;
                    Bukkit.getPluginManager().callEvent(new GeneratorEvolvedEvent(match,
                        generator, previous, generator.getLevel()));
                }
                updateHologram(match.getId(), generator);
            }
            if (eventSchedule.isNotifyPlayers()) {
                String message = ChatColor.GOLD + (resource == ResourceType.DIAMOND
                    ? "Geradores de Diamante" : "Geradores de Esmeralda")
                    + " evoluiram para nivel " + roman(scheduled.getLevel()) + ".";
                for (GameParticipant participant : match.getParticipantList()) {
                    org.bukkit.entity.Player player = Bukkit.getPlayer(participant.getPlayerId());
                    if (player != null && player.isOnline()) player.sendMessage(message);
                }
            }
        }
        nextEventIndexes.put(match.getId(), index);
    }

    private Location generatorLocation(ResourceGeneratorDefinition definition, World world) {
        ArenaRegion region = definition.getRegion();
        if (definition.getTeamId() == null
                && (definition.getResource() == ResourceType.DIAMOND
                    || definition.getResource() == ResourceType.EMERALD)) {
            Location anchor = arenas.toLocation(definition.getPosition(), world);
            if (region != null && !region.contains(definition.getPosition())) return null;
            if (anchor.getBlockY() < 1 || anchor.getBlockY() + 1 >= world.getMaxHeight()
                    || !world.getBlockAt(anchor.getBlockX(), anchor.getBlockY() - 1,
                        anchor.getBlockZ()).getType().isSolid()
                    || !openForItem(world.getBlockAt(anchor.getBlockX(), anchor.getBlockY(),
                        anchor.getBlockZ()).getType())
                    || !openForItem(world.getBlockAt(anchor.getBlockX(), anchor.getBlockY() + 1,
                        anchor.getBlockZ()).getType())) return null;
            return anchor;
        }
        if (region == null) {
            return arenas.toLocation(definition.getPosition(), world);
        }
        Location center = dropAt(world, (region.getMinimumX() + region.getMaximumX()) / 2,
            region.getMinimumY(), (region.getMinimumZ() + region.getMaximumZ()) / 2,
            definition.getTeamId() != null);
        if (center != null) return center;
        for (int attempt = 0; attempt < 128; attempt++) {
            int x = region.getMinimumX() + random.nextInt(region.getMaximumX() - region.getMinimumX() + 1);
            int y = region.getMinimumY() + random.nextInt(region.getMaximumY() - region.getMinimumY() + 1);
            int z = region.getMinimumZ() + random.nextInt(region.getMaximumZ() - region.getMinimumZ() + 1);
            Location drop = dropAt(world, x, y, z, definition.getTeamId() != null);
            if (drop != null) return drop;
        }
        return null;
    }

    private Location dropAt(World world, int x, int y, int z, boolean allowFloorSelection) {
        if (y > 0 && y + 1 < world.getMaxHeight()
                && world.getBlockAt(x, y - 1, z).getType().isSolid()
                && openForItem(world.getBlockAt(x, y, z).getType())
                && openForItem(world.getBlockAt(x, y + 1, z).getType())) {
            return new Location(world, x + 0.5D, y + 0.2D, z + 0.5D);
        }
        if (allowFloorSelection && y + 2 < world.getMaxHeight()
                && world.getBlockAt(x, y, z).getType().isSolid()
                && openForItem(world.getBlockAt(x, y + 1, z).getType())
                && openForItem(world.getBlockAt(x, y + 2, z).getType())) {
            return new Location(world, x + 0.5D, y + 1.2D, z + 0.5D);
        }
        return null;
    }

    private boolean openForItem(Material material) {
        return material == Material.AIR || material == Material.SNOW
            || material == Material.LONG_GRASS || material == Material.DEAD_BUSH
            || material == Material.RED_ROSE || material == Material.YELLOW_FLOWER;
    }

    private void logInvalidLocationOnce(GameMatch match, GeneratorInstance instance) {
        ResourceGeneratorDefinition definition = instance.getDefinition();
        if (!warnedLocations.add(match.getId() + ":" + definition.getId())) return;
        plugin.getLogger().warning("Gerador sem ponto livre: arena=" + match.getArena().getId()
            + " partida=" + match.getId() + " gerador=" + definition.getId()
            + " equipe=" + definition.getTeamId() + " recurso=" + definition.getResource()
            + " regiao=" + regionDescription(definition.getRegion())
            + " estado=" + (instance.isActive() ? "ativo" : "inativo") + ".");
    }

    private String regionDescription(ArenaRegion region) {
        return region == null ? "ausente" : region.getMinimumX() + "," + region.getMinimumY()
            + "," + region.getMinimumZ() + ".." + region.getMaximumX() + ","
            + region.getMaximumY() + "," + region.getMaximumZ();
    }

    private GeneratorHologram createHologram(ResourceGeneratorDefinition definition, World world) {
        Location position = arenas.toLocation(definition.getPosition(), world);
        Location center = position.clone().add(0.0D, VISUAL_CENTER_RAISE, 0.0D);
        Material displayBlock = definition.getResource() == ResourceType.DIAMOND
            ? hologramSettings.diamondBlock : hologramSettings.emeraldBlock;
        ArmorStand blockStand = null;
        Location blockBase = center.clone().subtract(0.0D, NORMAL_HELMET_OFFSET, 0.0D);
        if (hologramSettings.block) {
            blockStand = armorStand(blockBase);
            blockStand.setHelmet(new ItemStack(displayBlock));
        }
        List<ArmorStand> ores = new ArrayList<ArmorStand>();
        Location orbitBase = center.clone().add(0.0D, hologramSettings.orbitHeight - HAND_ITEM_OFFSET, 0.0D);
        if (hologramSettings.orbit) {
            for (int index = 0; index < hologramSettings.iconCount; index++) {
                Location icon = orbitBase.clone().add(
                    OrbitMath.x(currentTick, index, hologramSettings.iconCount,
                        hologramSettings.orbitSpeed, hologramSettings.orbitRadius), 0.0D,
                    OrbitMath.z(currentTick, index, hologramSettings.iconCount,
                        hologramSettings.orbitSpeed, hologramSettings.orbitRadius));
                ArmorStand ore = armorStand(icon);
                ore.setArms(true);
                ore.setRightArmPose(new EulerAngle(-Math.PI / 2.0D, 0.0D, 0.0D));
                ore.setItemInHand(new ItemStack(definition.getResource() == ResourceType.DIAMOND
                    ? hologramSettings.diamondIcon : hologramSettings.emeraldIcon));
                ores.add(ore);
            }
        }
        List<ArmorStand> text = new ArrayList<ArmorStand>();
        if (hologramSettings.text) {
            if (hologramSettings.level) text.add(namedStand(center.clone().add(0.0D, 1.3D, 0.0D), ""));
            if (hologramSettings.resource) text.add(namedStand(center.clone().add(0.0D, 0.95D, 0.0D), ""));
            if (hologramSettings.countdown) text.add(namedStand(center.clone().add(0.0D, 0.6D, 0.0D), ""));
        }
        return new GeneratorHologram(blockStand, blockBase, ores, orbitBase, text, world);
    }

    private void animateHolograms(UUID matchId) {
        Map<String, GeneratorHologram> active = holograms.get(matchId);
        if (active == null) return;
        for (GeneratorHologram hologram : active.values()) {
            if (hologramSettings.pauseWithoutViewers && !hasNearbyViewer(hologram)) continue;
            if (hologram.block != null && hologram.block.isValid()) {
                if (hologramSettings.floatBlock) {
                    Location at = hologram.blockBase.clone().add(0.0D,
                        OrbitMath.floatOffset(currentTick, hologramSettings.floatAmplitude,
                            hologramSettings.floatPeriod), 0.0D);
                    hologram.block.teleport(at);
                }
                if (hologramSettings.rotate) {
                    double angle = Math.toRadians((currentTick * hologramSettings.blockSpeed) % 360.0D);
                    hologram.block.setHeadPose(new EulerAngle(0.0D, angle, 0.0D));
                }
            }
            for (int index = 0; index < hologram.ores.size(); index++) {
                ArmorStand ore = hologram.ores.get(index);
                if (!ore.isValid()) continue;
                double angle = OrbitMath.angle(currentTick, index, hologram.ores.size(),
                    hologramSettings.orbitSpeed);
                double y = hologramSettings.oscillate
                    ? Math.sin(angle * 2.0D) * hologramSettings.orbitAmplitude : 0.0D;
                Location at = hologram.orbitBase.clone().add(Math.cos(angle) * hologramSettings.orbitRadius,
                    y, Math.sin(angle) * hologramSettings.orbitRadius);
                at.setYaw((float) (Math.toDegrees(angle) + 90.0D));
                ore.teleport(at);
            }
        }
    }

    private boolean hasNearbyViewer(GeneratorHologram hologram) {
        double limit = hologramSettings.activationDistance * hologramSettings.activationDistance;
        for (org.bukkit.entity.Player player : hologram.world.getPlayers()) {
            if (player.getLocation().distanceSquared(hologram.blockBase) <= limit) return true;
        }
        return false;
    }

    private ArmorStand armorStand(Location location) {
        ArmorStand stand = (ArmorStand) (mobGuard == null
            ? location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND)
            : mobGuard.spawnAuthorized(location, EntityType.ARMOR_STAND));
        if (!stand.isValid()) throw new IllegalStateException("ArmorStand do gerador nao foi criado.");
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setRemoveWhenFarAway(false);
        return stand;
    }

    private ArmorStand namedStand(Location location, String name) {
        ArmorStand stand = armorStand(location);
        stand.setCustomName(name);
        stand.setCustomNameVisible(true);
        return stand;
    }

    private void updateHologram(UUID matchId, GeneratorInstance instance) {
        Map<String, GeneratorHologram> matchHolograms = holograms.get(matchId);
        if (matchHolograms == null) return;
        GeneratorHologram hologram = matchHolograms.get(instance.getDefinition().getId());
        if (hologram == null) return;
        ResourceType resource = instance.getDefinition().getResource();
        List<GeneratorLevel> levels = settings.getLevels(resource);
        if (levels.isEmpty() || instance.getLevel() < 1 || instance.getLevel() > levels.size()) return;
        int seconds = (int) Math.ceil(instance.getTicksUntilProduction(currentTick) / 20.0D);
        ChatColor color = resource == ResourceType.DIAMOND ? ChatColor.AQUA : ChatColor.GREEN;
        int line = 0;
        if (hologramSettings.text && hologramSettings.level)
            setNameIfChanged(hologram.text.get(line++), ChatColor.YELLOW + "Nível " + roman(instance.getLevel()));
        if (hologramSettings.text && hologramSettings.resource)
            setNameIfChanged(hologram.text.get(line++), color + (resource == ResourceType.DIAMOND
                ? "Diamante" : "Esmeralda"));
        if (hologramSettings.text && hologramSettings.countdown)
            setNameIfChanged(hologram.text.get(line), ChatColor.GOLD + "Gera em "
                + ChatColor.RED + seconds + " segundos");
    }

    private void setNameIfChanged(ArmorStand stand, String text) {
        if (stand.isValid() && !text.equals(stand.getCustomName())) stand.setCustomName(text);
    }

    private String roman(int level) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level <= numerals.length ? numerals[level - 1] : Integer.toString(level);
    }

    private int countTaggedItems(GeneratorInstance instance, UUID matchId) {
        int amount = 0;
        String expected = tag(matchId, instance);
        Set<Item> tracked = spawnedItems.get(matchId);
        if (tracked == null) return 0;
        for (Item item : tracked) {
            if (item != null && item.isValid() && hasTag(item, expected)
                    && item.getItemStack().getType() == instance.getDefinition().getResource().getMaterial()) {
                amount += item.getItemStack().getAmount();
            }
        }
        return amount;
    }

    private void confineItems(UUID matchId, List<GeneratorInstance> generators, World world) {
        Set<Item> tracked = spawnedItems.get(matchId);
        if (tracked == null) return;
        java.util.Iterator<Item> iterator = tracked.iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next();
            if (item == null || !item.isValid()) {
                iterator.remove();
                continue;
            }
            if (item.getWorld() != world) continue;
            for (GeneratorInstance instance : generators) {
                ArenaRegion region = instance.getDefinition().getRegion();
                if (region == null || !hasTag(item, tag(matchId, instance))) continue;
                Location point = item.getLocation();
                if (point.getX() >= region.getMinimumX() && point.getX() < region.getMaximumX() + 1.0D
                        && point.getY() >= region.getMinimumY()
                        && point.getY() < region.getMaximumY() + (instance.getDefinition().getTeamId() == null ? 1.0D : 2.0D)
                        && point.getZ() >= region.getMinimumZ() && point.getZ() < region.getMaximumZ() + 1.0D) break;
                Location home = generatorLocation(instance.getDefinition(), world);
                if (home != null) {
                    item.teleport(home);
                    item.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
                }
                break;
            }
        }
    }

    private void stopMatch(UUID matchId) {
        List<GeneratorInstance> generators = instances.remove(matchId);
        if (generators != null) {
            for (GeneratorInstance generator : generators) generator.stop();
        }
        matches.remove(matchId);
        nextEventIndexes.remove(matchId);
        warnedLocations.removeIf(key -> key.startsWith(matchId.toString() + ":"));
        Map<String, GeneratorHologram> matchHolograms = holograms.remove(matchId);
        if (matchHolograms != null) {
            for (GeneratorHologram hologram : matchHolograms.values()) hologram.remove(mobGuard);
        }
        Set<Item> items = spawnedItems.remove(matchId);
        if (items != null) {
            for (Item item : items) {
                if (item != null && item.isValid()) item.remove();
            }
        }
    }

    private World worldFor(GameMatch match) {
        return matchEngine.getWorld(match.getId());
    }

    private String tag(UUID matchId, GeneratorInstance instance) {
        return matchId.toString() + ":" + instance.getDefinition().getId();
    }

    private boolean hasTag(Item item, String expected) {
        org.bukkit.metadata.MetadataValue value = metadataValue(item);
        return value != null && expected.equals(value.asString());
    }

    private org.bukkit.metadata.MetadataValue metadataValue(Item item) {
        if (item == null || !item.hasMetadata(ITEM_TAG)) return null;
        for (org.bukkit.metadata.MetadataValue value : item.getMetadata(ITEM_TAG)) {
            if (value.getOwningPlugin() == plugin) return value;
        }
        return null;
    }

    @EventHandler
    public void onMerge(ItemMergeEvent event) {
        Item source = event.getEntity();
        Item target = event.getTarget();
        org.bukkit.metadata.MetadataValue sourceMetadata = metadataValue(source);
        org.bukkit.metadata.MetadataValue targetMetadata = metadataValue(target);
        if (sourceMetadata == null && targetMetadata == null) return;
        String sourceTag = sourceMetadata == null ? null : sourceMetadata.asString();
        String targetTag = targetMetadata == null ? null : targetMetadata.asString();
        if (sourceTag != null && targetTag != null && !sourceTag.equals(targetTag)) {
            event.setCancelled(true);
            return;
        }
        String preserve = sourceTag == null ? targetTag : sourceTag;
        if (preserve != null) {
            target.setMetadata(ITEM_TAG, new FixedMetadataValue(plugin, preserve));
            String[] parts = preserve.split(":", 2);
            if (parts.length == 2) {
                try {
                    Set<Item> tracked = spawnedItems.get(UUID.fromString(parts[0]));
                    if (tracked != null) tracked.add(target);
                } catch (IllegalArgumentException malformedTag) {
                    plugin.getLogger().warning("Item de gerador possui identificador de partida invalido.");
                }
            }
        }
        forget(source);
    }

    @EventHandler
    public void onDespawn(ItemDespawnEvent event) {
        forget(event.getEntity());
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        final Item pickedUp = event.getItem();
        org.bukkit.metadata.MetadataValue metadata = metadataValue(pickedUp);
        if (metadata == null) return;
        String[] tagParts = metadata.asString().split(":", 2);
        GameMatch match = null;
        try {
            if (tagParts.length == 2) match = matches.get(UUID.fromString(tagParts[0]));
        } catch (IllegalArgumentException malformedTag) {
            plugin.getLogger().warning("Item de gerador possui identificador de partida invalido.");
        }
        GameMatch playerMatch = matchEngine.getMatchForPlayer(event.getPlayer().getUniqueId());
        GameParticipant participant = match == null ? null
            : match.getParticipant(event.getPlayer().getUniqueId());
        if (match == null || match.getState() != MatchState.IN_GAME || playerMatch != match
                || participant == null || participant.getState() != ParticipantState.CONNECTED
                || event.getPlayer().getWorld() != matchEngine.getWorld(match.getId())) {
            event.setCancelled(true);
            return;
        }
        if (metadata != null) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() {
                    if (!pickedUp.isValid()) forget(pickedUp);
                }
            });
        }
    }

    private void forget(Item item) {
        for (Set<Item> items : spawnedItems.values()) items.remove(item);
    }

    private static final class GeneratorHologram {
        private final ArmorStand block;
        private final List<ArmorStand> ores;
        private final List<ArmorStand> text;
        private final Location blockBase;
        private final Location orbitBase;
        private final World world;

        private GeneratorHologram(ArmorStand block, Location blockBase, List<ArmorStand> ores,
                                  Location orbitBase, List<ArmorStand> text, World world) {
            this.block = block;
            this.blockBase = blockBase;
            this.ores = ores;
            this.orbitBase = orbitBase;
            this.text = text;
            this.world = world;
        }

        private void remove(ManagedMobGuard mobGuard) {
            if (mobGuard != null) mobGuard.forget(block);
            if (block != null && block.isValid()) block.remove();
            for (ArmorStand ore : ores) {
                if (mobGuard != null) mobGuard.forget(ore);
                if (ore.isValid()) ore.remove();
            }
            for (ArmorStand line : text) {
                if (mobGuard != null) mobGuard.forget(line);
                if (line.isValid()) line.remove();
            }
        }
    }
}
