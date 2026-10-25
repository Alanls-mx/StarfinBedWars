package com.starfinplugins.bedwars.stats;

import java.util.UUID;

/** Immutable completed-match delta; matchId + playerId is its idempotency key. */
public final class MatchResult {
    public enum Outcome { WIN, LOSS, DRAW }

    public final UUID matchId;
    public final UUID playerId;
    public final String name;
    public final int kills;
    public final int finalKills;
    public final int deaths;
    public final int beds;
    public final Outcome outcome;
    public final long xp;

    public MatchResult(UUID matchId, UUID playerId, String name, int kills, int finalKills,
                       int deaths, int beds, Outcome outcome, long xp) {
        if (matchId == null || playerId == null || outcome == null || xp < 0
                || kills < 0 || finalKills < 0 || deaths < 0 || beds < 0 || finalKills > kills) {
            throw new IllegalArgumentException("Resultado de partida invalido");
        }
        this.matchId = matchId;
        this.playerId = playerId;
        this.name = name == null ? "" : name.replaceAll("[^A-Za-z0-9_]", "");
        this.kills = kills;
        this.finalKills = finalKills;
        this.deaths = deaths;
        this.beds = beds;
        this.outcome = outcome;
        this.xp = xp;
    }
}
