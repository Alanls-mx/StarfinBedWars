package com.starfinplugins.bedwars.ranked;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankedSeasonSettingsTest {
    private YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources/seasons.yml"));
    }
    @Test public void defaultProposalNeverStartsProduction() {
        RankedSeasonSettings settings = new RankedSeasonSettings(defaults());
        assertFalse(settings.enabled);
        assertEquals("season-01", settings.id);
        assertFalse(settings.modes.contains("custom"));
        assertEquals(20, settings.rankingCacheSeconds);
    }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsReversedDates() {
        YamlConfiguration yml = defaults();
        yml.set("current.ends-at", "2026-10-01T00:00:00Z");
        new RankedSeasonSettings(yml);
    }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsSoftResetWithoutConfirmedSource() {
        YamlConfiguration yml = defaults();
        yml.set("current.reset.type", "SOFT_RESET");
        new RankedSeasonSettings(yml);
    }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateRewardIds() {
        YamlConfiguration yml = defaults();
        Map<String, Object> title = new HashMap<String, Object>();
        title.put("id", "same_id"); title.put("pt_BR", "Teste");
        yml.set("current.rewards.titles", Arrays.asList(title, title));
        new RankedSeasonSettings(yml);
    }
}
