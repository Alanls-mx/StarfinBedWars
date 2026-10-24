package com.starfinplugins.bedwars.event;

import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class MatchLifecycleEvent extends Event {
    public enum Type {
        CREATED, PLAYER_JOINED, PLAYER_LEFT, SPECTATOR_JOINED, SPECTATOR_LEFT,
        PLAYER_RECONNECTED, COUNTDOWN_CANCELLED, SPAWN_OPENING,
        STARTED, BED_DESTROYED, PLAYER_ELIMINATED, ENDED, PLAYERS_RETURNED, INSTANCE_REMOVED
    }

    private static final HandlerList HANDLERS = new HandlerList();
    private final Type type;
    private final GameMatch match;
    private final GameParticipant participant;
    private final String teamId;

    public MatchLifecycleEvent(Type type, GameMatch match, GameParticipant participant, String teamId) {
        this.type = type;
        this.match = match;
        this.participant = participant;
        this.teamId = teamId;
    }

    public Type getType() { return type; }
    public GameMatch getMatch() { return match; }
    public GameParticipant getParticipant() { return participant; }
    public String getTeamId() { return teamId; }
    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
