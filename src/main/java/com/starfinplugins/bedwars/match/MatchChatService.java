package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class MatchChatService implements ManagedService, Listener {
    private enum Channel { GLOBAL, TEAM, SPECTATOR }
    private final Plugin plugin;
    private final MatchEngineService matches;
    private final Map<UUID, Channel> channels = new HashMap<UUID, Channel>();

    public MatchChatService(Plugin plugin, MatchEngineService matches) {
        this.plugin = plugin;
        this.matches = matches;
    }

    @Override public String getName() { return "match-chat"; }
    @Override public void start() { Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public void stop() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        channels.clear();
    }

    public String selectChannel(Player player, String requested) {
        GameMatch match = matchOf(player);
        if (match == null) return "Entre em uma partida para escolher um canal.";
        boolean spectator = isSpectator(player, match);
        Channel channel;
        if ("global".equalsIgnoreCase(requested) && !spectator) channel = Channel.GLOBAL;
        else if ("team".equalsIgnoreCase(requested) && !spectator) channel = Channel.TEAM;
        else if ("spectator".equalsIgnoreCase(requested) && spectator) channel = Channel.SPECTATOR;
        else return "Canal indisponivel. Use global/team enquanto joga ou spectator ao assistir.";
        channels.put(player.getUniqueId(), channel);
        return "Canal ativo: " + channel.name().toLowerCase(java.util.Locale.ROOT) + ".";
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        final UUID senderId = event.getPlayer().getUniqueId();
        if (!matches.hasPlayer(senderId)) return;
        final String message = ChatColor.stripColor(event.getMessage());
        // Player/match state and recipients are resolved on the server thread.
        if (event.isAsynchronous()) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { deliver(senderId, message); }
            });
        } else if (matchOf(event.getPlayer()) != null) {
            event.setCancelled(true);
            deliver(senderId, message);
        }
    }

    private void deliver(UUID senderId, String message) {
        Player sender = Bukkit.getPlayer(senderId);
        if (sender == null || !sender.isOnline()) return;
        GameMatch match = matchOf(sender);
        if (match == null) return;
        GameParticipant participant = match.getParticipant(senderId);
        boolean spectator = isSpectator(sender, match);
        Channel channel = channels.get(senderId);
        if (spectator) channel = Channel.SPECTATOR;
        else if (channel == null || channel == Channel.SPECTATOR) channel = Channel.GLOBAL;
        String tag = spectator ? ChatColor.GRAY + "[ESPEC] " : teamTag(match, participant);
        String prefix = channel == Channel.TEAM ? ChatColor.AQUA + "[TIME] " : "";
        String format = plugin.getConfig().getString("match-chat.format", "{channel}{tag}{player} &7> &f{message}");
        String shownMessage = sender.hasPermission("starfinbedwars.chat.color")
            ? ChatColor.translateAlternateColorCodes('&', message) : message;
        String line = ChatColor.translateAlternateColorCodes('&', format)
            .replace("{channel}", prefix).replace("{tag}", tag)
            .replace("{player}", sender.getName()).replace("{message}", ChatColor.WHITE + shownMessage);
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (matchOf(recipient) != match) continue;
            boolean recipientSpectator = isSpectator(recipient, match);
            if (channel == Channel.SPECTATOR && !recipientSpectator) continue;
            if (channel != Channel.SPECTATOR && recipientSpectator) continue;
            if (channel == Channel.TEAM) {
                GameParticipant target = match.getParticipant(recipient.getUniqueId());
                if (participant == null || target == null
                        || !participant.getTeamId().equals(target.getTeamId())) continue;
            }
            recipient.sendMessage(line);
        }
    }

    private String teamTag(GameMatch match, GameParticipant participant) {
        if (participant == null || match.getTeam(participant.getTeamId()) == null) return "";
        com.starfinplugins.bedwars.domain.arena.TeamDefinition team =
            match.getTeam(participant.getTeamId()).getDefinition();
        ChatColor color = ChatColor.getByChar(team.getColor());
        if (color == null) color = ChatColor.WHITE;
        return color + "[" + team.getDisplayName().substring(0, 1).toUpperCase(java.util.Locale.ROOT) + "] ";
    }

    private boolean isSpectator(Player player, GameMatch match) {
        if (matches.getSpectatedMatch(player.getUniqueId()) == match) return true;
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        return participant == null || participant.getState() == ParticipantState.ELIMINATED;
    }

    private GameMatch matchOf(Player player) {
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        return match == null ? matches.getSpectatedMatch(player.getUniqueId()) : match;
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        channels.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler public void onLifecycle(MatchLifecycleEvent event) {
        if (event.getType() == MatchLifecycleEvent.Type.ENDED) {
            for (GameParticipant participant : event.getMatch().getParticipantList()) {
                channels.remove(participant.getPlayerId());
            }
        }
    }
}
