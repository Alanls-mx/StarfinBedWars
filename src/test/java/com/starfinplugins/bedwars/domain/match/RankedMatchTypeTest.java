package com.starfinplugins.bedwars.domain.match;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankedMatchTypeTest {
    @Test public void casualIsDefaultAndRankedRosterFreezesBeforeStart() {
        ArenaDefinition arena = new ArenaDefinition("test", "world");
        arena.addTeam(new TeamDefinition("red", "Vermelho", "c", 1));
        arena.addTeam(new TeamDefinition("blue", "Azul", "9", 1));
        GameMatch match = new GameMatch(arena);
        assertEquals(MatchType.CASUAL, match.getType());
        assertTrue(match.addParticipant(UUID.randomUUID(), "red", null, 1, 0));
        match.authorizeRanked("preseason-1");
        assertEquals(MatchType.RANKED, match.getType());
        assertFalse(match.addParticipant(UUID.randomUUID(), "blue", null, 1, 0));
        assertEquals(GameMatch.TeamChoice.UNAVAILABLE,
            match.chooseTeam(match.getParticipantList().get(0).getPlayerId(), "blue"));
        match.transition(MatchState.STARTING);
        match.transition(MatchState.IN_GAME);
        try { match.authorizeRanked("preseason-2"); fail("late conversion"); }
        catch (IllegalStateException expected) { /* immutable after start */ }
    }
}
