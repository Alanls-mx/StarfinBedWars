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
import com.starfinplugins.bedwars.ranked.RankedSeason;
import com.starfinplugins.bedwars.ranked.RankedSeasonRepository;
import com.starfinplugins.bedwars.ranked.RankedSeasonSettings;
import com.starfinplugins.bedwars.ranked.RankedStanding;
import com.starfinplugins.bedwars.ranked.RankedSeasonReward;
import com.starfinplugins.bedwars.ranked.RankedSeasonLifecycleEvent;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import com.starfinplugins.minigames.api.ReservationSnapshot;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Authorized admission + durable, asynchronous competitive match processing. */
public final class RankedService implements ManagedService, Listener, RankedApi {
    private final StarfinBedWarsPlugin plugin;
    private RankedSettings settings;
    private RankedSeasonSettings seasonSettings;
    private volatile RankedSeason season;
    private volatile long networkPolicyVersion;
    private volatile boolean policyInitialized;
    private RankedSeasonRepository seasons;
    private final AtomicInteger activeMatches = new AtomicInteger();
    private final AtomicInteger journalingResults = new AtomicInteger();
    private final AtomicInteger failedJournals = new AtomicInteger();
    private StatsSettings database;
    private RankedOutbox outbox;
    private RankedRepository repository;
    private ExecutorService worker;
    private ExecutorService journalWorker;
    private BukkitTask retry;
    private BukkitTask snapshotTask;
    private final Map<UUID, Map<String, String>> presentationCache =
        new ConcurrentHashMap<UUID, Map<String, String>>();
    private final Map<String, CachedLeaderboard> leaderboardCache =
        new ConcurrentHashMap<String, CachedLeaderboard>();
    private static final class CachedLeaderboard {
        final List<RankedStanding> rows; final long expiresAt;
        CachedLeaderboard(List<RankedStanding> rows, long expiresAt) {
            this.rows = rows; this.expiresAt = expiresAt;
        }
    }
    private volatile boolean databaseReady;
    private final Map<UUID, RankedAllocation> pendingAllocations = new HashMap<UUID, RankedAllocation>();
    private final Map<UUID, RankedAllocation> admissionProofs = new HashMap<UUID, RankedAllocation>();
    private BukkitTask admissionTimeout;

