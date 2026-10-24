package com.starfinplugins.bedwars.match;

import net.minecraft.server.v1_8_R3.EntityArmorStand;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntityLiving;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Packet-only label: no server entity can leak into another player's chunk view. */
final class VirtualBedLabelV1_8_R3 {
    private final EntityArmorStand entity;

    VirtualBedLabelV1_8_R3(Location at, String name) {
        entity = new EntityArmorStand(((CraftWorld) at.getWorld()).getHandle(),
            at.getX(), at.getY(), at.getZ());
        entity.setInvisible(true);
        entity.setGravity(false);
        entity.setBasePlate(false);
        entity.n(true);
        entity.setCustomName(name);
        entity.setCustomNameVisible(true);
    }

    void show(Player player) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(
            new PacketPlayOutSpawnEntityLiving(entity));
    }

    void rename(Player player, String text) {
        entity.setCustomName(text);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(
            new PacketPlayOutEntityMetadata(entity.getId(), entity.getDataWatcher(), true));
    }

    void hide(Player player) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(
            new PacketPlayOutEntityDestroy(entity.getId()));
    }
}
