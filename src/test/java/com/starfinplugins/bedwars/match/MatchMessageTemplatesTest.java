package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class MatchMessageTemplatesTest {
    @Test public void formatsJoinWithAccurateCounts() {
        assertEquals("Alan entrou (2/8)!",
            MatchMessageTemplates.format("{player} entrou ({on}/{max})!",
                "player", "Alan", "on", "2", "max", "8"));
    }

    @Test public void formatsSeparateCountdowns() {
        assertEquals("Lobby: 10s", MatchMessageTemplates.format("Lobby: {time}s", "time", "10"));
        assertEquals("Spawn: 5s", MatchMessageTemplates.format("Spawn: {time}s", "time", "5"));
    }

    @Test public void retainsColorCodesForBukkitProcessing() {
        assertEquals("&cVoce nao pode destruir sua propria cama!",
            MatchMessageTemplates.format("&cVoce nao pode destruir sua propria cama!"));
    }

    @Test public void routesVoidAndFinalKillReasons() {
        assertEquals("messages.death.void",
            MatchMessageTemplates.deathKey("void", false, false));
        assertEquals("messages.death.void-killer",
            MatchMessageTemplates.deathKey("void", true, false));
        assertEquals("messages.death.void-killer-final",
            MatchMessageTemplates.deathKey("void", true, true));
        assertEquals("messages.death.fall",
            MatchMessageTemplates.deathKey("fall", false, false));
        assertEquals("messages.death.generic",
            MatchMessageTemplates.deathKey("something-else", false, false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsIncompleteReplacementArguments() {
        MatchMessageTemplates.format("{player}", "player");
    }

    @Test public void replacesMultipleInstancesAndNull() {
        assertEquals("X + X, ",
            MatchMessageTemplates.format("{x} + {x}, {y}", "x", "X", "y", null));
    }
}
