package com.starfinplugins.bedwars.gameplay;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.GameTeam;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchCombatDeathEvent;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.domain.economy.GeneratorInstance;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.match.MatchEquipmentService;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.stats.MatchMode;
import com.starfinplugins.bedwars.stats.MatchStatisticsService;
import com.starfinplugins.bedwars.stats.PreferencesOutbox;
import com.starfinplugins.bedwars.stats.StatsProfile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;

/** Selection inventory and match-scoped kit/ability lifecycle on the Bukkit thread. */
public final class GameplayService implements ManagedService, Listener {
    public static final int KIT_HOTBAR_SLOT = 3;
    public static final int ABILITY_HOTBAR_SLOT = 5;
    private static final String KIT_MARKER = ChatColor.BLACK + "SBW:KIT-SELECTOR";
    private static final String ABILITY_MARKER = ChatColor.BLACK + "SBW:ABILITY-SELECTOR";
    private final StarfinBedWarsPlugin plugin;
    private final MatchEngineService matches;
    private final MatchStatisticsService stats;
    private final MatchEquipmentService equipment;
    private final Map<UUID, MatchChoices> active = new HashMap<UUID, MatchChoices>();
    private final Map<UUID, TimedEffect> ownedEffects = new HashMap<UUID, TimedEffect>();
    private final Map<UUID, Set<String>> countedBuildPositions = new HashMap<UUID, Set<String>>();
    private final Map<UUID, Long> recentCombatVelocity = new HashMap<UUID, Long>();
    private final Map<String, Integer> forgeBonusCounts = new HashMap<String, Integer>();
    private final Random random = new Random();
    private BukkitTask sensingTask;
    private GameplaySettings settings;
    private AbilityCatalog evolved;

