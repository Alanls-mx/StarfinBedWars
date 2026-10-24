package com.starfinplugins.bedwars;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PluginDescriptorTest {
    @Test
    public void descriptorDeclaresPluginEntryAndReconnectCommand() throws Exception {
        InputStream descriptor = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(descriptor);
        PluginDescriptionFile description;
        try (InputStream input = descriptor) {
            description = new PluginDescriptionFile(input);
        }

        assertEquals("StarfinBedWars", description.getName());
        assertEquals("1.0.0-SNAPSHOT", description.getVersion());
        assertEquals("com.starfinplugins.bedwars.StarfinBedWarsPlugin", description.getMain());
        assertTrue(!description.getSoftDepend().contains("StarfinCore"));
        assertTrue(description.getCommands().containsKey("starfinbedwars"));
        assertTrue(description.getCommands().containsKey("reconnect"));
        Map<String, Object> command = description.getCommands().get("starfinbedwars");
        Object aliases = command.get("aliases");
        assertTrue(aliases instanceof List);
        assertTrue(((List<?>) aliases).contains("sbw"));
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            yaml.load(input);
        }
        assertTrue(yaml.getConfigurationSection("permissions")
            .contains("starfinbedwars.command.status"));
        assertTrue(yaml.getConfigurationSection("permissions")
            .contains("starfinbedwars.command.arena"));
    }
}
