package com.starfinplugins.bedwars.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Non-cancellable audit hooks. Success is emitted after restoration. */
public final class ReconnectLifecycleEvent extends Event {
    public enum Type { CREATED, ATTEMPTED, COMPLETED, REJECTED, EXPIRED, INVALIDATED, RESTORE_FAILED }
    private static final HandlerList HANDLERS = new HandlerList();
    private final Type type;
    private final UUID playerId;
    private final UUID matchId;
    private final String reason;

    public ReconnectLifecycleEvent(Type type, UUID playerId, UUID matchId, String reason) {
        this.type = type;
        this.playerId = playerId;
        this.matchId = matchId;
        this.reason = reason;
    }
    public Type getType() { return type; }
    public UUID getPlayerId() { return playerId; }
    public UUID getMatchId() { return matchId; }
    public String getReason() { return reason; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
