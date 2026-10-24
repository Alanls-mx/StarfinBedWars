package com.starfinplugins.bedwars.config;

import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BedWarsSettingsTest {
    private YamlConfiguration validConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("config-version", 1);
        config.set("general.debug", false);
        config.set("match.spawn-opening-seconds", 10);
        config.set("arena-bounds.default-width", 280);
        config.set("arena-bounds.default-depth", 280);
        config.set("arena-bounds.default-up-height", 50);
        config.set("arena-bounds.default-down-depth", 50);
        config.set("arena-bounds.maximum-dimension", 1000);
        return config;
    }

    @Test
    public void readsValidatedSettings() throws Exception {
        BedWarsSettings settings = BedWarsSettings.from(validConfig());
        assertFalse(settings.isDebugEnabled());
        assertEquals(3, settings.getRespawnSeconds());
        assertEquals(10, settings.getSpawnOpeningSeconds());
        assertTrue(settings.isTntAutoPrime());
        assertEquals(40, settings.getTntFuseTicks());
        assertEquals(280, settings.getAutomaticBoundsWidth());
        assertEquals(1000, settings.getAutomaticBoundsMaximumDimension());
    }

    @Test
    public void readsTntConfiguration() throws Exception {
        YamlConfiguration config = validConfig();
        config.set("match.tnt-auto-prime", false);
        config.set("match.tnt-fuse-ticks", 65);
        BedWarsSettings settings = BedWarsSettings.from(config);
        assertFalse(settings.isTntAutoPrime());
        assertEquals(65, settings.getTntFuseTicks());
    }

    @Test
    public void rejectsInvalidTntConfiguration() throws Exception {
        YamlConfiguration config = validConfig();
        config.set("match.tnt-auto-prime", "yes");
        assertInvalid(config);
        config = validConfig();
        config.set("match.tnt-fuse-ticks", 0);
        assertInvalid(config);
        config = validConfig();
        config.set("match.tnt-fuse-ticks", 201);
        assertInvalid(config);
    }

    @Test
    public void rejectsUnsupportedConfigurationVersion() throws Exception {
        YamlConfiguration config = validConfig();
        config.set("config-version", 2);
        assertInvalid(config);
    }

    @Test
    public void rejectsWrongTypesAndInvalidBounds() throws Exception {
        YamlConfiguration wrongType = validConfig();
        wrongType.set("general.debug", "false");
        assertInvalid(wrongType);

        YamlConfiguration invalidRespawn = validConfig();
        invalidRespawn.set("match.respawn-seconds", "five");
        assertInvalid(invalidRespawn);

        YamlConfiguration invalidBounds = validConfig();
        invalidBounds.set("arena-bounds.maximum-dimension", 279);
        assertInvalid(invalidBounds);
    }

    private void assertInvalid(YamlConfiguration config) throws Exception {
        try {
            BedWarsSettings.from(config);
            fail("Invalid configuration was accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }
}
