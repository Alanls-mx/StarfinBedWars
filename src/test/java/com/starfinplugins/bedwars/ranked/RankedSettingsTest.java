package com.starfinplugins.bedwars.ranked;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankedSettingsTest {
    private static RankedSettings defaults() {
        return new RankedSettings(YamlConfiguration.loadConfiguration(
            new File("src/main/resources/ranked.yml")));
    }
    @Test public void divisionsAndPlacementAreDerivedFromRating() {
        RankedSettings settings = defaults();
        assertEquals(5, settings.placements);
        assertEquals("bronze", settings.division(899).id);
        assertEquals("silver", settings.division(900).id);
        assertEquals("legend", settings.division(1800).id);
        assertFalse(settings.modes.contains("custom"));
        RankedProfile fresh = new RankedProfile(java.util.UUID.randomUUID(), "solo",
            settings.context, 1000, 1000, 0, 0, 0, 0, 0, 0);
        assertFalse(fresh.isPlaced(settings.placements));
        RankedProfile placed = new RankedProfile(fresh.playerId, "solo", settings.context,
            1000, 1000, 5, 3, 2, 0, 5, 0);
        assertTrue(placed.isPlaced(settings.placements));
    }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverlappingThresholds() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File("src/main/resources/ranked.yml"));
        yaml.set("divisions.silver.minimum-rating", 0);
        new RankedSettings(yaml);
    }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidDivisionOrder() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File("src/main/resources/ranked.yml"));
        yaml.set("divisions.silver.order", 1);
        new RankedSettings(yaml);
    }
}
