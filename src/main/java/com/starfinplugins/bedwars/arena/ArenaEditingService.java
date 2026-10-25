package com.starfinplugins.bedwars.arena;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.world.WorldManagementService;
import com.starfinplugins.bedwars.world.WorldManagementService.WorldType;
import com.starfinplugins.bedwars.world.SafeLocationResolver;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class ArenaEditingService implements ManagedService, Listener {
    private static final int PUBLISH_DESTINATION_SEARCH_RADIUS = 32;
    private final Plugin plugin;
    private final ArenaService arenas;
    private final WorldManagementService worldManagement;
    private com.starfinplugins.bedwars.setup.ArenaSetupWizardService setupWizard;
    private final Map<String, EditSession> sessions = new HashMap<String, EditSession>();
    private final Map<UUID, Location> returnLocations = new HashMap<UUID, Location>();
    private final Map<UUID, PlayerState> playerStates = new HashMap<UUID, PlayerState>();

    public ArenaEditingService(Plugin plugin, ArenaService arenas) {
        this(plugin, arenas, null);
    }

    public ArenaEditingService(Plugin plugin, ArenaService arenas, WorldManagementService worldManagement) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.worldManagement = worldManagement;
    }

    public void setSetupWizard(com.starfinplugins.bedwars.setup.ArenaSetupWizardService setupWizard) {
        this.setupWizard = setupWizard;
    }

    @Override
    public String getName() { return "arena-editing"; }

    @Override
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void stop() throws Exception {
        org.bukkit.event.HandlerList.unregisterAll(this);
        IOException failure = null;
        for (EditSession session : new java.util.ArrayList<EditSession>(sessions.values())) {
            try {
                if (session.world != null && !session.publishing
                        && !arenas.getWorlds().removeInstance(session.world)) {
                    throw new IOException("Instancia de edicao nao removida: " + session.arenaId);
                }
            } catch (IOException error) {
                if (failure == null) failure = new IOException("Falha ao limpar sessoes de edicao.");
                failure.addSuppressed(error);
            } finally {
                arenas.releaseEditing(session.arenaId);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = returnLocations.get(player.getUniqueId());
            if (location != null && location.getWorld() != null) player.teleport(location);
            restorePlayerState(player);
        }
        sessions.clear();
        returnLocations.clear();
        playerStates.clear();
        if (failure != null) throw failure;
    }

    public boolean isEditWorld(ArenaDefinition arena, World world) {
        EditSession session = arena == null ? null : sessions.get(arena.getId());
        return session != null && session.world == world;
    }

    public boolean isEditingWorld(World world) {
        for (EditSession session : sessions.values()) {
            if (session.world == world) return true;
        }
        return false;
    }

    public boolean isEditOwner(Player player, ArenaDefinition arena) {
        EditSession session = player == null || arena == null ? null : sessions.get(arena.getId());
        return session != null && session.owner.equals(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        EditSession session = sessionFor(event.getBlock().getWorld());
        if (session != null && !session.owner.equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        EditSession session = sessionFor(event.getBlock().getWorld());
        if (session != null && !session.owner.equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        if (sessionFor(event.getLocation().getWorld()) != null) event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        if (sessionFor(event.getBlock().getWorld()) != null) event.blockList().clear();
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        EditSession session = sessionFor(event.getPlayer().getWorld());
        if (session != null && !session.owner.equals(event.getPlayer().getUniqueId())) {
            event.getPlayer().teleport(event.getFrom().getSpawnLocation());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (returnLocations.containsKey(id)) {
            Location back = returnLocations.get(id);
            if (back.getWorld() != null) player.teleport(back);
            restorePlayerState(player);
        }
        for (EditSession session : new java.util.ArrayList<EditSession>(sessions.values())) {
            if (!session.owner.equals(id) || session.publishing) continue;
            try {
                if (session.world != null) arenas.getWorlds().removeInstance(session.world);
            } catch (IOException error) {
                plugin.getLogger().warning("Nao foi possivel limpar a sessao de edicao de "
                    + session.arenaId + ": " + error.getMessage());
            } finally {
                sessions.remove(session.arenaId);
                arenas.releaseEditing(session.arenaId);
            }
        }
        if (!hasSession(id)) {
            returnLocations.remove(id);
            playerStates.remove(id);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final UUID id = event.getPlayer().getUniqueId();
        if (!returnLocations.containsKey(id)) return;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                Player player = Bukkit.getPlayer(id);
                Location back = returnLocations.get(id);
                if (player == null || back == null || back.getWorld() == null) return;
                player.teleport(back);
                restorePlayerState(player);
                if (!hasSession(id)) returnLocations.remove(id);
            }
        });
    }

    private EditSession sessionFor(World world) {
        for (EditSession session : sessions.values()) {
            if (session.world == world) return session;
        }
        return null;
    }

    public Position canonicalPosition(ArenaDefinition arena, Location location) {
        if (arena == null || location == null || location.getWorld() == null
                || !isEditWorld(arena, location.getWorld())) {
            throw new IllegalArgumentException("Jogador deve estar na copia temporaria de edicao da arena.");
        }
        return new Position(arena.getSourceWorld(), location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch());
    }

    public void start(final Player player, final ArenaDefinition arena, final Completion completion) {
        if (player == null || arena == null || completion == null) {
            throw new IllegalArgumentException("Jogador, arena e callback sao obrigatorios.");
        }
        final UUID playerId = player.getUniqueId();
        EditSession existing = sessions.get(arena.getId());
        if (existing != null) {
            if (!existing.owner.equals(playerId)) {
                throw new IllegalStateException("Arena ja esta sendo editada por outro administrador.");
            }
            teleportIntoSession(player, arena, existing);
            completion.complete(null);
            return;
        }
        final boolean firstSession = !returnLocations.containsKey(playerId);
        if (firstSession) {
            returnLocations.put(playerId, player.getLocation().clone());
            playerStates.put(playerId, new PlayerState(player));
        }
        try {
            arenas.reserveEditing(arena);
            arenas.getWorlds().createInstance(arena, new WorldInstanceManager.Completion() {
            @Override
            public void complete(World world, Exception error) {
                if (error != null) {
                    arenas.releaseEditing(arena.getId());
                    if (firstSession) clearReturnState(playerId);
                    completion.complete(error);
                    return;
                }
                if (!player.isOnline()) {
                    arenas.releaseEditing(arena.getId());
                    try {
                        arenas.getWorlds().removeInstance(world);
                    } catch (IOException cleanupError) {
                        if (error == null) error = cleanupError;
                    }
                    if (firstSession) clearReturnState(playerId);
                    completion.complete(error == null
                        ? new IllegalStateException("Jogador desconectou antes do fim da preparacao.")
                        : error);
                    return;
                }
                EditSession session = new EditSession(arena.getId(), playerId, world);
                sessions.put(arena.getId(), session);
                try {
                    teleportIntoSession(player, arena, session);
                } catch (RuntimeException teleportError) {
                    sessions.remove(arena.getId());
                    arenas.releaseEditing(arena.getId());
                    try {
                        arenas.getWorlds().removeInstance(world);
                    } catch (IOException cleanupError) {
                        teleportError.addSuppressed(cleanupError);
                    }
                    restorePlayerState(player);
                    if (firstSession) clearReturnState(playerId);
                    completion.complete(teleportError);
                    return;
                }
                completion.complete(null);
            }
            });
        } catch (RuntimeException error) {
            arenas.releaseEditing(arena.getId());
            if (firstSession) clearReturnState(playerId);
            throw error;
        }
    }

    public void exit(Player player) throws IOException {
        if (player == null) throw new IllegalArgumentException("Jogador ausente.");
        UUID owner = player.getUniqueId();
        java.util.List<EditSession> owned = new java.util.ArrayList<EditSession>();
        for (EditSession session : sessions.values()) {
            if (session.owner.equals(owner) && !session.publishing) owned.add(session);
            else if (session.owner.equals(owner)) {
                throw new IllegalStateException("A publicacao ainda esta em andamento.");
            }
        }
        if (owned.isEmpty()) throw new IllegalStateException("Voce nao possui uma sessao de edicao ativa.");
        for (EditSession session : owned) {
            if (session.world != null && session.world.getPlayers().size()
                    > (session.world.getPlayers().contains(player) ? 1 : 0)) {
                throw new IllegalStateException("Retire outros jogadores das instancias de edicao.");
            }
        }
        for (EditSession session : owned) {
            if (session.world != null && !arenas.getWorlds().removeInstance(session.world)) {
                throw new IOException("Instancia de edicao nao foi removida: " + session.arenaId);
            }
            sessions.remove(session.arenaId);
            arenas.releaseEditing(session.arenaId);
        }
        restoreReturn(player);
    }

    public void publish(final Player player, final ArenaDefinition arena, final Completion completion)
            throws IOException {
        final EditSession session = requireSession(player, arena);
        java.util.List<String> validationErrors = arenas.validate(arena);
        if (!validationErrors.isEmpty()) {
            throw new IllegalStateException("Configure e valide a arena antes de publicar: "
                + arenas.join(validationErrors));
        }
        java.util.List<String> missingBeds = ArenaBedWorldValidator.validate(arena, session.world);
        if (!missingBeds.isEmpty()) {
            throw new IllegalStateException("Corrija as camas fisicas antes de publicar: "
                + arenas.join(missingBeds));
        }
        if (session.world.getPlayers().size() > (session.world.getPlayers().contains(player) ? 1 : 0)) {
            throw new IllegalStateException("Retire outros jogadores do mundo de edicao antes de publicar.");
        }
        if (setupWizard != null) setupWizard.clearEditorHolograms(arena.getId());
        Location destinationLocation = findPublishDestination(player, session);
        if (destinationLocation == null) {
            throw new IllegalStateException("Nao existe outro mundo carregado para descarregar a instancia.");
        }
        boolean requiresTemporaryFlight = !SafeLocationResolver.hasSafeFloor(destinationLocation);
        if (requiresTemporaryFlight) {
            session.publishPreviousAllowFlight = player.getAllowFlight();
            session.publishPreviousFlying = player.isFlying();
            session.publishFlightOverride = true;
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setFallDistance(0.0F);
        }
        if (!player.teleport(destinationLocation)) {
            restorePublishFlight(player, session);
            throw new IllegalStateException("O servidor recusou o teleporte para fora da instancia; "
                + "a sessao de edicao foi mantida.");
        }
        session.publishing = true;
        try {
            arenas.getWorlds().publishInstanceAsTemplate(arena, session.world,
            new WorldInstanceManager.ImportCompletion() {
                @Override
                public void complete(Exception error) {
                    session.publishing = false;
                    if (error == null) {
                        sessions.remove(arena.getId());
                        arenas.releaseEditing(arena.getId());
                        if (!hasSession(player.getUniqueId())) restoreReturn(player);
                        else {
                            teleportToOwnedSession(player);
                            restorePublishFlight(player, session);
                        }
                        completion.complete(null);
                        return;
                    }
                    try {
                        World restored = arenas.getWorlds().loadInstance(arena, session.world.getName());
                        if (restored == null) throw new IOException("Bukkit nao restaurou a instancia de edicao.");
                        session.world = restored;
                        if (!player.isOnline()) {
                            throw new IOException("Jogador desconectou antes de retornar a edicao.");
                        }
                        teleportIntoSession(player, arena, session);
                        if (player.getWorld() != restored) {
                            throw new IOException("Nao foi possivel retornar ao mundo de edicao.");
                        }
                        restorePublishFlight(player, session);
                    } catch (Exception restoreError) {
                        error.addSuppressed(restoreError);
                        sessions.remove(arena.getId());
                        arenas.releaseEditing(arena.getId());
                        plugin.getLogger().severe("Falha ao restaurar a sessao de edicao de "
                            + arena.getId() + "; os arquivos foram preservados.");
                    }
                    completion.complete(error);
                }
            });
        } catch (IOException error) {
            session.publishing = false;
            World restored = Bukkit.getWorld(session.world.getName());
            if (restored != null && player.isOnline()) {
                session.world = restored;
                try {
                    teleportIntoSession(player, arena, session);
                    restorePublishFlight(player, session);
                } catch (RuntimeException restoreError) {
                    error.addSuppressed(restoreError);
                }
            }
            throw error;
        }
    }

    private Location findPublishDestination(Player player, EditSession session) {
        Location returnLocation = returnLocations.get(player.getUniqueId());
        if (returnLocation != null && returnLocation.getWorld() != null
                && returnLocation.getWorld() != session.world) {
            Location safeReturn = SafeLocationResolver.findSurface(returnLocation,
                PUBLISH_DESTINATION_SEARCH_RADIUS);
            if (safeReturn != null) return safeReturn;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world == session.world || world.getName().matches("sbw_[a-f0-9]{32}")) continue;
            Location safe = SafeLocationResolver.findSurface(world.getSpawnLocation(),
                PUBLISH_DESTINATION_SEARCH_RADIUS);
            if (safe != null) return safe;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world == session.world || world.getName().matches("sbw_[a-f0-9]{32}")) continue;
            Location spawn = world.getSpawnLocation();
            int highestY = world.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ());
            double y = Math.max(spawn.getY(), highestY + 2.0D);
            spawn.setY(Math.max(2.0D, Math.min(world.getMaxHeight() - 3.0D, y)));
            return spawn;
        }
        return null;
    }

    private void restorePublishFlight(Player player, EditSession session) {
        if (!session.publishFlightOverride) return;
        player.setFlying(false);
        player.setAllowFlight(session.publishPreviousAllowFlight);
        if (session.publishPreviousFlying && session.publishPreviousAllowFlight) player.setFlying(true);
        session.publishFlightOverride = false;
    }

    public void cancel(Player player, ArenaDefinition arena) throws IOException {
        EditSession session = requireSession(player, arena);
        if (session.world.getPlayers().size() > (session.world.getPlayers().contains(player) ? 1 : 0)) {
            throw new IllegalStateException("Retire outros jogadores do mundo de edicao antes de cancelar.");
        }
        if (!arenas.getWorlds().removeInstance(session.world)) {
            throw new IOException("Instancia de edicao nao foi removida.");
        }
        sessions.remove(arena.getId());
        arenas.releaseEditing(arena.getId());
        if (!hasSession(player.getUniqueId())) restoreReturn(player);
        else teleportToOwnedSession(player);
    }

    private void teleportIntoSession(Player player, ArenaDefinition arena, EditSession session) {
        Location target = null;
        if (arena.getEditSpawn() != null) {
            target = arenas.toLocation(arena.getEditSpawn(), session.world);
        } else if (arena.getLobby() != null) {
            target = arenas.toLocation(arena.getLobby(), session.world);
        } else {
            target = session.world.getSpawnLocation();
        }
        WorldType type = worldManagement == null ? null : worldManagement.getType(arena.getSourceWorld());
        if (type == WorldType.VOID_EMPTY || type == WorldType.VOID_PLATFORM) {
            target.setY(Math.max(2.0, Math.min(session.world.getMaxHeight() - 3.0, target.getY())));
            player.setAllowFlight(true);
            player.setFlying(true);
        } else {
            Location safe = SafeLocationResolver.findSurface(target, 8);
            if (safe == null) {
                target.setY(Math.max(2.0, Math.min(session.world.getMaxHeight() - 3.0, target.getY())));
                player.setAllowFlight(true);
                player.setFlying(true);
            } else {
                target = safe;
            }
        }
        if (!player.teleport(target)) {
            throw new IllegalStateException("Nao foi possivel teleportar para o mundo de edicao.");
        }
    }

    private void teleportToOwnedSession(Player player) {
        for (EditSession session : sessions.values()) {
            if (!session.owner.equals(player.getUniqueId())) continue;
            ArenaDefinition arena = arenas.getArena(session.arenaId);
            if (arena != null) {
                teleportIntoSession(player, arena, session);
                return;
            }
        }
    }

    private boolean hasSession(UUID owner) {
        for (EditSession session : sessions.values()) {
            if (session.owner.equals(owner)) return true;
        }
        return false;
    }

    private void restoreReturn(Player player) {
        Location location = returnLocations.get(player.getUniqueId());
        if (location != null && location.getWorld() != null) player.teleport(location);
        restorePlayerState(player);
        returnLocations.remove(player.getUniqueId());
    }

    private void restorePlayerState(Player player) {
        PlayerState state = playerStates.remove(player.getUniqueId());
        if (state == null) return;
        player.setFlying(false);
        player.setAllowFlight(state.allowFlight);
        if (state.flying && state.allowFlight) player.setFlying(true);
        player.setGameMode(state.gameMode);
    }

    private void clearReturnState(UUID owner) {
        returnLocations.remove(owner);
        playerStates.remove(owner);
    }

    private EditSession requireSession(Player player, ArenaDefinition arena) {
        if (player == null || arena == null) throw new IllegalArgumentException("Jogador ou arena ausente.");
        EditSession session = sessions.get(arena.getId());
        if (session == null || !session.owner.equals(player.getUniqueId())) {
            throw new IllegalStateException("Nao existe uma sessao de edicao sua para esta arena.");
        }
        return session;
    }

    public interface Completion {
        void complete(Exception error);
    }

    private static final class EditSession {
        private final String arenaId;
        private final UUID owner;
        private World world;
        private boolean publishing;
        private boolean publishFlightOverride;
        private boolean publishPreviousAllowFlight;
        private boolean publishPreviousFlying;

        private EditSession(String arenaId, UUID owner, World world) {
            this.arenaId = arenaId;
            this.owner = owner;
            this.world = world;
        }
    }

    private static final class PlayerState {
        private final boolean allowFlight;
        private final boolean flying;
        private final GameMode gameMode;

        private PlayerState(Player player) {
            allowFlight = player.getAllowFlight();
            flying = player.isFlying();
            gameMode = player.getGameMode();
        }
    }
}
