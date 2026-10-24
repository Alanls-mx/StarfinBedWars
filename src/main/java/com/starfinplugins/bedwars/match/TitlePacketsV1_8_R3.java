package com.starfinplugins.bedwars.match;

import net.minecraft.server.v1_8_R3.ChatComponentText;
import net.minecraft.server.v1_8_R3.PacketPlayOutTitle;
import net.minecraft.server.v1_8_R3.PacketPlayOutTitle.EnumTitleAction;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

final class TitlePacketsV1_8_R3 {
    private TitlePacketsV1_8_R3() { }

    static void send(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        net.minecraft.server.v1_8_R3.PlayerConnection connection =
            ((CraftPlayer) player).getHandle().playerConnection;
        connection.sendPacket(new PacketPlayOutTitle(fadeIn, stay, fadeOut));
        connection.sendPacket(new PacketPlayOutTitle(EnumTitleAction.TITLE,
            new ChatComponentText(title)));
        connection.sendPacket(new PacketPlayOutTitle(EnumTitleAction.SUBTITLE,
            new ChatComponentText(subtitle)));
    }
}
