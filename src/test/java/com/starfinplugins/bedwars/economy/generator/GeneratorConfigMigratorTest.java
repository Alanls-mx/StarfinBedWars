package com.starfinplugins.bedwars.economy.generator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GeneratorConfigMigratorTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void updatesOldDefaultsAndKeepsCustomRates() throws Exception {
        File file = folder.newFile("generators.yml");
        String original = "resources:\n  iron:\n    levels:\n      1:\n        interval-ticks: 40\n"
            + "  gold:\n    levels:\n      1:\n        interval-ticks: 270\n";
        Files.write(file.toPath(), original.getBytes(StandardCharsets.UTF_8));
        assertTrue(GeneratorConfigMigrator.migrate(file));
        YamlConfiguration updated = YamlConfiguration.loadConfiguration(file);
        assertEquals(80, updated.getInt("resources.iron.levels.1.interval-ticks"));
        assertEquals(270, updated.getInt("resources.gold.levels.1.interval-ticks"));
        assertEquals(2, updated.getInt("config-version"));
        assertEquals(original, new String(Files.readAllBytes(new File(folder.getRoot(),
            "generators.yml.pre-slow-lv1.bak").toPath()), StandardCharsets.UTF_8));
        assertFalse(GeneratorConfigMigrator.migrate(file));
    }
}
