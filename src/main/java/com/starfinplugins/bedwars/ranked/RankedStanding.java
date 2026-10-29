package com.starfinplugins.bedwars.ranked;

import java.util.UUID;

/** Competitive ranking row; final seasons come from immutable snapshots. */
public final class RankedStanding {
    public final UUID playerId;
    public final String playerName, seasonId, mode, divisionId, peakDivisionId;
    public final long position;
    public final int rating, peakRating, games, wins, losses, draws, placementGames;
    public RankedStanding(UUID playerId, String playerName, String seasonId, String mode,
            String divisionId, String peakDivisionId, long position, int rating,
            int peakRating, int games, int wins, int losses, int draws, int placementGames) {
        this.playerId = playerId; this.playerName = playerName; this.seasonId = seasonId;
        this.mode = mode; this.divisionId = divisionId; this.peakDivisionId = peakDivisionId;
        this.position = position; this.rating = rating; this.peakRating = peakRating;
        this.games = games; this.wins = wins; this.losses = losses;
        this.draws = draws; this.placementGames = placementGames;
    }
}
