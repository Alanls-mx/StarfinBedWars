package com.starfinplugins.bedwars.ranked;

import java.util.UUID;

public final class RankedResult {
    public final UUID matchId, playerId;
    public final String teamId, outcome;
    public final int before, change, after;
    public final long completedAt;
    public RankedResult(UUID matchId, UUID playerId, String teamId, String outcome,
                        int before, int change, int after, long completedAt) {
        this.matchId = matchId; this.playerId = playerId; this.teamId = teamId;
        this.outcome = outcome; this.before = before; this.change = change;
        this.after = after; this.completedAt = completedAt;
    }
}
