package com.starfinplugins.bedwars.stats;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchEndReason;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchCombatDeathEvent;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
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
    private final Map<UUID, Long> preferenceRevisions = new ConcurrentHashMap<UUID, Long>();
    private final AtomicLong revisionCounter = new AtomicLong();
    private StatsSettings settings;
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
                flushPreferencesOutbox();
                refreshPreferences(id, legacy);
            } catch (Exception error) { logDatabaseFailure(error); }
        });
    }

    @EventHandler public void onLifecycle(MatchLifecycleEvent event) {
        if (!isEnabled()) return;
        GameMatch match = event.getMatch();
        switch (event.getType()) {
            case STARTED:
                MatchStatsTracker tracker = new MatchStatsTracker(match.getId(), settings.awards);
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
                    match.getEndReason() == MatchEndReason.DRAW);
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
                    repository.saveMatch(results);
                    outbox.acknowledge(file);
                    for (MatchResult result : results) refreshProfile(result.playerId);
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
                    } else {
                        repository.updateQuickBuy(pending.uuid, PlayerPreferences.decode(pending.value));
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
