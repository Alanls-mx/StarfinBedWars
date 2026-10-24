package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;
import com.starfinplugins.bedwars.domain.arena.SpawnStructureDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.GameTeam;
import com.starfinplugins.bedwars.domain.match.MatchEndReason;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.reconnect.ReconnectService;
import com.starfinplugins.bedwars.reconnect.ReconnectSession;
import com.starfinplugins.bedwars.reconnect.PlayerStateSnapshot;
import com.starfinplugins.bedwars.world.SafeLocationResolver;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

public final class MatchEngineService implements ManagedService, Listener {
    private final Plugin plugin;
    private final ArenaService arenas;
    private final int respawnSeconds;
    private final int spawnOpeningSeconds;
    private final Map<String, GameMatch> byArena = new HashMap<String, GameMatch>();
    private final Map<UUID, GameMatch> byPlayer = new java.util.concurrent.ConcurrentHashMap<UUID, GameMatch>();
    private final Map<UUID, GameMatch> spectators = new java.util.concurrent.ConcurrentHashMap<UUID, GameMatch>();
    private final Map<UUID, World> matchWorlds = new HashMap<UUID, World>();
    private final Map<UUID, Set<String>> playerPlacedBlocks = new HashMap<UUID, Set<String>>();
    private final Set<String> unknownBedWarnings = new HashSet<String>();
    private final Map<UUID, Location> returnLocations = new HashMap<UUID, Location>();
    private final Map<UUID, GameMode> returnGameModes = new HashMap<UUID, GameMode>();
    private final Map<UUID, Location> offlineReturnLocations = new HashMap<UUID, Location>();
    private final Map<UUID, GameMode> offlineReturnModes = new HashMap<UUID, GameMode>();
    private final Map<String, BukkitTask> countdowns = new HashMap<String, BukkitTask>();
    private final Map<String, Integer> countdownValues = new HashMap<String, Integer>();
    private final Map<String, BukkitTask> spawnOpeningTasks = new HashMap<String, BukkitTask>();
    private final Map<String, Integer> spawnOpeningValues = new HashMap<String, Integer>();
    private final Map<String, Boolean> loading = new HashMap<String, Boolean>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<UUID, BukkitTask>();
    private final Map<UUID, Long> fallProtectionUntil = new HashMap<UUID, Long>();
    private boolean started;
    private ReconnectService reconnect;
    private MatchEquipmentService equipment;
    private final Map<UUID, RecentAttacker> lastAttackers = new HashMap<UUID, RecentAttacker>();

    public void setReconnect(ReconnectService reconnect) { this.reconnect = reconnect; }
    public void setEquipment(MatchEquipmentService equipment) { this.equipment = equipment; }

    public MatchEngineService(Plugin plugin, ArenaService arenas, int respawnSeconds) {
        this(plugin, arenas, respawnSeconds, 10);
    }

