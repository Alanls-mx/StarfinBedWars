package com.starfinplugins.bedwars.stats;

import java.io.StringReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class RewardsCatalogTest {
    private final XpProgression progression = new XpProgression(1000, 250);

    @Test public void titleThresholdsFollowExistingXpCurveAndTranslations() {
        RewardsCatalog catalog = RewardsCatalog.parse(YamlConfiguration.loadConfiguration(new StringReader(
            "levels:\n  5:\n    rewards:\n      - type: TITLE_UNLOCK\n        id: hero\n"
            + "        titles: {pt_BR: Heroi, en_US: Hero, es_ES: Heroe}\n")), progression);
        RewardsCatalog.TitleReward title = catalog.title("hero");
        assertNotNull(title);
        assertEquals(5, title.level);
        assertEquals(progression.totalXpForLevel(5), title.requiredTotalXp);
        assertEquals("Hero", title.name("en_US"));
        assertEquals("Heroe", title.name("es_ES"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateIdsAcrossLevels() {
        RewardsCatalog.parse(YamlConfiguration.loadConfiguration(new StringReader(
            "levels:\n  5:\n    rewards:\n      - type: TITLE_UNLOCK\n        id: repeat\n"
            + "  10:\n    rewards:\n      - type: TITLE_UNLOCK\n        id: repeat\n")), progression);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownRewardType() {
        RewardsCatalog.parse(YamlConfiguration.loadConfiguration(new StringReader(
            "levels:\n  5:\n    rewards:\n      - type: CONSOLE_COMMAND\n        id: unsafe\n")), progression);
    }
}
