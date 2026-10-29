package com.starfinplugins.bedwars.domain.match;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.ranked.RankedLifecycleEvent;
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
    private BukkitTask retry;
    private volatile boolean databaseReady;

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
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getServicesManager().register(RankedApi.class, this, plugin, ServicePriority.Normal);
        worker.execute(this::flush);
        retry = Bukkit.getScheduler().runTaskTimer(plugin, () -> worker.execute(this::flush), 400L, 400L);
    }
    @Override public void stop() {
        if (retry != null) retry.cancel();
        HandlerList.unregisterAll(this);
        Bukkit.getServicesManager().unregister(RankedApi.class, this);
        databaseReady = false;
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

    /** Future matchmaking adapter calls this on the main thread after its roster is fixed. */
    @Override public void authorize(GameMatch match, Plugin issuer) {
        if (!Bukkit.isPrimaryThread() || issuer == null || !issuer.isEnabled()
                || settings == null || !settings.authorizedPlugins.contains(issuer.getName())
                || !isOperational() || match == null
                || plugin.getMatchEngine().getMatch(match.getId()) != match
                || !settings.modes.contains(MatchMode.of(match.getArena()))
                || match.getEligibleTeams().size() < 2
                || match.getConnectedCount() < match.getArena().getMinimumPlayers()
                || match.getConnectedCount() != match.getParticipantList().size()
                || plugin.getMatchEngine().isOpeningSpawns(match.getArena().getId()))
            throw new IllegalStateException("Admissao competitiva nao autorizada ou indisponivel");
        match.authorizeRanked(settings.context);
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
            worker.execute(() -> {
                try { outbox.write(snapshot); flush(); }
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
