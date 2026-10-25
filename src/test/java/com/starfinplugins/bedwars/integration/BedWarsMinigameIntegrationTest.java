package com.starfinplugins.bedwars.integration;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchEndReason;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.minigames.api.MinigameArenaSnapshot;
import com.starfinplugins.minigames.api.MinigameEvent;
import com.starfinplugins.minigames.api.MinigameMatchSnapshot;
import com.starfinplugins.minigames.api.MinigameMatchState;
import com.starfinplugins.minigames.api.MinigamePlayerSnapshot;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Test;

public class BedWarsMinigameIntegrationTest {
    @Test public void arenaAndMatchSnapshotsFollowExistingStateWithoutMutation() {
        ArenaDefinition arena = arena(1);
        UUID player = UUID.randomUUID();
        GameMatch match = new GameMatch(arena);
        Assert.assertTrue(match.addParticipant(player));
        MinigameArenaSnapshot before = BedWarsMinigameProvider.snapshotArena(arena, match, false, "bedwars-01");
        Assert.assertTrue(before.isJoinable());
        Assert.assertEquals("solo", before.getMode());
        Assert.assertEquals("default", before.getGroup());
        Assert.assertEquals(2, before.getMaxPlayers());
        Assert.assertEquals(match.getId(), before.getMatchId());
        MinigameMatchSnapshot waiting = BedWarsMinigameProvider.snapshotMatch(match, "bedwars-01", null);
        Assert.assertEquals(MinigameMatchState.WAITING, waiting.getState());
        Assert.assertTrue(waiting.getParticipantIds().contains(player));
        match.transition(MatchState.STARTING);
        match.transition(MatchState.IN_GAME);
        Assert.assertEquals(MinigameMatchState.IN_GAME,
            BedWarsMinigameProvider.snapshotMatch(match, "bedwars-01", null).getState());
        Assert.assertFalse(BedWarsMinigameProvider.snapshotArena(arena, match, false, "bedwars-01").isJoinable());
        Assert.assertEquals(MinigameMatchState.WAITING, waiting.getState());
        Assert.assertThrows(UnsupportedOperationException.class, () -> waiting.getParticipantIds().clear());
        Assert.assertEquals(MatchState.IN_GAME, match.getState());
    }

    @Test public void modesAndDisabledArenaFallback() {
        ArenaDefinition arena = arena(2);
        Assert.assertEquals("duo", BedWarsMinigameProvider.mode(arena));
        arena.addTeam(new TeamDefinition("green", "Green", "a", 1));
        Assert.assertEquals("mixed", BedWarsMinigameProvider.mode(arena));
        ArenaDefinition incomplete = new ArenaDefinition("empty", "world");
        MinigameArenaSnapshot snapshot = BedWarsMinigameProvider.snapshotArena(incomplete, null, false, "bedwars-01");
        Assert.assertFalse(snapshot.isJoinable());
        Assert.assertEquals("custom", snapshot.getMode());
        Assert.assertEquals(1, snapshot.getMaxPlayers());
    }

    @Test public void internalLifecycleMapsOnceToGenericEvents() {
        Assert.assertEquals(MinigameEvent.Type.MATCH_CREATED,
            BedWarsMinigameIntegration.eventType(MatchLifecycleEvent.Type.CREATED));
        Assert.assertEquals(MinigameEvent.Type.MATCH_STARTING,
            BedWarsMinigameIntegration.eventType(MatchLifecycleEvent.Type.COUNTDOWN_STARTED));
        Assert.assertEquals(MinigameEvent.Type.MATCH_STARTED,
            BedWarsMinigameIntegration.eventType(MatchLifecycleEvent.Type.STARTED));
        Assert.assertEquals(MinigameEvent.Type.PLAYER_JOINED_MATCH,
            BedWarsMinigameIntegration.eventType(MatchLifecycleEvent.Type.PLAYER_JOINED));
        Assert.assertEquals(MinigameEvent.Type.PLAYER_LEFT_MATCH,
            BedWarsMinigameIntegration.eventType(MatchLifecycleEvent.Type.PLAYER_LEFT));
        Assert.assertEquals(MinigameEvent.Type.PLAYER_ELIMINATED,
            BedWarsMinigameIntegration.eventType(MatchLifecycleEvent.Type.PLAYER_ELIMINATED));
        Assert.assertEquals(MinigameEvent.Type.MATCH_ENDED,
            BedWarsMinigameIntegration.eventType(MatchLifecycleEvent.Type.ENDED));
        Assert.assertEquals(MinigameEvent.Type.MATCH_REMOVED,
            BedWarsMinigameIntegration.eventType(MatchLifecycleEvent.Type.INSTANCE_REMOVED));
        Assert.assertNull(BedWarsMinigameIntegration.eventType(MatchLifecycleEvent.Type.PLAYER_DIED));
        Assert.assertNull(BedWarsMinigameIntegration.eventType(MatchLifecycleEvent.Type.COUNTDOWN_CANCELLED));
    }

    @Test public void removedMatchIsPubliclyClosedAfterInternalReset() {
        ArenaDefinition arena = arena(1);
        GameMatch match = new GameMatch(arena);
        match.transition(MatchState.STARTING);
        match.transition(MatchState.IN_GAME);
        Assert.assertTrue(match.finish(MatchEndReason.DRAW, null));
        match.transition(MatchState.RESETTING);
        match.transition(MatchState.WAITING);
        MinigameMatchSnapshot removed = BedWarsMinigameProvider.snapshotMatch(match,
            "bedwars-01", MinigameMatchState.CLOSED);
        Assert.assertEquals(MinigameMatchState.CLOSED, removed.getState());
        Assert.assertEquals(MatchState.WAITING, match.getState());
    }

    @Test public void respawningAndEliminatedPlayersArePublicSpectators() {
        GameParticipant participant = new GameParticipant(UUID.randomUUID(), "match", "red");
        Assert.assertEquals(MinigamePlayerSnapshot.Role.PLAYER, BedWarsMinigameProvider.role(participant));
        participant.setState(ParticipantState.RESPAWNING);
        Assert.assertEquals(MinigamePlayerSnapshot.Role.SPECTATOR, BedWarsMinigameProvider.role(participant));
        participant.setState(ParticipantState.ELIMINATED);
        Assert.assertEquals(MinigamePlayerSnapshot.Role.SPECTATOR, BedWarsMinigameProvider.role(participant));
    }

    private static ArenaDefinition arena(int capacity) {
        ArenaDefinition arena = new ArenaDefinition("lighthouse", "world");
        arena.addTeam(new TeamDefinition("red", "Red", "c", capacity));
        arena.addTeam(new TeamDefinition("blue", "Blue", "9", capacity));
        arena.setEnabled(true);
        return arena;
    }
}
