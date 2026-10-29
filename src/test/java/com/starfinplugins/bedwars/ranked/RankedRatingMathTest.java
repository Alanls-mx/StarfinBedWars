package com.starfinplugins.bedwars.ranked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankedRatingMathTest {
    private static RankedMatchSnapshot snapshot(int teams, int playersPerTeam, String winner,
                                                 boolean draw, boolean abandonWinner) {
        List<RankedMatchSnapshot.Member> members = new ArrayList<RankedMatchSnapshot.Member>();
        for (int team = 0; team < teams; team++)
            for (int player = 0; player < playersPerTeam; player++)
                members.add(new RankedMatchSnapshot.Member(UUID.randomUUID(), "Player", "team" + team,
                    abandonWinner && team == 0 && player == 0));
        return new RankedMatchSnapshot(UUID.randomUUID(), "solo", "arena", "server", "instance",
            "preseason-1", winner, 1, 2, draw, members, 1000, 0, 24);
    }
    private static Map<UUID, Integer> ratings(RankedMatchSnapshot match, int value) {
        Map<UUID, Integer> map = new HashMap<UUID, Integer>();
        for (RankedMatchSnapshot.Member member : match.members) map.put(member.playerId, value);
        return map;
    }
    @Test public void upsetGainsMoreAndLossAgainstStrongerCostsLess() {
        RankedMatchSnapshot match = snapshot(2, 1, "team0", false, false);
        UUID winner = match.members.get(0).playerId, loser = match.members.get(1).playerId;
        Map<UUID, Integer> weaker = ratings(match, 1000);
        weaker.put(loser, 1200);
        Map<UUID, Integer> stronger = ratings(match, 1000);
        stronger.put(winner, 1200);
        Map<UUID, Integer> upset = RankedRatingMath.changes(match, weaker, 24);
        Map<UUID, Integer> expected = RankedRatingMath.changes(match, stronger, 24);
        assertTrue(upset.get(winner) > expected.get(winner));
        assertTrue(Math.abs(upset.get(loser)) > Math.abs(expected.get(loser)));
    }
    @Test public void manyTeamsNeverMultiplyKAndLosersTieWithoutInventedPlaces() {
        for (int count : new int[] { 4, 8 }) {
            RankedMatchSnapshot match = snapshot(count, 1, "team0", false, false);
            Map<UUID, Integer> changes = RankedRatingMath.changes(match, ratings(match, 1000), 24);
            assertEquals(12, changes.get(match.members.get(0).playerId).intValue());
            for (int i = 1; i < match.members.size(); i++)
                assertTrue(changes.get(match.members.get(i).playerId) <= 0);
        }
    }
    @Test public void teamMembersShareDeltaDespiteDifferentIndividualRatings() {
        for (int size : new int[] { 2, 3, 4 }) {
            RankedMatchSnapshot match = snapshot(2, size, "team0", false, false);
            Map<UUID, Integer> rating = ratings(match, 1000);
            rating.put(match.members.get(0).playerId, 1400);
            Map<UUID, Integer> changes = RankedRatingMath.changes(match, rating, 24);
            int win = changes.get(match.members.get(0).playerId);
            for (int i = 1; i < size; i++) assertEquals(win, changes.get(match.members.get(i).playerId).intValue());
        }
    }
    @Test public void drawAndDefinitiveAbandonment() {
        RankedMatchSnapshot draw = snapshot(2, 1, null, true, false);
        Map<UUID, Integer> rating = ratings(draw, 1000);
        assertEquals(0, RankedRatingMath.changes(draw, rating, 24).get(draw.members.get(0).playerId).intValue());
        RankedMatchSnapshot abandoned = snapshot(2, 1, "team0", false, true);
        assertTrue(RankedRatingMath.changes(abandoned, ratings(abandoned, 1000), 24)
            .get(abandoned.members.get(0).playerId) < 0);
    }
}
