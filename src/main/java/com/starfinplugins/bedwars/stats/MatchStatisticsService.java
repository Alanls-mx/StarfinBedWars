package com.starfinplugins.bedwars.stats;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchEndReason;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchCombatDeathEvent;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.event.PlayerProgressionEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/** Main thread accumulates match events, one I/O worker owns JDBC and the durable outbox. */
public final class MatchStatisticsService implements ManagedService, Listener {
    private static final String PREFIX = ChatColor.AQUA + "[StarfinBedWars] " + ChatColor.RESET;
    private final StarfinBedWarsPlugin plugin;
    private final Map<UUID, MatchStatsTracker> matches = new HashMap<UUID, MatchStatsTracker>();
    /** Immutable snapshots; safe to read from the 1-second lobby scoreboard refresh. */
    private final Map<UUID, StatsProfile> profileCache = new ConcurrentHashMap<UUID, StatsProfile>();
    private final Map<UUID, PlayerPreferences> preferencesCache = new ConcurrentHashMap<UUID, PlayerPreferences>();
    private final Map<UUID, Set<String>> unlocksCache = new ConcurrentHashMap<UUID, Set<String>>();
    private final Map<UUID, Map<String, Integer>> abilityLevelsCache =
        new ConcurrentHashMap<UUID, Map<String, Integer>>();
    private final Map<UUID, Long> rankPositionCache = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> lastMatchXpCache = new ConcurrentHashMap<UUID, Long>();
    private final AtomicInteger pendingRankings = new AtomicInteger();
    private final Map<UUID, Long> preferenceRevisions = new ConcurrentHashMap<UUID, Long>();
    private final AtomicLong revisionCounter = new AtomicLong();
    private StatsSettings settings;
    private RewardsCatalog rewards = RewardsCatalog.empty();
    private RankingsService rankings;
    private StatsOutbox outbox;
    private PreferencesOutbox preferencesOutbox;
    private ExecutorService worker;
    private MySqlStatsRepository repository;
    private BukkitTask retryTask;

    public MatchStatisticsService(StarfinBedWarsPlugin plugin) { this.plugin = plugin; }
    @Override public String getName() { return "Estatisticas persistentes"; }

