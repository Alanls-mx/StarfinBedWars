package com.starfinplugins.bedwars.gameplay;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class AbilityCatalogTest {
    private AbilityCatalog bundled() {
        return AbilityCatalog.parse(YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/abilities.yml"), StandardCharsets.UTF_8)));
    }

    @Test public void allEighteenHaveFiveConfiguredLevelsAndPermissions() {
        AbilityCatalog catalog = bundled();
        Assert.assertEquals(18, catalog.abilities.size());
        for (String id : AbilityCatalog.IDS) {
            AbilityCatalog.Definition ability = catalog.abilities.get(id);
            Assert.assertNotNull(ability);
            Assert.assertEquals(5, ability.maxLevel);
            Assert.assertEquals("starfinbedwars.ability." + id, ability.permission);
            Assert.assertTrue(ability.priceFor(2) >= 0);
            Assert.assertTrue(ability.priceFor(5) >= 0);
            Assert.assertFalse(ability.values(1).isEmpty());
            Assert.assertFalse(ability.values(3).isEmpty());
            Assert.assertFalse(ability.values(5).isEmpty());
        }
    }

    @Test public void unsafeValuesFailBeforeReload() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/abilities.yml"), StandardCharsets.UTF_8));
        yaml.set("abilities.respawn_boost.levels.5.reduction-percent", 99);
        try { AbilityCatalog.parse(yaml); Assert.fail("unsafe value accepted"); }
        catch (IllegalArgumentException expected) { }
        yaml.set("abilities.respawn_boost.levels.5.reduction-percent", 25);
        yaml.set("abilities.respawn_boost.permission", "");
        try { AbilityCatalog.parse(yaml); Assert.fail("missing permission accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
