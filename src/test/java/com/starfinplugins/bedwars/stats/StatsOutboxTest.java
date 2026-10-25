package com.starfinplugins.bedwars.stats;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class StatsOutboxTest {
    @Test public void acceptsLegacyOutboxWithoutFinalDeaths() throws Exception {
        Path folder = Files.createTempDirectory("sbw-old-stat-outbox");
        UUID match = UUID.randomUUID(), player = UUID.randomUUID();
        java.util.Properties previous = new java.util.Properties();
        previous.setProperty("match", match.toString());
        previous.setProperty("count", "1");
        previous.setProperty("player.0", player + "|Tester|4|2|3|1|WIN|200");
        Path file = folder.resolve(match + ".properties");
        try (java.io.OutputStream stream = Files.newOutputStream(file)) { previous.store(stream, "old"); }
        MatchResult recovered = new StatsOutbox(folder).read(file).get(0);
        assertEquals(0, recovered.finalDeaths);
        assertEquals(3, recovered.deaths);
        assertEquals(1, recovered.beds);
        assertEquals(200, recovered.xp);
        Files.delete(file);
        Files.delete(folder);
    }

    @Test public void survivesRestartAndAcknowledgesOnlyAfterCommit() throws Exception {
        Path folder = Files.createTempDirectory("sbw-stat-outbox-test");
        StatsOutbox queue = new StatsOutbox(folder);
        MatchResult result = new MatchResult(UUID.randomUUID(), UUID.randomUUID(), "Player_1",
            3, 1, 2, 1, MatchResult.Outcome.WIN, 250);
        Path file = queue.write(Collections.singletonList(result));
        StatsOutbox reloaded = new StatsOutbox(folder);
        assertEquals(1, reloaded.pending().size());
        MatchResult recovered = reloaded.read(file).get(0);
        assertEquals(result.matchId, recovered.matchId);
        assertEquals(result.playerId, recovered.playerId);
        assertEquals(250, recovered.xp);
        assertEquals(1, recovered.finalKills);
        assertEquals(0, recovered.finalDeaths);
        reloaded.acknowledge(file);
        assertEquals(0, reloaded.pending().size());
        Files.delete(folder);
    }

    @Test public void readsPreviousNineFieldFormatAndNewModeTimestamp() throws Exception {
        Path folder = Files.createTempDirectory("sbw-stat-outbox-v2");
        UUID match = UUID.randomUUID(), player = UUID.randomUUID();
        java.util.Properties old = new java.util.Properties();
        old.setProperty("match", match.toString());
        old.setProperty("count", "1");
        old.setProperty("player.0", player + "|Tester|4|2|3|1|1|WIN|200");
        Path legacy = folder.resolve(match + ".properties");
        try (java.io.OutputStream output = Files.newOutputStream(legacy)) { old.store(output, "old"); }
        MatchResult recovered = new StatsOutbox(folder).read(legacy).get(0);
        assertNull(recovered.mode);
        assertTrue(recovered.completedAtMillis > 0);
        assertEquals(1, recovered.finalDeaths);
        Files.delete(legacy);
        MatchResult modern = new MatchResult(UUID.randomUUID(), player, "Tester", 4, 2, 3, 1, 1,
            MatchResult.Outcome.WIN, 200, "solo", 123456789L);
        Path file = new StatsOutbox(folder).write(Collections.singletonList(modern));
        MatchResult roundTrip = new StatsOutbox(folder).read(file).get(0);
        assertEquals("solo", roundTrip.mode);
        assertEquals(123456789L, roundTrip.completedAtMillis);
        Files.delete(file);
        Files.delete(folder);
    }
}
