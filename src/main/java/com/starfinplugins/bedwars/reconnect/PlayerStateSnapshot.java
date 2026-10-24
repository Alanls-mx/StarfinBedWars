package com.starfinplugins.bedwars.reconnect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/** Temporary BedWars inventory, separate from the pre-match inventory. */
public final class PlayerStateSnapshot {
    public static final int VERSION = 1;
    private final int version = VERSION;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final int heldSlot;
    private final double health;
    private final int food;
    private final float saturation;
    private final List<PotionEffect> effects = new ArrayList<PotionEffect>();
    private final Location location;

    public PlayerStateSnapshot(Player player) {
        contents = copy(player.getInventory().getContents());
        armor = copy(player.getInventory().getArmorContents());
        heldSlot = player.getInventory().getHeldItemSlot();
        health = player.getHealth();
        food = player.getFoodLevel();
        saturation = player.getSaturation();
        location = player.getLocation().clone();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            effects.add(new PotionEffect(effect.getType(), effect.getDuration(),
                effect.getAmplifier(), effect.isAmbient()));
        }
    }

    public Location getLocation() { return location.clone(); }
    public int getVersion() { return version; }

    public void restore(Player player) {
        if (version != VERSION || contents == null || armor == null || location == null) {
            throw new IllegalStateException("Snapshot de reconnect invalido.");
        }
        player.getInventory().setContents(copy(contents));
        player.getInventory().setArmorContents(copy(armor));
        player.getInventory().setHeldItemSlot(heldSlot);
        Collection<PotionEffect> current = new ArrayList<PotionEffect>(player.getActivePotionEffects());
        for (PotionEffect effect : current) player.removePotionEffect(effect.getType());
        for (PotionEffect effect : effects) player.addPotionEffect(effect, true);
        player.setHealth(Math.max(1.0, Math.min(health, player.getMaxHealth())));
        player.setFoodLevel(food);
        player.setSaturation(saturation);
        player.updateInventory();
    }

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] == null ? null : source[i].clone();
        }
        return result;
    }
}
