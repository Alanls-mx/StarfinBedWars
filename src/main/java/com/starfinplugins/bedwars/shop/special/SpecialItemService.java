package com.starfinplugins.bedwars.shop.special;

import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.world.ManagedMobGuard;
import java.util.ArrayList;
import java.util.List;
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
import org.bukkit.entity.Creature;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Match-scoped gameplay for classic special items. Uses explicitly tagged shop
 * items only; ordinary eggs, pumpkins and snowballs retain vanilla behavior.
 */
public final class SpecialItemService implements ManagedService, Listener {
    private static final String MOB_TAG = "starfinbedwars-special-mob";
    private final Plugin plugin;
    private final MatchEngineService matches;
    private ManagedMobGuard guard;
    private final Map<UUID, SpecialMob> mobs = new HashMap<UUID, SpecialMob>();
    private final Map<UUID, BukkitTask> bridges = new HashMap<UUID, BukkitTask>();
    private final Map<UUID, UUID> bridgeMatch = new HashMap<UUID, UUID>();
    /** Both active construction and completed fading bridges live here. */
    private final Map<UUID, BridgeTrail> bridgeTrails = new HashMap<UUID, BridgeTrail>();
    private long bridgeTick;
    private BukkitTask bridgeDecayTicker;
    private final Map<UUID, MilkProtection> milk = new HashMap<UUID, MilkProtection>();
    private final Map<UUID, BukkitTask> pendingGolemKnockback =
        new HashMap<UUID, BukkitTask>();
    private BukkitTask expiry;
    private BukkitTask defenderEdgeGuard;
    
    public SpecialItemService(Plugin plugin, MatchEngineService matches) {
        this.plugin = plugin;
        this.matches = matches;
    }

    public void setMobGuard(ManagedMobGuard guard) { this.guard = guard; }
    @Override public String getName() { return "special-items"; }

