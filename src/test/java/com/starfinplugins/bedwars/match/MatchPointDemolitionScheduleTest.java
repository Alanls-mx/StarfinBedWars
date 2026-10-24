package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MatchPointDemolitionScheduleTest {
    @Test public void reachesEightyPercentAfterFiveMinutesAndAllAfterEight() {
        MatchPointDemolitionSchedule schedule = new MatchPointDemolitionSchedule(5, 8, 80);
        long total = 100000L;
        assertEquals(0L, schedule.target(0L, total));
        assertEquals(40000L, schedule.target(150000L, total));
        assertEquals(80000L, schedule.target(300000L, total));
        assertEquals(90000L, schedule.target(390000L, total));
        assertEquals(100000L, schedule.target(480000L, total));
        assertEquals(100000L, schedule.target(600000L, total));
    }

    @Test public void usesOriginalDestructibleBlockCountNotAirVolume() {
        MatchPointDemolitionSchedule schedule = new MatchPointDemolitionSchedule(5, 8, 80);
        assertEquals(0L, schedule.target(480000L, 0L));
        assertEquals(80L, schedule.target(300000L, 100L));
        assertEquals(100L, schedule.target(480000L, 100L));
    }

    @Test public void customTargetsAreMonotonic() {
        MatchPointDemolitionSchedule schedule = new MatchPointDemolitionSchedule(3, 6, 65);
        long previous = 0;
        for (long time = 0; time <= 400000; time += 1000) {
            long now = schedule.target(time, 1200);
            assertTrue(now >= previous);
            assertTrue(now <= 1200);
            previous = now;
        }
        assertEquals(780L, schedule.target(180000L, 1200L));
        assertEquals(1200L, schedule.target(360000L, 1200L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void completeDeadlineMustFollowPartial() {
        new MatchPointDemolitionSchedule(8, 5, 80);
    }
}