    public RankedService(StarfinBedWarsPlugin plugin) { this.plugin = plugin; }
    @Override public String getName() { return "Classificacao competitiva"; }
    @Override public void start() {
        try { settings = RankedSettings.load(plugin); seasonSettings = RankedSeasonSettings.load(plugin);
            database = StatsSettings.load(plugin); }
        catch (RuntimeException invalid) {
            settings = null;
            seasonSettings = null;
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
        snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getConfig().getBoolean("scoreboard.ranked.enabled", false)
                    && !plugin.getConfig().getBoolean("tab.ranked.enabled", false)) return;
            for (Player online : Bukkit.getOnlinePlayers()) {
                GameMatch match = plugin.getMatchEngine().getMatchForPlayer(online.getUniqueId());
                refreshSnapshot(online.getUniqueId(),
                    match == null ? "solo" : MatchMode.of(match.getArena()));
            }
        }, 100L, 1200L);
        admissionTimeout = Bukkit.getScheduler().runTaskTimer(plugin, this::expireAdmissions, 20L, 20L);
    }
    @Override public void stop() {
        if (retry != null) retry.cancel();
        if (snapshotTask != null) snapshotTask.cancel();
        presentationCache.clear();
        leaderboardCache.clear();
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
    public RankedSeason getCachedSeason() { return season; }
    public String contextForDisplay() { return season != null ? season.id
        : networkPolicyVersion == 0 && settings != null ? settings.context : "-"; }
    public RankedSeasonSettings getSeasonSettings() { return seasonSettings; }
    public void syncNow() {
        if (worker != null && !worker.isShutdown()) worker.execute(this::flush);
    }
    public interface SeasonQuery<T> { T run(RankedSeasonRepository repository) throws SQLException; }
    public <T> CompletableFuture<T> seasonQuery(SeasonQuery<T> query) {
        if (worker == null || worker.isShutdown()) return failed(new IllegalStateException("Banco indisponivel"));
        return CompletableFuture.supplyAsync(() -> {
            try { ensureOpen(); return query.run(seasons); }
            catch (SQLException error) { throw new IllegalStateException("Consulta sazonal falhou", error); }
        }, worker);
    }
    public boolean isDatabaseReady() { return databaseReady; }
    public boolean isOperational() { return settings != null && settings.enabled && databaseReady
        && (networkPolicyVersion == 0 || (season != null && season.state == RankedSeason.State.ACTIVE
                && System.currentTimeMillis() < season.endsAt)); }
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
                || proof == null || !proof.context.equals(competitiveContext())
                || !proof.arenaId.equals(match.getArena().getId())
                || !proof.mode.equals(MatchMode.of(match.getArena()))
                || proof.expiresAt <= System.currentTimeMillis()
                || !sameRoster(match, proof))
            throw new IllegalStateException("Admissao competitiva nao autorizada ou indisponivel");
        match.authorizeRanked(competitiveContext());
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
        refreshSnapshot(reservation.getPlayerId(), MatchMode.of(match.getArena()));
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
                ensureOpen(); return repository.profile(player, canonical, competitiveContext(), initialRating());
            } catch (SQLException error) { databaseReady = false; throw new IllegalStateException("Consulta competitiva falhou", error); }
        }, worker);
    }
    @Override public CompletableFuture<Integer> getRating(UUID player, String mode) {
        return getCompetitiveProfile(player, mode).thenApply(profile -> profile.rating);
    }
    @Override public CompletableFuture<String> getDivision(UUID player, String mode) {
        return getCompetitiveProfile(player, mode).thenCompose(profile ->
            profile.isPlaced(season != null && profile.context.equals(season.id)
                ? season.placementRequired : settings.placements)
                ? seasonQuery(repo -> repo.division(profile.context, profile.rating, settings).id)
                : CompletableFuture.completedFuture("placement"));
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
                    competitiveContext(), initialRating());
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
    @Override public CompletableFuture<List<RankedResult>> getCompetitiveHistory(UUID playerId,
            String context, int page) {
        if (playerId == null || context == null || !context.matches("[a-z0-9_-]{1,32}")
                || worker == null || worker.isShutdown())
            return failed(new IllegalArgumentException("Jogador ou contexto invalido"));
        return CompletableFuture.supplyAsync(() -> {
            try { ensureOpen(); return repository.playerResults(playerId, context, page); }
            catch (SQLException error) { throw new IllegalStateException("Historico indisponivel", error); }
        }, worker);
    }
    @Override public CompletableFuture<RankedSeason> getActiveSeason() {
        return seasonQuery(RankedSeasonRepository::active);
    }
    @Override public CompletableFuture<RankedSeason> getSeason(String id) {
        return seasonQuery(repository -> repository.find(id));
    }
    @Override public CompletableFuture<List<RankedSeason>> getSeasons(int limit, int page) {
        return seasonQuery(repository -> repository.list(Math.max(1, Math.min(50, limit)),
            Math.max(0, page - 1) * Math.max(1, Math.min(50, limit))));
    }
    @Override public CompletableFuture<List<RankedStanding>> getLeaderboard(String seasonId,
            String mode, int pageSize, int page) {
        return getLeaderboard(seasonId, mode, null, pageSize, page);
    }
    @Override public CompletableFuture<List<RankedStanding>> getLeaderboard(String seasonId,
            String mode, String divisionId, int pageSize, int page) {
        String canonical = MatchMode.persisted(mode);
        String key = seasonId + "|" + canonical + "|" + (divisionId == null ? "" : divisionId)
            + "|" + pageSize + "|" + page;
        CachedLeaderboard cached = leaderboardCache.get(key);
        if (cached != null && cached.expiresAt > System.currentTimeMillis())
            return CompletableFuture.completedFuture(cached.rows);
        return seasonQuery(repository -> {
            List<RankedStanding> rows = repository.leaderboard(seasonId, canonical,
                divisionId, pageSize, page);
            leaderboardCache.put(key, new CachedLeaderboard(rows, System.currentTimeMillis()
                + (seasonSettings == null ? 20 : seasonSettings.rankingCacheSeconds) * 1000L));
            return rows;
        });
    }
    @Override public CompletableFuture<RankedStanding> getRankedPosition(UUID playerId,
            String seasonId, String mode) {
        String canonical = MatchMode.persisted(mode);
        return seasonQuery(repository -> repository.position(playerId, seasonId, canonical));
    }
    @Override public CompletableFuture<List<RankedStanding>> getSeasonHistory(UUID playerId, int page) {
        return seasonQuery(repository -> repository.history(playerId, 36, page));
    }
    @Override public CompletableFuture<List<RankedSeasonReward>> getSeasonRewards(UUID playerId,
            String seasonId) {
        return seasonQuery(repository -> repository.rewards(playerId, seasonId));
    }
    @Override public CompletableFuture<Boolean> claimSeasonReward(UUID playerId, String seasonId,
            String mode, String rewardId) {
        String canonical = MatchMode.persisted(mode);
        return seasonQuery(repository -> repository.claim(playerId, seasonId, canonical, rewardId))
            .thenApply(claimed -> {
                if (claimed && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.getPluginManager().callEvent(new RankedSeasonLifecycleEvent(
                        RankedSeasonLifecycleEvent.Type.REWARD_CLAIMED, seasonId,
                        networkPolicyVersion, playerId, rewardId)));
                return claimed;
            });
    }
    private static <T> CompletableFuture<T> failed(RuntimeException error) {
        CompletableFuture<T> future = new CompletableFuture<T>(); future.completeExceptionally(error); return future;
    }

    @EventHandler public void onMatch(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        if (match.getType() != MatchType.RANKED || worker == null) return;
        if (event.getType() == MatchLifecycleEvent.Type.STARTED) {
            activeMatches.incrementAndGet();
            for (GameParticipant participant : match.getParticipantList())
                refreshSnapshot(participant.getPlayerId(), MatchMode.of(match.getArena()));
            Bukkit.getPluginManager().callEvent(new RankedLifecycleEvent(
                RankedLifecycleEvent.Type.STARTED, match.getId(), null, null));
        } else if (event.getType() == MatchLifecycleEvent.Type.ENDED
                && (match.getEndReason() == MatchEndReason.VICTORY
                    || (match.getEndReason() == MatchEndReason.DRAW && settings.allowDraws))) {
            activeMatches.updateAndGet(value -> Math.max(0, value - 1));
            journalingResults.incrementAndGet();
            RankedMatchSnapshot snapshot;
            try { snapshot = snapshot(match); }
            catch (RuntimeException invalid) {
                journalingResults.decrementAndGet();
                plugin.getLogger().log(Level.SEVERE, "Resultado competitivo invalido " + match.getId(), invalid);
                return;
            }
            for (RankedMatchSnapshot.Member member : snapshot.members) {
                Player player = Bukkit.getPlayer(member.playerId);
                if (player != null) {
                    String language = plugin.getStatistics().getLanguage(member.playerId);
                    player.sendMessage("§7" + ("en_US".equals(language)
                        ? "Your competitive result is being processed."
                        : "es_ES".equals(language) ? "Tu resultado competitivo se esta procesando."
                        : "Seu resultado competitivo esta sendo processado."));
                }
            }
            journalWorker.execute(() -> {
                try {
                    outbox.write(snapshot);
                    if (worker != null && !worker.isShutdown()) worker.execute(this::flush);
                }
                catch (IOException error) {
                    failedJournals.incrementAndGet();
                    plugin.getLogger().log(Level.SEVERE,
                        "Falha ao registrar resultado competitivo " + snapshot.matchId, error);
                } finally { journalingResults.decrementAndGet(); }
            });
        } else if (event.getType() == MatchLifecycleEvent.Type.ENDED) {
            activeMatches.updateAndGet(value -> Math.max(0, value - 1));
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
            initialRating(), settings.minimum, settings.kFactor);
    }
    private void ensureOpen() throws SQLException {
        if (repository == null) repository = new RankedRepository();
        if (!repository.isOpen()) repository.open(database);
        if (seasons == null) seasons = new RankedSeasonRepository(repository);
        repository.ping();
        databaseReady = true;
    }
    private void flush() {
        try {
            ensureOpen();
            long previousVersion = networkPolicyVersion;
            RankedSeason previousSeason = season;
            if (seasonSettings.enabled) {
                try { seasons.tick(seasonSettings); }
                catch (IllegalStateException deferred) {
                    plugin.getLogger().fine("Transicao sazonal pendente: " + deferred.getMessage());
                }
            }
            season = seasons.active();
            networkPolicyVersion = seasons.controlVersion();
            if (networkPolicyVersion != previousVersion) leaderboardCache.clear();
            if (policyInitialized && networkPolicyVersion != previousVersion && plugin.isEnabled()) {
                RankedSeason changed = season == null && previousSeason != null
                    ? seasons.find(previousSeason.id) : season;
                if (changed != null) {
                    RankedSeasonLifecycleEvent.Type type = changed.state == RankedSeason.State.ACTIVE
                        ? RankedSeasonLifecycleEvent.Type.STARTED
                        : changed.state == RankedSeason.State.CLOSING
                            ? RankedSeasonLifecycleEvent.Type.CLOSING
                            : changed.state == RankedSeason.State.FINISHED
                                ? RankedSeasonLifecycleEvent.Type.FINISHED : null;
                    if (type != null) Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.getPluginManager().callEvent(new RankedSeasonLifecycleEvent(
                            type, changed.id, changed.version, null, null)));
                }
            }
            policyInitialized = true;
            if (plugin.getNetworkRegistry() != null && plugin.getNetworkRegistry().isEnabled())
                repository.publishPolicy(plugin.getNetworkRegistry().getServerId(),
                    plugin.getNetworkRegistry().getInstanceId(), settings,
                    settings.authorizedPlugins.contains("StarfinMinigames")
                        && Bukkit.getPluginManager().getPlugin("StarfinMinigames") != null
                        && (networkPolicyVersion == 0 || season != null), season,
                    outbox.pending().size() + journalingResults.get() + failedJournals.get(),
                    activeMatches.get());
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
            if (plugin.getNetworkRegistry() != null && plugin.getNetworkRegistry().isEnabled())
                repository.publishPolicy(plugin.getNetworkRegistry().getServerId(),
                    plugin.getNetworkRegistry().getInstanceId(), settings,
                    settings.authorizedPlugins.contains("StarfinMinigames")
                        && Bukkit.getPluginManager().getPlugin("StarfinMinigames") != null
                        && (networkPolicyVersion == 0 || season != null), season,
                    outbox.pending().size() + journalingResults.get() + failedJournals.get(),
                    activeMatches.get());
        } catch (SQLException | IOException failed) {
            databaseReady = false;
            plugin.getLogger().warning("Processamento competitivo pendente ("
                + failed.getClass().getSimpleName() + "); fila local preservada.");
            if (repository != null) { repository.close(); repository = null; }
            seasons = null;
        }
    }
    private String competitiveContext() { return season != null ? season.id : settings.context; }
    private int initialRating() { return season != null ? season.initialRating : settings.initial; }
    public int placementRequired() { return season != null ? season.placementRequired : settings.placements; }
    public Map<String, String> presentation(UUID player, String mode) {
        Map<String, String> cached = presentationCache.get(player);
        Map<String, String> tokens = new HashMap<String, String>();
        String context = season == null ? networkPolicyVersion == 0 ? settings.context : "-" : season.name;
        tokens.put("ranked_season", context);
        tokens.put("ranked_mode", mode == null ? "-" : mode);
        tokens.put("ranked_division", "-"); tokens.put("ranked_rating", "-");
        tokens.put("ranked_position", "-"); tokens.put("ranked_placement", "-");
        tokens.put("ranked_season_remaining", season == null ? "-"
            : Math.max(0, (season.endsAt - System.currentTimeMillis()) / 3600000L) + "h");
        if (cached != null && mode != null && mode.equals(cached.get("ranked_mode"))
                && context.equals(cached.get("ranked_season"))) tokens.putAll(cached);
        return tokens;
    }
    private void refreshSnapshot(UUID player, String mode) {
        if (worker == null || worker.isShutdown() || !databaseReady) return;
        worker.execute(() -> {
            try {
                ensureOpen();
                RankedSeason active = season;
                if (active == null || !active.modes.contains(mode)) return;
                RankedProfile profile = repository.profile(player, mode, active.id, active.initialRating);
                RankedStanding standing = seasons.position(player, active.id, mode);
                Map<String, String> tokens = new HashMap<String, String>();
                tokens.put("ranked_season", active.name); tokens.put("ranked_mode", mode);
                tokens.put("ranked_division", profile.isPlaced(active.placementRequired)
                    ? seasons.division(active.id, profile.rating, settings).name : "Posicionamento");
                tokens.put("ranked_rating", Integer.toString(profile.rating));
                tokens.put("ranked_position", standing == null ? "-" : Long.toString(standing.position));
                tokens.put("ranked_placement", Math.min(active.placementRequired, profile.placementGames)
                    + "/" + active.placementRequired);
                tokens.put("ranked_season_remaining",
                    Math.max(0, (active.endsAt - System.currentTimeMillis()) / 3600000L) + "h");
                presentationCache.put(player, Collections.unmodifiableMap(tokens));
            } catch (SQLException error) { databaseReady = false; }
        });
    }
    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) {
        refreshSnapshot(event.getPlayer().getUniqueId(), "solo");
    }
    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) {
        presentationCache.remove(event.getPlayer().getUniqueId());
    }
    private void publish(RankedMatchSnapshot match, RankedRepository.Applied applied) {
        leaderboardCache.keySet().removeIf(key -> key.startsWith(match.context + "|"));
        Bukkit.getPluginManager().callEvent(new RankedLifecycleEvent(
            RankedLifecycleEvent.Type.COMPLETED, match.matchId, null, null));
        for (RankedResult result : applied.results) {
            Bukkit.getPluginManager().callEvent(new RankedLifecycleEvent(
                RankedLifecycleEvent.Type.RATING_UPDATED, match.matchId, result.playerId, result));
            RankedSettings.Division oldDivision = applied.beforeDivisions.get(result.playerId);
            RankedSettings.Division newDivision = applied.afterDivisions.get(result.playerId);
            boolean changed = oldDivision != null && newDivision != null
                && !oldDivision.id.equals(newDivision.id);
            if (changed)
                Bukkit.getPluginManager().callEvent(new RankedLifecycleEvent(
                    RankedLifecycleEvent.Type.DIVISION_CHANGED, match.matchId, result.playerId, result));
            if (applied.placementCompletions.contains(result.playerId))
                Bukkit.getPluginManager().callEvent(new RankedLifecycleEvent(
                    RankedLifecycleEvent.Type.PLACEMENT_COMPLETED, match.matchId,
                    result.playerId, result));
            Player player = Bukkit.getPlayer(result.playerId);
            if (player == null) continue;
            String language = plugin.getStatistics().getLanguage(result.playerId);
            boolean won = "WIN".equals(result.outcome);
            String outcome = "en_US".equals(language) ? (won ? "RANKED VICTORY" : "RANKED RESULT")
                : "es_ES".equals(language) ? (won ? "VICTORIA CLASIFICATORIA" : "RESULTADO CLASIFICATORIO")
                : won ? "VITORIA RANQUEADA" : "RESULTADO RANQUEADO";
            player.sendMessage("§b" + outcome + " §7| MMR §f" + result.before + " §7→ §f"
                + result.after + " §7(" + (result.change >= 0 ? "§a+" : "§c") + result.change + "§7)");
            if (newDivision != null)
                player.sendMessage("§7" + ("en_US".equals(language) ? "Division" :
                    "es_ES".equals(language) ? "Division" : "Divisao") + ": "
                    + ChatColor.translateAlternateColorCodes('&', newDivision.color + newDivision.name));
            if (applied.placementCompletions.contains(result.playerId))
                player.sendMessage("§a" + ("en_US".equals(language) ? "Placement complete!" :
                    "es_ES".equals(language) ? "Posicionamiento completo!" : "Posicionamento concluido!"));
            if (changed) {
                boolean promoted = newDivision.minimum > oldDivision.minimum;
                player.sendMessage((promoted ? "§a" : "§e")
                    + ("en_US".equals(language) ? (promoted ? "Promoted to " : "Demoted to ")
                    : "es_ES".equals(language) ? (promoted ? "Ascendido a " : "Descendido a ")
                    : promoted ? "Promovido para " : "Rebaixado para ") + newDivision.name);
                if (promoted && settings.promotionTitle)
                    player.sendTitle("§a" + outcome, "§f" + newDivision.name);
                if (promoted && settings.promotionSound)
                    player.playSound(player.getLocation(), Sound.LEVEL_UP, 1F, 1F);
            }
        }
    }
}
