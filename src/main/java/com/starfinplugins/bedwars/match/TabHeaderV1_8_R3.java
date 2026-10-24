package com.starfinplugins.bedwars.match;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.entity.Player;

/** Sends the 1.8.8 player-list header/footer without changing the sidebar scoreboard. */
final class TabHeaderV1_8_R3 {
    private TabHeaderV1_8_R3() { }

    static void send(Player player, String header, String footer) {
        try {
            if (!player.getClass().getPackage().getName().contains(".v1_8_R3.")) {
                throw new IllegalStateException("TAB exige CraftBukkit v1_8_R3.");
            }
            Class<?> component = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent");
            Class<?> text = Class.forName("net.minecraft.server.v1_8_R3.ChatComponentText");
            Class<?> packetClass = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutPlayerListHeaderFooter");
            Constructor<?> textConstructor = text.getConstructor(String.class);
            Object packet = packetClass.getConstructor(component)
                .newInstance(textConstructor.newInstance(header));
            Field footerField = packetClass.getDeclaredField("b");
            footerField.setAccessible(true);
            footerField.set(packet, textConstructor.newInstance(footer));
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = handle.getClass().getField("playerConnection").get(handle);
            Class<?> packetType = Class.forName("net.minecraft.server.v1_8_R3.Packet");
            Method send = connection.getClass().getMethod("sendPacket", packetType);
            send.invoke(connection, packet);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Falha ao enviar cabecalho TAB v1_8_R3.", error);
        }
    }
}
