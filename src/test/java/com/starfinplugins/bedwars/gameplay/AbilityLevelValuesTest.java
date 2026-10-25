package com.starfinplugins.bedwars.gameplay;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assert;
import org.junit.Test;

/** Locks the published level curves, including half-point levels, for all 18 abilities. */
public class AbilityLevelValuesTest {
    private AbilityCatalog catalog() {
        return AbilityCatalog.parse(YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/abilities.yml"), StandardCharsets.UTF_8)));
    }

    private void levels(AbilityCatalog catalog, String id, String key, double... expected) {
        for (int level = 1; level <= 5; level++)
            Assert.assertEquals(id + " level " + level, expected[level - 1],
                catalog.abilities.get(id).value(level, key), 0.0001);
    }

    @Test public void publishedCurvesMatchAllEighteenEffects() {
        AbilityCatalog c = catalog();
        levels(c, "respawn_boost", "reduction-percent", 5, 10, 15, 20, 25);
        levels(c, "knockback_resist", "knockback-reduction", 3, 5, 7, 9, 12);
        levels(c, "fall_resistance", "reduction-percent", 5, 10, 15, 20, 25);
        levels(c, "fire_resistance", "duration-reduction-percent", 10, 15, 20, 25, 30);
        levels(c, "projectile_resistance", "reduction-percent", 3, 5, 7, 9, 12);
        levels(c, "miner", "duration-seconds", 1.5, 2, 2.5, 3, 3.5);
        levels(c, "momentum", "duration-seconds", 1, 1.5, 2, 2.5, 3);
        levels(c, "sentinel", "radius", 5, 6, 7, 8, 9);
        levels(c, "collector", "extra-radius", 0.5, 0.75, 1, 1.25, 1.5);
        levels(c, "archer", "chance-percent", 5, 7.5, 10, 12.5, 15);
        levels(c, "builder", "blocks-required", 24, 21, 18, 15, 12);
        levels(c, "merchant", "discount-percent", 2, 3, 4, 5, 6);
        levels(c, "aegis", "absorption-hearts", 0.5, 1, 1.5, 1.5, 2);
        levels(c, "vampire", "heal-hearts", 0.5, 1, 1.5, 2, 2.5);
        levels(c, "frenzy", "damage-percent", 2, 3, 4, 6, 8);
        levels(c, "bodyguard", "reduction-percent", 4, 5.5, 7, 8.5, 10);
        levels(c, "forge_master", "chance-percent", 2, 3, 4, 5, 6);
        levels(c, "last_stand", "reduction-percent", 3, 4.5, 6, 7.5, 9);
        Assert.assertEquals(5, c.abilities.get("aegis").value(5, "duration-seconds"), 0.001);
        Assert.assertEquals(3, c.abilities.get("builder").value(5, "duration-seconds"), 0.001);
    }
}