    @Override public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        expiry = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { expireEffects(); }
        }, 20L, 20L);
        defenderEdgeGuard = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { preventDefenderFalls(); }
        }, 4L, 4L);
        bridgeTick = 0L;
        bridgeDecayTicker = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { tickBridgeDecay(); }
        }, 1L, 1L);
    }

    @Override public void stop() {
        HandlerList.unregisterAll(this);
        if (expiry != null) expiry.cancel();
        expiry = null;
        if (defenderEdgeGuard != null) defenderEdgeGuard.cancel();
        defenderEdgeGuard = null;
        if (bridgeDecayTicker != null) bridgeDecayTicker.cancel();
        bridgeDecayTicker = null;
        for (BukkitTask task : bridges.values()) task.cancel();
        for (BukkitTask task : pendingGolemKnockback.values()) task.cancel();
        pendingGolemKnockback.clear();
        bridges.clear();
        bridgeMatch.clear();
        bridgeTrails.clear();
        for (SpecialMob mob : mobs.values()) removeMob(mob);
        mobs.clear();
        milk.clear();
    }

    public static ItemStack decoratePurchase(String id, ItemStack product) {
        return SpecialItemType.mark(id, product);
    }

    /** Read by TeamUpgradeService before triggering or consuming a trap. */
    public boolean isTrapImmune(Player player, GameMatch match) {
        if (player == null || match == null) return false;
        MilkProtection protection = milk.get(player.getUniqueId());
        return protection != null && protection.matchId.equals(match.getId())
            && protection.untilMillis > System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        // On Spigot 1.8 a right-click-air event may arrive already cancelled
        // because vanilla has no matching use action for some custom items.
        // Respect cancellation of protected block interactions.
        if (event.isCancelled()
                && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        final Player player = event.getPlayer();
        SpecialItemType special = SpecialItemType.identify(player.getItemInHand());
        if (special == null) return;
        GameMatch match = activeMatch(player);
        if (match == null) return;
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        if (participant == null) return;
        if (!plugin.getConfig().getBoolean("special-items.enabled", true)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Itens especiais estao desabilitados.");
            return;
        }
        // The match's vanilla block-interaction guard may cancel clicks against
        // protected map blocks; allow right-click-air and valid block targeting.
        boolean used = false;
        try {
            switch (special) {
                case FIREBALL:
                    Fireball fireball = player.launchProjectile(Fireball.class);
                    fireball.setIsIncendiary(false);
                    fireball.setYield(2.0F);
                    fireball.setVelocity(player.getEyeLocation().getDirection().multiply(1.3D));
                    used = true;
                    break;
                case BRIDGE_EGG:
                    Egg egg = player.launchProjectile(Egg.class);
                    egg.setVelocity(player.getEyeLocation().getDirection().multiply(1.35D));
                    egg.setMetadata("starfinbedwars-bridge-egg", new FixedMetadataValue(plugin, true));
                    startBridge(egg, match, participant);
                    used = true;
                    break;
                case BEDBUG:
                    Snowball ball = player.launchProjectile(Snowball.class);
                    ball.setVelocity(player.getEyeLocation().getDirection().multiply(1.25D));
                    ball.setMetadata(MOB_TAG, new FixedMetadataValue(plugin,
                        match.getId() + "|" + participant.getTeamId()));
                    used = true;
                    break;
                case DREAM_DEFENDER:
                    if (event.getClickedBlock() != null
                            && event.getBlockFace() == org.bukkit.block.BlockFace.UP) {
                        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
                        if (target.getType() == Material.AIR
                                && target.getRelative(org.bukkit.block.BlockFace.UP).getType() == Material.AIR
                                && isInsideArena(match, target.getLocation())
                                && countMobs(match, participant.getTeamId(), EntityType.IRON_GOLEM) < 2) {
                            used = spawnDefender(match, participant.getTeamId(),
                                target.getLocation().add(0.5D, 0.0D, 0.5D));
                        }
                    }
                    if (!used) player.sendMessage(ChatColor.RED
                        + "Coloque o defensor em um piso livre. Maximo: 2 por equipe.");
                    break;
                case MAGIC_MILK:
                    int seconds = Math.max(5, Math.min(120,
                        plugin.getConfig().getInt("special-items.magic-milk-seconds", 30)));
                    milk.put(player.getUniqueId(), new MilkProtection(match.getId(),
                        System.currentTimeMillis() + seconds * 1000L));
                    player.sendMessage(ChatColor.GREEN + "Imune a armadilhas por " + seconds + " segundos!");
                    used = true;
                    break;
                default:
                    break;
            }
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Falha em item especial " + special.getId()
                + " na partida " + match.getId() + ": " + error.getMessage());
        }
        event.setCancelled(true);
        if (used) consumeHand(player);
    }

    private GameMatch activeMatch(Player player) {
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (match == null || match.getState() != MatchState.IN_GAME
                || matches.getWorld(match.getId()) != player.getWorld()
                || matches.isSpectating(player.getUniqueId())) return null;
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        return participant != null && participant.getState() == ParticipantState.CONNECTED
            ? match : null;
    }

    private void consumeHand(Player player) {
        ItemStack hand = player.getItemInHand();
        if (hand == null) return;
        if (hand.getAmount() <= 1) player.setItemInHand(null);
        else {
            ItemStack remaining = hand.clone();
            remaining.setAmount(hand.getAmount() - 1);
            player.setItemInHand(remaining);
        }
        player.updateInventory();
    }

    private boolean isInsideArena(GameMatch match, Location location) {
        if (location == null || matches.getWorld(match.getId()) != location.getWorld()) return false;
        ArenaRegion region = match.getArena().getRegion(ArenaRegionType.MAIN);
        return region != null && region.contains(new Position(
            match.getArena().getSourceWorld(), location.getX(), location.getY(),
            location.getZ(), 0.0F, 0.0F));
    }

    private void startBridge(final Egg egg, final GameMatch match, final GameParticipant participant) {
        final UUID projectileId = egg.getUniqueId();
        final UUID matchId = match.getId();
        final World world = egg.getWorld();
        final byte color = match.getTeam(participant.getTeamId()).getDefinition().getColorData();

        int requestedWidth = plugin.getConfig().getInt("special-items.bridge-egg.width", 3);
        final int width = requestedWidth == 1 || requestedWidth == 3 || requestedWidth == 5
            ? requestedWidth : 3;
        final int thickness = Math.max(1, Math.min(2,
            plugin.getConfig().getInt("special-items.bridge-egg.thickness", 2)));
        final int maximumBlocks = Math.max(48, Math.min(640,
            plugin.getConfig().getInt("special-items.bridge-egg.max-blocks", 480)));
        final int maximumTicks = Math.max(5, Math.min(120,
            plugin.getConfig().getInt("special-items.bridge-egg.max-ticks", 70)));
        final int delaySeconds = Math.max(0, Math.min(120,
            plugin.getConfig().getInt("special-items.bridge-egg.decay-delay-seconds", 5)));
        final int intervalTicks = Math.max(1, Math.min(20,
            plugin.getConfig().getInt("special-items.bridge-egg.decay-interval-ticks", 3)));
        final BridgeTrail trail = new BridgeTrail(matchId, world, color,
            delaySeconds * 20L, intervalTicks);

        bridgeTrails.put(projectileId, trail);
        bridgeMatch.put(projectileId, matchId);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int ticks;
            private int placedBlocks;
            private int lastX = Integer.MIN_VALUE;
            private int lastZ = Integer.MIN_VALUE;

            @Override public void run() {
                if (++ticks > maximumTicks || placedBlocks >= maximumBlocks
                        || !egg.isValid() || egg.isDead()
                        || match.getState() != MatchState.IN_GAME
                        || matches.getWorld(matchId) != world) {
                    stopBridge(projectileId);
                    return;
                }

                Location at = egg.getLocation();
                if (!isInsideArena(match, at)) {
                    stopBridge(projectileId);
                    return;
                }

                // The previous single-block strip was difficult to cross.
                // Build a 3-wide, 2-thick path by default (configurable), normal
                // to the direction of travel. Fill small gaps between ticks.
                int x = at.getBlockX();
                int z = at.getBlockZ();
                int floorY = (int) Math.floor(at.getY() - 1.2D);
                if (floorY < 0 || floorY > 255) {
                    stopBridge(projectileId);
                    return;
                }
                int distance = lastX == Integer.MIN_VALUE ? 0
                    : Math.max(Math.abs(x - lastX), Math.abs(z - lastZ));
                // Never draw an enormous line if the projectile is teleported.
                if (distance > 6) {
                    lastX = x;
                    lastZ = z;
                    return;
                }
                Vector velocity = egg.getVelocity();
                java.util.List<BridgeEggShape.Offset> shape =
                    BridgeEggShape.footprint(width, thickness, velocity.getX(), velocity.getZ());
                int steps = Math.max(1, distance);
                int fromX = lastX == Integer.MIN_VALUE ? x : lastX;
                int fromZ = lastZ == Integer.MIN_VALUE ? z : lastZ;

                for (int step = 1; step <= steps && placedBlocks < maximumBlocks; step++) {
                    List<BridgeCell> built = new ArrayList<BridgeCell>();
                    int centerX = (int) Math.round(fromX + (x - fromX) * (step / (double) steps));
                    int centerZ = (int) Math.round(fromZ + (z - fromZ) * (step / (double) steps));
                    for (BridgeEggShape.Offset offset : shape) {
                        if (placedBlocks >= maximumBlocks) break;
                        int bx = centerX + offset.getX();
                        int by = floorY + offset.getY();
                        int bz = centerZ + offset.getZ();
                        if (by < 0 || by > 255 || !world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
                        Block block = world.getBlockAt(bx, by, bz);
                        // The match engine guards the arena bounds and never
                        // replaces template or other players' existing blocks.
                        if (matches.placeSpecialBlock(match, block, Material.WOOL, color,
                                projectileId)) {
                            built.add(new BridgeCell(bx, by, bz));
                            placedBlocks++;
                        }
                    }
                    // A slice is one consecutive cross-section of the pathway.
                    // It is later removed as one step, beginning at the origin.
                    trail.slices.addSlice(built);
                }
                lastX = x;
                lastZ = z;
                if (placedBlocks >= maximumBlocks) stopBridge(projectileId);
            }
        }, 1L, 1L);
        bridges.put(projectileId, task);
    }

    /** Ends building, but deliberately retains the bridge for later decay. */
    private void stopBridge(UUID projectileId) {
        BukkitTask task = bridges.remove(projectileId);
        if (task != null) task.cancel();
        bridgeMatch.remove(projectileId);
        BridgeTrail trail = bridgeTrails.get(projectileId);
        if (trail == null) return;
        if (trail.slices.isEmpty()) {
            bridgeTrails.remove(projectileId);
            return;
        }
        if (!trail.finished) {
            trail.finished = true;
            trail.nextRemovalTick = bridgeTick + trail.delayTicks;
        }
    }

    /** One shared scheduler removes one cross-section per bridge at a time. */
    private void tickBridgeDecay() {
        bridgeTick++;
        for (Iterator<Map.Entry<UUID, BridgeTrail>> it = bridgeTrails.entrySet().iterator();
             it.hasNext();) {
            Map.Entry<UUID, BridgeTrail> entry = it.next();
            BridgeTrail trail = entry.getValue();
            if (!trail.finished) continue;

            GameMatch match = matches.getMatchInWorld(trail.world);
            if (match == null || !trail.matchId.equals(match.getId())
                    || match.getState() != MatchState.IN_GAME
                    || matches.getWorld(trail.matchId) != trail.world) {
                it.remove();
                continue;
            }
            if (bridgeTick < trail.nextRemovalTick) continue;
            List<BridgeCell> slice = trail.slices.peekOldest();
            if (slice == null) {
                it.remove();
                continue;
            }
            // Do not lose a bridge section if its chunk is temporarily
            // unavailable. Retry later when the chunk can be loaded.
            boolean available = true;
            for (BridgeCell cell : slice) {
                int chunkX = cell.x >> 4;
                int chunkZ = cell.z >> 4;
                if (!trail.world.isChunkLoaded(chunkX, chunkZ)
                        && !trail.world.loadChunk(chunkX, chunkZ, false)) {
                    available = false;
                    break;
                }
            }
            if (!available) {
                trail.nextRemovalTick = bridgeTick + 20L;
                continue;
            }
            trail.slices.pollOldest();
            // Erase in EXACT construction order. A reused location owned by
            // someone else will be skipped by decayBridgeBlock().
            for (BridgeCell cell : slice) {
                Block block = trail.world.getBlockAt(cell.x, cell.y, cell.z);
                matches.decayBridgeBlock(match, block, entry.getKey(), trail.color);
            }
            if (trail.slices.isEmpty()) it.remove();
            else trail.nextRemovalTick = bridgeTick + trail.intervalTicks;
        }
    }

    @EventHandler
    public void onSpecialEgg(PlayerEggThrowEvent event) {
        if (event.getEgg().hasMetadata("starfinbedwars-bridge-egg")) {
            event.setHatching(false);
        }
    }

    @EventHandler
    public void onProjectileImpact(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile instanceof Egg && bridges.containsKey(projectile.getUniqueId())) {
            stopBridge(projectile.getUniqueId());
        }
        if (!(projectile instanceof Snowball)) return;
        String data = ownMetadata(projectile);
        if (data == null) return;
        String[] parts = data.split("\\|", 2);
        if (parts.length != 2) return;
        GameMatch match = matches.getMatchInWorld(projectile.getWorld());
        if (match == null || !match.getId().toString().equals(parts[0])
                || match.getState() != MatchState.IN_GAME) return;
        if (countMobs(match, parts[1], EntityType.SILVERFISH) >= 3) return;
        Location spawn = projectile.getLocation();
        if (spawn.getBlock().getType() != Material.AIR) spawn.add(0.0D, 1.0D, 0.0D);
        if (isInsideArena(match, spawn)) spawnPet(match, parts[1], spawn, EntityType.SILVERFISH, 15);
    }

    private boolean spawnDefender(GameMatch match, String teamId, Location at) {
        // Existing servers may still have the old "30 seconds" value saved.
        // The new persistence flag defaults to true and takes precedence.
        int lifespan = plugin.getConfig().getBoolean("special-items.defender-persistent", true)
            ? 0 : Math.max(0, Math.min(3600, plugin.getConfig().getInt(
                "special-items.defender-lifetime-seconds", 30)));
        return spawnPet(match, teamId, at, EntityType.IRON_GOLEM, lifespan);
    }

    private boolean spawnPet(GameMatch match, String teamId, Location at,
                             EntityType type, int lifetimeSeconds) {
        Entity mob = guard == null ? at.getWorld().spawnEntity(at, type)
            : guard.spawnAuthorized(at, type);
        if (!(mob instanceof Creature)) {
            if (mob != null) mob.remove();
            return false;
        }
        Creature creature = (Creature) mob;
        creature.setRemoveWhenFarAway(false);
        creature.setMetadata(MOB_TAG, new FixedMetadataValue(plugin, match.getId() + "|" + teamId));
        if (creature instanceof IronGolem) creature.setCustomName(ChatColor.AQUA + "Defensor");
        mobs.put(creature.getUniqueId(), new SpecialMob(match.getId(), teamId, creature,
            lifetimeSeconds <= 0 ? Long.MAX_VALUE :
                System.currentTimeMillis() + lifetimeSeconds * 1000L));
        return true;
    }

    private int countMobs(GameMatch match, String team, EntityType type) {
        int count = 0;
        for (SpecialMob mob : mobs.values()) {
            if (mob.matchId.equals(match.getId()) && mob.teamId.equals(team)
                    && mob.entity.getType() == type && mob.entity.isValid()) count++;
        }
        return count;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetTarget(EntityTargetLivingEntityEvent event) {
        String data = ownMetadata(event.getEntity());
        if (data == null || !(event.getTarget() instanceof Player)) return;
        if (isFriendlyTarget(data, (Player) event.getTarget())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetAttack(EntityDamageByEntityEvent event) {
        String attacker = ownMetadata(event.getDamager());
        if (attacker != null && event.getEntity() instanceof Player
                && isFriendlyTarget(attacker, (Player) event.getEntity())) {
            event.setCancelled(true);
        }
        String victim = ownMetadata(event.getEntity());
        if (victim != null && event.getDamager() instanceof Player
                && isFriendlyTarget(victim, (Player) event.getDamager())) {
            event.setCancelled(true);
        }
    }

    /**
     * Vanilla damage and velocity are processed by the server around the
     * damage event. Scale the resulting velocity on the following tick,
     * preserving golem movement and combat AI. Only our summoned defenders
     * are affected; NPC Villagers and natural mobs remain unchanged.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDefenderDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof IronGolem)) return;
        switch (event.getCause()) {
            case ENTITY_ATTACK:
            case PROJECTILE:
            case ENTITY_EXPLOSION:
            case BLOCK_EXPLOSION:
                break;
            default:
                return;
        }
        final IronGolem golem = (IronGolem) event.getEntity();
        final UUID entityId = golem.getUniqueId();
        final SpecialMob defender = mobs.get(entityId);
        if (defender == null || pendingGolemKnockback.containsKey(entityId)) return;

        BukkitTask task = Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                pendingGolemKnockback.remove(entityId);
                if (!golem.isValid() || golem.isDead()
                        || mobs.get(entityId) != defender) return;
                GameMatch match = matches.getMatchInWorld(golem.getWorld());
                if (match == null || !defender.matchId.equals(match.getId())
                        || match.getState() != MatchState.IN_GAME) return;

                double horizontal = SpecialItemBalance.knockbackMultiplier(
                    plugin.getConfig().getDouble("special-items.defender-knockback.horizontal", 0.75D),
                    0.75D);
                double vertical = SpecialItemBalance.knockbackMultiplier(
                    plugin.getConfig().getDouble("special-items.defender-knockback.vertical", 0.85D),
                    0.85D);
                if (horizontal == 1.0D && vertical == 1.0D) return;
                Vector original = golem.getVelocity();
                golem.setVelocity(new Vector(original.getX() * horizontal,
                    original.getY() * vertical, original.getZ() * horizontal));
            }
        });
        pendingGolemKnockback.put(entityId, task);
    }

    @EventHandler
    public void onPetDeath(EntityDeathEvent event) {
        if (ownMetadata(event.getEntity()) == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        SpecialMob removed = mobs.remove(event.getEntity().getUniqueId());
        BukkitTask velocityTask = pendingGolemKnockback.remove(event.getEntity().getUniqueId());
        if (velocityTask != null) velocityTask.cancel();
        if (removed != null && guard != null) guard.forget(removed.entity);
    }

    private boolean isFriendlyTarget(String data, Player target) {
        String[] parts = data.split("\\|", 2);
        if (parts.length != 2) return true;
        GameMatch match = matches.getMatchForPlayer(target.getUniqueId());
        if (match == null || !match.getId().toString().equals(parts[0])
                || matches.getWorld(match.getId()) != target.getWorld()) return true;
        GameParticipant participant = match.getParticipant(target.getUniqueId());
        return participant == null || participant.getState() != ParticipantState.CONNECTED
            || participant.getTeamId().equals(parts[1])
            || matches.isSpectating(target.getUniqueId());
    }

    private String ownMetadata(Entity entity) {
        if (entity == null || !entity.hasMetadata(MOB_TAG)) return null;
        for (MetadataValue value : entity.getMetadata(MOB_TAG)) {
            if (value.getOwningPlugin() == plugin) return value.asString();
        }
        return null;
    }

    @EventHandler public void onMatchLifecycle(MatchLifecycleEvent event) {
        if (event.getType() != MatchLifecycleEvent.Type.ENDED
                && event.getType() != MatchLifecycleEvent.Type.INSTANCE_REMOVED) return;
        UUID matchId = event.getMatch().getId();
        for (UUID id : new ArrayList<UUID>(bridgeMatch.keySet())) {
            if (!matchId.equals(bridgeMatch.get(id))) continue;
            BukkitTask building = bridges.remove(id);
            if (building != null) building.cancel();
            bridgeMatch.remove(id);
        }
        // The instance is being removed: do not schedule any more world writes.
        for (Iterator<Map.Entry<UUID, BridgeTrail>> it = bridgeTrails.entrySet().iterator();
             it.hasNext();) {
            if (matchId.equals(it.next().getValue().matchId)) it.remove();
        }
        for (Iterator<Map.Entry<UUID, SpecialMob>> it = mobs.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, SpecialMob> entry = it.next();
            if (matchId.equals(entry.getValue().matchId)) {
                removeMob(entry.getValue());
                it.remove();
            }
        }
        for (Iterator<Map.Entry<UUID, MilkProtection>> it = milk.entrySet().iterator(); it.hasNext();) {
            if (matchId.equals(it.next().getValue().matchId)) it.remove();
        }
    }

    private void expireEffects() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, SpecialMob>> it = mobs.entrySet().iterator(); it.hasNext();) {
            SpecialMob mob = it.next().getValue();
            if (mob.expiresAt < now || !mob.entity.isValid()) {
                removeMob(mob);
                it.remove();
            } else {
                selectEnemyTarget(mob);
            }
        }
        for (Iterator<Map.Entry<UUID, MilkProtection>> it = milk.entrySet().iterator(); it.hasNext();) {
            if (it.next().getValue().untilMillis < now) it.remove();
        }
    }

    /**
     * Vanilla navigation can path directly off floating BedWars islands.
     * Scan the next step and the golem's feet every four ticks, remembering
     * a valid last-safe platform position for emergencies.
     */
    private void preventDefenderFalls() {
        for (SpecialMob mob : mobs.values()) {
            if (!(mob.entity instanceof IronGolem) || !mob.entity.isValid()
                    || mob.entity.isDead()) continue;
            GameMatch match = matches.getMatchInWorld(mob.entity.getWorld());
            if (match == null || !match.getId().equals(mob.matchId)
                    || match.getState() != MatchState.IN_GAME) continue;
            Location at = mob.entity.getLocation();
            if (hasSolidFloor(at)) {
                mob.lastSafe = at.clone();
                org.bukkit.entity.LivingEntity enemy = mob.entity.getTarget();
                if (enemy == null) continue;
                Vector approach = enemy.getLocation().toVector().subtract(at.toVector());
                approach.setY(0.0D);
                if (approach.lengthSquared() < 0.01D) continue;
                approach.normalize().multiply(1.5D);
                Location ahead = at.clone().add(approach);
                if (!hasSolidFloor(ahead)) {
                    mob.entity.setTarget(null);
                    Vector v = mob.entity.getVelocity();
                    mob.entity.setVelocity(new Vector(0.0D, Math.max(0.0D, v.getY()), 0.0D));
                }
            } else if (mob.lastSafe != null) {
                mob.entity.setTarget(null);
                mob.entity.teleport(mob.lastSafe);
                mob.entity.setVelocity(new Vector(0, 0, 0));
            }
        }
    }

    private boolean hasSolidFloor(Location at) {
        if (at == null || at.getWorld() == null || at.getY() < 2) return false;
        Location below = at.clone().subtract(0.0D, 1.0D, 0.0D);
        // BedWars islands have gaps; never consider air, liquids or leaves safe.
        org.bukkit.block.Block floor = below.getBlock();
        return floor.getType().isSolid() && floor.getType() != Material.LEAVES
            && floor.getType() != Material.LEAVES_2;
    }

    /** Give defenders and bedbugs a target: vanilla IronGolems do not always chase players. */
    private void selectEnemyTarget(SpecialMob mob) {
        Creature creature = mob.entity;
        GameMatch match = matches.getMatchInWorld(creature.getWorld());
        if (match == null || !match.getId().equals(mob.matchId)
                || match.getState() != MatchState.IN_GAME) {
            creature.setTarget(null);
            return;
        }
        Player nearest = null;
        double best = 12.0D * 12.0D;
        for (Player player : creature.getWorld().getPlayers()) {
            GameMatch joined = matches.getMatchForPlayer(player.getUniqueId());
            GameParticipant participant = match.getParticipant(player.getUniqueId());
            if (joined != match || participant == null
                    || participant.getState() != ParticipantState.CONNECTED
                    || matches.isSpectating(player.getUniqueId())
                    || mob.teamId.equals(participant.getTeamId())) continue;
            double distance = player.getLocation().distanceSquared(creature.getLocation());
            if (distance < best
                    && (!(creature instanceof IronGolem)
                        || hasSolidFloor(creature.getLocation().clone().add(
                            player.getLocation().toVector()
                                .subtract(creature.getLocation().toVector())
                                .setY(0).normalize().multiply(1.5D))))) {
                nearest = player;
                best = distance;
            }
        }
        creature.setTarget(nearest);
    }

    private void removeMob(SpecialMob mob) {
        BukkitTask velocityTask = pendingGolemKnockback.remove(mob.entity.getUniqueId());
        if (velocityTask != null) velocityTask.cancel();
        if (guard != null) guard.forget(mob.entity);
        if (mob.entity.isValid()) mob.entity.remove();
    }

    private static final class BridgeCell {
        private final int x;
        private final int y;
        private final int z;

        private BridgeCell(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class BridgeTrail {
        private final UUID matchId;
        private final World world;
        private final byte color;
        private final long delayTicks;
        private final int intervalTicks;
        private final BridgeEggDecayQueue<BridgeCell> slices =
            new BridgeEggDecayQueue<BridgeCell>();
        private boolean finished;
        private long nextRemovalTick;

        private BridgeTrail(UUID matchId, World world, byte color,
                            long delayTicks, int intervalTicks) {
            this.matchId = matchId;
            this.world = world;
            this.color = color;
            this.delayTicks = delayTicks;
            this.intervalTicks = intervalTicks;
        }
    }

    private static final class SpecialMob {
        private final UUID matchId;
        private final String teamId;
        private final Creature entity;
        private final long expiresAt;
        private Location lastSafe;
        private SpecialMob(UUID matchId, String teamId, Creature entity, long expiresAt) {
            this.matchId = matchId;
            this.teamId = teamId;
            this.entity = entity;
            this.expiresAt = expiresAt;
            if (entity instanceof IronGolem && entity.isValid()) {
                this.lastSafe = entity.getLocation().clone();
            }
        }
    }

    private static final class MilkProtection {
        private final UUID matchId;
        private final long untilMillis;
        private MilkProtection(UUID matchId, long untilMillis) {
            this.matchId = matchId;
            this.untilMillis = untilMillis;
        }
    }
}
