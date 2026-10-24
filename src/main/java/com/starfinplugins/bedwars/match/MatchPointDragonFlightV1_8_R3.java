package com.starfinplugins.bedwars.match;

import net.minecraft.server.v1_8_R3.EntityEnderDragon;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEnderDragon;
import org.bukkit.entity.EnderDragon;

/**
 * Drives the real 1.8.8 Ender Dragon flight AI, not Bukkit teleport().
 *
 * EnderDragon#m() naturally calculates forward velocity, body-part poses and
 * the yaw/height history used by the dragon's neck and tail animation. Bukkit
 * teleport every few ticks resets that interpolation, causing violent twisting.
 *
 * NMS fields a/b/c are the target coordinates, bA is the vanilla mob target,
 * and bw requests a random new target. These are specific to v1_8_R3.
 */
public final class MatchPointDragonFlightV1_8_R3 {
    public void steer(EnderDragon dragon, Location waypoint) {
        if (dragon == null || waypoint == null
                || dragon.getWorld() != waypoint.getWorld()) {
            throw new IllegalArgumentException("Invalid Match Point dragon waypoint.");
        }
        EntityEnderDragon handle = ((CraftEnderDragon) dragon).getHandle();
        handle.bA = null;
        handle.bw = false;
        handle.a = waypoint.getX();
        handle.b = waypoint.getY();
        handle.c = waypoint.getZ();
    }
}
