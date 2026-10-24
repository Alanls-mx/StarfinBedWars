package com.starfinplugins.bedwars.reconnect;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerStateSnapshotTest {
    private static final class InventoryState implements InvocationHandler {
        ItemStack[] contents = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];
        int held;
        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (name.equals("getContents")) return contents;
            if (name.equals("getArmorContents")) return armor;
            if (name.equals("getHeldItemSlot")) return held;
            if (name.equals("setContents")) { contents = (ItemStack[]) args[0]; return null; }
            if (name.equals("setArmorContents")) { armor = (ItemStack[]) args[0]; return null; }
            if (name.equals("setHeldItemSlot")) { held = (Integer) args[0]; return null; }
            return null;
        }
    }

    @Test public void restoresByReplacementWithIndependentItemCopies() {
        final InventoryState state = new InventoryState();
        state.contents[0] = new ItemStack(Material.IRON_INGOT, 12);
        state.contents[1] = new ItemStack(Material.GOLD_INGOT, 3);
        state.armor[0] = new ItemStack(Material.LEATHER_BOOTS, 1);
        final PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {PlayerInventory.class}, state);
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {Player.class}, new InvocationHandler() {
                @Override public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if (name.equals("getInventory")) return inventory;
                    if (name.equals("getHealth") || name.equals("getMaxHealth")) return 20.0;
                    if (name.equals("getFoodLevel")) return 20;
                    if (name.equals("getSaturation")) return 5.0f;
                    if (name.equals("getLocation")) return new Location(null, 0, 64, 0);
                    if (name.equals("getActivePotionEffects")) return Collections.emptyList();
                    return null;
                }
            });
        PlayerStateSnapshot snapshot = new PlayerStateSnapshot(player);
        state.contents[0].setAmount(1);
        state.contents[2] = new ItemStack(Material.DIAMOND, 64);
        state.armor[0] = null;
        snapshot.restore(player);
        assertEquals(12, state.contents[0].getAmount());
        assertEquals(3, state.contents[1].getAmount());
        assertNull(state.contents[2]);
        assertEquals(Material.LEATHER_BOOTS, state.armor[0].getType());
        state.contents[0].setAmount(5);
        snapshot.restore(player);
        assertEquals(12, state.contents[0].getAmount());
    }
}
