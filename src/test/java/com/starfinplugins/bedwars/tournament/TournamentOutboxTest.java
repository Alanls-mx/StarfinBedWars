package com.starfinplugins.bedwars.tournament;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TournamentOutboxTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void resultSurvivesNewReaderAndAcknowledgesOnce() throws Exception {
        Path directory = folder.getRoot().toPath().resolve("pending");
        UUID game = UUID.randomUUID(), match = UUID.randomUUID();
        TournamentOutbox first = new TournamentOutbox(directory);
        first.write(new TournamentOutbox.Result(game, match, "VICTORY", "red"));
        TournamentOutbox restarted = new TournamentOutbox(directory);
        assertEquals(1, restarted.pending().size());
        Path pending = restarted.pending().get(0);
        TournamentOutbox.Result result = restarted.read(pending);
        assertEquals(game, result.gameId);
        assertEquals(match, result.minecraftMatch);
        assertEquals("VICTORY", result.reason);
        assertEquals("red", result.winningArenaTeam);
        restarted.acknowledge(pending);
        assertTrue(restarted.pending().isEmpty());
    }
}
