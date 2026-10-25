package com.starfinplugins.bedwars.world;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.Difficulty;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class AlwaysSettingsTest {
    @Test public void worldAndArenaRulesInheritInOrder() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("defaults:\n  enabled: false\n  time: DAY\n  difficulty: 3\n"
            + "  weather: CLEAR\n  mob-spawning: false\n"
            + "arena-defaults:\n  enabled: true\n  time: 1000\n"
            + "worlds:\n  Speedway:\n    enabled: true\n    time: NIGHT\n"
            + "arenas:\n  '1':\n    time: DAY\n    difficulty: 2\n    weather: RAIN\n"
            + "    mob-spawning: unchanged\n    allow-monsters: true\n");
        AlwaysSettings settings = AlwaysSettings.parse(yaml);
        Assert.assertFalse(settings.forWorld("world").enabled);
        AlwaysSettings.Rule source = settings.forWorld("speedway");
        Assert.assertTrue(source.enabled);
        Assert.assertEquals(AlwaysSettings.TimeMode.NIGHT, source.time.mode);
        Assert.assertEquals(Difficulty.HARD, source.difficulty);
        AlwaysSettings.Rule genericArena = settings.forArena("other", "Speedway");
        Assert.assertEquals(AlwaysSettings.TimeMode.FIXED, genericArena.time.mode);
        Assert.assertEquals(1000, genericArena.time.tick);
        AlwaysSettings.Rule specificArena = settings.forArena("1", "Speedway");
        Assert.assertTrue(specificArena.enabled);
        Assert.assertEquals(AlwaysSettings.TimeMode.DAY, specificArena.time.mode);
        Assert.assertEquals(Difficulty.NORMAL, specificArena.difficulty);
        Assert.assertEquals(AlwaysSettings.Weather.RAIN, specificArena.weather);
        Assert.assertNull(specificArena.mobSpawning);
        Assert.assertEquals(Boolean.TRUE, specificArena.allowMonsters);
    }

    @Test public void validatesBundledDefaultsAndRejectsInvalidValues() throws Exception {
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/always.yml"), StandardCharsets.UTF_8));
        AlwaysSettings settings = AlwaysSettings.parse(bundled);
        Assert.assertFalse(settings.forWorld("world").enabled);
        Assert.assertTrue(settings.forArena("1", "Speedway").enabled);
        Assert.assertEquals(Difficulty.HARD, settings.forArena("1", "Speedway").difficulty);

        assertInvalid("defaults:\n  time: 24000\n");
        assertInvalid("defaults:\n  difficulty: 4\n");
        assertInvalid("defaults:\n  weather: SNOW\n");
        assertInvalid("defaults:\n  mob-spawning: maybe\n");
        assertInvalid("defaults:\n  unknown: true\n");
        assertInvalid("arena:\n  '1':\n    enabled: true\n");
    }

    private void assertInvalid(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        try {
            AlwaysSettings.parse(yaml);
            Assert.fail("Configuracao invalida aceita: " + text);
        } catch (IllegalArgumentException expected) { }
    }
}