    public MatchEngineService(Plugin plugin, ArenaService arenas,
                              int respawnSeconds, int spawnOpeningSeconds) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.respawnSeconds = respawnSeconds;
        if (spawnOpeningSeconds < 1 || spawnOpeningSeconds > 60) {
            throw new IllegalArgumentException("Duracao de abertura dos spawns deve estar entre 1 e 60 segundos.");
        }
        this.spawnOpeningSeconds = spawnOpeningSeconds;
    }

    @Override
    public String getName() { return "match-engine"; }

    @Override
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        started = true;
    }

    @Override
    public void stop() {
        started = false;
        for (BukkitTask task : countdowns.values()) task.cancel();
        for (BukkitTask task : spawnOpeningTasks.values()) task.cancel();
        for (BukkitTask task : respawnTasks.values()) task.cancel();
        countdowns.clear();
        countdownValues.clear();
        spawnOpeningTasks.clear();
        spawnOpeningValues.clear();
        respawnTasks.clear();
        fallProtectionUntil.clear();
        for (GameMatch match : byArena.values()) {
            World world = matchWorlds.get(match.getId());
            if (world != null) {
                for (GameParticipant participant : match.getParticipantList()) {
                    Player player = Bukkit.getPlayer(participant.getPlayerId());
                    if (player != null) {
                        teleportToReturn(player);
                    }
                }
                for (Map.Entry<UUID, GameMatch> entry : spectators.entrySet()) {
                    if (entry.getValue() == match) {
                        Player player = Bukkit.getPlayer(entry.getKey());
                        if (player != null) teleportToReturn(player);
                    }
                }
                try {
                    arenas.getWorlds().removeInstance(world);
                } catch (IOException error) {
                    plugin.getLogger().warning("Instancia protegida mantida durante shutdown: "
                        + world.getName() + " (" + error.getMessage() + ")");
                }
            }
        }
        byArena.clear();
        byPlayer.clear();
        spectators.clear();
        lastAttackers.clear();
        matchWorlds.clear();
        playerPlacedBlocks.clear();
        unknownBedWarnings.clear();
        returnLocations.clear();
        returnGameModes.clear();
        offlineReturnLocations.clear();
        offlineReturnModes.clear();
    }

    public GameMatch getMatchForPlayer(UUID playerId) {
        return byPlayer.get(playerId);
    }

    public boolean hasPlayer(UUID playerId) {
        return byPlayer.containsKey(playerId) || spectators.containsKey(playerId);
    }

    public World getWorld(UUID matchId) {
        return matchWorlds.get(matchId);
    }

    public boolean isMatchWorld(World world) {
        return world != null && matchWorlds.containsValue(world);
    }

    public GameMatch getMatchInWorld(World world) {
        return world == null ? null : matchInWorld(world);
    }

    public void parkPendingReconnect(Player player) {
        GameMatch match = byPlayer.get(player.getUniqueId());
        GameParticipant participant = match == null ? null : match.getParticipant(player.getUniqueId());
        if (participant == null || participant.getState() != ParticipantState.DISCONNECTED) return;
        Location returnLocation = returnLocations.get(player.getUniqueId());
        Location destination = returnLocation == null ? null : returnLocation.clone();
        if (destination == null || destination.getWorld() == null
                || Bukkit.getWorld(destination.getWorld().getName()) != destination.getWorld()) {
            destination = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        if (destination != null) player.teleport(destination);
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        player.updateInventory();
    }

    public boolean isArenaActive(String arenaId) {
        return arenaId != null && byArena.containsKey(arenaId);
    }

    public boolean isSpectating(UUID playerId) {
        return spectators.containsKey(playerId);
    }

    public GameMatch getSpectatedMatch(UUID playerId) {
        return playerId == null ? null : spectators.get(playerId);
    }

    public Integer getLobbyCountdown(GameMatch match) {
        return match == null ? null : countdownValues.get(match.getArena().getId());
    }

    public Integer getSpawnOpeningCountdown(GameMatch match) {
        return match == null ? null : spawnOpeningValues.get(match.getArena().getId());
    }

    public void join(final Player player, final String arenaId) {
        if (!started || player == null || arenaId == null) {
            throw new IllegalStateException("Engine indisponivel ou argumentos invalidos.");
        }
        if (byPlayer.containsKey(player.getUniqueId()) || spectators.containsKey(player.getUniqueId())) {
            throw new IllegalStateException("Voce ja esta em uma partida.");
        }
        if (reconnect != null && reconnect.hasPending(player.getUniqueId())) {
            throw new IllegalStateException("Use /reconnect antes de entrar em outra partida.");
        }
        final ArenaDefinition arena = arenas.getArena(arenaId);
        if (arena == null || !arena.isEnabled()) {
            throw new IllegalArgumentException("Arena inexistente ou desativada.");
        }
        if (arenas.isEditing(arena.getId())) {
            throw new IllegalStateException("Arena temporariamente indisponivel durante o modo de edicao.");
        }
        GameMatch match = byArena.get(arena.getId());
        if (match != null) {
            addPlayer(match, player);
            return;
        }
        if (Boolean.TRUE.equals(loading.get(arena.getId()))) {
            throw new IllegalStateException("A instancia da arena esta sendo preparada.");
        }
        loading.put(arena.getId(), true);
        arenas.getWorlds().createInstance(arena, new ArenaServiceWorldCallback(arena, player));
    }

    public void spectate(Player player, String arenaId) {
        if (player == null || arenaId == null) {
            throw new IllegalArgumentException("Jogador e arena sao obrigatorios.");
        }
        if (byPlayer.containsKey(player.getUniqueId()) || spectators.containsKey(player.getUniqueId())) {
            throw new IllegalStateException("Voce ja esta em uma partida ou assistindo.");
        }
        if (reconnect != null && reconnect.hasPending(player.getUniqueId())) {
            throw new IllegalStateException("Use /reconnect antes de assistir outra partida.");
        }
        ArenaDefinition arena = arenas.getArena(arenaId);
        GameMatch match = arena == null ? null : byArena.get(arena.getId());
        if (match == null || match.getState() != MatchState.IN_GAME) {
            throw new IllegalStateException("Nao existe partida ativa para assistir nessa arena.");
        }
        World world = matchWorlds.get(match.getId());
        Position spectator = arena.getSpectator();
        Location target = spectator == null ? world.getSpawnLocation() : arenas.toLocation(spectator, world);
        returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        returnGameModes.put(player.getUniqueId(), player.getGameMode());
        player.setGameMode(GameMode.SPECTATOR);
        if (!player.teleport(target)) {
            player.setGameMode(returnGameModes.remove(player.getUniqueId()));
            returnLocations.remove(player.getUniqueId());
            throw new IllegalStateException("O servidor recusou o teleporte para espectador.");
        }
        spectators.put(player.getUniqueId(), match);
        fire(MatchLifecycleEvent.Type.SPECTATOR_JOINED, match, null, null);
    }

    public void leave(Player player) {
        if (player == null) return;
        GameMatch spectatedMatch = spectators.remove(player.getUniqueId());
        if (spectatedMatch != null) {
            teleportToReturn(player);
            fire(MatchLifecycleEvent.Type.SPECTATOR_LEFT, spectatedMatch, null, null);
            return;
        }
        GameMatch match = byPlayer.get(player.getUniqueId());
        if (match == null) return;
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        if (match.getState() == MatchState.WAITING || match.getState() == MatchState.STARTING) {
            match.removeWaitingParticipant(player.getUniqueId());
            fire(MatchLifecycleEvent.Type.PLAYER_LEFT, match, participant,
                participant == null ? null : participant.getTeamId());
        } else if (match.getState() == MatchState.IN_GAME && participant != null) {
            match.setParticipantState(player.getUniqueId(), ParticipantState.ABANDONED);
            fire(MatchLifecycleEvent.Type.PLAYER_LEFT, match, participant, participant.getTeamId());
            resolveWinner(match);
        }
        byPlayer.remove(player.getUniqueId());
        lastAttackers.remove(player.getUniqueId());
        cancel(respawnTasks.remove(player.getUniqueId()));
        fallProtectionUntil.remove(player.getUniqueId());
        teleportToReturn(player);
        if (match.getState() == MatchState.WAITING || match.getState() == MatchState.STARTING) {
            updateCountdown(match);
            if (match.getParticipantList().isEmpty()) {
                finish(match, MatchEndReason.CANCELLED, null);
            }
        }
    }

    private void addPlayer(final GameMatch match, final Player player) {
        if (spawnOpeningTasks.containsKey(match.getArena().getId())) {
            throw new IllegalStateException("Os spawns estao abrindo; aguarde a proxima partida.");
        }
        if (!match.addParticipant(player.getUniqueId())) {
            throw new IllegalStateException("A partida esta cheia ou nao aceita jogadores.");
        }
        byPlayer.put(player.getUniqueId(), match);
        returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        returnGameModes.put(player.getUniqueId(), player.getGameMode());
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        World world = matchWorlds.get(match.getId());
        Location spawn = arenas.toLocation(match.getArena().getLobby(), world);
        player.setGameMode(GameMode.SURVIVAL);
        if (!player.teleport(spawn)) {
            match.removeWaitingParticipant(player.getUniqueId());
            byPlayer.remove(player.getUniqueId());
            teleportToReturn(player);
            throw new IllegalStateException("O servidor recusou o teleporte para a arena.");
        }
        fire(MatchLifecycleEvent.Type.PLAYER_JOINED, match, participant, participant.getTeamId());
        updateCountdown(match);
    }

    private void updateCountdown(final GameMatch match) {
        String id = match.getArena().getId();
        int ready = 0;
        for (GameParticipant participant : match.getParticipantList()) {
            if (participant.getState() != ParticipantState.ELIMINATED
                    && participant.getState() != ParticipantState.ABANDONED) ready++;
        }
        if (ready < match.getArena().getMinimumPlayers()) {
            BukkitTask task = countdowns.remove(id);
            cancel(task);
            countdownValues.remove(id);
            cancel(spawnOpeningTasks.remove(id));
            spawnOpeningValues.remove(id);
            if (match.getState() == MatchState.STARTING) {
                match.transition(MatchState.WAITING);
                teleportWaitingParticipantsToLobby(match);
                fire(MatchLifecycleEvent.Type.COUNTDOWN_CANCELLED, match, null, null);
            }
            return;
        }
        if (match.getState() != MatchState.WAITING) return;
        if (countdowns.containsKey(id)) return;
        match.transition(MatchState.STARTING);
        countdownValues.put(id, match.getArena().getCountdownSeconds());
        fire(MatchLifecycleEvent.Type.COUNTDOWN_STARTED, match, null, null);
        countdowns.put(id, Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                int remaining = countdownValues.get(match.getArena().getId());
                int playerCount = 0;
                for (GameParticipant participant : match.getParticipantList()) {
                    if (participant.getState() != ParticipantState.ELIMINATED
                            && participant.getState() != ParticipantState.ABANDONED) playerCount++;
                }
                if (playerCount < match.getArena().getMinimumPlayers()) {
                    updateCountdown(match);
                    return;
                }
                if (remaining <= 0) {
                    cancel(countdowns.remove(match.getArena().getId()));
                    countdownValues.remove(match.getArena().getId());
                    beginSpawnOpening(match);
                    return;
                }
                if (remaining <= 5 || remaining % 10 == 0) {
                    announce(match, "messages.lobby-countdown",
                        "&eA partida comeca em &6{time} &esegundos!",
                        "time", Integer.toString(remaining));
                }
                countdownValues.put(match.getArena().getId(), remaining - 1);
            }
        }, 20L, 20L));
    }

    private void beginSpawnOpening(final GameMatch match) {
        if (match.getState() != MatchState.STARTING) return;
        if (match.getConnectedCount() < match.getArena().getMinimumPlayers()) {
            match.transition(MatchState.WAITING);
            return;
        }
        World world = matchWorlds.get(match.getId());
        if (world == null) {
            plugin.getLogger().severe("Instancia de mundo ausente ao iniciar a partida " + match.getId() + ".");
            finish(match, MatchEndReason.CANCELLED, null);
            return;
        }
        // Templates may contain dropped items from an editing session. None should
        // be available while players wait inside their closed spawn structures.
        clearPregameItems(world);
        boolean teleportFailed = false;
        for (GameParticipant participant : match.getParticipantList()) {
            Player player = Bukkit.getPlayer(participant.getPlayerId());
            if (player != null && participant.getState() == ParticipantState.CONNECTED) {
                GameTeam team = match.getTeam(participant.getTeamId());
                Location spawn = arenas.toLocation(team.getDefinition().getSpawn(), world);
                spawn.setPitch(0.0F);
                if (!player.teleport(spawn)) {
                    fire(MatchLifecycleEvent.Type.PLAYER_LEFT, match, participant, participant.getTeamId());
                    match.removeWaitingParticipant(player.getUniqueId());
                    byPlayer.remove(player.getUniqueId());
                    teleportToReturn(player);
                    player.sendMessage(org.bukkit.ChatColor.RED
                        + "Teleporte para o spawn recusado; voce saiu da partida.");
                    teleportFailed = true;
                    continue;
                }
                player.setGameMode(GameMode.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setFallDistance(0.0F);
            }
        }
        clearPregameItems(world);
        if (teleportFailed && match.getConnectedCount() < match.getArena().getMinimumPlayers()) {
            cancel(countdowns.remove(match.getArena().getId()));
            countdownValues.remove(match.getArena().getId());
            match.transition(MatchState.WAITING);
            teleportWaitingParticipantsToLobby(match);
            updateCountdown(match);
            return;
        }
        if (match.getConnectedCount() < match.getArena().getMinimumPlayers()) {
            match.transition(MatchState.WAITING);
            teleportWaitingParticipantsToLobby(match);
            updateCountdown(match);
            return;
        }
        fire(MatchLifecycleEvent.Type.SPAWN_OPENING, match, null, null);
        final String arenaId = match.getArena().getId();
        spawnOpeningValues.put(arenaId, spawnOpeningSeconds);
        announce(match, "messages.spawn-preparing",
            "&ePrepare-se! A partida comeca em &6{time} &esegundos.",
            "time", Integer.toString(spawnOpeningSeconds));
        spawnOpeningTasks.put(arenaId, Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                Integer remaining = spawnOpeningValues.get(arenaId);
                if (remaining == null || match.getState() != MatchState.STARTING) {
                    cancel(spawnOpeningTasks.remove(arenaId));
                    spawnOpeningValues.remove(arenaId);
                    return;
                }
                if (match.getConnectedCount() < match.getArena().getMinimumPlayers()) {
                    cancel(spawnOpeningTasks.remove(arenaId));
                    spawnOpeningValues.remove(arenaId);
                    match.transition(MatchState.WAITING);
                    teleportWaitingParticipantsToLobby(match);
                    fire(MatchLifecycleEvent.Type.COUNTDOWN_CANCELLED, match, null, null);
                    updateCountdown(match);
                    return;
                }
                if (remaining <= 0) {
                    cancel(spawnOpeningTasks.remove(arenaId));
                    spawnOpeningValues.remove(arenaId);
                    startMatch(match);
                    return;
                }
                if (remaining <= 5 || remaining % 5 == 0) {
                    announce(match, "messages.spawn-countdown",
                        "&eAs barreiras abrem em &6{time} &esegundos!",
                        "time", Integer.toString(remaining));
                }
                spawnOpeningValues.put(arenaId, remaining - 1);
            }
        }, 20L, 20L));
    }

    private void startMatch(GameMatch match) {
        if (match.getState() != MatchState.STARTING
                || match.getConnectedCount() < match.getArena().getMinimumPlayers()) return;
        World world = matchWorlds.get(match.getId());
        if (world == null) {
            finish(match, MatchEndReason.CANCELLED, null);
            return;
        }
        removeSpawnStructures(match, world);
        for (GameParticipant participant : match.getParticipantList()) {
            if (participant.getState() != ParticipantState.CONNECTED) continue;
            fallProtectionUntil.put(participant.getPlayerId(), System.currentTimeMillis() + 10000L);
            Player player = Bukkit.getPlayer(participant.getPlayerId());
            if (player != null) player.setFallDistance(0.0F);
        }
        match.transition(MatchState.IN_GAME);
        announce(match, "messages.match-started",
            "&a&lA PARTIDA COMECOU! &7Proteja sua cama e destrua as adversarias.");
        fire(MatchLifecycleEvent.Type.STARTED, match, null, null);
    }

    private void clearPregameItems(World world) {
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity instanceof org.bukkit.entity.Item) entity.remove();
        }
    }

    @EventHandler
    public void onPregameChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        GameMatch match = matchInWorld(event.getWorld());
        if (match == null || match.getState() == MatchState.IN_GAME) return;
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof org.bukkit.entity.Item) entity.remove();
        }
    }

    private void teleportWaitingParticipantsToLobby(GameMatch match) {
        World world = matchWorlds.get(match.getId());
        if (world == null) return;
        Location lobby = arenas.toLocation(match.getArena().getLobby(), world);
        for (GameParticipant participant : match.getParticipantList()) {
            Player player = Bukkit.getPlayer(participant.getPlayerId());
            if (player == null || participant.getState() != ParticipantState.CONNECTED) continue;
            player.setFallDistance(0.0F);
            player.teleport(lobby);
        }
    }

    private void removeSpawnStructures(GameMatch match, World world) {
        for (TeamDefinition team : match.getArena().getTeamList()) {
            SpawnStructureDefinition structure = team.getSpawnStructure();
            if (structure == null) continue;
            for (SpawnStructureDefinition.BlockPosition position : structure.getBlocks()) {
                Block block = world.getBlockAt(position.getX(), position.getY(), position.getZ());
                if (block.getTypeId() == structure.getBlockTypeId()
                        && block.getData() == structure.getBlockData()) {
                    block.setTypeIdAndData(Material.AIR.getId(), (byte) 0, false);
                }
            }
        }
    }

    private void finish(GameMatch match, MatchEndReason reason, String winner) {
        if (!match.finish(reason, winner)) return;
        playerPlacedBlocks.remove(match.getId());
        unknownBedWarnings.removeIf(key -> key.startsWith(match.getId().toString() + ":"));
        for (GameParticipant participant : match.getParticipantList()) {
            cancel(respawnTasks.remove(participant.getPlayerId()));
            fallProtectionUntil.remove(participant.getPlayerId());
        }
        BukkitTask countdown = countdowns.remove(match.getArena().getId());
        cancel(countdown);
        countdownValues.remove(match.getArena().getId());
        cancel(spawnOpeningTasks.remove(match.getArena().getId()));
        spawnOpeningValues.remove(match.getArena().getId());
        fire(MatchLifecycleEvent.Type.ENDED, match, null, winner);
        broadcast(match, winner == null ? "Partida encerrada." : "Equipe vencedora: "
            + match.getTeam(winner).getDefinition().getDisplayName());
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                restore(match);
            }
        }, 100L);
    }

    private void resolveWinner(GameMatch match) {
        if (match.getState() != MatchState.IN_GAME) return;
        java.util.List<GameTeam> eligible = match.getEligibleTeams();
        if (eligible.size() == 1) {
            finish(match, MatchEndReason.VICTORY, eligible.get(0).getDefinition().getId());
        } else if (eligible.isEmpty()) {
            finish(match, MatchEndReason.DRAW, null);
        }
    }

    private void restore(GameMatch match) {
        World world = matchWorlds.get(match.getId());
        for (GameParticipant participant : match.getParticipantList()) {
            Player player = Bukkit.getPlayer(participant.getPlayerId());
            cancel(respawnTasks.remove(participant.getPlayerId()));
            fallProtectionUntil.remove(participant.getPlayerId());
            if (player != null) teleportToReturn(player);
            else {
                Location returnLocation = returnLocations.get(participant.getPlayerId());
                if (returnLocation != null) {
                    offlineReturnLocations.put(participant.getPlayerId(), returnLocation.clone());
                    offlineReturnModes.put(participant.getPlayerId(), returnGameModes.get(participant.getPlayerId()));
                }
            }
            byPlayer.remove(participant.getPlayerId());
            returnLocations.remove(participant.getPlayerId());
            returnGameModes.remove(participant.getPlayerId());
        }
        Iterator<Map.Entry<UUID, GameMatch>> iterator = spectators.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, GameMatch> entry = iterator.next();
            if (entry.getValue() != match) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) teleportToReturn(player);
            returnLocations.remove(entry.getKey());
            returnGameModes.remove(entry.getKey());
            iterator.remove();
        }
        fire(MatchLifecycleEvent.Type.PLAYERS_RETURNED, match, null, null);
        try {
            if (world != null && !arenas.getWorlds().removeInstance(world)) {
                throw new IOException("Instancia nao reconhecida como mundo gerenciado.");
            }
            match.transition(MatchState.RESETTING);
            match.transition(MatchState.WAITING);
            byArena.remove(match.getArena().getId());
            matchWorlds.remove(match.getId());
            fire(MatchLifecycleEvent.Type.INSTANCE_REMOVED, match, null, null);
        } catch (IOException error) {
            match.transition(MatchState.ERROR);
            plugin.getLogger().severe("Falha ao restaurar arena " + match.getArena().getId()
                + "; mundo mantido intacto e arena bloqueada: " + error.getMessage());
        }
    }

    private void fire(MatchLifecycleEvent.Type type, GameMatch match, GameParticipant participant, String team) {
        Bukkit.getPluginManager().callEvent(new MatchLifecycleEvent(type, match, participant, team));
    }

    private String message(String path, String fallback, String... tokens) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&',
            MatchMessageTemplates.format(plugin.getConfig().getString(path, fallback), tokens));
    }

    private void announce(GameMatch match, String path, String fallback, String... tokens) {
        broadcast(match, message(path, fallback, tokens));
    }

    /** Get the killer before prepareValidDeath consumes recent combat attribution. */
    private Player deathKiller(Player victim, GameMatch match, GameParticipant participant) {
        Player direct = victim.getKiller();
        if (validKiller(direct, match, participant)) return direct;
        RecentAttacker previous = lastAttackers.get(victim.getUniqueId());
        if (previous == null || !previous.matchId.equals(match.getId())
                || System.currentTimeMillis() - previous.atMillis > 15000L) return null;
        Player recent = Bukkit.getPlayer(previous.attackerId);
        return validKiller(recent, match, participant) ? recent : null;
    }

    private String deathReason(DamageCause cause) {
        if (cause == null) return "generic";
        switch (cause) {
            case VOID: return "void";
            case FALL: return "fall";
            case FIRE: case FIRE_TICK: return "fire";
            case LAVA: return "lava";
            case DROWNING: return "drowning";
            case BLOCK_EXPLOSION: case ENTITY_EXPLOSION: return "explosion";
            case PROJECTILE: return "projectile";
            case ENTITY_ATTACK: return "combat";
            default: return "generic";
        }
    }

    private void announcePlayerDeath(GameMatch match, Player victim, GameParticipant participant) {
        DamageCause cause = victim.getLastDamageCause() == null ? null
            : victim.getLastDamageCause().getCause();
        String reason = deathReason(cause);
        Player killer = deathKiller(victim, match, participant);
        boolean finalKill = !match.getTeam(participant.getTeamId()).isBedAlive();
        String key = MatchMessageTemplates.deathKey(reason, killer != null, finalKill);
        String fallback;
        if ("void".equals(reason)) {
            fallback = killer == null ? "&7{player} &7caiu no void."
                : "&7{player} &7foi derrubado no void por &e{killer}&7.";
        } else if ("fall".equals(reason)) {
            fallback = killer == null ? "&7{player} &7morreu de queda."
                : "&7{player} &7foi derrubado por &e{killer}&7.";
        } else if ("fire".equals(reason) || "lava".equals(reason)) {
            fallback = "&7{player} &7morreu queimado.";
        } else if ("drowning".equals(reason)) {
            fallback = "&7{player} &7se afogou.";
        } else if ("explosion".equals(reason)) {
            fallback = killer == null ? "&7{player} &7foi explodido."
                : "&7{player} &7foi explodido por &e{killer}&7.";
        } else {
            fallback = killer == null ? "&7{player} &7morreu."
                : "&7{player} &7foi eliminado por &e{killer}&7.";
        }
        // Non-duplicated final-kill suffix with configurable overrides.
        String selected = plugin.getConfig().getString(key);
        if (selected == null) {
            String nonfinal = MatchMessageTemplates.deathKey(reason, killer != null, false);
            selected = plugin.getConfig().getString(nonfinal, fallback);
            if (finalKill) selected += " &b&lABATE FINAL!";
        }
        broadcast(match, org.bukkit.ChatColor.translateAlternateColorCodes('&',
            MatchMessageTemplates.format(selected, "player", victim.getName(),
                "killer", killer == null ? "" : killer.getName())));
    }

    private void broadcast(GameMatch match, String message) {
        for (GameParticipant participant : match.getParticipantList()) {
            Player player = Bukkit.getPlayer(participant.getPlayerId());
            if (player != null) player.sendMessage(org.bukkit.ChatColor.AQUA + "[BedWars] "
                + org.bukkit.ChatColor.YELLOW + message);
        }
    }

    private void teleportToReturn(Player player) {
        Location location = returnLocations.remove(player.getUniqueId());
        if (location == null || location.getWorld() == null) {
            World fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (fallback != null) location = fallback.getSpawnLocation();
        }
        if (location != null && location.getWorld() != null) {
            GameMode gameMode = returnGameModes.remove(player.getUniqueId());
            player.setGameMode(gameMode == null ? GameMode.SURVIVAL : gameMode);
            player.teleport(location);
        } else {
            returnGameModes.remove(player.getUniqueId());
        }
    }

    private void cancel(BukkitTask task) {
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        if (spectators.remove(player.getUniqueId()) != null) {
            returnLocations.remove(player.getUniqueId());
            returnGameModes.remove(player.getUniqueId());
            return;
        }
        final GameMatch match = byPlayer.get(player.getUniqueId());
        if (match == null) return;
        if (match.getState() == MatchState.WAITING || match.getState() == MatchState.STARTING) {
            leave(player);
            return;
        }
        if (match.getState() != MatchState.IN_GAME) return;
        final UUID uuid = player.getUniqueId();
        GameParticipant participant = match.getParticipant(uuid);
        if (participant == null || participant.getState() == ParticipantState.ELIMINATED
                || participant.getState() == ParticipantState.ABANDONED) {
            byPlayer.remove(uuid);
            returnLocations.remove(uuid);
            returnGameModes.remove(uuid);
            return;
        }
        cancel(respawnTasks.remove(uuid));
        if (reconnect == null || !reconnect.disconnected(player, match, participant)) {
            match.setParticipantState(uuid, ParticipantState.ABANDONED);
            byPlayer.remove(uuid);
            returnLocations.remove(uuid);
            returnGameModes.remove(uuid);
            resolveWinner(match);
        }
    }

    @EventHandler public void onJoinAfterMatch(org.bukkit.event.player.PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final Location old = offlineReturnLocations.remove(player.getUniqueId());
        final GameMode mode = offlineReturnModes.remove(player.getUniqueId());
        if (old == null) return;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                World world = old.getWorld() == null ? null : Bukkit.getWorld(old.getWorld().getName());
                Location destination = world == null || world != old.getWorld()
                    ? (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation()) : old;
                if (destination != null) player.teleport(destination);
                player.setGameMode(mode == null ? GameMode.SURVIVAL : mode);
            }
        });
    }

    public boolean restoreReconnect(Player player, ReconnectSession session) {
        GameMatch match = byPlayer.get(player.getUniqueId());
        if (match == null || match.getState() != MatchState.IN_GAME
                || !match.getId().equals(session.getMatchId())) return false;
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        World world = matchWorlds.get(match.getId());
        if (participant == null || participant.getState() != ParticipantState.DISCONNECTED
                || world == null || Bukkit.getWorld(world.getName()) != world) return false;
        GameTeam team = match.getTeam(session.getTeamId());
        if (team == null) return false;
        boolean died = session.getLifeState() == ParticipantState.RESPAWNING;
        boolean eliminated = died && !team.isBedAlive();
        Location target = session.getSnapshot().getLocation();
        if (eliminated && match.getArena().getSpectator() != null) {
            target = arenas.toLocation(match.getArena().getSpectator(), world);
        } else if (died || target.getWorld() != world || !SafeLocationResolver.hasSafeFloor(target)) {
            target = teamRespawn(team.getDefinition(), world);
        }
        if (target == null || target.getWorld() != world) return false;
        Location previousLocation = player.getLocation().clone();
        GameMode previousMode = player.getGameMode();
        PlayerStateSnapshot previousState = new PlayerStateSnapshot(player);
        if (!player.teleport(target)) return false;
        try {
        if (died) {
            session.getSnapshot().restore(player);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            if (eliminated) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
                player.setGameMode(GameMode.SPECTATOR);
                match.setParticipantState(player.getUniqueId(), ParticipantState.ELIMINATED);
                fire(MatchLifecycleEvent.Type.PLAYER_ELIMINATED, match, participant, participant.getTeamId());
                resolveWinnerLater(match);
            } else {
                if (equipment != null) equipment.rebuildAfterDeath(player, match, participant);
                player.setGameMode(GameMode.ADVENTURE);
                match.setParticipantState(player.getUniqueId(), ParticipantState.RESPAWNING);
                final UUID playerId = player.getUniqueId();
                cancel(respawnTasks.remove(playerId));
                respawnTasks.put(playerId, Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override public void run() {
                        GameMatch currentMatch = byPlayer.get(playerId);
                        Player current = Bukkit.getPlayer(playerId);
                        if (currentMatch != match || current == null || !current.isOnline()
                                || match.getState() != MatchState.IN_GAME) return;
                        GameParticipant part = match.getParticipant(playerId);
                        if (part == null || part.getState() != ParticipantState.RESPAWNING) return;
                        if (!match.getTeam(part.getTeamId()).isBedAlive()) {
                            match.setParticipantState(playerId, ParticipantState.ELIMINATED);
                            current.setGameMode(GameMode.SPECTATOR);
                            fire(MatchLifecycleEvent.Type.PLAYER_ELIMINATED, match, part, part.getTeamId());
                            resolveWinner(match);
                        } else {
                            current.setGameMode(GameMode.SURVIVAL);
                            match.setParticipantState(playerId, ParticipantState.CONNECTED);
                            fallProtectionUntil.put(playerId, System.currentTimeMillis() + 3000L);
                        }
                        respawnTasks.remove(playerId);
                    }
                }, respawnSeconds * 20L));
            }
        } else {
            session.getSnapshot().restore(player);
            player.setGameMode(GameMode.SURVIVAL);
            match.setParticipantState(player.getUniqueId(), ParticipantState.CONNECTED);
        }
        fire(MatchLifecycleEvent.Type.PLAYER_RECONNECTED, match, participant, participant.getTeamId());
        return true;
        } catch (RuntimeException error) {
            try {
                previousState.restore(player);
                player.setGameMode(previousMode);
                player.teleport(previousLocation);
            } catch (RuntimeException rollbackFailure) {
                plugin.getLogger().severe("Rollback de reconnect falhou para " + player.getUniqueId());
            }
            if (match.getState() == MatchState.IN_GAME
                    && participant.getState() != ParticipantState.ELIMINATED) {
                match.setParticipantState(player.getUniqueId(), ParticipantState.DISCONNECTED);
            }
            throw error;
        }
    }

    public void expireReconnect(ReconnectSession session) {
        GameMatch match = byPlayer.get(session.getPlayerId());
        if (match == null || !match.getId().equals(session.getMatchId())
                || match.getState() != MatchState.IN_GAME) return;
        GameParticipant participant = match.getParticipant(session.getPlayerId());
        if (participant == null || participant.getState() != ParticipantState.DISCONNECTED) return;
        match.setParticipantState(session.getPlayerId(), ParticipantState.ABANDONED);
        byPlayer.remove(session.getPlayerId());
        fire(MatchLifecycleEvent.Type.PLAYER_LEFT, match, participant, participant.getTeamId());
        Player online = Bukkit.getPlayer(session.getPlayerId());
        if (online != null) teleportToReturn(online);
        returnLocations.remove(session.getPlayerId());
        returnGameModes.remove(session.getPlayerId());
        resolveWinner(match);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        GameMatch match = byPlayer.get(player.getUniqueId());
        if (match == null || match.getState() != MatchState.IN_GAME) return;
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        if (participant == null || participant.getState() != ParticipantState.CONNECTED) return;
        event.setDeathMessage(null); // Prevent duplicate server-wide vanilla death broadcasts.
        announcePlayerDeath(match, player, participant);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        prepareValidDeath(player, match, participant);
        GameTeam team = match.getTeam(participant.getTeamId());
        if (!team.isBedAlive()) {
            match.setParticipantState(player.getUniqueId(), ParticipantState.ELIMINATED);
            fire(MatchLifecycleEvent.Type.PLAYER_ELIMINATED, match, participant, participant.getTeamId());
            resolveWinnerLater(match);
        } else {
            match.setParticipantState(player.getUniqueId(), ParticipantState.RESPAWNING);
            cancel(respawnTasks.remove(player.getUniqueId()));
        }
        // Evita a tela de morte e inicia imediatamente o periodo de espectador.
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                if (player.isOnline() && player.isDead()) player.spigot().respawn();
            }
        }, 2L);
    }

    public void processDisconnectDeath(Player player, GameMatch match, GameParticipant participant) {
        if (byPlayer.get(player.getUniqueId()) != match || match.getState() != MatchState.IN_GAME
                || participant.getState() != ParticipantState.CONNECTED) return;
        prepareValidDeath(player, match, participant);
    }

    private void prepareValidDeath(Player player, GameMatch match, GameParticipant participant) {
        Player killer = validKiller(player.getKiller(), match, participant) ? player.getKiller() : null;
        RecentAttacker recent = lastAttackers.remove(player.getUniqueId());
        if (killer == null && recent != null && recent.matchId.equals(match.getId())
                && System.currentTimeMillis() - recent.atMillis <= 15000L) {
            Player candidate = Bukkit.getPlayer(recent.attackerId);
            if (validKiller(candidate, match, participant)) killer = candidate;
        }
        transferResources(player, killer);
        if (equipment != null) equipment.onValidDeath(player, participant);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        player.closeInventory();
    }

    private boolean validKiller(Player candidate, GameMatch match, GameParticipant victim) {
        if (candidate == null || candidate.getUniqueId().equals(victim.getPlayerId())
                || byPlayer.get(candidate.getUniqueId()) != match
                || candidate.getWorld() != matchWorlds.get(match.getId())) return false;
        GameParticipant attacker = match.getParticipant(candidate.getUniqueId());
        return attacker != null && attacker.getState() == ParticipantState.CONNECTED
            && !attacker.getTeamId().equals(victim.getTeamId());
    }

    private void transferResources(Player victim, Player killer) {
        for (org.bukkit.inventory.ItemStack stack : victim.getInventory().getContents()) {
            if (stack == null || stack.getAmount() <= 0) continue;
            boolean resource = false;
            for (ResourceType type : ResourceType.values()) {
                if (stack.getType() == type.getMaterial()) { resource = true; break; }
            }
            if (!resource) continue;
            org.bukkit.inventory.ItemStack copy = stack.clone();
            if (killer == null) {
                victim.getWorld().dropItemNaturally(victim.getLocation(), copy);
                continue;
            }
            Map<Integer, org.bukkit.inventory.ItemStack> overflow = killer.getInventory().addItem(copy);
            for (org.bukkit.inventory.ItemStack leftover : overflow.values()) {
                if (leftover != null && leftover.getAmount() > 0) {
                    victim.getWorld().dropItemNaturally(victim.getLocation(), leftover);
                }
            }
        }
        if (killer != null) killer.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final GameMatch match = byPlayer.get(player.getUniqueId());
        if (match == null) return;
        final GameParticipant participant = match.getParticipant(player.getUniqueId());
        if (participant == null) return;
        if (match.getState() != MatchState.IN_GAME) {
            Location fallback = returnLocations.get(player.getUniqueId());
            if (fallback == null || fallback.getWorld() == null) {
                World lobby = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                fallback = lobby == null ? null : lobby.getSpawnLocation();
            }
            if (fallback != null) event.setRespawnLocation(fallback.clone());
            return;
        }
        final World world = matchWorlds.get(match.getId());
        if (world == null) return;
        if (participant.getState() == ParticipantState.ELIMINATED) {
            Position spectator = match.getArena().getSpectator();
            event.setRespawnLocation(spectator == null
                ? world.getSpawnLocation() : arenas.toLocation(spectator, world));
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (match.getState() == MatchState.IN_GAME && player.isOnline()) {
                        player.setGameMode(GameMode.SPECTATOR);
                    }
                }
            });
            return;
        }
        if (participant.getState() != ParticipantState.RESPAWNING) return;
        TeamDefinition team = match.getTeam(participant.getTeamId()).getDefinition();
        Location respawn = teamRespawn(team, world);
        respawn.setPitch(0.0F);
        event.setRespawnLocation(respawn);
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (match.getState() != MatchState.IN_GAME || !player.isOnline()
                        || participant.getState() != ParticipantState.RESPAWNING) return;
                player.setGameMode(GameMode.SPECTATOR);
                if (equipment != null && match.getTeam(participant.getTeamId()).isBedAlive()) {
                    equipment.rebuildAfterDeath(player, match, participant);
                }
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setFallDistance(0.0F);
                cancel(respawnTasks.remove(player.getUniqueId()));
                respawnTasks.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override public void run() {
                        GameParticipant current = match.getParticipant(player.getUniqueId());
                        if (player.isOnline() && match.getState() == MatchState.IN_GAME
                                && current != null && current.getState() == ParticipantState.RESPAWNING) {
                            GameTeam currentTeam = match.getTeam(current.getTeamId());
                            if (!currentTeam.isBedAlive()) {
                                match.setParticipantState(player.getUniqueId(), ParticipantState.ELIMINATED);
                                player.setGameMode(GameMode.SPECTATOR);
                                fire(MatchLifecycleEvent.Type.PLAYER_ELIMINATED, match, current,
                                    current.getTeamId());
                                resolveWinner(match);
                            } else {
                                Location returnSpawn = teamRespawn(currentTeam.getDefinition(), world);
                                returnSpawn.setPitch(0.0F);
                                player.teleport(returnSpawn);
                                player.setGameMode(GameMode.SURVIVAL);
                                player.setFallDistance(0.0F);
                                fallProtectionUntil.put(player.getUniqueId(),
                                    System.currentTimeMillis() + 3000L);
                                match.setParticipantState(player.getUniqueId(), ParticipantState.CONNECTED);
                                player.sendMessage(org.bukkit.ChatColor.GREEN + "Voce renasceu.");
                            }
                        }
                        respawnTasks.remove(player.getUniqueId());
                    }
                }, respawnSeconds * 20L));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        GameMatch match = matchInWorld(event.getBlock().getWorld());
        if (match == null) return;
        event.setCancelled(true);
        if (match.getState() != MatchState.IN_GAME) return;
        GameMatch playerMatch = byPlayer.get(event.getPlayer().getUniqueId());
        GameParticipant participant = match.getParticipant(event.getPlayer().getUniqueId());
        if (playerMatch != match || participant == null
                || participant.getState() != ParticipantState.CONNECTED
                || spectators.containsKey(event.getPlayer().getUniqueId())) return;
        if (event.getBlock().getType() == Material.BED_BLOCK) {
            destroyBedFromPlayer(match, participant, event.getPlayer(), event.getBlock());
            return;
        }
        Set<String> placed = playerPlacedBlocks.get(match.getId());
        if (placed != null && placed.contains(blockKey(event.getBlock()))) event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlacedBroken(BlockBreakEvent event) {
        GameMatch match = matchInWorld(event.getBlock().getWorld());
        if (match == null) return;
        Set<String> placed = playerPlacedBlocks.get(match.getId());
        if (placed != null) placed.remove(blockKey(event.getBlock()));
    }

    private GameTeam teamForBed(GameMatch match, Block block) {
        for (GameTeam team : match.getTeams().values()) {
            if (isBedBlock(block, team.getDefinition().getBed())) return team;
        }
        return null;
    }

    private void destroyBedFromPlayer(GameMatch match, GameParticipant participant,
                                      Player player, Block block) {
        GameTeam bedTeam = teamForBed(match, block);
        if (bedTeam == null) {
            player.sendMessage(org.bukkit.ChatColor.RED
                + "Esta cama nao corresponde a uma cama configurada na arena. Avise um administrador.");
            String warningKey = match.getId() + ":" + blockKey(block);
            if (unknownBedWarnings.add(warningKey)) {
                plugin.getLogger().warning("Cama nao reconhecida: arena=" + match.getArena().getId()
                    + " bloco=" + block.getX() + "," + block.getY() + "," + block.getZ()
                    + " equipeDoJogador=" + participant.getTeamId()
                    + ". Confira o ponto de cama salvo e o template da arena.");
            }
            return;
        }
        if (participant.getTeamId().equals(bedTeam.getDefinition().getId())) {
            player.sendMessage(message("messages.own-bed",
                "&cVoce nao pode destruir sua propria cama!"));
            return;
        }
        if (!bedTeam.isBedAlive()) return;
        if (!match.destroyBed(bedTeam.getDefinition().getId())) return;
        removeBedBlocks(block, bedTeam.getDefinition().getBed());
        fire(MatchLifecycleEvent.Type.BED_DESTROYED, match, participant,
            bedTeam.getDefinition().getId());
    }

    private boolean isBedBlock(Block block, Position bed) {
        if (bed == null || block.getType() != Material.BED_BLOCK) return false;
        int x = (int) Math.floor(bed.getX());
        int y = (int) Math.floor(bed.getY());
        int z = (int) Math.floor(bed.getZ());
        if (block.getY() != y) return false;
        if (block.getX() == x && block.getZ() == z) return true;
        Block configured = block.getWorld().getBlockAt(x, y, z);
        return configured.getType() == Material.BED_BLOCK
            && Math.abs(block.getX() - x) + Math.abs(block.getZ() - z) == 1;
    }

    private void removeBedBlocks(Block brokenBlock, Position savedBed) {
        Block other = bedCounterpart(brokenBlock);
        if ((other == null || other.getType() != Material.BED_BLOCK) && savedBed != null) {
            Block configured = brokenBlock.getWorld().getBlockAt(
                (int) Math.floor(savedBed.getX()), (int) Math.floor(savedBed.getY()),
                (int) Math.floor(savedBed.getZ()));
            if (configured != brokenBlock && configured.getType() == Material.BED_BLOCK
                    && Math.abs(configured.getX() - brokenBlock.getX())
                        + Math.abs(configured.getZ() - brokenBlock.getZ()) == 1) {
                other = configured;
            }
        }
        if (other == null || other.getType() != Material.BED_BLOCK) {
            for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.EAST,
                    BlockFace.SOUTH, BlockFace.WEST}) {
                Block adjacent = brokenBlock.getRelative(face);
                if (adjacent.getType() == Material.BED_BLOCK) {
                    other = adjacent;
                    break;
                }
            }
        }
        if (other != null && other.getType() == Material.BED_BLOCK) other.setType(Material.AIR, false);
        brokenBlock.setType(Material.AIR, false);
    }

    private Block bedCounterpart(Block block) {
        org.bukkit.material.MaterialData data = block.getState().getData();
        if (!(data instanceof org.bukkit.material.Bed)) return null;
        org.bukkit.material.Bed bed = (org.bukkit.material.Bed) data;
        BlockFace facing = bed.getFacing();
        if (facing != BlockFace.NORTH && facing != BlockFace.SOUTH
                && facing != BlockFace.EAST && facing != BlockFace.WEST) return null;
        return block.getRelative(bed.isHeadOfBed() ? facing.getOppositeFace() : facing);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        GameMatch match = matchInWorld(event.getBlock().getWorld());
        if (match == null) return;
        GameParticipant participant = match.getParticipant(event.getPlayer().getUniqueId());
        if (match.getState() != MatchState.IN_GAME || participant == null
                || participant.getState() != ParticipantState.CONNECTED
                || spectators.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        ArenaRegion mainRegion = match.getArena().getRegion(ArenaRegionType.MAIN);
        org.bukkit.Location placed = event.getBlockPlaced().getLocation();
        Position position = new Position(match.getArena().getSourceWorld(),
            placed.getBlockX() + 0.5D, placed.getBlockY() + 0.5D, placed.getBlockZ() + 0.5D,
            0.0F, 0.0F);
        if (mainRegion == null || !mainRegion.contains(position)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlacedAccepted(BlockPlaceEvent event) {
        GameMatch match = matchInWorld(event.getBlockPlaced().getWorld());
        if (match == null || match.getState() != MatchState.IN_GAME
                || byPlayer.get(event.getPlayer().getUniqueId()) != match) return;
        Set<String> placed = playerPlacedBlocks.get(match.getId());
        if (placed == null) {
            placed = new HashSet<String>();
            playerPlacedBlocks.put(match.getId(), placed);
        }
        placed.add(blockKey(event.getBlockPlaced()));
    }

    private String blockKey(Block block) {
        return block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    /** Register only new ability-generated blocks for normal break/explosion protection. */
    public boolean placeSpecialBlock(GameMatch match, Block block, Material material, byte data) {
        if (match == null || block == null || material != Material.WOOL
                || block.getType() != Material.AIR || match.getState() != MatchState.IN_GAME
                || matchWorlds.get(match.getId()) != block.getWorld()) return false;
        ArenaRegion region = match.getArena().getRegion(ArenaRegionType.MAIN);
        Location at = block.getLocation();
        Position position = new Position(match.getArena().getSourceWorld(),
            at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, 0.0F, 0.0F);
        if (region == null || !region.contains(position)) return false;
        block.setType(material);
        block.setData(data);
        Set<String> placed = playerPlacedBlocks.get(match.getId());
        if (placed == null) {
            placed = new HashSet<String>();
            playerPlacedBlocks.put(match.getId(), placed);
        }
        placed.add(blockKey(block));
        return true;
    }

    /** Release a TNT placement after the block has become a primed entity. */
    void forgetPlacedBlock(GameMatch match, Block block) {
        if (match == null || block == null) return;
        Set<String> placed = playerPlacedBlocks.get(match.getId());
        if (placed != null) placed.remove(blockKey(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        GameMatch match = matchInWorld(event.getPlayer().getWorld());
        if (match == null) return;
        GameParticipant participant = match.getParticipant(event.getPlayer().getUniqueId());
        if (match.getState() != MatchState.IN_GAME || participant == null
                || participant.getState() != ParticipantState.CONNECTED) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.BED_BLOCK) {
            event.setCancelled(true);
            if (byPlayer.get(event.getPlayer().getUniqueId()) == match
                    && !spectators.containsKey(event.getPlayer().getUniqueId())) {
                destroyBedFromPlayer(match, participant, event.getPlayer(), event.getClickedBlock());
            }
            return;
        }
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && protectedMapInteraction(event.getClickedBlock().getType())) {
            event.setCancelled(true);
        }
    }

    static boolean protectedMapInteraction(Material material) {
        if (material == null) return false;
        switch (material) {
            case BED_BLOCK:
            case ENCHANTMENT_TABLE:
            case ANVIL:
            case WORKBENCH:
            case FURNACE:
            case BURNING_FURNACE:
            case BREWING_STAND:
            case BEACON:
            case HOPPER:
            case DISPENSER:
            case DROPPER:
            case JUKEBOX:
            case NOTE_BLOCK:
            case CAULDRON:
            case LEVER:
            case STONE_BUTTON:
            case WOOD_BUTTON:
            case TRAP_DOOR:
            case WOODEN_DOOR:
            case IRON_DOOR_BLOCK:
            case SIGN_POST:
            case WALL_SIGN:
                return true;
            default:
                return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBedEnter(org.bukkit.event.player.PlayerBedEnterEvent event) {
        if (matchInWorld(event.getPlayer().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVanillaInventory(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (matchInWorld(event.getPlayer().getWorld()) == null) return;
        switch (event.getInventory().getType()) {
            case ENCHANTING:
            case ANVIL:
            case WORKBENCH:
            case FURNACE:
            case BREWING:
            case BEACON:
            case HOPPER:
            case DISPENSER:
            case DROPPER:
            case MERCHANT:
                event.setCancelled(true);
                break;
            default:
                break;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        if (matchInWorld(event.getPlayer().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(org.bukkit.event.player.PlayerBucketFillEvent event) {
        if (matchInWorld(event.getPlayer().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(org.bukkit.event.player.PlayerPortalEvent event) {
        if (matchInWorld(event.getFrom().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreak(org.bukkit.event.hanging.HangingBreakEvent event) {
        if (matchInWorld(event.getEntity().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingPlace(org.bukkit.event.hanging.HangingPlaceEvent event) {
        if (matchInWorld(event.getEntity().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDispense(org.bukkit.event.block.BlockDispenseEvent event) {
        if (matchInWorld(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockSpread(org.bukkit.event.block.BlockSpreadEvent event) {
        if (matchInWorld(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockForm(org.bukkit.event.block.BlockFormEvent event) {
        if (matchInWorld(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMapEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (matchInWorld(event.getPlayer().getWorld()) != null
                && (event.getRightClicked() instanceof org.bukkit.entity.Hanging
                    || event.getRightClicked() instanceof org.bukkit.entity.ArmorStand)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() == event.getTo().getWorld()
                && event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        GameMatch match = byPlayer.get(player.getUniqueId());
        GameParticipant participant = match == null ? null : match.getParticipant(player.getUniqueId());
        if (match == null || match.getState() != MatchState.IN_GAME || participant == null
                || participant.getState() != ParticipantState.CONNECTED) return;
        World matchWorld = matchWorlds.get(match.getId());
        if (matchWorld == null) return;
        if (event.getTo().getWorld() != matchWorld) {
            event.setTo(event.getFrom());
            return;
        }
        ArenaDefinition arena = match.getArena();
        if (event.getTo().getY() < arena.getVoidHeight()) {
            player.setHealth(0.0D);
            return;
        }
        if (event.getTo().getY() >= arena.getCeilingHeight()) {
            Location capped = event.getTo().clone();
            capped.setY(arena.getCeilingHeight() - 0.1D);
            event.setTo(capped);
            return;
        }
        ArenaRegion bounds = arena.getBounds();
        if (bounds != null && (event.getTo().getBlockX() < bounds.getMinimumX()
                || event.getTo().getBlockX() > bounds.getMaximumX()
                || event.getTo().getBlockZ() < bounds.getMinimumZ()
                || event.getTo().getBlockZ() > bounds.getMaximumZ())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        GameMatch match = matchInWorld(event.getLocation().getWorld());
        if (match == null) return;
        protectExplosionBlocks(match, event.blockList());
        // Defensas destruidas nao retornam ao inventario por drops vanilla.
        event.setYield(0.0F);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        GameMatch match = matchInWorld(event.getBlock().getWorld());
        if (match == null) return;
        protectExplosionBlocks(match, event.blockList());
        event.setYield(0.0F);
    }

    /**
     * Explosoes podem destruir somente blocos registrados como colocados pelos
     * participantes desta partida. A construcao original e as camas sao protegidas.
     */
    private void protectExplosionBlocks(GameMatch match, java.util.List<Block> affected) {
        Set<String> placed = playerPlacedBlocks.get(match.getId());
        boolean playing = match.getState() == MatchState.IN_GAME;
        java.util.Iterator<Block> iterator = affected.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (block.getType() == Material.BED_BLOCK
                    || !ExplosionBlockPolicy.canDestroy(playing, placed, blockKey(block))) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosionFinished(EntityExplodeEvent event) {
        GameMatch match = matchInWorld(event.getLocation().getWorld());
        if (match != null) forgetExplodedBlocks(match, event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosionFinished(BlockExplodeEvent event) {
        GameMatch match = matchInWorld(event.getBlock().getWorld());
        if (match != null) forgetExplodedBlocks(match, event.blockList());
    }

    private void forgetExplodedBlocks(GameMatch match, java.util.List<Block> destroyed) {
        Set<String> placed = playerPlacedBlocks.get(match.getId());
        if (placed == null) return;
        for (Block block : destroyed) placed.remove(blockKey(block));
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (matchInWorld(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (matchInWorld(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockFlow(BlockFromToEvent event) {
        if (matchInWorld(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onEntityBlockChange(EntityChangeBlockEvent event) {
        if (matchInWorld(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        GameMatch match = matchInWorld(player.getWorld());
        if (match == null && !byPlayer.containsKey(player.getUniqueId())
                && !spectators.containsKey(player.getUniqueId())) return;
        GameParticipant participant = match == null ? null : match.getParticipant(player.getUniqueId());
        if (match == null || byPlayer.get(player.getUniqueId()) != match
                || match.getState() != MatchState.IN_GAME || participant == null
                || participant.getState() != ParticipantState.CONNECTED) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Long fallProtection = fallProtectionUntil.get(player.getUniqueId());
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && fallProtection != null) {
            if (System.currentTimeMillis() < fallProtection) {
                event.setCancelled(true);
                player.setFallDistance(0.0F);
                return;
            }
            fallProtectionUntil.remove(player.getUniqueId());
        }
        GameMatch match = matchInWorld(player.getWorld());
        if (match == null) return;
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        if (spectators.containsKey(player.getUniqueId()) || match.getState() != MatchState.IN_GAME
                || participant == null || participant.getState() != ParticipantState.CONNECTED) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        GameMatch match = matchInWorld(victim.getWorld());
        if (match == null) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) event.getDamager()).getShooter();
            if (shooter instanceof Player) attacker = (Player) shooter;
        }
        if (attacker == null) return;
        GameParticipant victimParticipant = match.getParticipant(victim.getUniqueId());
        GameParticipant attackerParticipant = match.getParticipant(attacker.getUniqueId());
        if (match.getState() != MatchState.IN_GAME || victimParticipant == null || attackerParticipant == null
                || victimParticipant.getState() != ParticipantState.CONNECTED
                || attackerParticipant.getState() != ParticipantState.CONNECTED
                || victimParticipant.getTeamId().equals(attackerParticipant.getTeamId())) {
            event.setCancelled(true);
        } else if (!event.isCancelled()) {
            lastAttackers.put(victim.getUniqueId(), new RecentAttacker(
                attacker.getUniqueId(), match.getId(), System.currentTimeMillis()));
        }
    }

    private Location teamRespawn(TeamDefinition team, World world) {
        Position configured = team.getRespawn();
        if (configured != null) {
            Location preferred = arenas.toLocation(configured, world);
            if (SafeLocationResolver.hasSafeFloor(preferred)) return preferred;
            Location nearby = SafeLocationResolver.findSurface(preferred, 3);
            if (nearby != null) return nearby;
        }
        Position bed = team.getBed();
        if (bed != null) {
            Location nearBed = SafeLocationResolver.findSurface(arenas.toLocation(bed, world), 5);
            if (nearBed != null) return nearBed;
        }
        Position spectator = null;
        for (GameMatch match : byArena.values()) {
            if (world == matchWorlds.get(match.getId())) {
                spectator = match.getArena().getSpectator();
                break;
            }
        }
        if (spectator != null) {
            Location safe = SafeLocationResolver.findSurface(arenas.toLocation(spectator, world), 5);
            if (safe != null) return safe;
        }
        plugin.getLogger().warning("Sem respawn seguro para a equipe " + team.getId()
            + " no mundo " + world.getName() + ".");
        return world.getSpawnLocation();
    }

    private GameMatch matchInWorld(World world) {
        for (Map.Entry<UUID, World> entry : matchWorlds.entrySet()) {
            if (entry.getValue().equals(world)) {
                for (GameMatch match : byArena.values()) {
                    if (match.getId().equals(entry.getKey())) return match;
                }
            }
        }
        return null;
    }

    private void resolveWinnerLater(final GameMatch match) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() { resolveWinner(match); }
        });
    }

    private static final class RecentAttacker {
        private final UUID attackerId;
        private final UUID matchId;
        private final long atMillis;
        private RecentAttacker(UUID attackerId, UUID matchId, long atMillis) {
            this.attackerId = attackerId;
            this.matchId = matchId;
            this.atMillis = atMillis;
        }
    }

    private final class ArenaServiceWorldCallback implements com.starfinplugins.bedwars.arena.WorldInstanceManager.Completion {
        private final ArenaDefinition arena;
        private final Player firstPlayer;

        private ArenaServiceWorldCallback(ArenaDefinition arena, Player firstPlayer) {
            this.arena = arena;
            this.firstPlayer = firstPlayer;
        }

        @Override
        public void complete(World world, Exception error) {
            loading.remove(arena.getId());
            if (error != null) {
                firstPlayer.sendMessage("§cFalha ao preparar arena: " + error.getMessage());
                plugin.getLogger().warning("Falha ao preparar arena " + arena.getId() + ": " + error.getMessage());
                return;
            }
            if (!arena.isEnabled() || byArena.containsKey(arena.getId())) {
                try {
                    arenas.getWorlds().removeInstance(world);
                } catch (IOException cleanupError) {
                    plugin.getLogger().severe("Instancia de arena abandonada e preservada: "
                        + world.getName() + ": " + cleanupError.getMessage());
                }
                return;
            }
            if (!firstPlayer.isOnline()) {
                try {
                    arenas.getWorlds().removeInstance(world);
                } catch (IOException cleanupError) {
                    plugin.getLogger().severe("Instancia mantida apos desconexao durante carregamento: "
                        + world.getName() + ": " + cleanupError.getMessage());
                }
                return;
            }
            GameMatch match = new GameMatch(arena);
            byArena.put(arena.getId(), match);
            matchWorlds.put(match.getId(), world);
            fire(MatchLifecycleEvent.Type.CREATED, match, null, null);
            try {
                addPlayer(match, firstPlayer);
            } catch (RuntimeException joinError) {
                byArena.remove(arena.getId());
                matchWorlds.remove(match.getId());
                try {
                    arenas.getWorlds().removeInstance(world);
                } catch (IOException cleanupError) {
                    joinError.addSuppressed(cleanupError);
                }
                firstPlayer.sendMessage("§cNao foi possivel entrar na arena: " + joinError.getMessage());
            }
        }
    }
}