    public GameplayService(StarfinBedWarsPlugin plugin, MatchEngineService matches,
                           MatchStatisticsService stats, MatchEquipmentService equipment) {
        this.plugin = plugin; this.matches = matches; this.stats = stats; this.equipment = equipment;
    }
    @Override public String getName() { return "kits e habilidades"; }
    @Override public void start() {
        settings = GameplaySettings.load(plugin);
        evolved = AbilityCatalog.load(plugin);
        matches.setRespawnDelayModifier(this::respawnDelayTicks);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        sensingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::senseAndCollect, 20L, 10L);
    }
    @Override public void stop() {
        matches.setRespawnDelayModifier(null);
        if (sensingTask != null) sensingTask.cancel();
        HandlerList.unregisterAll(this);
        for (UUID id : new ArrayList<UUID>(ownedEffects.keySet())) clearEffect(id);
        active.clear();
        countedBuildPositions.clear();
        recentCombatVelocity.clear();
        forgeBonusCounts.clear();
    }
    public GameplaySettings getSettings() { return settings; }
    public AbilityCatalog getAbilityCatalog() { return evolved; }
    public void reload() {
        GameplaySettings nextGameplay = GameplaySettings.load(plugin);
        AbilityCatalog nextAbilities = AbilityCatalog.load(plugin);
        settings = nextGameplay;
        evolved = nextAbilities;
    }
    public String selectedKit(UUID player) { return stats.getSelectedKit(player); }
    public String selectedAbility(UUID player) { return stats.getSelectedAbility(player); }
    public long cooldownRemaining(UUID player) {
        GameMatch match = matches.getMatchForPlayer(player);
        MatchChoices choices = match == null ? null : active.get(match.getId());
        AbilityState state = choices == null ? null : choices.abilities.get(player);
        return state == null ? 0 : Math.max(0, (state.nextUseMillis - System.currentTimeMillis() + 999) / 1000);
    }

    private Integer respawnDelayTicks(GameMatch match, UUID playerId) {
        int base = match.getArena().getGameplayRules().respawn(
            plugin.getSettings().getRespawnSeconds()) * 20;
        GameParticipant participant = match.getParticipant(playerId);
        Player player = Bukkit.getPlayer(playerId);
        AbilityState state = state(match, playerId);
        if (state != null && "respawn_boost".equals(state.id) && state.level == 0)
            state.level = stats.getAbilityLevel(playerId, state.id);
        if (participant == null || player == null || state == null || state.level == 0
                || !"respawn_boost".equals(state.id)
                || !match.getTeam(participant.getTeamId()).isBedAlive()
                || !evolvedAvailable(player, match, evolved.abilities.get(state.id), state.level)) return base;
        return AbilityEffectMath.respawnTicks(base,
            evolved.abilities.get(state.id).value(state.level, "reduction-percent"),
            evolved.minimumRespawnTicks);
    }

    @EventHandler(priority = EventPriority.HIGH) public void onLifecycle(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        switch (event.getType()) {
            case PLAYER_JOINED:
            case SPAWN_OPENING:
            case COUNTDOWN_CANCELLED:
                for (GameParticipant participant : match.getParticipantList()) {
                    if (event.getType() == MatchLifecycleEvent.Type.PLAYER_JOINED
                            && event.getParticipant() != participant) continue;
                    Player player = Bukkit.getPlayer(participant.getPlayerId());
                    if (player != null && participant.getState() == ParticipantState.CONNECTED)
                        installSelectors(player);
                }
                break;
            case STARTED:
                MatchChoices choices = new MatchChoices();
                active.put(match.getId(), choices);
                for (GameParticipant participant : match.getParticipantList()) {
                    Player player = Bukkit.getPlayer(participant.getPlayerId());
                    if (player == null || participant.getState() != ParticipantState.CONNECTED) continue;
                    removeSelectors(player);
                    GameplaySettings.Kit kit = resolvedKit(player, match);
                    choices.kits.put(player.getUniqueId(), kit.id);
                    choices.abilities.put(player.getUniqueId(), resolvedAbilityState(player, match));
                    applyKit(player, match, participant, kit, false);
                }
                break;
            case PLAYER_RESPAWNED:
                if (event.getParticipant() != null) {
                    Player player = Bukkit.getPlayer(event.getParticipant().getPlayerId());
                    MatchChoices resumed = active.get(match.getId());
                    if (player != null && resumed != null) {
                        GameplaySettings.Kit kit = settings.kits.get(resumed.kits.get(player.getUniqueId()));
                        if (kit != null) applyKit(player, match, event.getParticipant(), kit, true);
                        AbilityState state = resumed.abilities.get(player.getUniqueId());
                        GameplaySettings.Ability ability = state == null ? null : settings.abilities.get(state.id);
                        if (ability != null && ability.effect == GameplaySettings.AbilityEffect.RESISTANCE_ON_RESPAWN)
                            activate(player, match, ability, state);
                        if (evolvedActive(player, match, state, "aegis")) applyAegis(player, state);
                        if (state != null) state.merchantUsedThisLife = false;
                    }
                }
                break;
            case PLAYER_RECONNECTED:
                if (event.getParticipant() != null) {
                    Player player = Bukkit.getPlayer(event.getParticipant().getPlayerId());
                    if (player != null && event.getParticipant().getState() == ParticipantState.CONNECTED) {
                        removeSelectors(player);
                        MatchChoices resumed = active.get(match.getId());
                        if (resumed != null && !resumed.kits.containsKey(player.getUniqueId())) {
                            GameplaySettings.Kit kit = resolvedKit(player, match);
                            resumed.kits.put(player.getUniqueId(), kit.id);
                            resumed.abilities.put(player.getUniqueId(), resolvedAbilityState(player, match));
                            applyKit(player, match, event.getParticipant(), kit, false);
                        }
                        reconcileEffect(player);
                    }
                }
                break;
            case BED_DESTROYED:
                UUID actor = event.getActorId();
                if (actor != null) {
                    Player breaker = Bukkit.getPlayer(actor);
                    AbilityState state = state(match, actor);
                    if (breaker != null && evolvedActive(breaker, match, state, "frenzy")) {
                        AbilityCatalog.Definition definition = evolved.abilities.get("frenzy");
                        if (canTrigger(state, definition)) {
                            state.buffUntilMillis = System.currentTimeMillis()
                                + (long) (definition.value(state.level, "duration-seconds") * 1000);
                            triggered(state, definition);
                        }
                    }
                }
                break;
            default: break;
        }
    }

    /** Cleanup precedes inventory restoration, including the player's original potion effects. */
    @EventHandler(priority = EventPriority.LOWEST) public void onLifecycleCleanup(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        switch (event.getType()) {
            case PLAYER_DIED:
            case PLAYER_ELIMINATED:
                if (event.getParticipant() != null) clearEffect(event.getParticipant().getPlayerId());
                break;
            case PLAYER_LEFT:
                if (event.getParticipant() != null) {
                    UUID id = event.getParticipant().getPlayerId();
                    clearEffect(id);
                    MatchChoices inMatch = active.get(match.getId());
                    if (inMatch != null) { inMatch.kits.remove(id); inMatch.abilities.remove(id); }
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) removeSelectors(player);
                }
                break;
            case ENDED:
            case INSTANCE_REMOVED:
                MatchChoices ended = active.remove(match.getId());
                if (ended != null) for (UUID id : ended.abilities.keySet()) clearEffect(id);
                countedBuildPositions.remove(match.getId());
                forgeBonusCounts.keySet().removeIf(key -> key.startsWith(match.getId().toString() + ":"));
                for (GameParticipant participant : match.getParticipantList()) {
                    Player player = Bukkit.getPlayer(participant.getPlayerId());
                    if (player != null) removeSelectors(player);
                }
                break;
            default: break;
        }
    }

    @EventHandler public void onCombatDeath(MatchCombatDeathEvent event) {
        UUID killer = event.getKillerId();
        if (killer == null) return;
        GameMatch match = matches.getMatchForPlayer(killer);
        if (match == null || !match.getId().equals(event.getMatchId())) return;
        AbilityState state = state(match, killer);
        Player player = Bukkit.getPlayer(killer);
        if (player == null) return;
        if (evolvedActive(player, match, state, "momentum")) {
            AbilityCatalog.Definition definition = evolved.abilities.get("momentum");
            applyEvolvedPotion(player, state, definition, PotionEffectType.SPEED,
                definition.value(state.level, "duration-seconds"));
            return;
        }
        if (event.isFinalKill() && evolvedActive(player, match, state, "vampire")) {
            AbilityCatalog.Definition definition = evolved.abilities.get("vampire");
            if (canTrigger(state, definition) && state.processedVictims.add(event.getVictimId())) {
                player.setHealth(AbilityEffectMath.heal(player.getHealth(), player.getMaxHealth(),
                    definition.value(state.level, "heal-hearts")));
                triggered(state, definition);
            }
            return;
        }
        GameplaySettings.Ability ability = state == null ? null : settings.abilities.get(state.id);
        if (ability == null || !available(player, match, ability)
                || ability.effect != GameplaySettings.AbilityEffect.SPEED_ON_KILL) return;
        activate(player, match, ability, state);
    }

    @EventHandler(ignoreCancelled = true) public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)
            recentCombatVelocity.remove(player.getUniqueId());
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (!eligibleInGame(player, match)) return;
        AbilityState state = state(match, player.getUniqueId());
        GameplaySettings.Ability ability = state == null ? null : settings.abilities.get(state.id);
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && ability != null
                && available(player, match, ability)
                && ability.effect == GameplaySettings.AbilityEffect.FALL_REDUCTION)
            event.setDamage(event.getDamage() * (1.0 - ability.potency));
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) return;
        double reduction = 0;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && evolvedActive(player, match, state, "fall_resistance"))
            reduction += evolved.abilities.get("fall_resistance").value(state.level, "reduction-percent");
        if (event instanceof EntityDamageByEntityEvent
                && ((EntityDamageByEntityEvent) event).getDamager() instanceof Arrow
                && evolvedActive(player, match, state, "projectile_resistance"))
            reduction += evolved.abilities.get("projectile_resistance").value(state.level, "reduction-percent");
        if (evolvedActive(player, match, state, "last_stand")) {
            GameParticipant participant = match.getParticipant(player.getUniqueId());
            AbilityCatalog.Definition definition = evolved.abilities.get("last_stand");
            if (!match.getTeam(participant.getTeamId()).isBedAlive()
                    && player.getHealth() <= definition.parameter("health-threshold", 8))
                reduction += definition.value(state.level, "reduction-percent");
        }
        reduction += nearbyBodyguardReduction(player, match);
        if (reduction > 0) event.setDamage(AbilityEffectMath.reduceDamage(event.getDamage(),
            reduction, evolved.maximumDamageReduction));
    }

    private double nearbyBodyguardReduction(Player victim, GameMatch match) {
        GameParticipant victimPart = match.getParticipant(victim.getUniqueId());
        if (victimPart == null) return 0;
        double bestDistance = Double.MAX_VALUE;
        double reduction = 0;
        for (GameParticipant part : match.getParticipantList()) {
            if (part.getPlayerId().equals(victim.getUniqueId())
                    || part.getState() != ParticipantState.CONNECTED
                    || !part.getTeamId().equals(victimPart.getTeamId())) continue;
            Player ally = Bukkit.getPlayer(part.getPlayerId());
            AbilityState allyState = state(match, part.getPlayerId());
            if (ally == null || ally.getWorld() != victim.getWorld()
                    || !evolvedActive(ally, match, allyState, "bodyguard")) continue;
            AbilityCatalog.Definition definition = evolved.abilities.get("bodyguard");
            double distance = ally.getLocation().distanceSquared(victim.getLocation());
            double limit = definition.parameter("max-distance", 6);
            if (distance > limit * limit || distance >= bestDistance) continue;
            bestDistance = distance;
            reduction = definition.value(allyState.level, "reduction-percent");
        }
        return reduction;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombatDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        Player attacker = event.getDamager() instanceof Player ? (Player) event.getDamager()
            : event.getDamager() instanceof Arrow
                && ((Arrow) event.getDamager()).getShooter() instanceof Player
                ? (Player) ((Arrow) event.getDamager()).getShooter() : null;
        if (attacker == null) return;
        GameMatch match = matches.getMatchForPlayer(attacker.getUniqueId());
        if (match == null || match != matches.getMatchForPlayer(victim.getUniqueId())
                || !eligibleInGame(attacker, match) || !eligibleInGame(victim, match)) return;
        GameParticipant attackingPart = match.getParticipant(attacker.getUniqueId());
        GameParticipant victimPart = match.getParticipant(victim.getUniqueId());
        if (attackingPart == null || victimPart == null
                || attackingPart.getTeamId().equals(victimPart.getTeamId())) return;
        AbilityState state = state(match, attacker.getUniqueId());
        if (event.getDamager() instanceof Player) {
            recentCombatVelocity.put(victim.getUniqueId(), System.currentTimeMillis());
            if (evolvedActive(attacker, match, state, "frenzy")
                    && state.buffUntilMillis > System.currentTimeMillis())
                event.setDamage(AbilityEffectMath.addDamage(event.getDamage(),
                    evolved.abilities.get("frenzy").value(state.level, "damage-percent")));
        } else if (event.getDamager() instanceof Arrow
                && evolvedActive(attacker, match, state, "archer")) {
            AbilityCatalog.Definition definition = evolved.abilities.get("archer");
            UUID projectile = event.getDamager().getUniqueId();
            // Consume the hit before rolling: a duplicate damage callback cannot reroll
            // the same arrow, even when the first roll fails or the inventory is full.
            if (!state.processedProjectiles.add(projectile) || !canTrigger(state, definition)) return;
            if (AbilityEffectMath.succeeds(definition.value(state.level, "chance-percent"),
                    random.nextDouble())
                    && attacker.getInventory().addItem(new ItemStack(Material.ARROW, 1)).isEmpty())
                triggered(state, definition);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombatVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        Long recent = recentCombatVelocity.remove(player.getUniqueId());
        if (recent == null || System.currentTimeMillis() - recent > 250L) return;
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        AbilityState state = match == null ? null : state(match, player.getUniqueId());
        if (!evolvedActive(player, match, state, "knockback_resist")) return;
        Vector velocity = event.getVelocity().clone();
        double fraction = evolved.abilities.get("knockback_resist")
            .value(state.level, "knockback-reduction") / 100.0;
        velocity.setX(velocity.getX() * (1.0 - fraction));
        velocity.setZ(velocity.getZ() * (1.0 - fraction));
        event.setVelocity(velocity);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        AbilityState state = match == null ? null : state(match, player.getUniqueId());
        if (evolvedActive(player, match, state, "fire_resistance"))
            event.setDuration(AbilityEffectMath.fireSeconds(event.getDuration(),
                evolved.abilities.get("fire_resistance")
                    .value(state.level, "duration-reduction-percent")));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBuild(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        AbilityState state = match == null ? null : state(match, player.getUniqueId());
        if (!evolvedActive(player, match, state, "builder")
                || player.getWorld() != matches.getWorld(match.getId())) return;
        String key = event.getBlockPlaced().getX() + ":" + event.getBlockPlaced().getY()
            + ":" + event.getBlockPlaced().getZ();
        Set<String> unique = countedBuildPositions.computeIfAbsent(match.getId(), ignored -> new HashSet<String>());
        if (!unique.add(key)) return;
        state.validBlocksPlaced++;
        AbilityCatalog.Definition definition = evolved.abilities.get("builder");
        if (state.validBlocksPlaced < definition.value(state.level, "blocks-required")) return;
        if (applyEvolvedPotion(player, state, definition, PotionEffectType.SPEED,
                definition.value(state.level, "duration-seconds"))) state.validBlocksPlaced = 0;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMining(BlockDamageEvent event) {
        Player player = event.getPlayer();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        AbilityState state = match == null ? null : state(match, player.getUniqueId());
        if (!evolvedActive(player, match, state, "miner")
                || event.getBlock().getType() == Material.BED_BLOCK
                || event.getBlock().getWorld() != matches.getWorld(match.getId())) return;
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        AbilityCatalog.Definition definition = evolved.abilities.get("miner");
        double radius = definition.parameter("enemy-bed-radius", 6);
        boolean nearEnemyBed = false;
        for (GameTeam team : match.getTeams().values()) {
            if (team.getDefinition().getId().equals(participant.getTeamId())
                    || !team.isBedAlive() || team.getDefinition().getBed() == null) continue;
            com.starfinplugins.bedwars.domain.arena.Position bed = team.getDefinition().getBed();
            double dx = event.getBlock().getX() - bed.getX();
            double dy = event.getBlock().getY() - bed.getY();
            double dz = event.getBlock().getZ() - bed.getZ();
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                nearEnemyBed = true; break;
            }
        }
        if (nearEnemyBed) applyEvolvedPotion(player, state, definition,
            PotionEffectType.FAST_DIGGING, definition.value(state.level, "duration-seconds"));
    }

    private void senseAndCollect() {
        for (Map.Entry<UUID, MatchChoices> entry : active.entrySet()) {
            GameMatch match = matches.getMatch(entry.getKey());
            if (match == null || match.getState() != MatchState.IN_GAME) continue;
            for (Map.Entry<UUID, AbilityState> equipped : entry.getValue().abilities.entrySet()) {
                Player player = Bukkit.getPlayer(equipped.getKey());
                AbilityState state = equipped.getValue();
                if (player != null && ownedEffects.containsKey(player.getUniqueId())) {
                    AbilityCatalog.Definition definition = evolved.abilities.get(state.id);
                    GameplaySettings.Ability legacy = settings.abilities.get(state.id);
                    if (definition != null && !evolvedAvailable(player, match, definition, state.level)
                            || legacy != null && !available(player, match, legacy))
                        clearEffect(player.getUniqueId());
                }
                if (player == null || !eligibleInGame(player, match)) continue;
                if (evolvedActive(player, match, state, "sentinel")) senseBed(player, match, state);
                else if (evolvedActive(player, match, state, "collector")) collectNearby(player, match, state);
            }
        }
    }

    private void senseBed(Player player, GameMatch match, AbilityState state) {
        AbilityCatalog.Definition definition = evolved.abilities.get("sentinel");
        if (!canTrigger(state, definition)) return;
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        GameTeam own = match.getTeam(participant.getTeamId());
        if (own == null || !own.isBedAlive() || own.getDefinition().getBed() == null) return;
        com.starfinplugins.bedwars.domain.arena.Position bed = own.getDefinition().getBed();
        double radius = definition.value(state.level, "radius");
        for (GameParticipant other : match.getParticipantList()) {
            if (other.getState() != ParticipantState.CONNECTED
                    || participant.getTeamId().equals(other.getTeamId())) continue;
            Player enemy = Bukkit.getPlayer(other.getPlayerId());
            if (enemy == null || enemy.getWorld() != player.getWorld()) continue;
            Location point = enemy.getLocation();
            double dx = point.getX() - bed.getX(), dy = point.getY() - bed.getY();
            double dz = point.getZ() - bed.getZ();
            if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
            player.sendMessage(ChatColor.RED + "[Sentinela] Inimigo perto da sua cama!");
            triggered(state, definition);
            return;
        }
    }

    private void collectNearby(Player player, GameMatch match, AbilityState state) {
        double extra = evolved.abilities.get("collector").value(state.level, "extra-radius");
        double radius = 1.5 + extra;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Item)) continue;
            Item item = (Item) entity;
            if (!item.hasMetadata("starfinbedwars-generator") || item.getPickupDelay() > 0) continue;
            boolean sameMatch = item.getMetadata("starfinbedwars-generator").stream()
                .anyMatch(value -> value.getOwningPlugin() == plugin
                    && value.asString().startsWith(match.getId().toString() + ":"));
            if (!sameMatch || item.getWorld() != player.getWorld()) continue;
            Material material = item.getItemStack().getType();
            if (material != Material.IRON_INGOT && material != Material.GOLD_INGOT
                    && material != Material.DIAMOND && material != Material.EMERALD) continue;
            item.teleport(player.getLocation().add(0, 0.1, 0));
        }
    }

    /** Preview is side-effect free; a benefit is consumed only after ShopService confirms delivery. */
    public int shopCost(Player player, GameMatch match, int original) {
        if (original <= 0) return original;
        AbilityState state = match == null ? null : state(match, player.getUniqueId());
        if (!evolvedActive(player, match, state, "merchant") || state.merchantUsedThisLife)
            return original;
        AbilityCatalog.Definition definition = evolved.abilities.get("merchant");
        return AbilityEffectMath.shopCost(original,
            definition.value(state.level, "discount-percent"),
            (int) definition.parameter("minimum-price", 1));
    }
    public void completedShopPurchase(Player player, GameMatch match, int original, int charged) {
        if (charged >= original || match == null) return;
        AbilityState state = state(match, player.getUniqueId());
        if (evolvedActive(player, match, state, "merchant")) state.merchantUsedThisLife = true;
    }

    /** One roll per team generator cycle, regardless of how many players have the ability. */
    public int forgeBonus(GameMatch match, GeneratorInstance generator) {
        if (match == null || match.getState() != MatchState.IN_GAME || generator == null
                || generator.getDefinition().getTeamId() == null) return 0;
        ResourceType resource = generator.getDefinition().getResource();
        if (resource != ResourceType.IRON && resource != ResourceType.GOLD) return 0;
        String teamId = generator.getDefinition().getTeamId();
        double highest = 0;
        for (GameParticipant participant : match.getParticipantList()) {
            if (!teamId.equals(participant.getTeamId())) continue;
            Player player = Bukkit.getPlayer(participant.getPlayerId());
            AbilityState state = state(match, participant.getPlayerId());
            if (player != null && evolvedActive(player, match, state, "forge_master"))
                highest = Math.max(highest, evolved.abilities.get("forge_master")
                    .value(state.level, "chance-percent"));
        }
        if (highest <= 0 || !AbilityEffectMath.succeeds(highest, random.nextDouble())) return 0;
        String key = match.getId() + ":" + generator.getDefinition().getId() + ":"
            + (System.currentTimeMillis() / 60000L);
        int limit = (int) evolved.abilities.get("forge_master")
            .parameter("max-bonus-per-minute", 4);
        int count = forgeBonusCounts.getOrDefault(key, 0);
        if (count >= limit) return 0;
        forgeBonusCounts.put(key, count + 1);
        return 1;
    }

    public void useAbility(Player player) {
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (!eligibleInGame(player, match)) { say(player, "Habilidade disponivel apenas em partida.",
            "Ability available only in a match.", "Habilidad solo disponible en partida."); return; }
        AbilityState state = state(match, player.getUniqueId());
        GameplaySettings.Ability ability = state == null ? null : settings.abilities.get(state.id);
        if (ability == null || !available(player, match, ability)
                || ability.type != GameplaySettings.AbilityType.ACTIVE) {
            say(player, "Nenhuma habilidade ativa selecionada.", "No active ability selected.",
                "No hay habilidad activa seleccionada."); return;
        }
        activate(player, match, ability, state);
    }

    private void activate(Player player, GameMatch match, GameplaySettings.Ability ability,
                          AbilityState state) {
        if (!eligibleInGame(player, match) || state == null
                || !available(player, match, ability)) return;
        long now = System.currentTimeMillis();
        if (state.nextUseMillis > now) {
            if (ability.type == GameplaySettings.AbilityType.ACTIVE)
                say(player, "Aguarde " + cooldownRemaining(player.getUniqueId()) + "s para usar novamente.",
                    "Wait " + cooldownRemaining(player.getUniqueId()) + "s before using again.",
                    "Espera " + cooldownRemaining(player.getUniqueId()) + "s para usarla otra vez.");
            return;
        }
        PotionEffectType type = ability.effect == GameplaySettings.AbilityEffect.RESISTANCE_ON_RESPAWN
            ? PotionEffectType.DAMAGE_RESISTANCE : PotionEffectType.SPEED;
        int ticks = ability.durationSeconds * 20;
        PotionEffect previous = player.getActivePotionEffects().stream()
            .filter(effect -> effect.getType().equals(type)).findFirst().orElse(null);
        if (previous != null && previous.getAmplifier() >= 0 && previous.getDuration() >= ticks) return;
        player.addPotionEffect(new PotionEffect(type, ticks, 0), true);
        ownedEffects.put(player.getUniqueId(), new TimedEffect(type, now + ability.durationSeconds * 1000L));
        state.nextUseMillis = now + ability.cooldownSeconds * 1000L;
        if (ability.type == GameplaySettings.AbilityType.ACTIVE)
            say(player, "Habilidade ativada!", "Ability activated!", "Habilidad activada!");
    }
    private boolean canTrigger(AbilityState state, AbilityCatalog.Definition definition) {
        if (state == null || definition == null || System.currentTimeMillis() < state.nextUseMillis) return false;
        return definition.activationLimit == 0 || state.activations < definition.activationLimit;
    }
    private void triggered(AbilityState state, AbilityCatalog.Definition definition) {
        state.activations++;
        state.nextUseMillis = System.currentTimeMillis() + definition.cooldownSeconds * 1000L;
    }
    private boolean applyEvolvedPotion(Player player, AbilityState state,
                                       AbilityCatalog.Definition definition,
                                       PotionEffectType type, double seconds) {
        if (!canTrigger(state, definition)) return false;
        int ticks = Math.max(1, (int) Math.ceil(seconds * 20));
        PotionEffect previous = player.getActivePotionEffects().stream()
            .filter(effect -> effect.getType().equals(type)).findFirst().orElse(null);
        if (previous != null && previous.getAmplifier() >= 0 && previous.getDuration() >= ticks) return false;
        player.addPotionEffect(new PotionEffect(type, ticks, 0), true);
        ownedEffects.put(player.getUniqueId(), new TimedEffect(type,
            System.currentTimeMillis() + ticks * 50L));
        triggered(state, definition);
        return true;
    }
    private void applyAegis(Player player, AbilityState state) {
        AbilityCatalog.Definition definition = evolved.abilities.get("aegis");
        double seconds = definition.value(state.level, "duration-seconds");
        if (!applyEvolvedPotion(player, state, definition, PotionEffectType.ABSORPTION, seconds)) return;
        float hearts = (float) (2.0 * definition.value(state.level, "absorption-hearts"));
        ((CraftPlayer) player).getHandle().setAbsorptionHearts(hearts);
    }
    private void clearEffect(UUID id) {
        TimedEffect owned = ownedEffects.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (owned == null || player == null) return;
        for (PotionEffect activeEffect : player.getActivePotionEffects()) {
            if (activeEffect.getType().equals(owned.type) && activeEffect.getAmplifier() == 0
                    && System.currentTimeMillis() < owned.expiresAt + 2000L)
                player.removePotionEffect(owned.type);
        }
    }
    private void reconcileEffect(Player player) {
        TimedEffect owned = ownedEffects.get(player.getUniqueId());
        if (owned == null) return;
        long remaining = owned.expiresAt - System.currentTimeMillis();
        player.removePotionEffect(owned.type);
        if (remaining <= 0) { ownedEffects.remove(player.getUniqueId()); return; }
        player.addPotionEffect(new PotionEffect(owned.type,
            (int) Math.max(1, Math.min(600, (remaining + 49L) / 50L)), 0), true);
    }
    private boolean eligibleInGame(Player player, GameMatch match) {
        if (match == null || match.getState() != MatchState.IN_GAME
                || matches.isSpectating(player.getUniqueId())) return false;
        GameParticipant part = match.getParticipant(player.getUniqueId());
        return part != null && part.getState() == ParticipantState.CONNECTED;
    }
    private AbilityState state(GameMatch match, UUID id) {
        MatchChoices matchState = active.get(match.getId());
        return matchState == null ? null : matchState.abilities.get(id);
    }

    private void applyKit(Player player, GameMatch match, GameParticipant participant,
                          GameplaySettings.Kit kit, boolean respawn) {
        if (participant.getState() != ParticipantState.CONNECTED) return;
        if (kit.permanentTool != null && equipment.canImprove(participant, kit.permanentTool))
            equipment.purchased(player, match, participant, kit.permanentTool);
        List<GameplaySettings.Grant> grants = respawn ? kit.respawnItems : kit.startItems;
        for (GameplaySettings.Grant grant : grants) {
            ItemStack stack = new ItemStack(grant.material, grant.amount,
                grant.material == Material.WOOL && grant.data == 0
                    ? match.getTeam(participant.getTeamId()).getDefinition().getColorData() : grant.data);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            for (ItemStack leftover : overflow.values()) if (leftover != null)
                player.sendMessage(ChatColor.RED + "Sem espaco para item do kit: " + leftover.getType());
        }
        player.updateInventory();
    }

    private GameplaySettings.Kit resolvedKit(Player player, GameMatch match) {
        GameplaySettings.Kit kit = settings.kits.get(stats.getSelectedKit(player.getUniqueId()));
        return available(player, match, kit) ? kit : settings.kits.get("default");
    }
    private GameplaySettings.Ability resolvedAbility(Player player, GameMatch match) {
        GameplaySettings.Ability ability = settings.abilities.get(stats.getSelectedAbility(player.getUniqueId()));
        return available(player, match, ability) ? ability : null;
    }
    private AbilityState resolvedAbilityState(Player player, GameMatch match) {
        String selected = stats.getSelectedAbility(player.getUniqueId());
        AbilityCatalog.Definition definition = evolved.abilities.get(selected);
        if (definition != null) return new AbilityState(selected,
            stats.getAbilityLevel(player.getUniqueId(), selected));
        // Previously selected IDs stay in MySQL and retain their older effect if the
        // server explicitly grants their individual legacy permission.
        GameplaySettings.Ability legacy = resolvedAbility(player, match);
        return legacy == null ? new AbilityState("none", 0) : new AbilityState(legacy.id, 0);
    }
    private boolean evolvedAvailable(Player player, GameMatch match,
                                     AbilityCatalog.Definition definition, int level) {
        if (definition == null) return false;
        StatsProfile profile = stats.getCachedProfile(player.getUniqueId());
        return AbilityAccess.canEquip(definition, level,
            player.hasPermission(definition.permission),
            stats.getProgress(profile == null ? 0 : profile.totalXp).level,
            MatchMode.of(match.getArena()),
            match.getArena().getGameplayRules().allowsAbility(definition.id));
    }
    private boolean evolvedActive(Player player, GameMatch match, AbilityState state, String id) {
        if (state != null && state.level == 0 && id.equals(state.id))
            state.level = stats.getAbilityLevel(player.getUniqueId(), id);
        return state != null && state.level > 0 && id.equals(state.id)
            && eligibleInGame(player, match)
            && evolvedAvailable(player, match, evolved.abilities.get(id), state.level);
    }
    private boolean available(Player player, GameMatch match, GameplaySettings.Choice choice) {
        if (choice == null || !choice.enabled || !choice.modes.contains(MatchMode.of(match.getArena()))) return false;
        if (!choice.permission.isEmpty() && !player.hasPermission(choice.permission)) return false;
        if (choice instanceof GameplaySettings.Ability && choice.permission.isEmpty()
                && !player.hasPermission("starfinbedwars.ability.legacy." + choice.id)) return false;
        if (choice instanceof GameplaySettings.Kit && !match.getArena().getGameplayRules().allowsKit(choice.id)) return false;
        if (choice instanceof GameplaySettings.Ability && !match.getArena().getGameplayRules().allowsAbility(choice.id)) return false;
        StatsProfile profile = stats.getCachedProfile(player.getUniqueId());
        return choice.level <= stats.getProgress(profile == null ? 0 : profile.totalXp).level;
    }

    public void openKits(Player player) { open(player, false); }
    public void openAbilities(Player player) { open(player, true); }
    private void open(Player player, boolean abilities) {
        if (abilities) { openEvolvedAbilities(player); return; }
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (match == null || (match.getState() != MatchState.WAITING
                && match.getState() != MatchState.STARTING)) return;
        String language = stats.getLanguage(player.getUniqueId());
        ChoiceView holder = new ChoiceView(abilities, match.getId());
        Inventory inventory = Bukkit.createInventory(holder, 27,
            ChatColor.DARK_GRAY + (abilities ? label(language, "Habilidades", "Abilities", "Habilidades")
                : label(language, "Selecao de Kits", "Kit Selection", "Seleccion de Kits")));
        holder.inventory = inventory;
        int slot = 0;
        if (abilities) {
            inventory.setItem(slot, menuItem(Material.BARRIER,
                ChatColor.GRAY + label(language, "Nenhuma habilidade", "No ability", "Sin habilidad"),
                Arrays.asList(ChatColor.YELLOW + ("none".equals(selectedAbility(player.getUniqueId()))
                    ? label(language, "Selecionada", "Selected", "Seleccionada") : ""))));
            holder.ids.put(slot++, "none");
        }
        Map<String, ? extends GameplaySettings.Choice> entries = abilities ? settings.abilities : settings.kits;
        for (GameplaySettings.Choice choice : entries.values()) {
            if (slot >= 27) break;
            boolean selected = choice.id.equals(abilities ? selectedAbility(player.getUniqueId())
                : selectedKit(player.getUniqueId()));
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + choice.description(language));
            lore.add(ChatColor.GRAY + label(language, "Nivel", "Level", "Nivel") + ": " + choice.level);
            if (!choice.permission.isEmpty())
                lore.add(ChatColor.GRAY + label(language, "Permissao", "Permission", "Permiso")
                    + ": " + choice.permission);
            lore.add(ChatColor.GRAY + label(language, "Modos", "Modes", "Modos")
                + ": " + String.join(", ", choice.modes));
            if (choice instanceof GameplaySettings.Ability) {
                GameplaySettings.Ability ability = (GameplaySettings.Ability) choice;
                lore.add(ChatColor.GRAY + "Cooldown: " + ability.cooldownSeconds + "s");
            }
            lore.add((selected ? ChatColor.GOLD : available(player, match, choice) ? ChatColor.GREEN : ChatColor.RED)
                + (selected ? label(language, "Selecionado", "Selected", "Seleccionado")
                    : available(player, match, choice) ? label(language, "Disponivel", "Available", "Disponible")
                    : !choice.enabled ? label(language, "Desativado", "Disabled", "Desactivado")
                    : label(language, "Bloqueado", "Locked", "Bloqueado")));
            inventory.setItem(slot, menuItem(choice.icon, ChatColor.AQUA + choice.name(language), lore));
            holder.ids.put(slot++, choice.id);
        }
        player.openInventory(inventory);
    }

    private void openEvolvedAbilities(Player player) {
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (!waiting(match)) return;
        AbilityView holder = new AbilityView(match.getId());
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_GRAY + "Habilidades");
        holder.inventory = inventory;
        inventory.setItem(0, menuItem(Material.BARRIER, ChatColor.GRAY + "Nenhuma habilidade",
            Arrays.asList(ChatColor.YELLOW + ("none".equals(selectedAbility(player.getUniqueId()))
                ? "Equipada" : "Clique para desequipar"))));
        holder.ids.put(0, "none");
        int slot = 1;
        for (AbilityCatalog.Definition definition : evolved.abilities.values()) {
            int level = stats.getAbilityLevel(player.getUniqueId(), definition.id);
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + ChatColor.stripColor(
                ChatColor.translateAlternateColorCodes('&', definition.description)));
            lore.add(ChatColor.GRAY + "Categoria: " + definition.category);
            lore.add(ChatColor.GRAY + "Nivel: " + level + "/" + definition.maxLevel);
            lore.add(ChatColor.GRAY + "Atual: " + (level == 0 ? "nao desbloqueada"
                : effectSummary(definition, level)));
        if (level < definition.maxLevel)
            lore.add(ChatColor.GRAY + "Proximo: " + effectSummary(definition, level + 1));
            lore.add(ChatColor.GRAY + "Permissao: " + definition.permission);
            lore.add(ChatColor.GRAY + "Preco: " + (level >= definition.maxLevel ? "nivel maximo"
                : (level == 0 ? definition.unlockPrice : definition.priceFor(level + 1)) + " moedas"));
            lore.add((definition.id.equals(selectedAbility(player.getUniqueId()))
                ? ChatColor.GOLD : !definition.enabled ? ChatColor.RED
                : !player.hasPermission(definition.permission) ? ChatColor.RED
                : level == 0 ? ChatColor.YELLOW : ChatColor.GREEN)
                + (definition.id.equals(selectedAbility(player.getUniqueId())) ? "Equipada"
                    : !definition.enabled ? "Desativada"
                    : !player.hasPermission(definition.permission) ? "Sem permissao"
                    : level == 0 ? "Compra indisponivel: sem provedor de moedas" : "Desbloqueada"));
            lore.add(ChatColor.AQUA + "Clique para detalhes");
            inventory.setItem(slot, menuItem(definition.icon,
                ChatColor.translateAlternateColorCodes('&', definition.name), lore));
            holder.ids.put(slot++, definition.id);
        }
        player.openInventory(inventory);
    }

    private void openAbilityDetail(Player player, GameMatch match, String id) {
        AbilityCatalog.Definition definition = evolved.abilities.get(id);
        if (definition == null) return;
        int level = stats.getAbilityLevel(player.getUniqueId(), id);
        AbilityDetailView holder = new AbilityDetailView(match.getId(), id);
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_GRAY + "Habilidade: " + id);
        holder.inventory = inventory;
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + definition.description);
        lore.add(ChatColor.GRAY + "Categoria: " + definition.category);
        lore.add(ChatColor.GRAY + "Nivel atual: " + level + "/" + definition.maxLevel);
        lore.add(ChatColor.GRAY + "Efeito atual: " + (level == 0 ? "nenhum" : effectSummary(definition, level)));
        if (level < definition.maxLevel)
            lore.add(ChatColor.GRAY + "Proximo efeito: " + effectSummary(definition, level + 1));
        lore.add(ChatColor.GRAY + "Permissao: " + definition.permission);
        lore.add(ChatColor.GRAY + "Cooldown: " + definition.cooldownSeconds + "s");
        inventory.setItem(4, menuItem(definition.icon,
            ChatColor.translateAlternateColorCodes('&', definition.name), lore));
        inventory.setItem(11, menuItem(Material.NETHER_STAR, ChatColor.GREEN + "Equipar habilidade",
            Arrays.asList(ChatColor.GRAY + "Requer desbloqueio e permissao.")));
        inventory.setItem(15, menuItem(Material.EMERALD, ChatColor.YELLOW
            + (level == 0 ? "Comprar habilidade" : "Melhorar habilidade"),
            Arrays.asList(ChatColor.GRAY + "Preco: " + (level >= definition.maxLevel ? "nivel maximo"
                : (level == 0 ? definition.unlockPrice : definition.priceFor(level + 1)) + " moedas"),
                ChatColor.RED + "Aguardando provedor de moedas.")));
        inventory.setItem(22, menuItem(Material.ARROW, ChatColor.GRAY + "Voltar",
            Arrays.asList(ChatColor.GRAY + "Lista de habilidades")));
        player.openInventory(inventory);
    }

    private static String effectSummary(AbilityCatalog.Definition ability, int level) {
        List<String> values = new ArrayList<String>();
        for (Map.Entry<String, Double> entry : ability.values(level).entrySet())
            values.add(entry.getKey() + "=" + entry.getValue());
        return String.join(", ", values);
    }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (!waiting(match)) return;
        ItemStack held = event.getItem();
        boolean kit = isSelector(held, false), ability = isSelector(held, true);
        if (!kit && !ability) return;
        event.setCancelled(true);
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (kit) openKits(player); else openAbilities(player);
        }
    }
    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        GameMatch match = matches.getMatchForPlayer(event.getPlayer().getUniqueId());
        if (waiting(match) && isAnySelector(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }
    @EventHandler public void onProjectile(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownExpBottle)
                || !(event.getEntity().getShooter() instanceof Player)) return;
        Player player = (Player) event.getEntity().getShooter();
        if (waiting(matches.getMatchForPlayer(player.getUniqueId()))) event.setCancelled(true);
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof AbilityView) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) return;
            AbilityView view = (AbilityView) top.getHolder();
            GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
            if (!waiting(match) || !match.getId().equals(view.matchId)) return;
            String id = view.ids.get(event.getRawSlot());
            if (id == null) return;
            if ("none".equals(id)) select(player, match, true, "none");
            else openAbilityDetail(player, match, id);
            return;
        }
        if (top.getHolder() instanceof AbilityDetailView) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) return;
            AbilityDetailView view = (AbilityDetailView) top.getHolder();
            GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
            if (!waiting(match) || !match.getId().equals(view.matchId)) return;
            if (event.getRawSlot() == 22) { openEvolvedAbilities(player); return; }
            AbilityCatalog.Definition definition = evolved.abilities.get(view.abilityId);
            if (definition == null) return;
            int level = stats.getAbilityLevel(player.getUniqueId(), definition.id);
            if (event.getRawSlot() == 11) {
                if (!evolvedAvailable(player, match, definition, level)) {
                    say(player, "Habilidade bloqueada, desativada ou sem permissao.",
                        "Ability locked, disabled or missing permission.",
                        "Habilidad bloqueada, desactivada o sin permiso.");
                    return;
                }
                select(player, match, true, definition.id);
            } else if (event.getRawSlot() == 15) {
                if (!player.hasPermission(definition.permission)) {
                    say(player, "Sem permissao para comprar ou melhorar.",
                        "No permission to buy or upgrade.", "Sin permiso para comprar o mejorar.");
                } else if (level >= definition.maxLevel) {
                    say(player, "Nivel maximo alcancado.", "Maximum level reached.",
                        "Nivel maximo alcanzado.");
                } else {
                    say(player, "Compras indisponiveis: a rede ainda nao possui provedor de moedas.",
                        "Purchases unavailable: no network currency provider is configured.",
                        "Compras no disponibles: falta el proveedor de monedas.");
                }
            }
            return;
        }
        if (top.getHolder() instanceof ChoiceView) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) return;
            ChoiceView holder = (ChoiceView) top.getHolder();
            String id = holder.ids.get(event.getRawSlot());
            if (id == null) return;
            GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
            if (!waiting(match) || !match.getId().equals(holder.matchId)) return;
            if (holder.abilities && "none".equals(id)) { select(player, match, true, id); return; }
            GameplaySettings.Choice choice = holder.abilities ? settings.abilities.get(id) : settings.kits.get(id);
            if (choice == null || !available(player, match, choice)) {
                say(player, "Opcao bloqueada ou indisponivel.", "Option locked or unavailable.",
                    "Opcion bloqueada o no disponible."); return;
            }
            select(player, match, holder.abilities, id);
            return;
        }
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (!waiting(match)) return;
        if (isAnySelector(event.getCurrentItem()) || isAnySelector(event.getCursor())
                || (event.getHotbarButton() >= 0 && isAnySelector(
                    player.getInventory().getItem(event.getHotbarButton())))
                || event.getClickedInventory() == player.getInventory()
                    && (event.getSlot() == KIT_HOTBAR_SLOT || event.getSlot() == ABILITY_HOTBAR_SLOT))
            event.setCancelled(true);
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof ChoiceView
                || event.getView().getTopInventory().getHolder() instanceof AbilityView
                || event.getView().getTopInventory().getHolder() instanceof AbilityDetailView) {
            event.setCancelled(true); return;
        }
        Player player = (Player) event.getWhoClicked();
        if (waiting(matches.getMatchForPlayer(player.getUniqueId()))) event.setCancelled(true);
    }
    private void select(Player player, GameMatch match, boolean ability, String id) {
        try {
            stats.setGameplaySelection(player, ability ? PreferencesOutbox.Kind.ABILITY
                : PreferencesOutbox.Kind.KIT, id);
            say(player, "Selecao salva: " + id, "Selection saved: " + id,
                "Seleccion guardada: " + id);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && waiting(matches.getMatchForPlayer(player.getUniqueId())))
                    open(player, ability);
            });
        } catch (IOException | IllegalStateException invalid) {
            say(player, "Nao foi possivel salvar a selecao.", "Could not save the selection.",
                "No se pudo guardar la seleccion.");
        }
    }
    private void installSelectors(Player player) {
        removeSelectors(player);
        if (!waiting(matches.getMatchForPlayer(player.getUniqueId()))) return;
        String lang = stats.getLanguage(player.getUniqueId());
        player.getInventory().setItem(KIT_HOTBAR_SLOT, selector(Material.NETHER_STAR,
            ChatColor.GREEN + label(lang, "Selecionar Kit", "Select Kit", "Elegir Kit"), KIT_MARKER,
            label(lang, "Clique para escolher o kit", "Click to choose a kit", "Clic para elegir un kit")));
        player.getInventory().setItem(ABILITY_HOTBAR_SLOT, selector(Material.EXP_BOTTLE,
            ChatColor.AQUA + label(lang, "Selecionar Habilidade", "Select Ability", "Elegir Habilidad"),
            ABILITY_MARKER, label(lang, "Clique para escolher sua habilidade",
                "Click to choose an ability", "Clic para elegir una habilidad")));
        player.updateInventory();
    }
    private void removeSelectors(Player player) {
        if (player.getOpenInventory() != null
                && (player.getOpenInventory().getTopInventory().getHolder() instanceof ChoiceView
                    || player.getOpenInventory().getTopInventory().getHolder() instanceof AbilityView
                    || player.getOpenInventory().getTopInventory().getHolder() instanceof AbilityDetailView))
            player.closeInventory();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++)
            if (isAnySelector(player.getInventory().getItem(slot))) player.getInventory().setItem(slot, null);
        player.updateInventory();
    }
    private static ItemStack selector(Material material, String name, String marker, String description) {
        return menuItem(material, name, Arrays.asList(ChatColor.GRAY + description, marker));
    }
    private static ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    private static boolean isAnySelector(ItemStack item) {
        return isSelector(item, false) || isSelector(item, true);
    }
    private static boolean isSelector(ItemStack item, boolean ability) {
        if (item == null || item.getType() != (ability ? Material.EXP_BOTTLE : Material.NETHER_STAR)
                || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;
        return item.getItemMeta().getLore().contains(ability ? ABILITY_MARKER : KIT_MARKER);
    }
    private static boolean waiting(GameMatch match) {
        return match != null && (match.getState() == MatchState.WAITING
            || match.getState() == MatchState.STARTING);
    }
    private void say(Player player, String pt, String en, String es) {
        player.sendMessage(ChatColor.AQUA + "[BedWars] " + ChatColor.RESET
            + label(stats.getLanguage(player.getUniqueId()), pt, en, es));
    }
    private static String label(String language, String pt, String en, String es) {
        return "en_US".equals(language) ? en : "es_ES".equals(language) ? es : pt;
    }

    private static final class MatchChoices {
        final Map<UUID, String> kits = new HashMap<UUID, String>();
        final Map<UUID, AbilityState> abilities = new HashMap<UUID, AbilityState>();
    }
    private static final class AbilityState {
        final String id;
        int level;
        long nextUseMillis;
        boolean merchantUsedThisLife;
        int activations;
        int validBlocksPlaced;
        long buffUntilMillis;
        final Set<UUID> processedProjectiles = new HashSet<UUID>();
        final Set<UUID> processedVictims = new HashSet<UUID>();
        AbilityState(String id, int level) { this.id = id; this.level = level; }
    }
    private static final class TimedEffect {
        final PotionEffectType type;
        final long expiresAt;
        TimedEffect(PotionEffectType type, long expiresAt) { this.type = type; this.expiresAt = expiresAt; }
    }
    private static final class ChoiceView implements InventoryHolder {
        final boolean abilities;
        final UUID matchId;
        final Map<Integer, String> ids = new HashMap<Integer, String>();
        Inventory inventory;
        ChoiceView(boolean abilities, UUID matchId) { this.abilities = abilities; this.matchId = matchId; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class AbilityView implements InventoryHolder {
        final UUID matchId;
        final Map<Integer, String> ids = new HashMap<Integer, String>();
        Inventory inventory;
        AbilityView(UUID matchId) { this.matchId = matchId; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class AbilityDetailView implements InventoryHolder {
        final UUID matchId;
        final String abilityId;
        Inventory inventory;
        AbilityDetailView(UUID matchId, String abilityId) {
            this.matchId = matchId; this.abilityId = abilityId;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