    @Override public void start() {
        settings = StatsSettings.load(plugin);
        if (!settings.enabled) {
            plugin.getLogger().info("Estatisticas desativadas: configure stats.yml e MySQL compartilhado.");
            return;
        }
        try { rewards = RewardsCatalog.load(plugin, settings.progression); }
        catch (RuntimeException invalid) {
            plugin.getLogger().severe("rewards.yml invalido (" + invalid.getMessage()
                + "); desbloqueios suspensos ate corrigir a configuracao.");
            rewards = RewardsCatalog.empty();
        }
        rankings = new RankingsService(settings.rankingsCacheSeconds);
        outbox = new StatsOutbox(plugin.getDataFolder().toPath().resolve("stats-pending"));
        preferencesOutbox = new PreferencesOutbox(plugin.getDataFolder().toPath().resolve("preferences-pending"));
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "StarfinBedWars-Stats-IO");
            thread.setDaemon(true);
            return thread;
        });
        Bukkit.getPluginManager().registerEvents(this, plugin);
        worker.execute(() -> {
            try {
                ensureOpen();
                flushOutbox();
                flushPreferencesOutbox();
            } catch (Exception error) { logDatabaseFailure(error); }
        });
        retryTask = Bukkit.getScheduler().runTaskTimer(plugin,
            () -> {
                List<UUID> online = new ArrayList<UUID>();
                for (Player player : Bukkit.getOnlinePlayers()) online.add(player.getUniqueId());
                worker.execute(() -> {
                    flushOutbox();
                    flushPreferencesOutbox();
                    for (UUID id : online) {
                        refreshProfile(id);
                        refreshPreferences(id, null);
                        refreshAbilityLevels(id);
                        refreshRewards(id);
                        refreshRankPosition(id);
                        refreshLastMatchXp(id);
                    }
                });
            }, 1200L, 1200L);
        // Deferred by one tick: shop-favorites.yml is loaded later in the service startup list.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) onJoin(online.getUniqueId(), online.getName());
        });
    }

    @Override public void stop() {
        if (retryTask != null) retryTask.cancel();
        HandlerList.unregisterAll(this);
        matches.clear();
        profileCache.clear();
        preferencesCache.clear();
        unlocksCache.clear();
        abilityLevelsCache.clear();
        rankPositionCache.clear();
        lastMatchXpCache.clear();
        preferenceRevisions.clear();
        if (worker == null) return;
        worker.shutdown();
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Fila de estatisticas ainda ativa no shutdown; pendencias persistidas serao reprocessadas.");
                worker.shutdownNow();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        } finally {
            if (repository != null) repository.close();
        }
    }

    public boolean isEnabled() { return settings != null && settings.enabled; }
    public StatsProfile getCachedProfile(UUID uuid) { return profileCache.get(uuid); }
    public PlayerPreferences getCachedPreferences(UUID uuid) { return preferencesCache.get(uuid); }
    public String getSelectedKit(UUID uuid) {
        PlayerPreferences preferences = preferencesCache.get(uuid);
        return preferences == null ? "default" : preferences.selectedKit;
    }
    public String getSelectedAbility(UUID uuid) {
        PlayerPreferences preferences = preferencesCache.get(uuid);
        return preferences == null ? "none" : preferences.selectedAbility;
    }
    public Set<String> getCachedUnlocks(UUID uuid) {
        return unlocksCache.getOrDefault(uuid, Collections.emptySet());
    }
    public Map<String, Integer> getAbilityLevels(UUID uuid) {
        return abilityLevelsCache.getOrDefault(uuid, Collections.emptyMap());
    }
    public int getAbilityLevel(UUID uuid, String abilityId) {
        return getAbilityLevels(uuid).getOrDefault(abilityId, 0);
    }

    /** An administrative grant is confirmed in MySQL before the cached level is published. */
    public void grantAbility(UUID uuid, String abilityId, int level,
                             java.util.function.IntConsumer completed) {
        if (!isEnabled() || worker == null) { completed.accept(0); return; }
        worker.execute(() -> {
            int actual = 0;
            try {
                ensureOpen();
                actual = repository.grantAbility(uuid, abilityId, level);
            } catch (Exception error) { logDatabaseFailure(error); }
            final int confirmed = actual;
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (confirmed > 0 && Bukkit.getPlayer(uuid) != null) {
                    Map<String, Integer> next = new HashMap<String, Integer>(getAbilityLevels(uuid));
                    next.put(abilityId, confirmed);
                    abilityLevelsCache.put(uuid, Collections.unmodifiableMap(next));
                }
                completed.accept(confirmed);
            });
        });
    }
    public RewardsCatalog getRewards() { return rewards; }
    public long getCachedRankPosition(UUID uuid) {
        return rankPositionCache.getOrDefault(uuid, 0L);
    }
    public long getCachedLastMatchXp(UUID uuid) {
        return lastMatchXpCache.getOrDefault(uuid, 0L);
    }
    public int getDefaultRankingPageSize() {
        return settings == null ? 10 : settings.rankingsDefaultPageSize;
    }
    public int getMaximumRankingPageSize() {
        return settings == null ? 50 : settings.rankingsMaximumPageSize;
    }
    public String getSelectedTitle(UUID uuid) {
        PlayerPreferences prefs = preferencesCache.get(uuid);
        if (prefs == null || prefs.selectedTitle == null) return "";
        RewardsCatalog.TitleReward title = rewards.title(prefs.selectedTitle);
        return title == null || !getCachedUnlocks(uuid).contains(title.id)
            ? "" : title.name(prefs.language);
    }
    public String getLanguage(UUID uuid) {
        PlayerPreferences prefs = preferencesCache.get(uuid);
        return prefs == null ? "pt_BR" : prefs.language;
    }
    public void setLanguage(Player player, String language) throws IOException {
        if (!isEnabled()) throw new IllegalStateException("MySQL de estatisticas nao esta habilitado.");
        if (!PlayerPreferences.supportedLanguage(language)) throw new IllegalArgumentException("Idioma invalido.");
        UUID id = player.getUniqueId();
        String normalized = PlayerPreferences.languageOrDefault(language);
        preferencesOutbox.write(id, PreferencesOutbox.Kind.LANGUAGE, normalized);
        PlayerPreferences old = preferencesCache.get(id);
        preferencesCache.put(id, old == null ? new PlayerPreferences(normalized, null)
            : old.withLanguage(normalized));
        preferenceRevisions.put(id, revisionCounter.incrementAndGet());
        worker.execute(() -> {
            flushPreferencesOutbox();
            refreshPreferences(id, null);
        });
    }
    public void setQuickBuy(UUID uuid, List<String> favorites) throws IOException {
        if (!isEnabled()) return;
        List<String> normalized = PlayerPreferences.normalizeItems(favorites);
        preferencesOutbox.write(uuid, PreferencesOutbox.Kind.QUICK_BUY,
            PlayerPreferences.encode(normalized));
        PlayerPreferences old = preferencesCache.get(uuid);
        preferencesCache.put(uuid, old == null ? new PlayerPreferences("pt_BR", normalized)
            : old.withQuickBuy(normalized));
        preferenceRevisions.put(uuid, revisionCounter.incrementAndGet());
        worker.execute(() -> {
            flushPreferencesOutbox();
            refreshPreferences(uuid, null);
        });
    }
    public void setGameplaySelection(Player player, PreferencesOutbox.Kind kind, String id)
            throws IOException {
        if (!isEnabled()) throw new IllegalStateException("MySQL de estatisticas indisponivel");
        if ((kind != PreferencesOutbox.Kind.KIT && kind != PreferencesOutbox.Kind.ABILITY)
                || !PlayerPreferences.validSelectionId(id)) throw new IllegalArgumentException("Selecao invalida");
        UUID uuid = player.getUniqueId();
        preferencesOutbox.write(uuid, kind, id);
        PlayerPreferences old = preferencesCache.get(uuid);
        if (old == null) old = new PlayerPreferences("pt_BR", null);
        preferencesCache.put(uuid, kind == PreferencesOutbox.Kind.KIT
            ? old.withSelectedKit(id) : old.withSelectedAbility(id));
        preferenceRevisions.put(uuid, revisionCounter.incrementAndGet());
        worker.execute(() -> {
            flushPreferencesOutbox();
            refreshPreferences(uuid, null);
        });
    }
    public XpProgression.Progress getProgress(long totalXp) {
        return settings == null ? new XpProgression(1000, 250).progress(totalXp)
            : settings.progression.progress(totalXp);
    }

    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;
        onJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) {
        if (!isEnabled()) return;
        UUID id = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        profileCache.remove(id);
        preferencesCache.remove(id);
        unlocksCache.remove(id);
        abilityLevelsCache.remove(id);
        rankPositionCache.remove(id);
        lastMatchXpCache.remove(id);
        preferenceRevisions.put(id, revisionCounter.incrementAndGet());
        worker.execute(() -> {
            try {
                ensureOpen();
                repository.updateName(id, name);
            } catch (Exception error) { logDatabaseFailure(error); }
        });
    }

    private void onJoin(UUID id, String name) {
        // Snapshot Bukkit/ShopService state on the main thread, never from JDBC executor.
        List<String> legacy = plugin.getShopService() == null ? null
            : plugin.getShopService().getLegacyFavoriteIds(id);
        worker.execute(() -> {
            try {
                ensureOpen();
                repository.updateName(id, name);
                refreshProfile(id);
                refreshRewards(id);
                refreshRankPosition(id);
                refreshLastMatchXp(id);
                flushPreferencesOutbox();
                refreshPreferences(id, legacy);
                refreshAbilityLevels(id);
            } catch (Exception error) { logDatabaseFailure(error); }
        });
    }

    @EventHandler public void onLifecycle(MatchLifecycleEvent event) {
        if (!isEnabled()) return;
        GameMatch match = event.getMatch();
        switch (event.getType()) {
            case STARTED:
                MatchStatsTracker tracker = new MatchStatsTracker(match.getId(), settings.awards,
                    MatchMode.of(match.getArena()));
                for (GameParticipant participant : match.getParticipantList()) {
                    Player online = Bukkit.getPlayer(participant.getPlayerId());
                    tracker.addPlayer(participant.getPlayerId(), participant.getTeamId(),
                        online == null ? "" : online.getName());
                }
                matches.put(match.getId(), tracker);
                break;
            case PLAYER_LEFT:
                if (match.getState() == MatchState.IN_GAME) {
                    MatchStatsTracker leaving = matches.get(match.getId());
                    if (leaving != null && event.getParticipant() != null) {
                        leaving.markAbandoned(event.getParticipant().getPlayerId());
                    }
                }
                break;
            case BED_DESTROYED:
                MatchStatsTracker beds = matches.get(match.getId());
                if (beds != null && event.getParticipant() != null) {
                    beds.bed(event.getParticipant().getPlayerId());
                }
                break;
            case ENDED:
                MatchStatsTracker completed = matches.remove(match.getId());
                if (completed == null || match.getStartedAt() == null ||
                        (match.getEndReason() != MatchEndReason.VICTORY
                         && match.getEndReason() != MatchEndReason.DRAW)) break;
                for (GameParticipant member : match.getParticipantList()) {
                    if (member.getState() == ParticipantState.ABANDONED) {
                        completed.markAbandoned(member.getPlayerId());
                    }
                }
                List<MatchResult> results = completed.finish(match.getWinnerTeamId(),
                    match.getEndReason() == MatchEndReason.DRAW,
                    match.getEndedAt() == null ? 0L : match.getEndedAt().toEpochMilli());
                if (!results.isEmpty()) {
                    // Always journal before JDBC so a DB outage can be retried on this node.
                    worker.execute(() -> {
                        try {
                            outbox.write(results);
                            flushOutbox();
                        } catch (IOException error) {
                            plugin.getLogger().log(Level.SEVERE,
                                "Falha ao salvar fila local de estatisticas da partida " + match.getId(), error);
                        }
                    });
                }
                break;
            case INSTANCE_REMOVED:
                matches.remove(match.getId());
                break;
            default:
                break;
        }
    }

    @EventHandler public void onDeath(MatchCombatDeathEvent event) {
        if (!isEnabled()) return;
        MatchStatsTracker tracker = matches.get(event.getMatchId());
        if (tracker != null) tracker.death(event.getVictimId(), event.getKillerId(), event.isFinalKill());
    }

    public void showProfile(CommandSender sender, String identifier) {
        if (!isEnabled()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Estatisticas desativadas. Configure stats.yml.");
            return;
        }
        if (identifier == null || identifier.trim().isEmpty() || identifier.length() > 36) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Informe um nome de jogador ou UUID valido.");
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.GRAY + "Consultando perfil da rede...");
        worker.execute(() -> {
            final StatsProfile profile;
            final String failure;
            StatsProfile result = null;
            String errorText = null;
            try {
                ensureOpen();
                result = repository.find(identifier);
            } catch (Exception error) {
                logDatabaseFailure(error);
                errorText = "Banco de estatisticas indisponivel. Tente novamente.";
            }
            profile = result;
            failure = errorText;
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sender instanceof Player && !((Player) sender).isOnline()) return;
                if (failure != null) { sender.sendMessage(PREFIX + ChatColor.RED + failure); return; }
                if (profile == null) {
                    sender.sendMessage(PREFIX + ChatColor.YELLOW + "Nenhum perfil encontrado para " + identifier + ".");
                    return;
                }
                XpProgression.Progress progress = settings.progression.progress(profile.totalXp);
                sender.sendMessage(ChatColor.AQUA + "--- Perfil BedWars: " + ChatColor.WHITE + profile.name + ChatColor.AQUA + " ---");
                sender.sendMessage(ChatColor.YELLOW + "Nivel " + progress.level + ChatColor.GRAY + " | XP: "
                    + profile.totalXp + " (" + progress.current + "/" + progress.required + " para o proximo)");
                sender.sendMessage(ChatColor.GRAY + "Partidas: " + ChatColor.WHITE + profile.games
                    + ChatColor.GRAY + " | Vitorias: " + ChatColor.GREEN + profile.wins
                    + ChatColor.GRAY + " | Derrotas: " + ChatColor.RED + profile.losses
                    + ChatColor.GRAY + " | Empates: " + profile.draws);
                sender.sendMessage(ChatColor.GRAY + "Abates: " + ChatColor.WHITE + profile.kills
                    + ChatColor.GRAY + " | Finais: " + profile.finalKills
                    + ChatColor.GRAY + " | Mortes: " + profile.deaths
                    + ChatColor.GRAY + " | Mortes finais: " + profile.finalDeaths
                    + ChatColor.GRAY + " | Camas: " + profile.beds);
                sender.sendMessage(ChatColor.GRAY + "Primeira atividade: "
                    + ChatColor.WHITE + date(profile.firstPlay)
                    + ChatColor.GRAY + " | Ultima: " + ChatColor.WHITE + date(profile.lastPlay));
            });
        });
    }

    private static String date(long value) {
        if (value <= 0L) return "Sem registro";
        return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ROOT)
            .format(new java.util.Date(value));
    }

    private void ensureOpen() throws Exception {
        if (repository != null) return;
        MySqlStatsRepository next = new MySqlStatsRepository();
        next.open(settings);
        repository = next;
        plugin.getLogger().info("Estatisticas: MySQL compartilhado conectado.");
    }

    private void flushOutbox() {
        try {
            List<Path> files = outbox.pending();
            if (files.isEmpty()) return;
            ensureOpen();
            for (Path file : files) {
                try {
                    List<MatchResult> results = outbox.read(file);
                    List<ProgressionUpdate> updates = repository.saveMatch(results, rewards,
                        settings.progression);
                    outbox.acknowledge(file);
                    for (MatchResult result : results) refreshProfile(result.playerId);
                    for (MatchResult result : results) refreshRewards(result.playerId);
                    for (MatchResult result : results) refreshRankPosition(result.playerId);
                    for (MatchResult result : results) refreshLastMatchXp(result.playerId);
                    if (settings.rankingsUpdateAfterMatch) rankings.invalidate();
                    notifyProgress(updates);
                } catch (IOException invalidOutbox) {
                    plugin.getLogger().log(Level.SEVERE,
                        "Outbox invalido: preservado para inspecao " + file.getFileName(), invalidOutbox);
                }
            }
        } catch (Exception error) {
            logDatabaseFailure(error);
        }
    }

    private void refreshProfile(UUID id) {
        try {
            if (repository == null) ensureOpen();
            StatsProfile profile = repository.find(id.toString());
            if (profile != null) profileCache.put(id, profile);
        } catch (Exception error) { logDatabaseFailure(error); }
    }

    private void refreshRewards(UUID id) {
        try {
            ensureOpen();
            unlocksCache.put(id, Collections.unmodifiableSet(repository.ensureRewards(id, rewards)));
        } catch (Exception error) { logDatabaseFailure(error); }
    }

    private void refreshRankPosition(UUID id) {
        if (!settings.rankingsEnabled) return;
        try {
            ensureOpen();
            rankPositionCache.put(id, repository.globalWinPosition(id));
        } catch (Exception error) { logDatabaseFailure(error); }
    }

    private void refreshLastMatchXp(UUID id) {
        try {
            ensureOpen();
            lastMatchXpCache.put(id, repository.latestMatchXp(id));
        } catch (Exception error) { logDatabaseFailure(error); }
    }

    public void requestRanking(Player player, RankingSpec spec,
                               java.util.function.BiConsumer<RankingPage, String> completion) {
        if (!isEnabled() || !settings.rankingsEnabled) {
            completion.accept(null, "Rankings indisponiveis.");
            return;
        }
        if (pendingRankings.incrementAndGet() > 16) {
            pendingRankings.decrementAndGet();
            completion.accept(null, "Muitas consultas de ranking. Tente novamente.");
            return;
        }
        UUID viewer = player.getUniqueId();
        try {
            worker.execute(() -> {
                RankingPage page = null;
                String failure = null;
                try {
                    ensureOpen();
                    page = rankings.load(repository, spec, viewer);
                } catch (Exception error) {
                    logDatabaseFailure(error);
                    failure = "Nao foi possivel consultar o ranking agora.";
                } finally { pendingRankings.decrementAndGet(); }
                final RankingPage result = page;
                final String message = failure;
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                    if (Bukkit.getPlayer(viewer) != null) completion.accept(result, message);
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            pendingRankings.decrementAndGet();
            completion.accept(null, "Rankings indisponiveis.");
        }
    }

    public void selectTitle(Player player, String titleId) {
        if (!isEnabled() || rewards.title(titleId) == null) {
            player.sendMessage(ChatColor.RED + Phase2Text.text(getLanguage(player.getUniqueId()), "unavailable"));
            return;
        }
        UUID id = player.getUniqueId();
        preferenceRevisions.put(id, revisionCounter.incrementAndGet());
        worker.execute(() -> {
            boolean selected = false;
            try { ensureOpen(); selected = repository.selectTitle(id, titleId); }
            catch (Exception error) { logDatabaseFailure(error); }
            final boolean confirmed = selected;
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player current = Bukkit.getPlayer(id);
                if (current == null) return;
                if (!confirmed) {
                    current.sendMessage(ChatColor.RED + Phase2Text.text(getLanguage(id), "title_blocked"));
                    return;
                }
                PlayerPreferences previous = preferencesCache.get(id);
                if (previous != null) preferencesCache.put(id, previous.withSelectedTitle(titleId));
                current.sendMessage(ChatColor.GREEN + Phase2Text.text(getLanguage(id), "title_selected") + ": "
                    + rewards.title(titleId).name(getLanguage(id)));
            });
        });
    }

    private void notifyProgress(List<ProgressionUpdate> updates) {
        if (updates.isEmpty() || !plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (ProgressionUpdate update : updates) {
                Bukkit.getPluginManager().callEvent(new PlayerProgressionEvent(update));
                Player player = Bukkit.getPlayer(update.playerId);
                if (player == null) continue;
                String language = getLanguage(update.playerId);
                if ("en_US".equals(language)) {
                    player.sendMessage(ChatColor.AQUA + "Match XP: +" + update.earnedXp);
                    if (update.currentLevel > update.previousLevel)
                        player.sendMessage(ChatColor.GOLD + "Level " + update.previousLevel
                            + " -> " + update.currentLevel + "!");
                } else if ("es_ES".equals(language)) {
                    player.sendMessage(ChatColor.AQUA + "XP de partida: +" + update.earnedXp);
                    if (update.currentLevel > update.previousLevel)
                        player.sendMessage(ChatColor.GOLD + "Nivel " + update.previousLevel
                            + " -> " + update.currentLevel + "!");
                } else {
                    player.sendMessage(ChatColor.AQUA + Phase2Text.text(language, "match_xp") + ": +" + update.earnedXp);
                    if (update.currentLevel > update.previousLevel)
                        player.sendMessage(ChatColor.GOLD + "Nivel " + update.previousLevel
                            + " -> " + update.currentLevel + "!");
                }
                for (String titleId : update.unlockedTitles) {
                    RewardsCatalog.TitleReward title = rewards.title(titleId);
                    if (title != null) player.sendMessage(ChatColor.GREEN
                        + Phase2Text.text(language, "title_unlocked") + ": "
                        + title.name(language));
                }
            }
        });
    }

    private void refreshPreferences(UUID id, List<String> legacy) {
        long expected = preferenceRevisions.getOrDefault(id, 0L);
        try {
            if (repository == null) ensureOpen();
            PlayerPreferences loaded = repository.loadPreferences(id, legacy);
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (Bukkit.getPlayer(id) != null
                        && preferenceRevisions.getOrDefault(id, 0L) == expected) {
                    preferencesCache.put(id, loaded);
                }
            });
        } catch (Exception error) { logDatabaseFailure(error); }
    }

    private void refreshAbilityLevels(UUID id) {
        try {
            ensureOpen();
            Map<String, Integer> loaded = Collections.unmodifiableMap(
                new HashMap<String, Integer>(repository.loadAbilityLevels(id)));
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (Bukkit.getPlayer(id) != null) abilityLevelsCache.put(id, loaded);
            });
        } catch (Exception error) { logDatabaseFailure(error); }
    }

    private void flushPreferencesOutbox() {
        try {
            List<Path> files = preferencesOutbox.pending();
            if (files.isEmpty()) return;
            ensureOpen();
            for (Path path : files) {
                try {
                    PreferencesOutbox.Pending pending = preferencesOutbox.read(path);
                    if (pending.kind == PreferencesOutbox.Kind.LANGUAGE) {
                        repository.updateLanguage(pending.uuid, pending.value);
                    } else if (pending.kind == PreferencesOutbox.Kind.QUICK_BUY) {
                        repository.updateQuickBuy(pending.uuid, PlayerPreferences.decode(pending.value));
                    } else {
                        repository.updateGameplaySelection(pending.uuid, pending.kind, pending.value);
                    }
                    preferencesOutbox.acknowledge(path, pending);
                } catch (IOException invalid) {
                    plugin.getLogger().log(Level.SEVERE,
                        "Preferencia pendente invalida: arquivo preservado " + path.getFileName(), invalid);
                }
            }
        } catch (Exception error) { logDatabaseFailure(error); }
    }

    private void logDatabaseFailure(Exception error) {
        // Do not log JDBC URLs or credentials from nested vendor exceptions.
        plugin.getLogger().warning("Estatisticas: operacao MySQL pendente/falhou ("
            + error.getClass().getSimpleName() + "). Resultados locais serao reenviados.");
        if (repository != null) { repository.close(); repository = null; }
    }
}
