package com.starfinplugins.bedwars.gameplay;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class KitCatalogTest {
    private PurchaseSettings defaults() {
        return PurchaseSettings.parse(YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/purchase-settings.yml"), StandardCharsets.UTF_8)));
    }
    private KitCatalog catalog() {
        return KitCatalog.parse(YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/kits.yml"), StandardCharsets.UTF_8)), defaults());
    }
    @Test public void twelveOfficialKitsHaveFiveLevelsAndPermission() {
        KitCatalog kits = catalog();
        Assert.assertEquals(12, kits.kits.size());
        for (String id : KitCatalog.IDS) {
            KitCatalog.Definition kit = kits.kits.get(id);
            Assert.assertEquals("starfinbedwars.kit." + id, kit.permission);
            Assert.assertEquals(PurchaseSettings.Scope.PER_MODE, kit.scope);
            Assert.assertTrue(kit.modes.contains("solo"));
            for (int level = 1; level <= 5; level++) {
                Assert.assertTrue(kit.value(level) > 0);
                Assert.assertTrue(kit.price(level) > 0);
            }
        }
        Assert.assertEquals(4, kits.kits.get("builder").value(1));
        Assert.assertEquals(12, kits.kits.get("builder").value(5));
        Assert.assertEquals(12, kits.kits.get("miner").value(1));
        Assert.assertEquals(36, kits.kits.get("lumberjack").value(5));
        Assert.assertEquals(14, kits.kits.get("shears").value(5));
        Assert.assertEquals(8, kits.kits.get("explorer").value(5));
        Assert.assertFalse(kits.kits.get("pyromancer").enabled);
    }
    @Test public void scopeCategoryAndModesCanChangeIndependently() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/kits.yml"), StandardCharsets.UTF_8));
        yaml.set("kits.builder.purchase.scope", "GLOBAL");
        yaml.set("kits.builder.category", "SPECIAL");
        yaml.set("kits.builder.allowed-modes", java.util.Arrays.asList("SOLO"));
        KitCatalog.Definition kit = KitCatalog.parse(yaml, defaults()).kits.get("builder");
        Assert.assertEquals(PurchaseSettings.Scope.GLOBAL, kit.scope);
        Assert.assertEquals(KitCatalog.Category.SPECIAL, kit.category);
        Assert.assertTrue(kit.modes.contains("solo"));
        Assert.assertFalse(kit.modes.contains("duo"));
        yaml.set("kits.builder.permission", "");
        try { KitCatalog.parse(yaml, defaults()); Assert.fail("Missing permission accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void permissionModeAndDeliveryAreEnforced() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/kits.yml"), StandardCharsets.UTF_8));
        yaml.set("kits.builder.allowed-modes", java.util.Arrays.asList("SOLO"));
        KitCatalog.Definition builder = KitCatalog.parse(yaml, defaults()).kits.get("builder");
        Assert.assertTrue(KitAccess.canEquip(builder, 3, true, 1, "solo", true));
        Assert.assertFalse(KitAccess.canEquip(builder, 3, true, 1, "duo", true));
        Assert.assertFalse(KitAccess.canEquip(builder, 3, false, 1, "solo", true));
        Assert.assertFalse(KitAccess.canEquip(builder, 0, true, 1, "solo", true));
        Assert.assertFalse(KitAccess.canEquip(builder, 3, true, 1, "solo", false));
        Assert.assertTrue(KitAccess.shouldDeliver(builder.delivery, false, false));
        Assert.assertFalse(KitAccess.shouldDeliver(builder.delivery, false, true));
        Assert.assertFalse(KitAccess.shouldDeliver(builder.delivery, true, true));
        Assert.assertTrue(KitAccess.shouldDeliver(KitCatalog.Delivery.EVERY_RESPAWN, true, true));
    }
}
