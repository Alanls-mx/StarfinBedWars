package com.starfinplugins.bedwars.domain.match;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.ranked.RankedLifecycleEvent;
import com.starfinplugins.bedwars.ranked.RankedAllocation;
import com.starfinplugins.bedwars.ranked.RankedApi;
import com.starfinplugins.bedwars.ranked.RankedRatingMath;
import com.starfinplugins.bedwars.ranked.RankedMatchSnapshot;
import com.starfinplugins.bedwars.ranked.RankedOutbox;
import com.starfinplugins.bedwars.ranked.RankedProfile;
import com.starfinplugins.bedwars.ranked.RankedRepository;
import com.starfinplugins.bedwars.ranked.RankedResult;
import com.starfinplugins.bedwars.ranked.RankedSettings;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.stats.MatchMode;
import com.starfinplugins.bedwars.stats.StatsSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import com.starfinplugins.minigames.api.ReservationSnapshot;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

/** Authorized admission + durable, asynchronous competitive match processing. */
public final class RankedService implements ManagedService, Listener, RankedApi {
    private final StarfinBedWarsPlugin plugin;
    private RankedSettings settings;
    private StatsSettings database;
    private RankedOutbox outbox;
    private RankedRepository repository;
    private ExecutorService worker;
    private ExecutorService journalWorker;
    private BukkitTask retry;
    private volatile boolean databaseReady;
    private final Map<UUID, RankedAllocation> pendingAllocations = new HashMap<UUID, RankedAllocation>();
    private final Map<UUID, RankedAllocation> admissionProofs = new HashMap<UUID, RankedAllocation>();
    private BukkitTask admissionTimeout;

