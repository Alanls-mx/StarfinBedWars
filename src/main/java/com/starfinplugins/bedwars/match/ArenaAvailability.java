package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.MatchState;

/** The admission rule shared by local discovery, MySQL advertisements and joining. */
public final class ArenaAvailability {
    private ArenaAvailability() { }

    public static boolean isJoinable(ArenaDefinition arena, GameMatch match,
                                     boolean editing, boolean preparing, boolean openingSpawns) {
        if (arena == null || !arena.isEnabled() || editing || preparing || openingSpawns
                || arena.getMaximumPlayers() < arena.getMinimumPlayers()
                || arena.getMaximumPlayers() < 1) return false;
        if (match == null) return true;
        MatchState state = match.getState();
        return (state == MatchState.WAITING || state == MatchState.STARTING)
            && match.getParticipantList().size() < arena.getMaximumPlayers();
    }
}
