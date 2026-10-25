package com.starfinplugins.bedwars.stats;

import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatchStatsTrackerTest {
    @Test public void countsFinalKillAndBedOnlyForTheActor() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        MatchStatsTracker tracker = new MatchStatsTracker(UUID.randomUUID(),
            new MatchStatsTracker.Awards(25, 100, 10, 20, 40));
        tracker.addPlayer(first, "RED", "RedTester");
        tracker.addPlayer(second, "BLUE", "BlueTester");
        tracker.death(second, first, true);
        tracker.bed(first);
        List<MatchResult> values = tracker.finish("RED", false);
        assertEquals(2, values.size());
        MatchResult winner = values.get(0);
        assertEquals(MatchResult.Outcome.WIN, winner.outcome);
        assertEquals(1, winner.kills);
        assertEquals(1, winner.finalKills);
        assertEquals(1, winner.beds);
        assertEquals(195, winner.xp);
        assertEquals(MatchResult.Outcome.LOSS, values.get(1).outcome);
        assertEquals(1, values.get(1).deaths);
    }

    @Test public void doesNotCountFriendlyFireOrCreditAbandonedWinner() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        MatchStatsTracker tracker = new MatchStatsTracker(UUID.randomUUID(),
            new MatchStatsTracker.Awards(25, 100, 10, 20, 40));
        tracker.addPlayer(a, "RED", "Alpha");
        tracker.addPlayer(b, "RED", "Bravo");
        tracker.death(b, a, true);
        tracker.markAbandoned(a);
        List<MatchResult> results = tracker.finish("RED", false);
        assertEquals(0, results.get(0).kills);
        assertEquals(MatchResult.Outcome.LOSS, results.get(0).outcome);
        assertEquals(MatchResult.Outcome.WIN, results.get(1).outcome);
    }

    @Test public void drawNeverAwardsVictory() {
        MatchStatsTracker tracker = new MatchStatsTracker(UUID.randomUUID(),
            new MatchStatsTracker.Awards(25, 100, 10, 20, 40));
        tracker.addPlayer(UUID.randomUUID(), "A", "Player");
        assertEquals(MatchResult.Outcome.DRAW, tracker.finish(null, true).get(0).outcome);
        assertEquals(25, tracker.finish(null, true).get(0).xp);
    }
}
