package com.starfinplugins.bedwars.tournament;

import java.util.UUID;

/** Immutable allocation and result snapshot. */
public final class TournamentGame {
    public final UUID id, matchId, allocationId, minecraftMatchId, winner;
    public final String tournamentId, state, serverId, serverInstance, arenaId;
    public final String arenaTeamA, arenaTeamB, resultReason;
    public final int number;
    public final long leaseExpires;

    TournamentGame(UUID id, String tournamentId, UUID matchId, int number, String state,
            UUID allocationId, String serverId, String serverInstance, String arenaId,
            String arenaTeamA, String arenaTeamB, UUID minecraftMatchId, UUID winner,
            String resultReason, long leaseExpires) {
        this.id = id; this.tournamentId = tournamentId; this.matchId = matchId;
        this.number = number; this.state = state; this.allocationId = allocationId;
        this.serverId = serverId; this.serverInstance = serverInstance;
        this.arenaId = arenaId; this.arenaTeamA = arenaTeamA; this.arenaTeamB = arenaTeamB;
        this.minecraftMatchId = minecraftMatchId; this.winner = winner;
        this.resultReason = resultReason; this.leaseExpires = leaseExpires;
    }
}
