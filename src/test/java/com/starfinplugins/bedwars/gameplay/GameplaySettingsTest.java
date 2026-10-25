package com.starfinplugins.bedwars.gameplay;

import java.io.InputStreamReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameplaySettingsTest {
    @Test public void defaultCatalogContainsRealKitsAndAbilities() throws Exception {
        GameplaySettings settings = GameplaySettings.parse(YamlConfiguration.loadConfiguration(
            new InputStreamReader(getClass().getResourceAsStream("/gameplay.yml"), "UTF-8")));
        assertEquals(5, settings.kits.size());
        assertEquals(4, settings.abilities.size());
        assertEquals(4, settings.kits.get("default").startItems.get(0).amount);
        assertEquals(org.bukkit.Material.STONE_PICKAXE, settings.kits.get("miner").permanentTool);
        assertEquals(GameplaySettings.AbilityType.PASSIVE, settings.abilities.get("surefoot").type);
        assertEquals(GameplaySettings.AbilityType.ACTIVE, settings.abilities.get("dash").type);
        assertFalse(settings.splitEnabled);
        assertFalse(settings.antiAfkEnabled);
    }
}
