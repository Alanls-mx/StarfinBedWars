package com.starfinplugins.bedwars.network;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.stats.StatsSettings;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Publishes read-only arena and match metadata; never accesses JDBC on Bukkit's main thread. */
public final class NetworkRegistryService implements ManagedService, Listener {
    private final StarfinBedWarsPlugin plugin;
    private final ArenaService arenas;
    private final MatchEngineService matches;
    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicReference<NetworkSnapshot> pending = new AtomicReference<NetworkSnapshot>();
    private final AtomicBoolean draining = new AtomicBoolean();
    private NetworkSettings settings;
    private StatsSettings databaseSettings;
    private ExecutorService worker;
    private MySqlNetworkRepository repository;
    private BukkitTask heartbeat;
    private long lastWarning;

    public NetworkRegistryService(StarfinBedWarsPlugin plugin, ArenaService arenas,
                                  MatchEngineService matches) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.matches = matches;
    }

    @Override public String getName() { return "Catalogo BungeeCord/MySQL"; }

    @Override public void start() {
        settings = NetworkSettings.load(plugin);
        if (!settings.enabled) {
            plugin.getLogger().info("Catalogo de rede desativado em network.yml (modo local).");
            return;
        }
        databaseSettings = StatsSettings.load(plugin);
        if (!databaseSettings.enabled) {
            throw new IllegalStateException("network.yml exige stats.yml enabled: true e MySQL configurado.");
        }
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "StarfinBedWars-Network-IO");
            thread.setDaemon(true);
            return thread;
        });
        Bukkit.getPluginManager().registerEvents(this, plugin);
        heartbeat = Bukkit.getScheduler().runTaskTimer(plugin, this::captureAndPublish,
            1L, settings.refreshTicks);
        plugin.getLogger().info("Catalogo BungeeCord ativo: " + settings.serverId
            + " (instancia " + instanceId + ").");
    }

    @Override public void stop() {
        if (heartbeat != null) heartbeat.cancel();
        HandlerList.unregisterAll(this);
        pending.set(null);
        if (worker == null) return;
        worker.execute(() -> {
            try {
                if (repository != null) repository.markOffline(settings.serverId, instanceId);
            } catch (Exception error) {
                plugin.getLogger().warning("Falha ao marcar servidor offline; lease expirara em 30s.");
            }
        });
        worker.shutdown();
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                worker.shutdownNow();
                plugin.getLogger().warning("Catalogo de rede encerrado com operacoes pendentes; heartbeat expirara.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
        if (repository != null) repository.close();
        repository = null;
    }

    public boolean isEnabled() { return settings != null && settings.enabled; }
    public String getServerId() { return isEnabled() ? settings.serverId : "local"; }

    @EventHandler public void onMatch(MatchLifecycleEvent event) {
        if (!isEnabled()) return;
        // Events originate on Bukkit's main thread; the worker receives immutable copies only.
        captureAndPublish();
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (isEnabled()) Bukkit.getScheduler().runTask(plugin, this::captureAndPublish);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (isEnabled()) Bukkit.getScheduler().runTask(plugin, this::captureAndPublish);
    }

    /** Available for explicit publish/migration requests; still nonblocking for MySQL. */
    public void refresh() { if (isEnabled()) captureAndPublish(); }

    private void captureAndPublish() {
        if (worker == null || worker.isShutdown()) return;
        try {
            pending.set(snapshot());
            submitDrain();
        } catch (IOException | IllegalArgumentException error) {
            warn("Nao foi possivel ler arena local para catalogo: " + error.getMessage());
        }
    }

    private NetworkSnapshot snapshot() throws IOException {
        List<NetworkSnapshot.Arena> advertisements = new ArrayList<NetworkSnapshot.Arena>();
        List<NetworkSnapshot.Match> running = new ArrayList<NetworkSnapshot.Match>();
        List<NetworkSnapshot.Player> players = new ArrayList<NetworkSnapshot.Player>();
        for (ArenaDefinition arena : arenas.getArenas()) {
            GameMatch match = matches.getMatchForArena(arena.getId());
            boolean editing = arenas.isEditing(arena.getId());
            int players = match == null ? 0 : match.getParticipantList().size();
            String state = match == null
                ? (editing ? "EDITING" : arena.isEnabled() ? "AVAILABLE" : "DISABLED")
                : match.getState().name();
            boolean joinable = arena.isEnabled() && !editing
                && (match == null || match.getState() == MatchState.WAITING
                    || match.getState() == MatchState.STARTING)
                && players < arena.getMaximumPlayers();
            int teamSize = -1;
            for (TeamDefinition team : arena.getTeamList()) {
                if (teamSize == -1) teamSize = team.getCapacity();
                else if (teamSize != team.getCapacity()) teamSize = 0;
            }
            String mode = teamSize == 0 ? "MIXED" : teamSize == 1 ? "SOLO"
                : teamSize == 2 ? "DUO" : teamSize == 3 ? "TRIO"
                : teamSize == 4 ? "SQUAD" : "CUSTOM";
            advertisements.add(new NetworkSnapshot.Arena(arena.getId(),
                shortName(arena.getDisplayName()), arena.getSourceWorld(),
                arenas.getStoredConfiguration(arena), mode, arena.getGroup(), arena.getTeamList().size(), teamSize,
                arena.getGeneratorList().size(), arena.getMinimumPlayers(),
                arena.getMaximumPlayers(), players, arena.getCountdownSeconds(),
                arena.isEnabled(), editing, joinable, state,
                match == null ? null : match.getId().toString()));
            if (match != null) {
                running.add(new NetworkSnapshot.Match(match.getId().toString(), arena.getId(),
                    match.getState().name(), match.getWinnerTeamId(),
                    match.getEndReason() == null ? null : match.getEndReason().name(),
                    players, match.getConnectedCount(), arena.getMaximumPlayers(),
                    epoch(match.getStartedAt()), epoch(match.getEndedAt())));
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
            boolean spectator = false;
            if (match == null) {
                match = matches.getSpectatedMatch(player.getUniqueId());
                spectator = match != null;
            }
            String teamId = null;
            if (match != null && !spectator && match.getParticipant(player.getUniqueId()) != null) {
                teamId = match.getParticipant(player.getUniqueId()).getTeamId();
            }
            players.add(new NetworkSnapshot.Player(player.getUniqueId().toString(),
                player.getName(), match == null ? "LOBBY" : spectator ? "SPECTATOR" : "PLAYER",
                match == null ? null : match.getArena().getId(),
                match == null ? null : match.getId().toString(), teamId));
        }
        return new NetworkSnapshot(settings.serverId, instanceId, System.currentTimeMillis(),
            Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers(), advertisements, running, players);
    }

    private static long epoch(Instant instant) { return instant == null ? 0L : instant.toEpochMilli(); }

    private static String shortName(String value) {
        if (value == null) return "";
        return value.length() > 128 ? value.substring(0, 128) : value;
    }

    private void submitDrain() {
        if (!draining.compareAndSet(false, true)) return;
        try {
            worker.execute(() -> {
                try {
                    NetworkSnapshot update;
                    while ((update = pending.getAndSet(null)) != null) {
                        try {
                            if (repository == null) repository = new MySqlNetworkRepository();
                            repository.open(databaseSettings, settings);
                            repository.synchronize(update);
                        } catch (Exception error) {
                            // A fresh main-thread snapshot will retry on the next heartbeat.
                            warn("Catalogo MySQL indisponivel (" + error.getClass().getSimpleName()
                                + "). Nova tentativa no proximo heartbeat.");
                        }
                    }
                } finally {
                    draining.set(false);
                    if (pending.get() != null && !worker.isShutdown()) submitDrain();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException stopping) {
            draining.set(false);
        }
    }

    private synchronized void warn(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarning >= 60000L) {
            plugin.getLogger().warning(message);
            lastWarning = now;
        }
    }
}
