package com.starfinplugins.bedwars.ranked;

import java.util.UUID;

public final class RankedProfile {
    public final UUID playerId;
    public final String mode, context;
    public final int rating, peakRating, games, wins, losses, draws, placementGames;
    public final long lastMatchAt;
    public RankedProfile(UUID playerId, String mode, String context, int rating, int peakRating,
                         int games, int wins, int losses, int draws, int placementGames, long lastMatchAt) {
        this.playerId = playerId; this.mode = mode; this.context = context;
        this.rating = rating; this.peakRating = peakRating; this.games = games;
        this.wins = wins; this.losses = losses; this.draws = draws;
        this.placementGames = placementGames; this.lastMatchAt = lastMatchAt;
    }
    public boolean isPlaced(int required) { return placementGames >= required; }
}
