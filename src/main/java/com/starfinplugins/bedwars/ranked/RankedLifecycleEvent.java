package com.starfinplugins.bedwars.ranked;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class RankedLifecycleEvent extends Event {
    public enum Type { STARTED, COMPLETED, RATING_UPDATED, DIVISION_CHANGED, PLACEMENT_COMPLETED }
    private static final HandlerList HANDLERS = new HandlerList();
    private final Type type;
    private final UUID matchId, playerId;
    private final RankedResult result;
    public RankedLifecycleEvent(Type type, UUID matchId, UUID playerId, RankedResult result) {
        this.type = type; this.matchId = matchId; this.playerId = playerId; this.result = result;
    }
    public Type getType() { return type; }
    public UUID getMatchId() { return matchId; }
    public UUID getPlayerId() { return playerId; }
    public RankedResult getResult() { return result; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
