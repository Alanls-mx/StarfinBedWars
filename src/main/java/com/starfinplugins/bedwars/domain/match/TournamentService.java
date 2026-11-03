package com.starfinplugins.bedwars.domain.match;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.stats.StatsSettings;
import com.starfinplugins.bedwars.tournament.TournamentAdmission;
import com.starfinplugins.bedwars.tournament.TournamentOutbox;
import com.starfinplugins.bedwars.tournament.TournamentRepository;
import com.starfinplugins.bedwars.tournament.TournamentSettings;
import com.starfinplugins.bedwars.tournament.Tournament;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.starfinplugins.minigames.api.ReservationSnapshot;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** Backend tournament admission and durable result delivery; JDBC never runs on a tick. */
public final class TournamentService implements ManagedService, Listener, PluginMessageListener {
    private static final String PARTY_CHANNEL = "SMTournament";
    private final StarfinBedWarsPlugin plugin;
    private final TournamentRepository repository = new TournamentRepository();
    private final Map<UUID, TournamentAdmission> pending = new HashMap<UUID, TournamentAdmission>();
    private final Map<UUID, RegistrationRequest> registrations = new HashMap<UUID, RegistrationRequest>();
    private final java.util.Set<UUID> activating =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private TournamentSettings settings;
    private StatsSettings database;
    private TournamentOutbox outbox;
    private ExecutorService io;
    private BukkitTask retry;
    private volatile boolean ready;
    private volatile java.util.Set<String> arenaGroups = java.util.Collections.emptySet();

