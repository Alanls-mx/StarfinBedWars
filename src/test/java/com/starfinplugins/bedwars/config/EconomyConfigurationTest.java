package com.starfinplugins.bedwars.config;

import com.starfinplugins.bedwars.domain.economy.GeneratorLevel;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.domain.economy.ShopItemDefinition;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EconomyConfigurationTest {
    @Test
    public void shopCatalogHasValidItemsAndUniqueSlots() throws Exception {
        YamlConfiguration yaml = load("shop.yml");
        ConfigurationSection items = yaml.getConfigurationSection("items");
        assertNotNull(items);
        Set<Integer> slots = new HashSet<Integer>();
        for (String id : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(id);
            assertNotNull(item);
            new ShopItemDefinition(id, item.getString("name"),
                Material.valueOf(item.getString("material")), (short) item.getInt("data"),
                item.getInt("amount"), ResourceType.valueOf(item.getString("resource")),
                item.getInt("cost"), item.getInt("slot"), item.getBoolean("quick-buy"));
            assertTrue("duplicate shop slot", slots.add(item.getInt("slot")));
        }
        assertTrue(items.getKeys(false).size() >= 20);
    }

    @Test
    public void generatorLevelsAreSequentialAndWithinDomainLimits() throws Exception {
        YamlConfiguration yaml = load("generators.yml");
        ConfigurationSection resources = yaml.getConfigurationSection("resources");
        assertNotNull(resources);
        for (ResourceType resource : ResourceType.values()) {
            ConfigurationSection levels = resources.getConfigurationSection(
                resource.name().toLowerCase(java.util.Locale.ROOT) + ".levels");
            assertNotNull(levels);
            int expected = 1;
            for (String key : levels.getKeys(false)) {
                assertEquals(Integer.toString(expected++), key);
                ConfigurationSection level = levels.getConfigurationSection(key);
                assertNotNull(level);
                new GeneratorLevel(level.getInt("interval-ticks"), level.getInt("amount"),
                    level.getInt("max-dropped"));
            }
            assertFalse(levels.getKeys(false).isEmpty());
        }
    }

    private YamlConfiguration load(String name) throws Exception {
        InputStream input = getClass().getClassLoader().getResourceAsStream(name);
        assertNotNull("missing resource " + name, input);
        try {
            return YamlConfiguration.loadConfiguration(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        } finally {
            input.close();
        }
    }
}
