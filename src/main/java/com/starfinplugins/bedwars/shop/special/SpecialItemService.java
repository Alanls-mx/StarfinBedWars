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
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
    private final Map<UUID, MilkProtection> milk = new HashMap<UUID, MilkProtection>();
    private BukkitTask expiry;
    
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
    }

    @Override public void stop() {
        HandlerList.unregisterAll(this);
        if (expiry != null) expiry.cancel();
        expiry = null;
        for (BukkitTask task : bridges.values()) task.cancel();
        bridges.clear();
        bridgeMatch.clear();
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
        final short color = match.getTeam(participant.getTeamId()).getDefinition().getColorData();
        bridgeMatch.put(projectileId, matchId);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int ticks;
            private String lastBlock;
            @Override public void run() {
                if (++ticks > 70 || !egg.isValid() || egg.isDead()
                        || match.getState() != MatchState.IN_GAME
                        || matches.getWorld(matchId) != egg.getWorld()) {
                    stopBridge(projectileId);
                    return;
                }
                // Lay colored wool below the moving egg, never overwrite the map.
                Location location = egg.getLocation().add(0.0D, -1.2D, 0.0D);
                Block block = location.getBlock();
                String key = block.getX() + ":" + block.getY() + ":" + block.getZ();
                if (key.equals(lastBlock)) return;
                lastBlock = key;
                if (isInsideArena(match, location)) {
                    matches.placeSpecialBlock(match, block, Material.WOOL, (byte) color);
                }
            }
        }, 1L, 1L);
        bridges.put(projectileId, task);
    }

    private void stopBridge(UUID projectileId) {
        BukkitTask task = bridges.remove(projectileId);
        if (task != null) task.cancel();
        bridgeMatch.remove(projectileId);
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
        return spawnPet(match, teamId, at, EntityType.IRON_GOLEM,
            Math.max(5, Math.min(120, plugin.getConfig().getInt(
                "special-items.defender-lifetime-seconds", 30))));
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
            if (matchId.equals(bridgeMatch.get(id))) stopBridge(id);
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

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        milk.remove(event.getPlayer().getUniqueId());
    }

    private void expireEffects() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, SpecialMob>> it = mobs.entrySet().iterator(); it.hasNext();) {
            SpecialMob mob = it.next().getValue();
            if (mob.expiresAt < now || !mob.entity.isValid()) {
                removeMob(mob);
                it.remove();
            }
        }
        for (Iterator<Map.Entry<UUID, MilkProtection>> it = milk.entrySet().iterator(); it.hasNext();) {
            if (it.next().getValue().untilMillis < now) it.remove();
        }
    }

    private void removeMob(SpecialMob mob) {
        if (guard != null) guard.forget(mob.entity);
        if (mob.entity.isValid()) mob.entity.remove();
    }

    private static final class SpecialMob {
        private final UUID matchId;
        private final String teamId;
        private final Creature entity;
        private final long expiresAt;
        private SpecialMob(UUID matchId, String teamId, Creature entity, long expiresAt) {
            this.matchId = matchId;
            this.teamId = teamId;
            this.entity = entity;
            this.expiresAt = expiresAt;
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
