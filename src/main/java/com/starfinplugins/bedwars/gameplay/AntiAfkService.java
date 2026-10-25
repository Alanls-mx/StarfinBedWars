package com.starfinplugins.bedwars.gameplay;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.stats.MatchStatisticsService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/** AFK removal uses the engine's normal leave path, preserving result semantics. */
public final class AntiAfkService implements ManagedService, Listener {
    private final StarfinBedWarsPlugin plugin;
    private final MatchEngineService matches;
    private final MatchStatisticsService stats;
    private final GameplayService gameplay;
    private final Map<UUID, State> tracked = new HashMap<UUID, State>();
    private BukkitTask task;

    public AntiAfkService(StarfinBedWarsPlugin plugin, MatchEngineService matches,
                          MatchStatisticsService stats, GameplayService gameplay) {
        this.plugin = plugin; this.matches = matches; this.stats = stats; this.gameplay = gameplay;
    }
    @Override public String getName() { return "anti-afk"; }
    @Override public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }
    @Override public void stop() {
        if (task != null) task.cancel();
        HandlerList.unregisterAll(this);
        tracked.clear();
    }

    @EventHandler public void onMatch(MatchLifecycleEvent event) {
        switch (event.getType()) {
            case PLAYER_JOINED:
            case PLAYER_RECONNECTED:
            case PLAYER_RESPAWNED:
                if (event.getParticipant() != null) {
                    Player player = Bukkit.getPlayer(event.getParticipant().getPlayerId());
                    if (player != null) reset(player, event.getMatch());
                }
                break;
            case STARTED:
                for (GameParticipant part : event.getMatch().getParticipantList()) {
                    Player player = Bukkit.getPlayer(part.getPlayerId());
                    if (player != null) reset(player, event.getMatch());
                }
                break;
            case PLAYER_LEFT:
            case PLAYER_ELIMINATED:
                if (event.getParticipant() != null) tracked.remove(event.getParticipant().getPlayerId());
                break;
            case INSTANCE_REMOVED:
                tracked.entrySet().removeIf(entry -> entry.getValue().matchId.equals(event.getMatch().getId()));
                break;
            default: break;
        }
    }
    @EventHandler public void onMove(PlayerMoveEvent event) {
        State state = tracked.get(event.getPlayer().getUniqueId());
        if (state != null && event.getTo() != null
                && state.activity.moved(System.currentTimeMillis(), event.getTo().getX(),
                    event.getTo().getY(), event.getTo().getZ())) state.warned = false;
    }
    @EventHandler public void onInteract(PlayerInteractEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) touch((Player) event.getWhoClicked());
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        tracked.remove(event.getPlayer().getUniqueId());
    }
    private void touch(Player player) {
        State state = tracked.get(player.getUniqueId());
        if (state != null) { state.activity.action(System.currentTimeMillis()); state.warned = false; }
    }
    private void reset(Player player, GameMatch match) {
        tracked.put(player.getUniqueId(), new State(match.getId(), new AfkActivity(
            System.currentTimeMillis(), player.getLocation().getX(), player.getLocation().getY(),
            player.getLocation().getZ())));
    }
    private void tick() {
        GameplaySettings config = gameplay.getSettings();
        if (config == null) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, State> entry : new HashMap<UUID, State>(tracked).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            State state = entry.getValue();
            GameMatch match = matches.getMatchForPlayer(entry.getKey());
            if (player == null || match == null || !match.getId().equals(state.matchId)) {
                tracked.remove(entry.getKey()); continue;
            }
            if (!match.getArena().getGameplayRules().afk(config.antiAfkEnabled)
                    || player.hasPermission(config.afkExemptPermission)
                    || matches.isSpectating(entry.getKey())) { state.activity.action(now); continue; }
            MatchState phase = match.getState();
            if (phase == MatchState.STARTING) { state.activity.action(now); continue; }
            if (phase != MatchState.WAITING && phase != MatchState.IN_GAME) {
                tracked.remove(entry.getKey()); continue;
            }
            GameParticipant part = match.getParticipant(entry.getKey());
            if (part == null || part.getState() != ParticipantState.CONNECTED) {
                state.activity.action(now); continue;
            }
            int timeout = phase == MatchState.WAITING
                ? config.waitingTimeoutSeconds : config.gameTimeoutSeconds;
            AfkActivity.Stage stage = state.activity.stage(now, timeout, config.warningSeconds);
            if (stage == AfkActivity.Stage.WARNING && !state.warned) {
                state.warned = true;
                message(player, "Voce sera removido por inatividade em " + config.warningSeconds + "s.",
                    "You will be removed for inactivity in " + config.warningSeconds + "s.",
                    "Seras expulsado por inactividad en " + config.warningSeconds + "s.");
            } else if (stage == AfkActivity.Stage.EXPIRED) {
                tracked.remove(entry.getKey());
                message(player, "Voce saiu da partida por inatividade.",
                    "You left the match due to inactivity.", "Saliste de la partida por inactividad.");
                matches.leave(player);
            }
        }
    }
    private void message(Player player, String pt, String en, String es) {
        String language = stats.getLanguage(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + ("en_US".equals(language) ? en
            : "es_ES".equals(language) ? es : pt));
    }
    private static final class State {
        final UUID matchId;
        final AfkActivity activity;
        boolean warned;
        State(UUID matchId, AfkActivity activity) { this.matchId = matchId; this.activity = activity; }
    }
}
