package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchEndReason;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class MatchVisualService implements ManagedService, Listener {
    private final Plugin plugin;
    private final MatchEngineService matches;
    private final Map<UUID, GameMatch> opening = new HashMap<UUID, GameMatch>();
    private final Map<UUID, GameMatch> lobbyStarting = new HashMap<UUID, GameMatch>();
    private final Map<UUID, Integer> lastLobbyCountdown = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> lastCountdown = new HashMap<UUID, Integer>();
    private final Map<UUID, PriorityTitle> titles = new HashMap<UUID, PriorityTitle>();
    private BukkitTask task;

    public MatchVisualService(Plugin plugin, MatchEngineService matches) {
        this.plugin = plugin;
        this.matches = matches;
    }
    @Override public String getName() { return "match-visual-notifications"; }
    @Override public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { refreshCountdowns(); }
        }, 1L, 20L);
    }
    @Override public void stop() {
        if (task != null) task.cancel();
        task = null;
        org.bukkit.event.HandlerList.unregisterAll(this);
        opening.clear();
        lobbyStarting.clear();
        lastLobbyCountdown.clear();
        lastCountdown.clear();
        titles.clear();
    }

    @EventHandler public void onLifecycle(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        switch (event.getType()) {
            case COUNTDOWN_STARTED:
                lobbyStarting.put(match.getId(), match);
                lastLobbyCountdown.remove(match.getId());
                break;
            case SPAWN_OPENING:
                lobbyStarting.remove(match.getId());
                lastLobbyCountdown.remove(match.getId());
                opening.put(match.getId(), match);
                lastCountdown.remove(match.getId());
                break;
            case COUNTDOWN_CANCELLED:
                opening.remove(match.getId());
                lobbyStarting.remove(match.getId());
                lastLobbyCountdown.remove(match.getId());
                lastCountdown.remove(match.getId());
                for (GameParticipant part : match.getParticipantList()) {
                    Player waiting = Bukkit.getPlayer(part.getPlayerId());
                    if (waiting != null && waiting.isOnline()) {
                        show(waiting, "", text("visuals.lobby-countdown.cancelled",
                            "&cAguardando mais jogadores..."), 1, 30);
                    }
                }
                break;
            case STARTED:
                opening.remove(match.getId());
                lobbyStarting.remove(match.getId());
                lastLobbyCountdown.remove(match.getId());
                lastCountdown.remove(match.getId());
                for (GameParticipant participant : match.getParticipantList()) {
                    Player player = Bukkit.getPlayer(participant.getPlayerId());
                    if (player != null && player.isOnline()) {
                        show(player, text("visuals.start.title", "&e&lBED WARS"),
                            text("visuals.start.subtitle", "&fProteja sua cama e elimine as equipes inimigas!"),
                            2, 70);
                        playSound(player, "visuals.start.sound", "LEVEL_UP");
                        particles(player, "visuals.start");
                    }
                }
                break;
            case BED_DESTROYED:
                for (GameParticipant participant : match.getParticipantList()) {
                    if (!participant.getTeamId().equals(event.getTeamId())) continue;
                    Player player = Bukkit.getPlayer(participant.getPlayerId());
                    if (player != null && player.isOnline()) show(player,
                        text("bed-alert.title", "&cCAMA DESTRUIDA!"),
                        text("bed-alert.subtitle", "&eVoce nao podera mais renascer!"), 3, 80);
                }
                break;
            case PLAYER_DIED:
                if (event.getParticipant() != null) {
                    Player player = Bukkit.getPlayer(event.getParticipant().getPlayerId());
                    if (player != null && player.isOnline()) show(player,
                        text("visuals.death.title", "&c&lVOCE MORREU!"),
                        text("visuals.death.subtitle", "&eRenascendo em &c{time} &esegundos.")
                            .replace("{time}", Integer.toString(
                                plugin.getConfig().getInt("match.respawn-seconds", 3))), 2, 45);
                }
                break;
            case PLAYER_ELIMINATED:
                if (event.getParticipant() != null) {
                    Player player = Bukkit.getPlayer(event.getParticipant().getPlayerId());
                    if (player != null && player.isOnline()) show(player,
                        text("visuals.defeat.title", "&c&lDERROTA!"),
                        text("visuals.defeat.subtitle", "&7Sua equipe foi eliminada!"), 4, 70);
                }
                break;
            case ENDED:
                opening.remove(match.getId());
                lobbyStarting.remove(match.getId());
                lastLobbyCountdown.remove(match.getId());
                lastCountdown.remove(match.getId());
                announceResult(match);
                break;
            case PLAYER_LEFT:
                if (event.getParticipant() != null) titles.remove(event.getParticipant().getPlayerId());
                break;
            default: break;
        }
    }

    private void refreshCountdowns() {
        for (GameMatch match : new java.util.ArrayList<GameMatch>(lobbyStarting.values())) {
            Integer seconds = matches.getLobbyCountdown(match);
            int startAt = Math.max(1, Math.min(10,
                plugin.getConfig().getInt("visuals.lobby-countdown.start-at", 5)));
            if (seconds == null || seconds < 1 || seconds > startAt
                    || seconds.equals(lastLobbyCountdown.get(match.getId()))) continue;
            lastLobbyCountdown.put(match.getId(), seconds);
            for (GameParticipant participant : match.getParticipantList()) {
                if (participant.getState() != ParticipantState.CONNECTED) continue;
                Player player = Bukkit.getPlayer(participant.getPlayerId());
                if (player == null || !player.isOnline()
                        || player.getWorld() != matches.getWorld(match.getId())) continue;
                show(player, text("visuals.lobby-countdown.title", "&e&lINICIANDO"),
                    text("visuals.lobby-countdown.subtitle", "&fA partida comeca em &e{time}&fs.")
                        .replace("{time}", Integer.toString(seconds)), 1, 24);
                playSound(player, "visuals.lobby-countdown.sound", "NOTE_PLING");
            }
        }
        for (GameMatch match : new java.util.ArrayList<GameMatch>(opening.values())) {
            Integer seconds = matches.getSpawnOpeningCountdown(match);
            int startAt = Math.max(1, Math.min(10,
                plugin.getConfig().getInt("visuals.countdown.start-at", 5)));
            if (seconds == null || seconds < 1 || seconds > startAt
                    || seconds.equals(lastCountdown.get(match.getId()))) continue;
            lastCountdown.put(match.getId(), seconds);
            for (GameParticipant participant : match.getParticipantList()) {
                if (participant.getState() != ParticipantState.CONNECTED) continue;
                Player player = Bukkit.getPlayer(participant.getPlayerId());
                if (player == null || !player.isOnline()
                        || player.getWorld() != matches.getWorld(match.getId())) continue;
                show(player, ChatColor.RED + Integer.toString(seconds),
                    text("visuals.countdown.subtitle", "&ePrepare-se!"), 1, 25);
                playSound(player, "visuals.countdown.sound", "NOTE_PLING");
                particles(player, "visuals.countdown");
            }
        }
    }

    private void announceResult(GameMatch match) {
        boolean winner = match.getEndReason() == MatchEndReason.VICTORY
            && match.getWinnerTeamId() != null;
        String winnerName = winner && match.getTeam(match.getWinnerTeamId()) != null
            ? match.getTeam(match.getWinnerTeamId()).getDefinition().getDisplayName() : "";
        long seconds = match.getStartedAt() == null || match.getEndedAt() == null ? 0
            : Math.max(0, Duration.between(match.getStartedAt(), match.getEndedAt()).getSeconds());
        for (GameParticipant participant : match.getParticipantList()) {
            Player player = Bukkit.getPlayer(participant.getPlayerId());
            if (player == null || !player.isOnline()) continue;
            boolean won = winner && match.getWinnerTeamId().equals(participant.getTeamId());
            if (won) {
                show(player, text("visuals.victory.title", "&a&lVITORIA!"),
                    text("visuals.victory.subtitle", "&fSeu time venceu!"), 5, 100);
                playSound(player, "visuals.victory.sound", "FIREWORK_LAUNCH");
                particles(player, "visuals.victory");
            } else if (winner) {
                show(player, text("visuals.defeat.title", "&c&lDERROTA!"),
                    text("visuals.defeat.subtitle", "&7Sua equipe foi eliminada!"), 5, 90);
            } else {
                show(player, text("visuals.draw.title", "&ePartida encerrada"),
                    text("visuals.draw.subtitle", "&7Sem equipe vencedora"), 5, 60);
            }
            player.sendMessage(ChatColor.GOLD + "BED WARS - FIM DE PARTIDA");
            player.sendMessage(ChatColor.GRAY + "Mapa: " + ChatColor.WHITE
                + match.getArena().getDisplayName());
            if (winner) player.sendMessage(ChatColor.GRAY + "Equipe vencedora: "
                + ChatColor.GREEN + winnerName);
            player.sendMessage(ChatColor.GRAY + "Duracao: " + ChatColor.WHITE
                + (seconds / 60) + "m " + (seconds % 60) + "s");
        }
    }

    private void show(Player player, String title, String subtitle, int priority, int stayTicks) {
        long now = System.currentTimeMillis();
        PriorityTitle current = titles.get(player.getUniqueId());
        if (current != null && current.untilMillis > now && current.priority > priority) return;
        int fadeIn = 5;
        int fadeOut = 10;
        TitlePacketsV1_8_R3.send(player, title, subtitle, fadeIn, stayTicks, fadeOut);
        titles.put(player.getUniqueId(), new PriorityTitle(priority,
            now + (fadeIn + stayTicks + fadeOut) * 50L));
    }
    private String text(String path, String fallback) {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(path, fallback));
    }
    private void playSound(Player player, String path, String fallback) {
        if (!plugin.getConfig().getBoolean(path + "-enabled", true)) return;
        try {
            Sound sound = Sound.valueOf(plugin.getConfig().getString(path, fallback));
            player.playSound(player.getLocation(), sound,
                (float) plugin.getConfig().getDouble(path + "-volume", 0.7),
                (float) plugin.getConfig().getDouble(path + "-pitch", 1.0));
        } catch (IllegalArgumentException invalid) {
            plugin.getLogger().warning("Som visual invalido em " + path + ".");
        }
    }
    private void particles(Player player, String path) {
        int amount = Math.max(0, Math.min(12,
            plugin.getConfig().getInt(path + ".particles", 0)));
        for (int index = 0; index < amount; index++) {
            double dx = (index % 3 - 1) * 0.4D;
            double dz = (index / 3 % 3 - 1) * 0.4D;
            player.playEffect(player.getLocation().clone().add(dx, 1.1D, dz),
                Effect.COLOURED_DUST, 0);
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { titles.remove(event.getPlayer().getUniqueId()); }

    private static final class PriorityTitle {
        private final int priority;
        private final long untilMillis;
        private PriorityTitle(int priority, long untilMillis) {
            this.priority = priority;
            this.untilMillis = untilMillis;
        }
    }
}