    public TournamentService(StarfinBedWarsPlugin plugin) { this.plugin = plugin; }
    @Override public String getName() { return "Torneios"; }
    @Override public void start() {
        try { settings = TournamentSettings.load(plugin); }
        catch (RuntimeException invalid) {
            plugin.getLogger().log(Level.SEVERE, "tournaments.yml invalido", invalid); return;
        }
        if (!settings.enabled) return;
        database = StatsSettings.load(plugin);
        if (!database.enabled) {
            plugin.getLogger().severe("Torneios exigem MySQL ativo em stats.yml"); return;
        }
        outbox = new TournamentOutbox(plugin.getDataFolder().toPath().resolve("tournament-pending"));
        io = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "StarfinBedWars-Tournament-IO");
            thread.setDaemon(true); return thread;
        });
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PARTY_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, PARTY_CHANNEL, this);
        io.execute(this::connectAndFlush);
        retry = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (io != null && !io.isShutdown()) io.execute(() -> {
                if (!ready) { connectAndFlush(); return; }
                try { repository.tick(); arenaGroups = repository.arenaGroups(); flush(); }
                catch (SQLException error) { plugin.getLogger().log(Level.WARNING,
                    "Falha ao atualizar torneios", error); }
            });
        }, 200L, 200L);
    }
    private void connectAndFlush() {
        try {
            repository.open(database);
            ready = true;
            repository.tick();
            arenaGroups = repository.arenaGroups();
            flush();
        } catch (SQLException | RuntimeException error) {
            ready = false;
            plugin.getLogger().log(Level.WARNING, "MySQL de torneios indisponivel; nova tentativa em 10 s", error);
        }
    }
    @Override public void stop() {
        ready = false;
        arenaGroups = java.util.Collections.emptySet();
        if (retry != null) retry.cancel();
        HandlerList.unregisterAll(this);
        pending.clear();
        registrations.clear();
        activating.clear();
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, PARTY_CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, PARTY_CHANNEL, this);
        if (io != null) {
            io.shutdown();
            try { if (!io.awaitTermination(10, TimeUnit.SECONDS)) io.shutdownNow(); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); io.shutdownNow();
            }
        }
        repository.close();
    }
    public boolean isOperational() { return ready && repository.isOpen(); }
    public boolean isTournamentArena(String group) {
        return "tournament".equalsIgnoreCase(group)
            || (settings != null && settings.arenaGroup.equalsIgnoreCase(group))
            || arenaGroups.contains(group);
    }
    public TournamentSettings settings() { return settings; }
    public void register(Player player, String tournamentId, String teamName) {
        if (player == null || !isOperational()) {
            if (player != null) player.sendMessage("§cTorneios indisponiveis.");
            return;
        }
        if (teamName == null || teamName.trim().isEmpty() || teamName.length() > 32) {
            player.sendMessage("§cNome da equipe invalido (1-32 caracteres)."); return;
        }
        query(repo -> repo.find(tournamentId)).whenComplete((tournament, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error != null || tournament == null
                        || tournament.state != Tournament.State.REGISTRATION_OPEN) {
                    player.sendMessage("§cInscricoes indisponiveis."); return;
                }
                if (tournament.teamSize == 1) {
                    finishRegistration(player, tournamentId, teamName,
                        java.util.Collections.singletonList(player.getUniqueId()));
                    return;
                }
                UUID request = UUID.randomUUID();
                registrations.put(request, new RegistrationRequest(player.getUniqueId(),
                    tournamentId, teamName, tournament.teamSize));
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("register"); out.writeUTF(request.toString());
                player.sendPluginMessage(plugin, PARTY_CHANNEL, out.toByteArray());
                player.sendMessage("§eValidando sua Party no proxy...");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    RegistrationRequest expired = registrations.remove(request);
                    if (expired != null && player.isOnline())
                        player.sendMessage("§cA consulta da Party expirou.");
                }, 200L);
            });
        });
    }
    @Override public void onPluginMessageReceived(String channel, Player carrier, byte[] message) {
        if (!PARTY_CHANNEL.equals(channel) || carrier == null || message == null) return;
        try {
            ByteArrayDataInput input = ByteStreams.newDataInput(message);
            if (!"register".equals(input.readUTF())) return;
            UUID requestId = UUID.fromString(input.readUTF());
            RegistrationRequest request = registrations.remove(requestId);
            if (request == null || !request.player.equals(carrier.getUniqueId())) return;
            String status = input.readUTF();
            int count = input.readInt();
            if (!"OK".equals(status) || count != request.teamSize || count < 1 || count > 16) {
                carrier.sendMessage("§cParty invalida para este torneio: " + status); return;
            }
            List<UUID> roster = new ArrayList<UUID>();
            for (int i = 0; i < count; i++) roster.add(UUID.fromString(input.readUTF()));
            if (!roster.contains(carrier.getUniqueId())
                    || new java.util.HashSet<UUID>(roster).size() != roster.size()) {
                carrier.sendMessage("§cElenco da Party invalido."); return;
            }
            finishRegistration(carrier, request.tournament, request.name, roster);
        } catch (RuntimeException invalid) {
            plugin.getLogger().warning("Resposta de Party de torneio invalida.");
        }
    }
    private void finishRegistration(Player player, String tournament, String name,
            List<UUID> roster) {
        query(repo -> repo.register(tournament, player.getUniqueId(), name,
            roster, player.getName())).whenComplete((team, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error != null) {
                    player.sendMessage("§cInscricao recusada: " + rootMessage(error)); return;
                }
                player.sendMessage("§aEquipe inscrita: §f" + team.name
                    + "§a. Membros devem usar /sbw tournament confirm " + tournament + ".");
            });
        });
    }
    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
    private static final class RegistrationRequest {
        final UUID player; final String tournament, name; final int teamSize;
        RegistrationRequest(UUID player, String tournament, String name, int teamSize) {
            this.player = player; this.tournament = tournament; this.name = name;
            this.teamSize = teamSize;
        }
    }
    public interface Query<T> { T run(TournamentRepository repository) throws SQLException; }
    public <T> CompletableFuture<T> query(Query<T> action) {
        if (!isOperational() || io == null || io.isShutdown()) {
            CompletableFuture<T> failed = new CompletableFuture<T>();
            failed.completeExceptionally(new IllegalStateException("Torneios indisponiveis"));
            return failed;
        }
        return CompletableFuture.supplyAsync(() -> {
            try { return action.run(repository); }
            catch (SQLException error) { throw new IllegalStateException("Consulta falhou", error); }
        }, io);
    }
    public void inspectReservation(ReservationSnapshot reservation,
            Consumer<TournamentAdmission> callback) {
        if (reservation == null || !reservation.isTournament() || !isOperational()) {
            callback.accept(null); return;
        }
        query(repo -> repo.inspect(reservation, plugin.getNetworkRegistry().getServerId(),
            plugin.getNetworkRegistry().getInstanceId())).whenComplete((admission, error) -> {
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                if (error == null && admission != null) pending.put(admission.game.id, admission);
                callback.accept(error == null ? admission : null);
            });
        });
    }
    public void onReservationAdmitted(ReservationSnapshot reservation, GameMatch match) {
        if (reservation == null || match == null || !reservation.isTournament()) return;
        if (match.getTournamentAdmission() != null
                && reservation.getTournamentGameId().equals(match.getTournamentAdmission().game.id)) return;
        TournamentAdmission admission = pending.get(reservation.getTournamentGameId());
        if (admission == null || !admission.roster.containsKey(reservation.getPlayerId())
                || !admission.roster.get(reservation.getPlayerId()).equals(reservation.getTeamId())
                || !admission.game.arenaId.equals(match.getArena().getId())) {
            reviewFailedAdmission(reservation.getTournamentGameId(), "ADMISSION_MISMATCH");
            plugin.getMatchEngine().cancelTournamentFormation(match); return;
        }
        query(repo -> repo.admit(admission.game.id, admission.game.allocationId,
            reservation.getPlayerId(), reservation.getId(), match.getId(),
            plugin.getNetworkRegistry().getServerId(),
            plugin.getNetworkRegistry().getInstanceId(), match.getArena().getId()))
            .whenComplete((count, error) -> {
                if (error != null) {
                    plugin.getLogger().log(Level.WARNING, "Admissao de torneio falhou", error);
                    reviewFailedAdmission(admission.game.id, "ADMISSION_FAILED");
                    if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin,
                        () -> plugin.getMatchEngine().cancelTournamentFormation(match));
                    return;
                }
                if (count == null || count != admission.roster.size()) return;
                if (!activating.add(admission.game.id)) return;
                query(repo -> { repo.startGame(admission.game.id, match.getId()); return true; })
                    .whenComplete((started, failure) -> {
                        if (!plugin.isEnabled()) {
                            activating.remove(admission.game.id); return;
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                if (failure != null || plugin.getMatchEngine()
                                        .getMatchForArena(admission.game.arenaId) != match) {
                                    reviewFailedAdmission(admission.game.id, "START_FAILED");
                                    plugin.getMatchEngine().cancelTournamentFormation(match);
                                    return;
                                }
                                match.authorizeTournament(admission);
                                pending.remove(admission.game.id);
                                plugin.getMatchEngine().tournamentAuthorized(match);
                            } catch (RuntimeException invalid) {
                                plugin.getLogger().log(Level.WARNING,
                                    "Elenco de torneio divergiu durante admissao", invalid);
                                reviewFailedAdmission(admission.game.id, "ROSTER_DIVERGED");
                                plugin.getMatchEngine().cancelTournamentFormation(match);
                            } finally {
                                activating.remove(admission.game.id);
                            }
                        });
                    });
            });
    }
    private void reviewFailedAdmission(UUID gameId, String reason) {
        if (!isOperational() || gameId == null) return;
        query(repo -> { repo.reviewGame(gameId, reason); return true; })
            .exceptionally(error -> {
                plugin.getLogger().log(Level.SEVERE, "Jogo de torneio precisa de revisao: " + gameId, error);
                return false;
            });
    }
    @EventHandler public void onLifecycle(MatchLifecycleEvent event) {
        if (event.getType() != MatchLifecycleEvent.Type.ENDED
                || event.getMatch().getType() != MatchType.TOURNAMENT || outbox == null
                || io == null || io.isShutdown()) return;
        GameMatch match = event.getMatch();
        TournamentAdmission admission = match.getTournamentAdmission();
        if (admission == null) return;
        String reason;
        switch (match.getEndReason()) {
            case VICTORY: reason = "VICTORY"; break;
            case DRAW: reason = "DRAW"; break;
            case CANCELLED: reason = "CANCELLED"; break;
            default: reason = "ERROR"; break;
        }
        TournamentOutbox.Result result = new TournamentOutbox.Result(admission.game.id,
            match.getId(), reason, match.getWinnerTeamId());
        try {
            // Persist before returning from ENDED, so a process crash cannot lose the result.
            outbox.write(result);
            io.execute(this::flush);
        } catch (IOException failure) {
            plugin.getLogger().log(Level.SEVERE,
                "Resultado de torneio nao pode ser gravado no journal", failure);
            reviewFailedAdmission(admission.game.id, "JOURNAL_FAILED");
        }
    }
    private void flush() {
        if (!isOperational()) return;
        try {
            for (Path path : outbox.pending()) {
                TournamentOutbox.Result result = outbox.read(path);
                try {
                    repository.recordResult(result.gameId, result.minecraftMatch,
                        result.reason, result.winningArenaTeam);
                    outbox.acknowledge(path);
                } catch (SQLException error) {
                    plugin.getLogger().log(Level.WARNING,
                        "Resultado de torneio pendente sera repetido", error);
                    break;
                }
            }
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Journal de torneio invalido", error);
        }
    }
}
