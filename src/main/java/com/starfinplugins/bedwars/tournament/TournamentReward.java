package com.starfinplugins.bedwars.tournament;

import java.util.UUID;

public final class TournamentReward {
    public final String tournamentId, rewardId, titleName, state;
    public final UUID teamId, playerId;
    TournamentReward(String tournamentId, UUID teamId, UUID playerId,
            String rewardId, String titleName, String state) {
        this.tournamentId = tournamentId; this.teamId = teamId; this.playerId = playerId;
        this.rewardId = rewardId; this.titleName = titleName; this.state = state;
    }
}
