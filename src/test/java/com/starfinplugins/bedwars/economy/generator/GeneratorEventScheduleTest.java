package com.starfinplugins.bedwars.economy.generator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

public class GeneratorEventScheduleTest {
    @Test
    public void defaultScheduleIsOrderedAndContainsBothResources() throws Exception {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/generator-events.yml"), StandardCharsets.UTF_8));
        GeneratorEventSchedule schedule = GeneratorEventSchedule.parse(yaml);
        assertTrue(schedule.isEnabled());
        assertEquals(4, schedule.getEntries().size());
        assertEquals(360, schedule.getEntries().get(0).getTimeSeconds());
        assertEquals(GeneratorEventSchedule.Type.DIAMOND_UPGRADE,
            schedule.getEntries().get(0).getType());
        assertEquals(GeneratorEventSchedule.Type.EMERALD_UPGRADE,
            schedule.getEntries().get(1).getType());
        assertEquals(3, schedule.getEntries().get(3).getLevel());
    }

    @Test(expected = IOException.class)
    public void rejectsNonIncreasingTimes() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("events.schedule.1.time-seconds", 20);
        yaml.set("events.schedule.1.type", "DIAMOND_UPGRADE");
        yaml.set("events.schedule.1.level", 2);
        yaml.set("events.schedule.2.time-seconds", 10);
        yaml.set("events.schedule.2.type", "EMERALD_UPGRADE");
        yaml.set("events.schedule.2.level", 2);
        GeneratorEventSchedule.parse(yaml);
    }
}
