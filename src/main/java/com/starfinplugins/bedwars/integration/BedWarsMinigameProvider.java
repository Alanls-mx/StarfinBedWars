package com.starfinplugins.bedwars.integration;

import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.domain.match.ConnectionState;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.match.ArenaAvailability;
import com.starfinplugins.bedwars.stats.MatchMode;
import com.starfinplugins.minigames.api.MinigameArenaSnapshot;
import com.starfinplugins.minigames.api.MinigameDescriptor;
import com.starfinplugins.minigames.api.MinigameMatchSnapshot;
import com.starfinplugins.minigames.api.MinigameMatchState;
import com.starfinplugins.minigames.api.MinigamePlayerSnapshot;
import com.starfinplugins.minigames.api.MinigameProvider;
import com.starfinplugins.minigames.api.MinigameRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;

/** Live BedWars data is copied at query time. No world or mutable match escapes. */
public final class BedWarsMinigameProvider implements MinigameProvider {
    private final ArenaService arenas;
    private final MatchEngineService matches;
    private final MinigameRegistry registry;

    public BedWarsMinigameProvider(ArenaService arenas, MatchEngineService matches, MinigameRegistry registry) {
        this.arenas = arenas;
        this.matches = matches;
        this.registry = registry;
    }

    @Override public MinigameDescriptor descriptor() {
        Set<String> modes = new LinkedHashSet<String>();
        for (ArenaDefinition arena : arenas.getArenas()) if (arena.isEnabled()) modes.add(mode(arena));
        return new MinigameDescriptor("bedwars", "StarfinBedWars",
            registry.getServerId(), registry.getInstanceId(), modes);
    }

    @Override public Collection<MinigameArenaSnapshot> arenas() {
        List<MinigameArenaSnapshot> result = new ArrayList<MinigameArenaSnapshot>();
        for (ArenaDefinition arena : arenas.getArenas()) {
            GameMatch match = matches.getMatchForArena(arena.getId());
            result.add(snapshotArena(arena, match, arenas.isEditing(arena.getId()),
                matches.isPreparingArena(arena.getId()), matches.isOpeningSpawns(arena.getId()), registry.getServerId()));
        }
        return result;
    }

    @Override public Collection<MinigameMatchSnapshot> matches() {
        List<MinigameMatchSnapshot> result = new ArrayList<MinigameMatchSnapshot>();
        for (ArenaDefinition arena : arenas.getArenas()) {
            GameMatch match = matches.getMatchForArena(arena.getId());
            if (match != null) result.add(snapshotMatch(match, registry.getServerId(), null));
        }
        return result;
    }

    @Override public Collection<MinigamePlayerSnapshot> players() {
        List<MinigamePlayerSnapshot> result = new ArrayList<MinigamePlayerSnapshot>();
        for (ArenaDefinition arena : arenas.getArenas()) {
            GameMatch match = matches.getMatchForArena(arena.getId());
            if (match == null) continue;
            for (GameParticipant participant : match.getParticipantList()) {
                if (participant.getLifeState() == ParticipantState.ABANDONED) continue;
                boolean online = Bukkit.getPlayer(participant.getPlayerId()) != null;
                result.add(new MinigamePlayerSnapshot(participant.getPlayerId(), "bedwars", match.getId(),
                    arena.getId(), registry.getServerId(), role(participant, online)));
            }
            for (java.util.UUID spectatorId : matches.getDirectSpectators(match))
                if (Bukkit.getPlayer(spectatorId) != null)
                    result.add(new MinigamePlayerSnapshot(spectatorId, "bedwars", match.getId(),
                        arena.getId(), registry.getServerId(), MinigamePlayerSnapshot.Role.SPECTATOR));
        }
        return result;
    }

    static String mode(ArenaDefinition arena) {
        return MatchMode.of(arena);
    }

    static MinigamePlayerSnapshot.Role role(GameParticipant participant) {
        return role(participant, true);
    }

    static MinigamePlayerSnapshot.Role role(GameParticipant participant, boolean online) {
        if (!online || participant.getConnectionState() == ConnectionState.DISCONNECTED)
            return MinigamePlayerSnapshot.Role.DISCONNECTED;
        ParticipantState state = participant.getLifeState();
        if (state == ParticipantState.RESPAWNING) return MinigamePlayerSnapshot.Role.RESPAWNING;
        if (state == ParticipantState.ELIMINATED) return MinigamePlayerSnapshot.Role.ELIMINATED;
        return MinigamePlayerSnapshot.Role.PLAYER;
    }

    static MinigameArenaSnapshot snapshotArena(ArenaDefinition arena, GameMatch match,
                                               boolean editing, String serverId) {
        return snapshotArena(arena, match, editing, false, false, serverId);
    }

    static MinigameArenaSnapshot snapshotArena(ArenaDefinition arena, GameMatch match,
                                               boolean editing, boolean preparing,
                                               boolean openingSpawns, String serverId) {
        int actualCapacity = arena.getMaximumPlayers();
        int capacity = Math.max(1, actualCapacity);
        int minimum = Math.max(1, Math.min(arena.getMinimumPlayers(), capacity));
        int count = match == null ? 0 : Math.min(capacity, match.getParticipantList().size());
        boolean joinable = ArenaAvailability.isJoinable(arena, match, editing, preparing, openingSpawns);
        return new MinigameArenaSnapshot("bedwars", arena.getId(), arena.getDisplayName(),
            mode(arena), arena.getGroup(), serverId, minimum, capacity, count,
            arena.isEnabled(), joinable, match == null ? null : match.getId());
    }

    static MinigameMatchSnapshot snapshotMatch(GameMatch match, String serverId,
                                               MinigameMatchState stateOverride) {
        Set<java.util.UUID> playerIds = new LinkedHashSet<java.util.UUID>();
        for (GameParticipant participant : match.getParticipantList()) playerIds.add(participant.getPlayerId());
        long started = match.getStartedAt() == null ? 0L : match.getStartedAt().toEpochMilli();
        return new MinigameMatchSnapshot(match.getId(), "bedwars", match.getArena().getId(), serverId,
            stateOverride == null ? state(match.getState()) : stateOverride,
            Math.max(1, match.getArena().getMaximumPlayers()), match.getConnectedCount(), playerIds, started);
    }

    static MinigameMatchState state(MatchState state) {
        switch (state) {
            case WAITING: return MinigameMatchState.WAITING;
            case STARTING: return MinigameMatchState.STARTING;
            case IN_GAME: return MinigameMatchState.IN_GAME;
            case ENDING: return MinigameMatchState.ENDING;
            case RESETTING: return MinigameMatchState.RESETTING;
            case ERROR: return MinigameMatchState.CLOSED;
            default: throw new IllegalArgumentException("Estado BedWars desconhecido: " + state);
        }
    }
}
