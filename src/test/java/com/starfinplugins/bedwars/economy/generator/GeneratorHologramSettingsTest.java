package com.starfinplugins.bedwars.economy.generator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

public class GeneratorHologramSettingsTest {
    private YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
    }

    @Test public void defaultPresetHasFourIconsAndTwoTickUpdates() {
        GeneratorHologramSettings settings = new GeneratorHologramSettings(defaults());
        assertTrue(settings.enabled);
        assertEquals(4, settings.iconCount);
        assertEquals(2, settings.orbitInterval);
        assertEquals(1.05D, settings.orbitRadius, 0.0D);
    }

    @Test public void disabledOrbitAcceptsZeroIcons() {
        YamlConfiguration config = defaults();
        config.set("generator-holograms.orbit.enabled", false);
        config.set("generator-holograms.orbit.icon-count", 0);
        GeneratorHologramSettings settings = new GeneratorHologramSettings(config);
        assertFalse(settings.orbit);
        assertEquals(0, settings.iconCount);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsIconCountOverMaximum() {
        YamlConfiguration config = defaults();
        config.set("generator-holograms.orbit.icon-count", 9);
        new GeneratorHologramSettings(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeRadius() {
        YamlConfiguration config = defaults();
        config.set("generator-holograms.orbit.radius", -1.0D);
        new GeneratorHologramSettings(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownMaterial() {
        YamlConfiguration config = defaults();
        config.set("generator-holograms.resources.emerald.orbit-material", "NETHERITE_INGOT");
        new GeneratorHologramSettings(config);
    }
}