    public RankedService(StarfinBedWarsPlugin plugin) { this.plugin = plugin; }
    @Override public String getName() { return "Classificacao competitiva"; }
    @Override public void start() {
        try { settings = RankedSettings.load(plugin); database = StatsSettings.load(plugin); }
        catch (RuntimeException invalid) {
            plugin.getLogger().severe("ranked.yml invalido; modo competitivo indisponivel ("
                + invalid.getClass().getSimpleName() + ").");
            return;
        }
        outbox = new RankedOutbox(plugin.getDataFolder().toPath().resolve("ranked-pending"));
        if (!database.enabled) {
            plugin.getLogger().warning("Competitivo requer stats.yml e MySQL ativos.");
            return;
        }
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "StarfinBedWars-Ranked-IO");
            thread.setDaemon(true); return thread;
        });
        journalWorker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "StarfinBedWars-Ranked-Journal");
            thread.setDaemon(true); return thread;
        });
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getServicesManager().register(RankedApi.class, this, plugin, ServicePriority.Normal);
        worker.execute(this::flush);
        retry = Bukkit.getScheduler().runTaskTimer(plugin, () -> worker.execute(this::flush), 400L, 400L);
        admissionTimeout = Bukkit.getScheduler().runTaskTimer(plugin, this::expireAdmissions, 20L, 20L);
    }
    @Override public void stop() {
        if (retry != null) retry.cancel();
        if (admissionTimeout != null) admissionTimeout.cancel();
        pendingAllocations.clear(); admissionProofs.clear();
        HandlerList.unregisterAll(this);
        Bukkit.getServicesManager().unregister(RankedApi.class, this);
        databaseReady = false;
        if (journalWorker != null) {
            journalWorker.shutdown();
            try {
                if (!journalWorker.awaitTermination(10, TimeUnit.SECONDS)) {
                    plugin.getLogger().severe("Escritas competitivas ainda pendentes no encerramento.");
                    journalWorker.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); journalWorker.shutdownNow();
            }
        }
        if (worker != null) {
            worker.shutdown();
            try {
                if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Fila competitiva pendente preservada para retomada.");
                    worker.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); worker.shutdownNow();
            }
        }
        if (repository != null) repository.close();
    }
    public RankedSettings getSettings() { return settings; }
    public boolean isDatabaseReady() { return databaseReady; }
    public boolean isOperational() { return settings != null && settings.enabled && databaseReady; }
    public boolean isRankedArena(String group) {
        return "ranked".equalsIgnoreCase(group)
            || (settings != null && settings.arenaGroups.contains(group));
    }

    /** Future matchmaking adapter calls this on the main thread after its roster is fixed. */
    @Override public void authorize(GameMatch match, Plugin issuer) {
        RankedAllocation proof = match == null ? null : admissionProofs.get(match.getId());
        if (!Bukkit.isPrimaryThread() || issuer == null || !issuer.isEnabled()
                || settings == null || !settings.authorizedPlugins.contains(issuer.getName())
                || !isOperational() || match == null
                || plugin.getMatchEngine().getMatch(match.getId()) != match
                || !settings.modes.contains(MatchMode.of(match.getArena()))
                || !isRankedArena(match.getArena().getGroup())
                || match.getEligibleTeams().size() < 2
                || match.getConnectedCount() < match.getArena().getMinimumPlayers()
                || match.getConnectedCount() != match.getParticipantList().size()
                || plugin.getMatchEngine().isOpeningSpawns(match.getArena().getId())
                || proof == null || !proof.context.equals(settings.context)
                || !proof.arenaId.equals(match.getArena().getId())
                || !proof.mode.equals(MatchMode.of(match.getArena()))
                || proof.expiresAt <= System.currentTimeMillis()
                || !sameRoster(match, proof))
            throw new IllegalStateException("Admissao competitiva nao autorizada ou indisponivel");
        match.authorizeRanked(settings.context);
        admissionProofs.remove(match.getId());
        pendingAllocations.remove(proof.id);
        plugin.getMatchEngine().rankedAuthorized(match);
    }

    public void inspectReservation(ReservationSnapshot reservation,
            java.util.function.Consumer<RankedAllocation> callback) {
        if (reservation == null || !reservation.isRanked() || !isOperational()
                || worker == null || worker.isShutdown()) { callback.accept(null); return; }
        worker.execute(() -> {
            RankedAllocation found = null;
            try {
                ensureOpen();
                found = repository.inspectAllocation(reservation,
                    plugin.getNetworkRegistry().getServerId(), plugin.getNetworkRegistry().getInstanceId());
            } catch (SQLException error) { databaseReady = false; }
            final RankedAllocation result = found;
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                if (result != null) pendingAllocations.put(result.id, result);
                callback.accept(result);
            });
        });
    }

    public void onReservationAdmitted(ReservationSnapshot reservation, GameMatch match) {
        if (reservation == null || !reservation.isRanked() || match == null
                || worker == null || worker.isShutdown()) return;
        RankedAllocation allocation = pendingAllocations.get(reservation.getRankedAllocationId());
        if (allocation == null || !sameAdmittedPlayer(match, allocation, reservation)) {
            plugin.getMatchEngine().cancelRankedFormation(match); return;
        }
        worker.execute(() -> {
            boolean all;
            try {
                ensureOpen();
                all = repository.markAdmitted(allocation.id, reservation.getId(), reservation.getPlayerId());
            } catch (SQLException error) { databaseReady = false; return; }
            if (all && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                GameMatch current = plugin.getMatchEngine().getMatchForArena(allocation.arenaId);
                if (current != match || !sameRoster(match, allocation)) {
                    plugin.getMatchEngine().cancelRankedFormation(match); return;
                }
                admissionProofs.put(match.getId(), allocation);
                try {
                    Plugin issuer = Bukkit.getPluginManager().getPlugin("StarfinMinigames");
                    if (issuer == null || !issuer.isEnabled()) throw new IllegalStateException("Emissor ausente");
                    issuer.getClass().getMethod("authorizeRanked", Object.class).invoke(issuer, match);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    admissionProofs.remove(match.getId());
                    plugin.getLogger().warning("Admissao ranqueada recusada para " + match.getId()
                        + " (" + error.getClass().getSimpleName() + ").");
                    plugin.getMatchEngine().cancelRankedFormation(match);
                }
            });
        });
    }
    private boolean sameAdmittedPlayer(GameMatch match, RankedAllocation allocation,
                                        ReservationSnapshot reservation) {
        return allocation.arenaId.equals(match.getArena().getId())
            && allocation.teams.containsKey(reservation.getPlayerId())
            && allocation.teams.get(reservation.getPlayerId()).equals(reservation.getTeamId());
    }
    private boolean sameRoster(GameMatch match, RankedAllocation allocation) {
        if (match.getParticipantList().size() != allocation.teams.size()
                || !new HashSet<UUID>(match.getParticipants().keySet()).equals(allocation.teams.keySet()))
            return false;
        for (GameParticipant member : match.getParticipantList())
            if (!allocation.teams.get(member.getPlayerId()).equals(member.getTeamId())) return false;
        return true;
    }
    private void expireAdmissions() {
        long now = System.currentTimeMillis();
        for (RankedAllocation allocation : new ArrayList<RankedAllocation>(pendingAllocations.values())) {
            if (allocation.expiresAt > now) continue;
            pendingAllocations.remove(allocation.id);
            GameMatch match = plugin.getMatchEngine().getMatchForArena(allocation.arenaId);
            if (match != null && match.getType() != MatchType.RANKED)
                plugin.getMatchEngine().cancelRankedFormation(match);
        }
    }

    @Override public CompletableFuture<RankedProfile> getCompetitiveProfile(UUID player, String mode) {
        String canonical = MatchMode.persisted(mode);
        if (player == null || worker == null || worker.isShutdown())
            return failed(new IllegalStateException("Banco competitivo indisponivel"));
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureOpen(); return repository.profile(player, canonical, settings.context, settings.initial);
            } catch (SQLException error) { databaseReady = false; throw new IllegalStateException("Consulta competitiva falhou", error); }
        }, worker);
    }
    @Override public CompletableFuture<Integer> getRating(UUID player, String mode) {
        return getCompetitiveProfile(player, mode).thenApply(profile -> profile.rating);
    }
    @Override public CompletableFuture<String> getDivision(UUID player, String mode) {
        return getCompetitiveProfile(player, mode).thenApply(profile ->
            profile.isPlaced(settings.placements) ? settings.division(profile.rating).id : "placement");
    }
    @Override public CompletableFuture<Integer> getPlacementProgress(UUID player, String mode) {
        return getCompetitiveProfile(player, mode).thenApply(profile -> profile.placementGames);
    }
    @Override public Map<UUID, Integer> calculateRatingChanges(RankedMatchSnapshot snapshot,
                                                                 Map<UUID, Integer> ratings) {
        return RankedRatingMath.changes(snapshot, ratings, snapshot.kFactor);
    }
    public CompletableFuture<RankedProfile> getCompetitiveProfile(String identifier, String mode) {
        String canonical = MatchMode.persisted(mode);
        if (identifier == null || worker == null || worker.isShutdown())
            return failed(new IllegalStateException("Banco competitivo indisponivel"));
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureOpen();
                UUID player = repository.resolvePlayer(identifier);
                return player == null ? null : repository.profile(player, canonical,
                    settings.context, settings.initial);
            } catch (SQLException error) { databaseReady = false; throw new IllegalStateException("Consulta competitiva falhou", error); }
        }, worker);
    }
    @Override public CompletableFuture<List<RankedResult>> getCompetitiveMatchResult(UUID matchId) {
        if (matchId == null || worker == null || worker.isShutdown())
            return failed(new IllegalStateException("Banco competitivo indisponivel"));
        return CompletableFuture.supplyAsync(() -> {
            try { ensureOpen(); return repository.results(matchId); }
            catch (SQLException error) { databaseReady = false; throw new IllegalStateException("Consulta competitiva falhou", error); }
        }, worker);
    }
    private static <T> CompletableFuture<T> failed(RuntimeException error) {
        CompletableFuture<T> future = new CompletableFuture<T>(); future.completeExceptionally(error); return future;
    }

    @EventHandler public void onMatch(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        if (match.getType() != MatchType.RANKED || worker == null) return;
        if (event.getType() == MatchLifecycleEvent.Type.STARTED) {
            Bukkit.getPluginManager().callEvent(new RankedLifecycleEvent(
                RankedLifecycleEvent.Type.STARTED, match.getId(), null, null));
        } else if (event.getType() == MatchLifecycleEvent.Type.ENDED
                && (match.getEndReason() == MatchEndReason.VICTORY
                    || (match.getEndReason() == MatchEndReason.DRAW && settings.allowDraws))) {
            RankedMatchSnapshot snapshot;
            try { snapshot = snapshot(match); }
            catch (RuntimeException invalid) {
                plugin.getLogger().log(Level.SEVERE, "Resultado competitivo invalido " + match.getId(), invalid);
                return;
            }
            journalWorker.execute(() -> {
                try {
                    outbox.write(snapshot);
                    if (worker != null && !worker.isShutdown()) worker.execute(this::flush);
                }
                catch (IOException error) {
                    plugin.getLogger().log(Level.SEVERE,
                        "Falha ao registrar resultado competitivo " + snapshot.matchId, error);
                }
            });
        }
    }
    private RankedMatchSnapshot snapshot(GameMatch match) {
        List<RankedMatchSnapshot.Member> members = new ArrayList<RankedMatchSnapshot.Member>();
        for (GameParticipant member : match.getParticipantList()) {
            Player player = Bukkit.getPlayer(member.getPlayerId());
            members.add(new RankedMatchSnapshot.Member(member.getPlayerId(),
                player == null ? "" : player.getName(), member.getTeamId(),
                member.getState() == ParticipantState.ABANDONED));
        }
        String server = plugin.getNetworkRegistry() == null ? "local"
            : plugin.getNetworkRegistry().getServerId();
        String instance = plugin.getNetworkRegistry() == null ? "local"
            : plugin.getNetworkRegistry().getInstanceId();
        return new RankedMatchSnapshot(match.getId(), MatchMode.of(match.getArena()),
            match.getArena().getId(), server, instance, match.getCompetitiveContext(),
            match.getWinnerTeamId(), match.getStartedAt().toEpochMilli(),
            match.getEndedAt().toEpochMilli(), match.getEndReason() == MatchEndReason.DRAW, members,
            settings.initial, settings.minimum, settings.kFactor);
    }
    private void ensureOpen() throws SQLException {
        if (repository == null) repository = new RankedRepository();
        if (!repository.isOpen()) repository.open(database);
        repository.ping();
        databaseReady = true;
    }
    private void flush() {
        try {
            ensureOpen();
            if (plugin.getNetworkRegistry() != null && plugin.getNetworkRegistry().isEnabled())
                repository.publishPolicy(plugin.getNetworkRegistry().getServerId(),
                    plugin.getNetworkRegistry().getInstanceId(), settings,
                    settings.authorizedPlugins.contains("StarfinMinigames")
                        && Bukkit.getPluginManager().getPlugin("StarfinMinigames") != null);
            for (Path path : outbox.pending()) {
                try {
                    RankedMatchSnapshot match = outbox.read(path);
                    RankedRepository.Applied applied = repository.apply(match, settings);
                    outbox.acknowledge(path);
                    if (applied.newResult && plugin.isEnabled())
                        Bukkit.getScheduler().runTask(plugin, () -> publish(match, applied));
                } catch (IOException invalid) {
                    plugin.getLogger().log(Level.SEVERE,
                        "Fila competitiva invalida preservada: " + path.getFileName(), invalid);
                }
            }
        } catch (SQLException | IOException failed) {
            databaseReady = false;
            plugin.getLogger().warning("Processamento competitivo pendente ("
                + failed.getClass().getSimpleName() + "); fila local preservada.");
            if (repository != null) { repository.close(); repository = null; }
        }
    }
    private void publish(RankedMatchSnapshot match, RankedRepository.Applied applied) {
        Bukkit.getPluginManager().callEvent(new RankedLifecycleEvent(
            RankedLifecycleEvent.Type.COMPLETED, match.matchId, null, null));
        for (RankedResult result : applied.results) {
            Bukkit.getPluginManager().callEvent(new RankedLifecycleEvent(
                RankedLifecycleEvent.Type.RATING_UPDATED, match.matchId, result.playerId, result));
            int before = result.before, after = result.after;
            if (!settings.division(before).id.equals(settings.division(after).id))
                Bukkit.getPluginManager().callEvent(new RankedLifecycleEvent(
                    RankedLifecycleEvent.Type.DIVISION_CHANGED, match.matchId, result.playerId, result));
            if (applied.placementCompletions.contains(result.playerId))
                Bukkit.getPluginManager().callEvent(new RankedLifecycleEvent(
                    RankedLifecycleEvent.Type.PLACEMENT_COMPLETED, match.matchId,
                    result.playerId, result));
        }
    }
}
