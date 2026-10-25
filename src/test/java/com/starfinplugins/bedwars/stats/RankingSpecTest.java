package com.starfinplugins.bedwars.stats;

import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankingSpecTest {
    @Test public void periodsUseUtcAcrossYearAndMonthBoundaries() {
        Instant now = Instant.parse("2027-01-01T00:30:00Z");
        RankingSpec week = new RankingSpec(RankingSpec.Metric.WINS, RankingSpec.Period.WEEKLY,
            "solo", 1, 10, 50, now);
        assertEquals(Instant.parse("2026-12-28T00:00:00Z").toEpochMilli(), week.fromMillis);
        assertEquals(Instant.parse("2027-01-04T00:00:00Z").toEpochMilli(), week.untilMillis);
        RankingSpec month = new RankingSpec(RankingSpec.Metric.WINS, RankingSpec.Period.MONTHLY,
            null, 1, 10, 50, now);
        assertEquals(Instant.parse("2027-01-01T00:00:00Z").toEpochMilli(), month.fromMillis);
        assertEquals(Instant.parse("2027-02-01T00:00:00Z").toEpochMilli(), month.untilMillis);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownModes() {
        new RankingSpec(RankingSpec.Metric.WINS, RankingSpec.Period.ALL,
            "sql' OR 1=1", 1, 10, 50, Instant.now());
    }
}
