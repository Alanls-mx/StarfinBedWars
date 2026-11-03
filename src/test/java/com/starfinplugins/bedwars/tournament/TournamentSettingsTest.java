package com.starfinplugins.bedwars.tournament;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class TournamentSettingsTest {
    @Test public void unsafeAutomaticWinsAndRankedMixAreRejected() {
        YamlConfiguration settings = new YamlConfiguration();
        settings.set("proposal.automatic-walkover", true);
        try { new TournamentSettings(settings); fail("automatic walkover"); }
        catch (IllegalArgumentException expected) { /* expected */ }
        settings.set("proposal.automatic-walkover", false);
        settings.set("statistics.affect-ranked-mmr", true);
        try { new TournamentSettings(settings); fail("ranked MMR mixing"); }
        catch (IllegalArgumentException expected) { /* expected */ }
    }
}
