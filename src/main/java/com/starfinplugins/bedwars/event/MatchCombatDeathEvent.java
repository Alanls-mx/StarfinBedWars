package com.starfinplugins.bedwars.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Credited death after the match has validated both participants. */
public final class MatchCombatDeathEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID matchId, victimId, killerId;
    private final boolean finalKill;

    public MatchCombatDeathEvent(UUID matchId, UUID victimId, UUID killerId, boolean finalKill) {
        this.matchId = matchId;
        this.victimId = victimId;
        this.killerId = killerId;
        this.finalKill = finalKill;
    }

    public UUID getMatchId() { return matchId; }
    public UUID getVictimId() { return victimId; }
    public UUID getKillerId() { return killerId; }
    public boolean isFinalKill() { return finalKill; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
