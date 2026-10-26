package com.starfinplugins.bedwars.gameplay;

import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class ScopedProgressTest {
    @Test public void globalAndModeLevelsStaySeparate() {
        ScopedProgress snapshot = ScopedProgress.EMPTY
            .withLevel(ScopedProgress.Kind.KIT, "builder", "GLOBAL", 3)
            .withLevel(ScopedProgress.Kind.KIT, "builder", "solo", 5)
            .withLevel(ScopedProgress.Kind.ABILITY, "respawn_boost", "duo", 2);
        Assert.assertEquals(5, snapshot.level(ScopedProgress.Kind.KIT, "builder", "solo"));
        Assert.assertEquals(3, snapshot.level(ScopedProgress.Kind.KIT, "builder", "duo"));
        Assert.assertEquals(0, snapshot.level(ScopedProgress.Kind.ABILITY, "respawn_boost", "solo"));
        Assert.assertEquals(2, snapshot.level(ScopedProgress.Kind.ABILITY, "respawn_boost", "DOUBLES"));
        snapshot = snapshot.withLevel(ScopedProgress.Kind.KIT, "builder", "solo", 1);
        Assert.assertEquals(5, snapshot.level(ScopedProgress.Kind.KIT, "builder", "solo"));
    }
    @Test public void selectionsAreIndependentOfPurchases() {
        ScopedProgress snapshot = ScopedProgress.EMPTY
            .withSelection(ScopedProgress.Kind.KIT, "solo", "builder")
            .withSelection(ScopedProgress.Kind.KIT, "duo", "miner")
            .withSelection(ScopedProgress.Kind.ABILITY, "solo", "respawn_boost");
        Assert.assertEquals("builder", snapshot.selection(ScopedProgress.Kind.KIT, "solo", "default"));
        Assert.assertEquals("miner", snapshot.selection(ScopedProgress.Kind.KIT, "duo", "default"));
        Assert.assertEquals("default", snapshot.selection(ScopedProgress.Kind.KIT, "trio", "default"));
        Assert.assertEquals("respawn_boost", snapshot.selection(ScopedProgress.Kind.ABILITY,
            "solo", "none"));
    }
    @Test public void modeAliasesUseExistingCanonicalIdentifiers() {
        PurchaseSettings defaults = PurchaseSettings.parse(YamlConfigurationForTest.defaults());
        Assert.assertEquals(5, defaults.modes.size());
        Assert.assertEquals("duo", PurchaseSettings.purchaseKey(PurchaseSettings.Scope.PER_MODE,
            "DOUBLES"));
        Assert.assertEquals("GLOBAL", PurchaseSettings.purchaseKey(PurchaseSettings.Scope.GLOBAL,
            "QUADS"));
    }

    private static final class YamlConfigurationForTest {
        static org.bukkit.configuration.file.YamlConfiguration defaults() {
            return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(ScopedProgressTest.class
                    .getResourceAsStream("/purchase-settings.yml"), java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
