package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.plugin.Plugin;

public final class ParticipantInventoryService implements ManagedService, Listener {
    private final Plugin plugin;
    private final MatchEngineService matches;
    private final Map<UUID, Snapshot> snapshots = new HashMap<UUID, Snapshot>();

    public ParticipantInventoryService(Plugin plugin, MatchEngineService matches) {
        this.plugin = plugin;
        this.matches = matches;
    }

    @Override public String getName() { return "participant-inventory"; }

    @Override
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void stop() {
        for (UUID playerId : new ArrayList<UUID>(snapshots.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) restore(playerId, player);
        }
        snapshots.clear();
    }

    @EventHandler
    public void onMatchLifecycle(MatchLifecycleEvent event) {
        if (event.getType() == MatchLifecycleEvent.Type.PLAYER_JOINED
                && event.getParticipant() != null) {
            Player player = Bukkit.getPlayer(event.getParticipant().getPlayerId());
            if (player != null && !snapshots.containsKey(player.getUniqueId())) {
                capture(player);
                prepare(player);
            } else if (player != null) {
                prepare(player);
            }
        } else if (event.getType() == MatchLifecycleEvent.Type.SPAWN_OPENING
                || event.getType() == MatchLifecycleEvent.Type.COUNTDOWN_CANCELLED) {
            for (GameParticipant participant : event.getMatch().getParticipantList()) {
                if (participant.getState() != ParticipantState.CONNECTED) continue;
                Player player = Bukkit.getPlayer(participant.getPlayerId());
                if (player != null) prepare(player);
            }
        } else if (event.getType() == MatchLifecycleEvent.Type.PLAYER_LEFT
                && event.getParticipant() != null) {
            UUID playerId = event.getParticipant().getPlayerId();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) restore(playerId, player);
        } else if (event.getType() == MatchLifecycleEvent.Type.PLAYERS_RETURNED
                || event.getType() == MatchLifecycleEvent.Type.INSTANCE_REMOVED) {
            for (GameParticipant participant : event.getMatch().getParticipantList()) {
                Player player = Bukkit.getPlayer(participant.getPlayerId());
                if (player != null && snapshots.containsKey(participant.getPlayerId()))
                    restore(participant.getPlayerId(), player);
            }
        }
    }

    /** Called before the engine switches CREATIVE/SPECTATOR to SURVIVAL. */
    public void capture(Player player) {
        if (player != null && !snapshots.containsKey(player.getUniqueId())) {
            snapshots.put(player.getUniqueId(), new Snapshot(player));
        }
    }

    public void discardCapture(UUID playerId) {
        snapshots.remove(playerId);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (!snapshots.containsKey(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
                if (player.isOnline() && (match == null || match.getState() != MatchState.IN_GAME)) {
                    restore(player.getUniqueId(), player);
                }
            }
        });
    }

    private void prepare(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        player.setFireTicks(0);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setFlySpeed(0.1f);
        player.setWalkSpeed(0.2f);
        player.setExp(0.0f);
        player.setLevel(0);
        player.setTotalExperience(0);
    }

    private void restore(UUID playerId, Player player) {
        Snapshot snapshot = snapshots.remove(playerId);
        if (snapshot == null) {
            // A restart or an interrupted admission may leave match armor without
            // a lobby snapshot. Never carry that armor out of a match.
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.updateInventory();
            plugin.getLogger().warning("Inventario anterior ausente para " + player.getName()
                + "; armadura da partida removida no retorno.");
            return;
        }
        player.getInventory().setContents(cloneItems(snapshot.contents));
        player.getInventory().setArmorContents(cloneItems(snapshot.armor));
        player.getInventory().setHeldItemSlot(snapshot.heldSlot);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : snapshot.effects) {
            player.addPotionEffect(effect, true);
        }
        player.setTotalExperience(snapshot.totalExperience);
        player.setLevel(snapshot.level);
        player.setExp(snapshot.experience);
        player.setFoodLevel(snapshot.food);
        player.setSaturation(snapshot.saturation);
        player.setExhaustion(snapshot.exhaustion);
        player.setFireTicks(snapshot.fireTicks);
        player.setAllowFlight(snapshot.allowFlight);
        if (snapshot.allowFlight) player.setFlying(snapshot.flying);
        player.setFlySpeed(snapshot.flySpeed);
        player.setWalkSpeed(snapshot.walkSpeed);
        double health = Math.max(0.5, Math.min(snapshot.health, player.getMaxHealth()));
        player.setHealth(health);
        player.updateInventory();
    }

    private ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int index = 0; index < items.length; index++) {
            copy[index] = items[index] == null ? null : items[index].clone();
        }
        return copy;
    }

    private static final class Snapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final int heldSlot;
        private final int totalExperience;
        private final int level;
        private final float experience;
        private final int food;
        private final float saturation;
        private final float exhaustion;
        private final int fireTicks;
        private final double health;
        private final boolean allowFlight;
        private final boolean flying;
        private final float flySpeed;
        private final float walkSpeed;
        private final Collection<PotionEffect> effects = new ArrayList<PotionEffect>();

        private Snapshot(Player player) {
            contents = cloneItems(player.getInventory().getContents());
            armor = cloneItems(player.getInventory().getArmorContents());
            heldSlot = player.getInventory().getHeldItemSlot();
            totalExperience = player.getTotalExperience();
            level = player.getLevel();
            experience = player.getExp();
            food = player.getFoodLevel();
            saturation = player.getSaturation();
            exhaustion = player.getExhaustion();
            fireTicks = player.getFireTicks();
            health = player.getHealth();
            allowFlight = player.getAllowFlight();
            flying = player.isFlying();
            flySpeed = player.getFlySpeed();
            walkSpeed = player.getWalkSpeed();
            for (PotionEffect effect : player.getActivePotionEffects()) {
                effects.add(new PotionEffect(effect.getType(), effect.getDuration(),
                    effect.getAmplifier(), effect.isAmbient()));
            }
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            ItemStack[] copy = new ItemStack[items.length];
            for (int index = 0; index < items.length; index++) {
                copy[index] = items[index] == null ? null : items[index].clone();
            }
            return copy;
        }
    }
}
