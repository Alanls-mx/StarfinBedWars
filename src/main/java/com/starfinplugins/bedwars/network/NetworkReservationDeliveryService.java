package com.starfinplugins.bedwars.network;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.minigames.api.ReservationSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** Persistent reservation delivery. A reservation is confirmed only after PLAYER_JOINED. */
public final class NetworkReservationDeliveryService
        implements ManagedService, Listener, PluginMessageListener {
    private static final String SAME_SERVER_CHANNEL = "SMJoin";
    private final StarfinBedWarsPlugin plugin;
    private final NetworkRegistryService network;
    private final MatchEngineService matches;
    private final Map<UUID, Pending> pending = new HashMap<UUID, Pending>();
    private boolean started;

    public NetworkReservationDeliveryService(StarfinBedWarsPlugin plugin,
                                             NetworkRegistryService network, MatchEngineService matches) {
        this.plugin = plugin;
        this.network = network;
        this.matches = matches;
    }

    @Override public String getName() { return "Entrega de reservas de rede"; }

    @Override public void start() {
        if (!network.isEnabled()) return;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
            plugin, SAME_SERVER_CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        matches.setReservationFailureHandler(this::failPending);
        started = true;
    }

    @Override public void stop() {
        started = false;
        matches.setReservationFailureHandler(null);
        HandlerList.unregisterAll(this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, "BungeeCord");
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(
            plugin, SAME_SERVER_CHANNEL, this);
        for (Pending item : pending.values()) {
            item.timeout.cancel();
            network.finishReservation(item.reservation.getId(), item.reservation.getPlayerId(), false);
        }
        pending.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!started) return;
        UUID playerId = event.getPlayer().getUniqueId();
        network.claimReservation(playerId, reservation -> deliver(playerId, reservation));
    }

    @Override public void onPluginMessageReceived(String channel, Player carrier, byte[] message) {
        if (!started || !SAME_SERVER_CHANNEL.equals(channel) || message == null) return;
        final UUID playerId;
        try {
            ByteArrayDataInput input = ByteStreams.newDataInput(message);
            playerId = UUID.fromString(input.readUTF());
        } catch (RuntimeException invalid) {
            plugin.getLogger().warning("Mensagem de entrada local invalida recebida do proxy.");
            return;
        }
        Player target = Bukkit.getPlayer(playerId);
        if (target == null || !target.isOnline()) return;
        network.claimReservation(playerId, reservation -> deliver(playerId, reservation));
    }

    private void deliver(UUID playerId, ReservationSnapshot reservation) {
        if (!started || reservation == null) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            network.finishReservation(reservation.getId(), playerId, false);
            return;
        }
        if (!"bedwars".equals(reservation.getMinigameId()) || pending.containsKey(playerId)) {
            reject(player, reservation, "Reserva invalida ou ja em processamento.");
            return;
        }
        int graceSeconds = Math.max(5, Math.min(120,
            plugin.getConfig().getInt("party-matchmaking.arrival-grace-seconds", 20)));
        if (reservation.getGroupId() != null && reservation.getGroupSize() > 1
                && reservation.getCreatedAtMillis() + graceSeconds * 1000L <= System.currentTimeMillis()) {
            reject(player, reservation, "A reserva da Party expirou.");
            return;
        }
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending current = pending.get(playerId);
            if (current != null && current.reservation.getId().equals(reservation.getId())) {
                pending.remove(playerId);
                matches.cancelPendingReservation(playerId);
                reject(Bukkit.getPlayer(playerId), reservation, "Tempo esgotado ao preparar a arena.");
            }
        }, Math.max(1L, Math.min(1800L,
            (reservation.getExpiresAtMillis() - System.currentTimeMillis()) / 50L)));
        pending.put(playerId, new Pending(reservation, timeout));
        try {
            matches.join(player, reservation.getArenaId(), reservation);
        } catch (RuntimeException error) {
            pending.remove(playerId);
            timeout.cancel();
            matches.cancelPendingReservation(playerId);
            reject(player, reservation, error.getMessage());
        }
    }

    @EventHandler public void onMatchLifecycle(MatchLifecycleEvent event) {
        if (event.getType() != MatchLifecycleEvent.Type.PLAYER_JOINED || event.getActorId() == null) return;
        UUID playerId = event.getActorId();
        Pending item = pending.get(playerId);
        if (item == null || !item.reservation.getArenaId().equals(event.getMatch().getArena().getId())
                || matches.getMatchForPlayer(playerId) != event.getMatch()) return;
        pending.remove(playerId);
        item.timeout.cancel();
        network.finishReservation(item.reservation.getId(), playerId, true);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Pending item = pending.remove(event.getPlayer().getUniqueId());
        if (item == null) return;
        item.timeout.cancel();
        matches.cancelPendingReservation(event.getPlayer().getUniqueId());
        network.finishReservation(item.reservation.getId(), item.reservation.getPlayerId(), false);
    }

    private void reject(Player player, ReservationSnapshot reservation, String reason) {
        network.finishReservation(reservation.getId(), reservation.getPlayerId(), false);
        if (player == null || !player.isOnline()) return;
        player.sendMessage("§cNao foi possivel entrar na arena: " + reason);
        String lobby = network.getLobbyServer();
        if (!lobby.isEmpty() && !matches.hasPlayer(player.getUniqueId())
                && !matches.hasPendingReconnect(player.getUniqueId())) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(lobby);
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        }
    }

    private void failPending(UUID playerId, String reason) {
        Pending item = pending.remove(playerId);
        if (item == null) return;
        item.timeout.cancel();
        matches.cancelPendingReservation(playerId);
        reject(Bukkit.getPlayer(playerId), item.reservation, reason);
    }

    private static final class Pending {
        final ReservationSnapshot reservation;
        final BukkitTask timeout;
        Pending(ReservationSnapshot reservation, BukkitTask timeout) {
            this.reservation = reservation;
            this.timeout = timeout;
        }
    }
}
