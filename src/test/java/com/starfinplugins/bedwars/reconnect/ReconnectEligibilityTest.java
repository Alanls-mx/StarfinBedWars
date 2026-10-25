package com.starfinplugins.bedwars.reconnect;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Test;

public class ReconnectEligibilityTest {
    @Test public void destroyedBedImmediatelyDisallowsDisconnectedPlayer() {
        ArenaDefinition arena = new ArenaDefinition("duel", "world");
        arena.addTeam(new TeamDefinition("red", "Red", "c", 1));
        arena.addTeam(new TeamDefinition("blue", "Blue", "9", 1));
        GameMatch match = new GameMatch(arena);
        UUID playerId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        Assert.assertTrue(match.addParticipant(playerId));
        Assert.assertTrue(match.addParticipant(opponentId));
        GameParticipant player = match.getParticipant(playerId);
        Assert.assertFalse(ReconnectService.canRecover(match, player));
        match.transition(MatchState.STARTING);
        match.transition(MatchState.IN_GAME);
        Assert.assertTrue(ReconnectService.canRecover(match, player));
        match.setParticipantState(playerId, ParticipantState.DISCONNECTED);
        Assert.assertTrue(ReconnectService.canRecover(match, player));
        Assert.assertTrue(match.destroyBed(player.getTeamId()));
        Assert.assertFalse(ReconnectService.canRecover(match, player));
        match.setParticipantState(playerId, ParticipantState.ABANDONED);
        Assert.assertEquals(1, match.getEligibleTeams().size());
        Assert.assertEquals(match.getParticipant(opponentId).getTeamId(),
            match.getEligibleTeams().get(0).getDefinition().getId());
    }
}
