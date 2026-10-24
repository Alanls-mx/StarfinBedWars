package com.starfinplugins.bedwars.shop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;

public class ShopNpcRouteTest {
    private static final UUID MATCH = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Test public void allowsVisitorsToPurchaseFromAnotherIsland() {
        ShopNpcRoute route = ShopNpcRoute.parse(MATCH + "|items:blue");
        assertNotNull(route);
        assertFalse(route.isUpgrades());
        assertEquals("blue", route.getIslandTeamId());
        assertTrue(route.canAccess(MATCH, "blue"));
        assertTrue(route.canAccess(MATCH, "red"));
        assertEquals("red", route.getPurchasingTeamId(MATCH, "red"));
        assertEquals("blue", route.getPurchasingTeamId(MATCH, "blue"));
    }

    @Test public void enemyUpgradeNpcMustUpgradeThePurchaserTeam() {
        ShopNpcRoute route = ShopNpcRoute.parse(MATCH + "|upgrades:red");
        assertNotNull(route);
        assertTrue(route.isUpgrades());
        assertEquals("red", route.getIslandTeamId());
        assertTrue(route.canAccess(MATCH, "blue"));
        assertEquals("blue", route.getPurchasingTeamId(MATCH, "blue"));
        assertEquals("green", route.getPurchasingTeamId(MATCH, "green"));
    }

    @Test public void rejectsMissingPurchaserTeamEvenAtSharedNpc() {
        ShopNpcRoute shared = ShopNpcRoute.parse(MATCH + "|items");
        assertFalse(shared.canAccess(MATCH, null));
        assertFalse(shared.canAccess(MATCH, ""));
        assertFalse(shared.canAccess(MATCH, "  "));
        assertNull(shared.getPurchasingTeamId(MATCH, ""));
    }

    @Test public void resolvesSharedUpgradeShop() {
        ShopNpcRoute route = ShopNpcRoute.parse(MATCH + "|upgrades");
        assertNotNull(route);
        assertTrue(route.isUpgrades());
        assertTrue(route.canAccess(MATCH, "red"));
    }

    @Test public void rejectsCrossMatchAccessEvenForTheSameTeam() {
        ShopNpcRoute route = ShopNpcRoute.parse(MATCH + "|items:red");
        UUID otherMatch = UUID.fromString("00000000-0000-0000-0000-000000000124");
        assertFalse(route.canAccess(otherMatch, "red"));
        assertFalse(route.canAccess(otherMatch, "blue"));
        assertNull(route.getPurchasingTeamId(otherMatch, "red"));
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
