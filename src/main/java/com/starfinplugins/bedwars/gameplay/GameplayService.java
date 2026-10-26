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
import com.starfinplugins.bedwars.domain.arena.ResourceGeneratorDefinition;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.match.MatchEquipmentService;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.stats.MatchMode;
import com.starfinplugins.bedwars.stats.MatchStatisticsService;
import com.starfinplugins.bedwars.stats.StatsProfile;
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
import org.bukkit.entity.SmallFireball;
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
import org.bukkit.metadata.FixedMetadataValue;

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
    private final Map<UUID, Long> compassNextUpdate = new HashMap<UUID, Long>();
    private final Set<UUID> emeraldCompassTarget = new HashSet<UUID>();
    private final Random random = new Random();
    private BukkitTask sensingTask;
    private GameplaySettings settings;
    private AbilityCatalog evolved;
    private KitCatalog officialKits;
    private PurchaseSettings purchases;
    private ProgressionPurchaseGateway purchaseGateway = ProgressionPurchaseGateway.unavailable();

    public GameplayService(StarfinBedWarsPlugin plugin, MatchEngineService matches,
                           MatchStatisticsService stats, MatchEquipmentService equipment) {
        this.plugin = plugin; this.matches = matches; this.stats = stats; this.equipment = equipment;
    }
    @Override public String getName() { return "kits e habilidades"; }
    @Override public void start() {
        settings = GameplaySettings.load(plugin);
        purchases = PurchaseSettings.load(plugin);
        officialKits = KitCatalog.load(plugin, purchases);
        evolved = AbilityCatalog.load(plugin, purchases.abilityDefault);
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
        compassNextUpdate.clear();
        emeraldCompassTarget.clear();
    }
    public GameplaySettings getSettings() { return settings; }
    public AbilityCatalog getAbilityCatalog() { return evolved; }
    public KitCatalog getKitCatalog() { return officialKits; }
    public PurchaseSettings getPurchaseSettings() { return purchases; }
    public void setPurchaseGateway(ProgressionPurchaseGateway gateway) {
        purchaseGateway = gateway == null ? ProgressionPurchaseGateway.unavailable() : gateway;
    }
    public void reload() {
        GameplaySettings nextGameplay = GameplaySettings.load(plugin);
        PurchaseSettings nextPurchases = PurchaseSettings.load(plugin);
        KitCatalog nextKits = KitCatalog.load(plugin, nextPurchases);
        AbilityCatalog nextAbilities = AbilityCatalog.load(plugin, nextPurchases.abilityDefault);
        settings = nextGameplay;
        purchases = nextPurchases;
        officialKits = nextKits;
        evolved = nextAbilities;
    }
    public String selectedKit(UUID player) { return stats.getSelectedKit(player); }
    public String selectedAbility(UUID player) { return stats.getSelectedAbility(player); }
    private String selected(UUID player, GameMatch match, ScopedProgress.Kind kind) {
        return stats.getSelection(player, kind, MatchMode.of(match.getArena()));
    }
    private int ownedLevel(UUID player, GameMatch match, ScopedProgress.Kind kind, String id) {
        return stats.getLevel(player, kind, id, MatchMode.of(match.getArena()));
    }
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
            state.level = ownedLevel(playerId, match, ScopedProgress.Kind.ABILITY, state.id);
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
                    startKit(player, match, participant, choices);
                    choices.abilities.put(player.getUniqueId(), resolvedAbilityState(player, match));
                }
                break;
            case PLAYER_RESPAWNED:
                if (event.getParticipant() != null) {
                    Player player = Bukkit.getPlayer(event.getParticipant().getPlayerId());
                    MatchChoices resumed = active.get(match.getId());
                    if (player != null && resumed != null) {
                        respawnKit(player, match, event.getParticipant(), resumed);
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
                            startKit(player, match, event.getParticipant(), resumed);
                            resumed.abilities.put(player.getUniqueId(), resolvedAbilityState(player, match));
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
                    if (inMatch != null) {
                        inMatch.kits.remove(id);
                        inMatch.kitLevels.remove(id);
                        inMatch.kitDelivered.remove(id);
                        inMatch.abilities.remove(id);
                    }
                    compassNextUpdate.remove(id);
                    emeraldCompassTarget.remove(id);
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
                    compassNextUpdate.remove(participant.getPlayerId());
                    emeraldCompassTarget.remove(participant.getPlayerId());
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
                if ("explorer".equals(entry.getValue().kits.get(player.getUniqueId())))
                    updateExplorerCompass(player, match, entry.getValue());
                if (evolvedActive(player, match, state, "sentinel")) senseBed(player, match, state);
                else if (evolvedActive(player, match, state, "collector")) collectNearby(player, match, state);
            }
        }
    }

    private String explorerMarker(GameMatch match) {
        return ChatColor.BLACK + "SBW:EXPLORER:" + match.getId();
    }
    private String pyroMarker(GameMatch match) {
        return ChatColor.BLACK + "SBW:PYRO:" + match.getId();
    }
    private static boolean marked(ItemStack item, String marker) {
        return item != null && item.hasItemMeta() && item.getItemMeta().hasLore()
            && item.getItemMeta().getLore().contains(marker);
    }
    private void updateExplorerCompass(Player player, GameMatch match, MatchChoices choices) {
        UUID id = player.getUniqueId();
        KitCatalog.Definition kit = officialKits.kits.get("explorer");
        int level = choices.kitLevels.getOrDefault(id, 0);
        if (!officialAvailable(player, match, kit, level)
                || System.currentTimeMillis() < compassNextUpdate.getOrDefault(id, 0L)) return;
        boolean hasKitCompass = false;
        for (ItemStack item : player.getInventory().getContents())
            if (marked(item, explorerMarker(match))) { hasKitCompass = true; break; }
        if (!hasKitCompass) return;
        compassNextUpdate.put(id, System.currentTimeMillis() + kit.value(level) * 1000L);
        ResourceType targetType = emeraldCompassTarget.contains(id)
            ? ResourceType.EMERALD : ResourceType.DIAMOND;
        Position nearest = null;
        double best = Double.MAX_VALUE;
        for (ResourceGeneratorDefinition generator : match.getArena().getGeneratorList()) {
            if (generator.getTeamId() != null || generator.getResource() != targetType) continue;
            Position point = generator.getPosition();
            double dx = point.getX() - player.getLocation().getX();
            double dz = point.getZ() - player.getLocation().getZ();
            double distance = dx * dx + dz * dz;
            if (distance < best) { best = distance; nearest = point; }
        }
        if (nearest != null) player.setCompassTarget(new Location(player.getWorld(),
            nearest.getX(), nearest.getY(), nearest.getZ()));
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

    private boolean officialAvailable(Player player, GameMatch match,
                                      KitCatalog.Definition kit, int level) {
        if (kit == null) return false;
        StatsProfile profile = stats.getCachedProfile(player.getUniqueId());
        return KitAccess.canEquip(kit, level, player.hasPermission(kit.permission),
            stats.getProgress(profile == null ? 0 : profile.totalXp).level,
            MatchMode.of(match.getArena()),
            match.getArena().getGameplayRules().allowsKit(kit.id));
    }

    private void startKit(Player player, GameMatch match, GameParticipant participant,
                          MatchChoices choices) {
        String selected = selected(player.getUniqueId(), match, ScopedProgress.Kind.KIT);
        KitCatalog.Definition official = officialKits.kits.get(selected);
        int level = official == null ? 0
            : ownedLevel(player.getUniqueId(), match, ScopedProgress.Kind.KIT, selected);
        if (officialAvailable(player, match, official, level)) {
            choices.kits.put(player.getUniqueId(), selected);
            choices.kitLevels.put(player.getUniqueId(), level);
            applyOfficialKit(player, match, participant, choices, official, false);
            return;
        }
        GameplaySettings.Kit legacy = resolvedKit(player, match);
        choices.kits.put(player.getUniqueId(), legacy.id);
        applyKit(player, match, participant, legacy, false);
    }

    private void respawnKit(Player player, GameMatch match, GameParticipant participant,
                            MatchChoices choices) {
        String id = choices.kits.get(player.getUniqueId());
        KitCatalog.Definition official = officialKits.kits.get(id);
        if (official != null) {
            applyOfficialKit(player, match, participant, choices, official, true);
            return;
        }
        GameplaySettings.Kit legacy = settings.kits.get(id);
        if (legacy != null && available(player, match, legacy))
            applyKit(player, match, participant, legacy, true);
    }

    private void applyOfficialKit(Player player, GameMatch match, GameParticipant participant,
                                  MatchChoices choices, KitCatalog.Definition kit, boolean respawn) {
        if (!eligibleInGame(player, match) || participant.getState() != ParticipantState.CONNECTED
                || player.getWorld() != matches.getWorld(match.getId())) return;
        int level = choices.kitLevels.getOrDefault(player.getUniqueId(), 0);
        if (!officialAvailable(player, match, kit, level)) return;
        if (!KitAccess.shouldDeliver(kit.delivery, respawn,
                choices.kitDelivered.contains(player.getUniqueId()))) return;
        if (!respawn) choices.kitDelivered.add(player.getUniqueId());
        int value = kit.value(level);
        ItemStack item;
        switch (kit.id) {
            case "builder":
                item = new ItemStack(Material.WOOL, value,
                    match.getTeam(participant.getTeamId()).getDefinition().getColorData());
                break;
            case "carpenter": item = new ItemStack(Material.WOOD, value); break;
            case "climber": item = new ItemStack(Material.LADDER, value); break;
            case "blacksmith": item = new ItemStack(Material.IRON_INGOT, value); break;
            case "archer": item = new ItemStack(Material.ARROW, value); break;
            case "trapper": item = new ItemStack(Material.WEB, value); break;
            case "defender": item = new ItemStack(kit.material, value); break;
            case "miner": item = limitedTool(player, participant, Material.WOOD_PICKAXE, value); break;
            case "lumberjack": item = limitedTool(player, participant, Material.WOOD_AXE, value); break;
            case "shears": item = limitedTool(player, participant, Material.SHEARS, value); break;
            case "explorer":
                item = menuItem(Material.COMPASS, ChatColor.AQUA + "Explorador: Diamante",
                    Arrays.asList(ChatColor.GRAY + "Clique para alternar Diamante/Esmeralda",
                        explorerMarker(match)));
                break;
            case "pyromancer":
                item = menuItem(kit.material, ChatColor.GOLD + "Incendiario",
                    Arrays.asList(ChatColor.GRAY + "Projetil temporario sem fogo em blocos",
                        pyroMarker(match)));
                item.setAmount(value);
                break;
            default: return;
        }
        if (item == null) return;
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) player.sendMessage(ChatColor.RED
            + "Sem espaco para item do kit: " + kit.name);
        player.updateInventory();
    }

    private ItemStack limitedTool(Player player, GameParticipant participant,
                                  Material material, int uses) {
        if (material == Material.WOOD_PICKAXE || material == Material.WOOD_AXE) {
            if (!equipment.canImprove(participant, material)) return null;
        }
        for (ItemStack existing : player.getInventory().getContents()) {
            if (existing == null) continue;
            if (material == Material.SHEARS && existing.getType() == Material.SHEARS) return null;
            if (material == Material.WOOD_PICKAXE && existing.getType().name().endsWith("_PICKAXE"))
                return null;
            if (material == Material.WOOD_AXE && existing.getType().name().endsWith("_AXE")) return null;
        }
        ItemStack tool = new ItemStack(material);
        tool.setDurability((short) Math.max(0, material.getMaxDurability() - uses));
        return tool;
    }

    private GameplaySettings.Kit resolvedKit(Player player, GameMatch match) {
        String id = selected(player.getUniqueId(), match, ScopedProgress.Kind.KIT);
        GameplaySettings.Kit kit = officialKits.kits.containsKey(id) ? null : settings.kits.get(id);
        return available(player, match, kit) ? kit : settings.kits.get("default");
    }
    private GameplaySettings.Ability resolvedAbility(Player player, GameMatch match) {
        GameplaySettings.Ability ability = settings.abilities.get(
            selected(player.getUniqueId(), match, ScopedProgress.Kind.ABILITY));
        return available(player, match, ability) ? ability : null;
    }
    private AbilityState resolvedAbilityState(Player player, GameMatch match) {
        String selected = selected(player.getUniqueId(), match, ScopedProgress.Kind.ABILITY);
        AbilityCatalog.Definition definition = evolved.abilities.get(selected);
        if (definition != null) return new AbilityState(selected,
            ownedLevel(player.getUniqueId(), match, ScopedProgress.Kind.ABILITY, selected));
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
            state.level = ownedLevel(player.getUniqueId(), match, ScopedProgress.Kind.ABILITY, id);
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

    public void openKits(Player player) { openOfficialKits(player); }
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

    private void openOfficialKits(Player player) {
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (!waiting(match)) return;
        KitView holder = new KitView(match.getId());
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_GRAY + "Kits");
        holder.inventory = inventory;
        String selected = selected(player.getUniqueId(), match, ScopedProgress.Kind.KIT);
        String mode = MatchMode.of(match.getArena());
        inventory.setItem(0, menuItem(Material.WOOL, ChatColor.GREEN + "Kit Padrao",
            Arrays.asList(ChatColor.GRAY + "Gratuito", ChatColor.YELLOW
                + ("default".equals(selected) ? "Equipado" : "Clique para equipar"))));
        holder.ids.put(0, "default");
        int slot = 1;
        for (KitCatalog.Definition kit : officialKits.kits.values()) {
            int level = ownedLevel(player.getUniqueId(), match, ScopedProgress.Kind.KIT, kit.id);
            List<String> lore = new ArrayList<String>();
            addWrappedLore(lore, kit.description);
            lore.add(ChatColor.GRAY + "Categoria: " + kit.category);
            lore.add(ChatColor.GRAY + "Escopo: " + kit.scope);
            lore.add(ChatColor.GRAY + "Modo: " + mode);
            addWrappedLore(lore, "Permitidos: " + String.join(", ", kit.modes));
            addWrappedLore(lore, "Permissao: " + kit.permission);
            lore.add(ChatColor.GRAY + "Nivel: " + level + "/5");
            lore.add(ChatColor.GRAY + "Atual: " + (level == 0 ? "nao adquirido" : kit.value(level)));
            if (level < 5) {
                lore.add(ChatColor.GRAY + "Proximo: " + kit.value(level + 1));
                lore.add(ChatColor.GRAY + "Preco: " + kit.price(level + 1));
            }
            StatsProfile profile = stats.getCachedProfile(player.getUniqueId());
            int profileLevel = stats.getProgress(profile == null ? 0 : profile.totalXp).level;
            String status = !kit.enabled || !kit.modes.contains(mode)
                    || !match.getArena().getGameplayRules().allowsKit(kit.id)
                ? "INDISPONIVEL NO MODO"
                : !player.hasPermission(kit.permission) ? "SEM PERMISSAO"
                : profileLevel < kit.minimumPlayerLevel ? "BLOQUEADO"
                : level == 0 ? "DISPONIVEL PARA COMPRA (sem provedor)"
                : kit.id.equals(selected) ? "EQUIPADO"
                : level >= 5 ? "NIVEL MAXIMO" : "ADQUIRIDO";
            addWrappedLore(lore, status);
            lore.add(ChatColor.AQUA + "Clique para detalhes");
            inventory.setItem(slot, menuItem(kit.icon, ChatColor.GREEN + kit.name, lore));
            holder.ids.put(slot++, kit.id);
        }
        player.openInventory(inventory);
    }

    private void openKitDetail(Player player, GameMatch match, String id) {
        KitCatalog.Definition kit = officialKits.kits.get(id);
        if (kit == null) return;
        int level = ownedLevel(player.getUniqueId(), match, ScopedProgress.Kind.KIT, id);
        KitDetailView holder = new KitDetailView(match.getId(), id);
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_GRAY + "Kit: " + id);
        holder.inventory = inventory;
        List<String> lore = new ArrayList<String>();
        addWrappedLore(lore, kit.description);
        lore.add(ChatColor.GRAY + "Modo: " + MatchMode.of(match.getArena()));
        lore.add(ChatColor.GRAY + "Escopo: " + kit.scope);
        lore.add(ChatColor.GRAY + "Categoria: " + kit.category);
        addWrappedLore(lore, "Permissao: " + kit.permission);
        lore.add(ChatColor.GRAY + "Nivel atual: " + level + "/5");
        lore.add(ChatColor.GRAY + "Beneficio: " + (level == 0 ? "nenhum" : kit.value(level)));
        if (level < 5) lore.add(ChatColor.GRAY + "Proximo: " + kit.value(level + 1));
        inventory.setItem(4, menuItem(kit.icon, ChatColor.GREEN + kit.name, lore));
        inventory.setItem(11, menuItem(Material.NETHER_STAR, ChatColor.GREEN + "Equipar kit",
            Arrays.asList(ChatColor.GRAY + "Requer aquisicao, modo e permissao.")));
        inventory.setItem(15, menuItem(Material.EMERALD,
            ChatColor.YELLOW + (level == 0 ? "Comprar kit" : "Evoluir kit"),
            Arrays.asList(ChatColor.GRAY + "Preco: " + (level >= 5 ? "nivel maximo"
                : kit.price(level + 1) + " moedas"),
                ChatColor.RED + "Aguardando provedor de moedas.")));
        inventory.setItem(22, menuItem(Material.ARROW, ChatColor.GRAY + "Voltar",
            Arrays.asList(ChatColor.GRAY + "Lista de kits")));
        player.openInventory(inventory);
    }

    private void openEvolvedAbilities(Player player) {
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (!waiting(match)) return;
        AbilityView holder = new AbilityView(match.getId());
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_GRAY + "Habilidades");
        holder.inventory = inventory;
        String selected = selected(player.getUniqueId(), match, ScopedProgress.Kind.ABILITY);
        inventory.setItem(0, menuItem(Material.BARRIER, ChatColor.GRAY + "Nenhuma habilidade",
            Arrays.asList(ChatColor.YELLOW + ("none".equals(selected)
                ? "Equipada" : "Clique para desequipar"))));
        holder.ids.put(0, "none");
        int slot = 1;
        for (AbilityCatalog.Definition definition : evolved.abilities.values()) {
            int level = ownedLevel(player.getUniqueId(), match, ScopedProgress.Kind.ABILITY,
                definition.id);
            List<String> lore = new ArrayList<String>();
            addWrappedLore(lore, ChatColor.stripColor(
                ChatColor.translateAlternateColorCodes('&', definition.description)));
            lore.add(ChatColor.GRAY + "Categoria: " + definition.category);
            lore.add(ChatColor.GRAY + "Modo: " + MatchMode.of(match.getArena()));
            lore.add(ChatColor.GRAY + "Compra: " + definition.scope);
            lore.add(ChatColor.GRAY + "Nivel: " + level + "/" + definition.maxLevel);
            addWrappedLore(lore, "Atual: " + (level == 0 ? "nao desbloqueada"
                : effectSummary(definition, level)));
            if (level < definition.maxLevel)
                addWrappedLore(lore, "Proximo: " + effectSummary(definition, level + 1));
            addWrappedLore(lore, "Permissao: " + definition.permission);
            lore.add(ChatColor.GRAY + "Preco: " + (level >= definition.maxLevel ? "nivel maximo"
                : (level == 0 ? definition.unlockPrice : definition.priceFor(level + 1)) + " moedas"));
            boolean modeAllowed = definition.modes.contains(MatchMode.of(match.getArena()))
                && match.getArena().getGameplayRules().allowsAbility(definition.id);
            lore.add((!definition.enabled || !modeAllowed
                || !player.hasPermission(definition.permission) ? ChatColor.RED
                : level == 0 ? ChatColor.YELLOW : ChatColor.GREEN)
                + (!definition.enabled || !modeAllowed ? "Indisponivel no modo"
                    : !player.hasPermission(definition.permission) ? "Sem permissao"
                    : level == 0 ? "Compra sem provedor"
                    : definition.id.equals(selected) ? "Equipada" : "Desbloqueada"));
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
        int level = ownedLevel(player.getUniqueId(), match, ScopedProgress.Kind.ABILITY, id);
        AbilityDetailView holder = new AbilityDetailView(match.getId(), id);
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_GRAY + "Habilidade: " + id);
        holder.inventory = inventory;
        List<String> lore = new ArrayList<String>();
        addWrappedLore(lore, ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
            definition.description)));
        lore.add(ChatColor.GRAY + "Categoria: " + definition.category);
        lore.add(ChatColor.GRAY + "Modo: " + MatchMode.of(match.getArena()));
        lore.add(ChatColor.GRAY + "Compra: " + definition.scope);
        lore.add(ChatColor.GRAY + "Nivel atual: " + level + "/" + definition.maxLevel);
        addWrappedLore(lore, "Efeito atual: " + (level == 0 ? "nenhum"
            : effectSummary(definition, level)));
        if (level < definition.maxLevel)
            addWrappedLore(lore, "Proximo efeito: " + effectSummary(definition, level + 1));
        addWrappedLore(lore, "Permissao: " + definition.permission);
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

    /** Minecraft 1.8 tooltips do not wrap long lore and can escape compact screens. */
    private static void addWrappedLore(List<String> lore, String text) {
        String remaining = text == null ? "" : text.trim();
        while (remaining.length() > 25) {
            int split = remaining.lastIndexOf(' ', 25);
            if (split < 10) split = 25;
            lore.add(ChatColor.GRAY + remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }
        if (!remaining.isEmpty()) lore.add(ChatColor.GRAY + remaining);
    }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (eligibleInGame(player, match) && (event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            MatchChoices choices = active.get(match.getId());
            ItemStack held = event.getItem();
            if (choices != null && "explorer".equals(choices.kits.get(player.getUniqueId()))
                    && marked(held, explorerMarker(match))) {
                event.setCancelled(true);
                KitCatalog.Definition kit = officialKits.kits.get("explorer");
                if (!officialAvailable(player, match, kit,
                        choices.kitLevels.getOrDefault(player.getUniqueId(), 0))) return;
                if (!emeraldCompassTarget.add(player.getUniqueId()))
                    emeraldCompassTarget.remove(player.getUniqueId());
                compassNextUpdate.remove(player.getUniqueId());
                updateExplorerCompass(player, match, choices);
                player.sendMessage(ChatColor.AQUA + "Bussola: "
                    + (emeraldCompassTarget.contains(player.getUniqueId()) ? "Esmeralda" : "Diamante"));
                return;
            }
            if (choices != null && "pyromancer".equals(choices.kits.get(player.getUniqueId()))
                    && marked(held, pyroMarker(match))) {
                event.setCancelled(true);
                KitCatalog.Definition kit = officialKits.kits.get("pyromancer");
                if (!officialAvailable(player, match, kit,
                        choices.kitLevels.getOrDefault(player.getUniqueId(), 0))) return;
                SmallFireball projectile = player.launchProjectile(SmallFireball.class);
                projectile.setIsIncendiary(false);
                projectile.setYield(0.0F);
                projectile.setMetadata("sbw-kit-pyro",
                    new FixedMetadataValue(plugin, match.getId().toString()));
                if (held.getAmount() <= 1) player.setItemInHand(null);
                else { held.setAmount(held.getAmount() - 1); player.setItemInHand(held); }
                player.updateInventory();
                return;
            }
        }
        if (!waiting(match)) return;
        ItemStack held = event.getItem();
        boolean kit = isSelector(held, false), ability = isSelector(held, true);
        if (!kit && !ability) return;
        event.setCancelled(true);
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (kit) openKits(player); else openAbilities(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPyromancerHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof SmallFireball)
                || !(event.getEntity() instanceof Player)) return;
        SmallFireball projectile = (SmallFireball) event.getDamager();
        if (!projectile.hasMetadata("sbw-kit-pyro")
                || !(projectile.getShooter() instanceof Player)) return;
        Player attacker = (Player) projectile.getShooter();
        Player victim = (Player) event.getEntity();
        GameMatch match = matches.getMatchForPlayer(attacker.getUniqueId());
        if (!eligibleInGame(attacker, match) || match != matches.getMatchForPlayer(victim.getUniqueId())
                || !eligibleInGame(victim, match)
                || !projectile.getMetadata("sbw-kit-pyro").stream().anyMatch(value ->
                    value.getOwningPlugin() == plugin && value.asString().equals(match.getId().toString())))
            return;
        GameParticipant own = match.getParticipant(attacker.getUniqueId());
        GameParticipant other = match.getParticipant(victim.getUniqueId());
        if (own.getTeamId().equals(other.getTeamId())) return;
        MatchChoices choices = active.get(match.getId());
        if (choices == null || !"pyromancer".equals(choices.kits.get(attacker.getUniqueId()))) return;
        KitCatalog.Definition kit = officialKits.kits.get("pyromancer");
        if (!officialAvailable(attacker, match, kit,
                choices.kitLevels.getOrDefault(attacker.getUniqueId(), 0))) return;
        victim.setFireTicks(Math.max(victim.getFireTicks(), kit.fireTicks));
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
        if (top.getHolder() instanceof KitView) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) return;
            KitView view = (KitView) top.getHolder();
            GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
            if (!waiting(match) || !match.getId().equals(view.matchId)) return;
            String id = view.ids.get(event.getRawSlot());
            if ("default".equals(id)) select(player, match, false, id);
            else if (id != null) openKitDetail(player, match, id);
            return;
        }
        if (top.getHolder() instanceof KitDetailView) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) return;
            KitDetailView view = (KitDetailView) top.getHolder();
            GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
            if (!waiting(match) || !match.getId().equals(view.matchId)) return;
            if (event.getRawSlot() == 22) { openKits(player); return; }
            KitCatalog.Definition kit = officialKits.kits.get(view.kitId);
            if (kit == null) return;
            int level = ownedLevel(player.getUniqueId(), match, ScopedProgress.Kind.KIT, kit.id);
            if (event.getRawSlot() == 11) {
                if (!officialAvailable(player, match, kit, level)) {
                    say(player, "Kit bloqueado, indisponivel no modo ou sem permissao.",
                        "Kit locked, unavailable in this mode or missing permission.",
                        "Kit bloqueado, no disponible o sin permiso.");
                    return;
                }
                select(player, match, false, kit.id);
            } else if (event.getRawSlot() == 15) {
                if (!kit.enabled || !kit.modes.contains(MatchMode.of(match.getArena()))
                        || !match.getArena().getGameplayRules().allowsKit(kit.id))
                    player.sendMessage(ChatColor.RED + "Kit indisponivel nesta modalidade.");
                else if (!player.hasPermission(kit.permission))
                    player.sendMessage(ChatColor.RED + "Sem permissao: " + kit.permission);
                else if (level >= 5)
                    player.sendMessage(ChatColor.YELLOW + "Nivel maximo alcancado.");
                else attemptPurchase(player, match, ScopedProgress.Kind.KIT, kit.id,
                    kit.scope, level, kit.price(level + 1), kit.minimumPlayerLevel);
            }
            return;
        }
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
            int level = ownedLevel(player.getUniqueId(), match, ScopedProgress.Kind.ABILITY,
                definition.id);
            if (event.getRawSlot() == 11) {
                if (!evolvedAvailable(player, match, definition, level)) {
                    say(player, "Habilidade bloqueada, desativada ou sem permissao.",
                        "Ability locked, disabled or missing permission.",
                        "Habilidad bloqueada, desactivada o sin permiso.");
                    return;
                }
                select(player, match, true, definition.id);
            } else if (event.getRawSlot() == 15) {
                if (!definition.enabled
                        || !definition.modes.contains(MatchMode.of(match.getArena()))
                        || !match.getArena().getGameplayRules().allowsAbility(definition.id)) {
                    say(player, "Habilidade indisponivel nesta modalidade.",
                        "Ability unavailable in this mode.",
                        "Habilidad no disponible en este modo.");
                } else if (!player.hasPermission(definition.permission)) {
                    say(player, "Sem permissao para comprar ou melhorar.",
                        "No permission to buy or upgrade.", "Sin permiso para comprar o mejorar.");
                } else if (level >= definition.maxLevel) {
                    say(player, "Nivel maximo alcancado.", "Maximum level reached.",
                        "Nivel maximo alcanzado.");
                } else {
                    attemptPurchase(player, match, ScopedProgress.Kind.ABILITY, definition.id,
                        definition.scope, level, level == 0 ? definition.unlockPrice
                            : definition.priceFor(level + 1), definition.minimumPlayerLevel);
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
                || event.getView().getTopInventory().getHolder() instanceof KitView
                || event.getView().getTopInventory().getHolder() instanceof KitDetailView
                || event.getView().getTopInventory().getHolder() instanceof AbilityView
                || event.getView().getTopInventory().getHolder() instanceof AbilityDetailView) {
            event.setCancelled(true); return;
        }
        Player player = (Player) event.getWhoClicked();
        if (waiting(matches.getMatchForPlayer(player.getUniqueId()))) event.setCancelled(true);
    }
    private void select(Player player, GameMatch match, boolean ability, String id) {
        String mode = MatchMode.of(match.getArena());
        stats.selectScoped(player.getUniqueId(), ability ? ScopedProgress.Kind.ABILITY
            : ScopedProgress.Kind.KIT, mode, id, saved -> {
            if (!player.isOnline()) return;
            if (!saved) {
                say(player, "Nao foi possivel salvar a selecao no MySQL.",
                    "Could not save the selection in MySQL.",
                    "No se pudo guardar la seleccion en MySQL.");
                return;
            }
            say(player, "Selecao salva para " + mode + ": " + id,
                "Selection saved for " + mode + ": " + id,
                "Seleccion guardada para " + mode + ": " + id);
            GameMatch current = matches.getMatchForPlayer(player.getUniqueId());
            if (waiting(current) && current.getId().equals(match.getId())) {
                if (ability) openAbilities(player); else openKits(player);
            }
        });
    }
    private void attemptPurchase(Player player, GameMatch match, ScopedProgress.Kind kind,
                                 String id, PurchaseSettings.Scope scope, int currentLevel, int price,
                                 int minimumPlayerLevel) {
        StatsProfile profile = stats.getCachedProfile(player.getUniqueId());
        if (stats.getProgress(profile == null ? 0 : profile.totalXp).level < minimumPlayerLevel) {
            player.sendMessage(ChatColor.RED + "Seu nivel de perfil e insuficiente.");
            return;
        }
        final UUID playerId = player.getUniqueId();
        final ProgressionPurchaseGateway.Request request =
            new ProgressionPurchaseGateway.Request(UUID.randomUUID(), playerId, kind, id,
                PurchaseSettings.purchaseKey(scope, MatchMode.of(match.getArena())),
                currentLevel, currentLevel + 1, price);
        purchaseGateway.purchase(request, outcome -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player current = Bukkit.getPlayer(playerId);
            if (current == null) return;
            if (outcome == ProgressionPurchaseGateway.Result.COMMITTED) {
                stats.requestScopedRefresh(playerId, refreshed -> current.sendMessage(
                    refreshed ? ChatColor.GREEN + "Compra confirmada e progresso atualizado."
                        : ChatColor.YELLOW + "Compra persistida; atualizacao pendente. Reconecte."));
            } else if (outcome == ProgressionPurchaseGateway.Result.INSUFFICIENT_FUNDS)
                current.sendMessage(ChatColor.RED + "Moedas insuficientes.");
            else if (outcome == ProgressionPurchaseGateway.Result.CONFLICT)
                current.sendMessage(ChatColor.YELLOW + "Nivel alterado na rede. Reabra o menu.");
            else if (outcome == ProgressionPurchaseGateway.Result.FAILED)
                current.sendMessage(ChatColor.RED + "Compra falhou sem confirmacao. Tente novamente.");
            else current.sendMessage(ChatColor.RED
                + "Compras indisponiveis: falta provedor de moedas da rede.");
        }));
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
                    || player.getOpenInventory().getTopInventory().getHolder() instanceof KitView
                    || player.getOpenInventory().getTopInventory().getHolder() instanceof KitDetailView
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
        final Map<UUID, Integer> kitLevels = new HashMap<UUID, Integer>();
        final Set<UUID> kitDelivered = new HashSet<UUID>();
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
    private static final class KitView implements InventoryHolder {
        final UUID matchId;
        final Map<Integer, String> ids = new HashMap<Integer, String>();
        Inventory inventory;
        KitView(UUID matchId) { this.matchId = matchId; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class KitDetailView implements InventoryHolder {
        final UUID matchId;
        final String kitId;
        Inventory inventory;
        KitDetailView(UUID matchId, String kitId) { this.matchId = matchId; this.kitId = kitId; }
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
