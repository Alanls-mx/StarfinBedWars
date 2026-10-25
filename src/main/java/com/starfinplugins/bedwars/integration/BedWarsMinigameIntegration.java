package com.starfinplugins.bedwars.integration;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.network.NetworkRegistryService;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.minigames.api.MinigameEvent;
import com.starfinplugins.minigames.api.MinigameMatchState;
import com.starfinplugins.minigames.api.MinigameRegistration;
import com.starfinplugins.minigames.api.MinigameRegistry;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Optional, main-thread bridge from the existing BedWars lifecycle to the shared API. */
public final class BedWarsMinigameIntegration implements ManagedService, Listener {
    private final StarfinBedWarsPlugin plugin;
    private final ArenaService arenas;
    private final MatchEngineService matches;
    private final NetworkRegistryService network;
    private MinigameRegistration registration;
    private MinigameRegistry registry;

    public BedWarsMinigameIntegration(StarfinBedWarsPlugin plugin, ArenaService arenas,
                                     MatchEngineService matches, NetworkRegistryService network) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.matches = matches;
        this.network = network;
    }

    @Override public String getName() { return "StarfinMinigames bridge"; }

    @Override public void start() {
        try {
            RegisteredServiceProvider<MinigameRegistry> service =
                Bukkit.getServicesManager().getRegistration(MinigameRegistry.class);
            if (service == null || !service.getPlugin().isEnabled()) {
                plugin.getLogger().warning("StarfinMinigames ausente; BedWars segue sem API compartilhada.");
                return;
            }
            MinigameRegistry found = service.getProvider();
            if (network.isEnabled() && !network.getServerId().equals(found.getServerId())) {
                plugin.getLogger().warning("server-id do StarfinMinigames difere de network.yml; "
                    + "corrija a identidade e reinicie. Registro BedWars ignorado.");
                return;
            }
            registration = found.register(new BedWarsMinigameProvider(arenas, matches, found));
            registry = found;
            Bukkit.getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("BedWars registrado no StarfinMinigames API " + MinigameRegistry.API_VERSION);
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("StarfinMinigames indisponivel: " + error.getClass().getSimpleName());
            stop();
        }
    }

    @Override public void stop() {
        HandlerList.unregisterAll(this);
        if (registration != null) {
            try { registration.close(); }
            catch (RuntimeException | LinkageError error) {
                plugin.getLogger().warning("Falha ao remover registro compartilhado: " + error.getClass().getSimpleName());
            }
        }
        registration = null;
        registry = null;
    }

    @EventHandler public void onMatchLifecycle(MatchLifecycleEvent source) {
        if (registration == null || registry == null) return;
        MinigameEvent.Type type = eventType(source.getType());
        if (type == null) return;
        try {
            MinigameMatchState override = type == MinigameEvent.Type.MATCH_REMOVED
                ? MinigameMatchState.CLOSED : null;
            registration.publish(new MinigameEvent(type, "bedwars",
                BedWarsMinigameProvider.snapshotMatch(source.getMatch(), registry.getServerId(), override),
                source.getActorId(),
                System.currentTimeMillis()));
        } catch (RuntimeException | LinkageError error) {
            // External API consumers cannot interrupt BedWars state transitions.
            plugin.getLogger().warning("Evento compartilhado ignorado: " + error.getClass().getSimpleName());
        }
    }

    static MinigameEvent.Type eventType(MatchLifecycleEvent.Type type) {
        switch (type) {
            case CREATED: return MinigameEvent.Type.MATCH_CREATED;
            case COUNTDOWN_STARTED: return MinigameEvent.Type.MATCH_STARTING;
            case STARTED: return MinigameEvent.Type.MATCH_STARTED;
            case PLAYER_JOINED: return MinigameEvent.Type.PLAYER_JOINED_MATCH;
            case PLAYER_LEFT: return MinigameEvent.Type.PLAYER_LEFT_MATCH;
            case PLAYER_ELIMINATED: return MinigameEvent.Type.PLAYER_ELIMINATED;
            case SPECTATOR_JOINED: return MinigameEvent.Type.SPECTATOR_JOINED_MATCH;
            case SPECTATOR_LEFT: return MinigameEvent.Type.SPECTATOR_LEFT_MATCH;
            case ENDED: return MinigameEvent.Type.MATCH_ENDED;
            case INSTANCE_REMOVED: return MinigameEvent.Type.MATCH_REMOVED;
            default: return null;
        }
    }
}
