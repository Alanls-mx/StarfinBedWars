package com.starfinplugins.bedwars.shop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.Test;

public class ShopInventoryGuardTest {
    private static final class DummyHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static Inventory wrap(final InventoryHolder holder, final int size) {
        return (Inventory) Proxy.newProxyInstance(
            Inventory.class.getClassLoader(),
            new Class<?>[] { Inventory.class },
            new InvocationHandler() {
                @Override public Object invoke(Object proxy, Method method, Object[] args) {
                    if ("getHolder".equals(method.getName())) return holder;
                    if ("getSize".equals(method.getName())) return size;
                    if ("toString".equals(method.getName())) return "InventoryWrapper";
                    return null;
                }
            });
    }

    @Test public void acceptsDifferentInventoryWrapperWhenHolderMatches() {
        DummyHolder menu = new DummyHolder();
        Inventory original = wrap(menu, 54);
        Inventory reopenedViewWrapper = wrap(menu, 54);
        assertTrue(original != reopenedViewWrapper);
        assertTrue(ShopInventoryGuard.isSameShop(reopenedViewWrapper, menu));
    }

    @Test public void rejectsAnotherShopEvenWithSameSize() {
        assertFalse(ShopInventoryGuard.isSameShop(wrap(new DummyHolder(), 54),
            new DummyHolder()));
    }

    @Test public void rejectsPlayerInventoryAndClosedView() {
        DummyHolder menu = new DummyHolder();
        assertFalse(ShopInventoryGuard.isSameShop(wrap(menu, 45), menu));
        assertFalse(ShopInventoryGuard.isSameShop(null, menu));
        assertFalse(ShopInventoryGuard.isSameShop(wrap(menu, 54), null));
    }
}
