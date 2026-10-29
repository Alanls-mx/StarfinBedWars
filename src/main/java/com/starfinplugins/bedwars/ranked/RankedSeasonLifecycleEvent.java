package com.starfinplugins.bedwars.ranked;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Emitted on the Bukkit thread after the corresponding MySQL commit. */
public final class RankedSeasonLifecycleEvent extends Event {
    public enum Type { STARTED, CLOSING, FINISHED, REWARD_CLAIMED }
    private static final HandlerList HANDLERS = new HandlerList();
    public final Type type;
    public final String seasonId, rewardId;
    public final UUID playerId;
    public final long policyVersion;
    public RankedSeasonLifecycleEvent(Type type, String seasonId, long policyVersion,
            UUID playerId, String rewardId) {
        this.type = type; this.seasonId = seasonId; this.policyVersion = policyVersion;
        this.playerId = playerId; this.rewardId = rewardId;
    }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
