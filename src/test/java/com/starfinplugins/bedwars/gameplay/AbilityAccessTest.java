package com.starfinplugins.bedwars.gameplay;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assert;
import org.junit.Test;

/** The same matrix is exercised for each ability, independent of category. */
public class AbilityAccessTest {
    @Test public void everyAbilityRequiresOwnershipPermissionAndAnActiveParticipant() {
        AbilityCatalog catalog = AbilityCatalog.parse(YamlConfiguration.loadConfiguration(
            new InputStreamReader(getClass().getResourceAsStream("/abilities.yml"),
                StandardCharsets.UTF_8)));
        for (AbilityCatalog.Definition ability : catalog.abilities.values()) {
            for (int level : new int[] {1, 3, 5}) {
                Assert.assertTrue(ability.id, AbilityAccess.canApply(ability, level, true, 100,
                    "solo", true, true, true, false));
                Assert.assertFalse(ability.id, AbilityAccess.canApply(ability, level, false, 100,
                    "solo", true, true, true, false));
                Assert.assertFalse(ability.id, AbilityAccess.canApply(ability, level, true, 100,
                    "solo", true, false, true, false));
                Assert.assertFalse(ability.id, AbilityAccess.canApply(ability, level, true, 100,
                    "solo", true, true, false, false));
                Assert.assertFalse(ability.id, AbilityAccess.canApply(ability, level, true, 100,
                    "solo", true, true, true, true));
                Assert.assertFalse(ability.id, AbilityAccess.canEquip(ability, 0, true, 100,
                    "solo", true));
                Assert.assertFalse(ability.id, AbilityAccess.canEquip(ability, level, true, 100,
                    "solo", false));
                Assert.assertFalse(ability.id, AbilityAccess.canEquip(ability, level, true, 100,
                    "unknown", true));
                Assert.assertFalse(ability.id, AbilityAccess.canEquip(ability, level, true, 0,
                    "solo", true));
            }
            Assert.assertTrue(ability.id + " must have level 1 values", !ability.values(1).isEmpty());
            Assert.assertTrue(ability.id + " must have level 5 values", !ability.values(5).isEmpty());
            YamlConfiguration disabledYaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/abilities.yml"), StandardCharsets.UTF_8));
            disabledYaml.set("abilities." + ability.id + ".enabled", false);
            AbilityCatalog.Definition disabled = AbilityCatalog.parse(disabledYaml).abilities.get(ability.id);
            Assert.assertFalse(ability.id, AbilityAccess.canApply(disabled, 5, true, 100,
                "solo", true, true, true, false));
        }
    }

    @Test public void coreCalculationsHaveBoundedEffects() {
        Assert.assertEquals(45, AbilityEffectMath.respawnTicks(60, 25, 20));
        Assert.assertEquals(20, AbilityEffectMath.respawnTicks(20, 25, 20));
        Assert.assertEquals(10, AbilityEffectMath.respawnTicks(10, 25, 20));
        Assert.assertEquals(7.5, AbilityEffectMath.reduceDamage(10, 25, 0.65), 0.001);
        Assert.assertEquals(3.5, AbilityEffectMath.reduceDamage(10, 99, 0.65), 0.001);
        Assert.assertEquals(10.8, AbilityEffectMath.addDamage(10, 8), 0.001);
        Assert.assertEquals(7, AbilityEffectMath.fireSeconds(10, 30));
        Assert.assertEquals(0, AbilityEffectMath.fireSeconds(0, 30));
        Assert.assertEquals(1, AbilityEffectMath.shopCost(1, 6, 1));
        Assert.assertEquals(5, AbilityEffectMath.shopCost(5, 6, 10));
        Assert.assertEquals(19, AbilityEffectMath.shopCost(20, 6, 1));
        Assert.assertEquals(20, AbilityEffectMath.heal(15, 20, 2.5), 0.001);
        Assert.assertTrue(AbilityEffectMath.succeeds(5, 0.049));
        Assert.assertFalse(AbilityEffectMath.succeeds(5, 0.05));
    }
}
