package com.starfinplugins.bedwars.tournament;

import java.util.UUID;

public final class TournamentMatch {
    public final UUID id, teamA, teamB, winner;
    public final String tournamentId, state;
    public final int round, slot, winsA, winsB, bestOf;
    public final long scheduledAt;
    TournamentMatch(UUID id, String tournamentId, int round, int slot, UUID teamA,
            UUID teamB, int winsA, int winsB, int bestOf, String state,
            UUID winner, long scheduledAt) {
        this.id = id; this.tournamentId = tournamentId; this.round = round; this.slot = slot;
        this.teamA = teamA; this.teamB = teamB; this.winsA = winsA; this.winsB = winsB;
        this.bestOf = bestOf; this.state = state; this.winner = winner;
        this.scheduledAt = scheduledAt;
    }
}
