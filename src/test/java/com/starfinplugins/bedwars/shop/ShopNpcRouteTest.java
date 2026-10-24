package com.starfinplugins.bedwars.shop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;

public class ShopNpcRouteTest {
    private static final UUID MATCH = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Test public void resolvesOwnTeamShop() {
        ShopNpcRoute route = ShopNpcRoute.parse(MATCH + "|items:blue");
        assertNotNull(route);
        assertFalse(route.isUpgrades());
        assertTrue(route.canAccess(MATCH, "blue"));
        assertFalse(route.canAccess(MATCH, "red"));
    }

    @Test public void resolvesSharedUpgradeShop() {
        ShopNpcRoute route = ShopNpcRoute.parse(MATCH + "|upgrades");
        assertNotNull(route);
        assertTrue(route.isUpgrades());
        assertTrue(route.canAccess(MATCH, "red"));
    }

    @Test public void rejectsCrossMatchAccess() {
        ShopNpcRoute route = ShopNpcRoute.parse(MATCH + "|items:red");
        assertFalse(route.canAccess(UUID.randomUUID(), "red"));
    }

    @Test public void rejectsInvalidAndUnsupportedRoutes() {
        assertNull(ShopNpcRoute.parse(null));
        assertNull(ShopNpcRoute.parse("garbage"));
        assertNull(ShopNpcRoute.parse("wrong-uuid|items"));
        assertNull(ShopNpcRoute.parse(MATCH + "|unknown"));
        assertNull(ShopNpcRoute.parse(MATCH + "|items:"));
        assertNull(ShopNpcRoute.parse(MATCH + "|items:red:extra"));
        assertNull(ShopNpcRoute.parse(MATCH + "|items|upgrades"));
    }
}
