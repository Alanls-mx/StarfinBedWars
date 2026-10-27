package com.starfinplugins.bedwars.domain;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class PartyTeamAssignmentTest {
    private GameMatch match(int capacity) {
        ArenaDefinition arena = new ArenaDefinition("party", "world");
        arena.addTeam(new TeamDefinition("red", "Red", "c", capacity));
        arena.addTeam(new TeamDefinition("blue", "Blue", "9", capacity));
        return new GameMatch(arena);
    }

    @Test public void partiesStayTogetherAtActualTeamCapacity() {
        for (int size : new int[] {2, 3, 4, 6}) {
            GameMatch match = match(size);
            UUID group = UUID.randomUUID();
            long expires = System.currentTimeMillis() + 20000;
            for (int i = 0; i < size; i++) {
                UUID player = UUID.randomUUID();
                assertTrue(match.addParticipant(player, "red", group, size, expires));
                assertEquals("red", match.getParticipant(player).getTeamId());
            }
            assertFalse(match.hasPendingGroup());
            assertEquals("one Party does not provide an opposing team", 1,
                match.getEligibleTeams().size());
        }
    }

    @Test public void heldSlotsBlockIndividualsAndOtherParties() {
        GameMatch match = match(2);
        UUID party = UUID.randomUUID();
        long expires = System.currentTimeMillis() + 20000;
        assertTrue(match.addParticipant(UUID.randomUUID(), "red", party, 2, expires));
        assertTrue(match.hasPendingGroup());
        UUID individual = UUID.randomUUID();
        assertTrue(match.addParticipant(individual));
        assertEquals("blue", match.getParticipant(individual).getTeamId());
        assertFalse(match.addParticipant(UUID.randomUUID(), "red", UUID.randomUUID(), 2, expires));
        assertTrue(match.addParticipant(UUID.randomUUID(), "red", party, 2, expires));
        assertFalse(match.hasPendingGroup());
        assertEquals(2, match.getEligibleTeams().size());
    }

    @Test public void soloMembersReceiveDifferentTeams() {
        GameMatch match = match(1);
        UUID party = UUID.randomUUID();
        long expires = System.currentTimeMillis() + 20000;
        UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        assertTrue(match.addParticipant(one, "red", party, 2, expires));
        assertTrue(match.addParticipant(two, "blue", party, 2, expires));
        assertNotEquals(match.getParticipant(one).getTeamId(), match.getParticipant(two).getTeamId());
    }

    @Test public void rejectsOversizedAndExpiredHolds() {
        GameMatch match = match(2);
        assertFalse(match.addParticipant(UUID.randomUUID(), "red", UUID.randomUUID(), 3,
            System.currentTimeMillis() + 20000));
        assertFalse(match.addParticipant(UUID.randomUUID(), "red", UUID.randomUUID(), 2,
            System.currentTimeMillis() - 1));
        assertTrue(match.addParticipant(UUID.randomUUID()));
    }

    @Test public void leaderMovesWholePartyOnlyAfterEveryoneArrives() {
        GameMatch match = match(2);
        UUID leader = UUID.randomUUID(), member = UUID.randomUUID(), group = UUID.randomUUID();
        long expires = System.currentTimeMillis() + 20000;
        assertTrue(match.addParticipant(leader, "red", group, 2, expires, leader));
        assertEquals(GameMatch.TeamChoice.WAIT_FOR_PARTY, match.chooseTeam(leader, "blue"));
        assertTrue(match.addParticipant(member, "red", group, 2, expires, leader));
        assertEquals(GameMatch.TeamChoice.LEADER_ONLY, match.chooseTeam(member, "blue"));
        assertEquals(GameMatch.TeamChoice.MOVED, match.chooseTeam(leader, "blue"));
        assertEquals("blue", match.getParticipant(leader).getTeamId());
        assertEquals("blue", match.getParticipant(member).getTeamId());
        assertEquals(2, match.getTeam("blue").getMembers().size());
        assertEquals(0, match.getTeam("red").getMembers().size());
        assertEquals(0, match.availableTeamSlots("blue"));
    }

    @Test public void individualMaySelectOnlyAnAvailableTeam() {
        GameMatch match = match(1);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(match.addParticipant(first));
        assertEquals(GameMatch.TeamChoice.MOVED, match.chooseTeam(first, "blue"));
        assertTrue(match.addParticipant(second));
        assertEquals("red", match.getParticipant(second).getTeamId());
        assertEquals(GameMatch.TeamChoice.FULL, match.chooseTeam(second, "blue"));
    }
}
