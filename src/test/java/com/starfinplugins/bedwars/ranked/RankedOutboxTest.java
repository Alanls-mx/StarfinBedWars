package com.starfinplugins.bedwars.ranked;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankedOutboxTest {
    @Test public void survivesRestartAndKeepsFrozenRatingPolicy() throws Exception {
        Path directory = Files.createTempDirectory("sbw-ranked-outbox");
        RankedOutbox writer = new RankedOutbox(directory);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        RankedMatchSnapshot original = new RankedMatchSnapshot(UUID.randomUUID(), "duo",
            "arena", "server", "instance", "preseason-1", "RED", 100, 200,
            false, Arrays.asList(new RankedMatchSnapshot.Member(first, "First", "RED", false),
                new RankedMatchSnapshot.Member(second, "Second", "BLUE", true)), 1200, 200, 32);
        writer.write(original);
        RankedOutbox restarted = new RankedOutbox(directory);
        RankedMatchSnapshot loaded = restarted.read(restarted.pending().get(0));
        assertEquals(original.matchId, loaded.matchId);
        assertEquals(1200, loaded.initialRating);
        assertEquals(200, loaded.minimumRating);
        assertEquals(32, loaded.kFactor);
        assertTrue(loaded.members.get(1).abandoned);
        restarted.acknowledge(restarted.pending().get(0));
        assertTrue(restarted.pending().isEmpty());
    }
}
