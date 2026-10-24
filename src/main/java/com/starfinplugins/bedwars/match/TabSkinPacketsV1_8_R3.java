package com.starfinplugins.bedwars.match;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Keeps the player's real UUID/name and changes only the TAB texture property. */
final class TabSkinPacketsV1_8_R3 {
    private static final Field ENTRIES;
    static {
        try {
            ENTRIES = PacketPlayOutPlayerInfo.class.getDeclaredField("b");
            ENTRIES.setAccessible(true);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private TabSkinPacketsV1_8_R3() { }

    static TabSkinProfileLookup.Texture liveTexture(Player player) {
        Collection<Property> properties = ((CraftPlayer) player).getProfile()
            .getProperties().get("textures");
        for (Property property : properties) {
            if (property.hasSignature()) {
                return new TabSkinProfileLookup.Texture(property.getValue(), property.getSignature());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static void send(Player viewer, Player subject, TabSkinProfileLookup.Texture texture) {
        EntityPlayer handle = ((CraftPlayer) subject).getHandle();
        PacketPlayOutPlayerInfo packet = new PacketPlayOutPlayerInfo(
            PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, handle);
        if (texture != null) {
            GameProfile profile = new GameProfile(subject.getUniqueId(), subject.getName());
            profile.getProperties().put("textures",
                new Property("textures", texture.value, texture.signature));
            try {
                List<PacketPlayOutPlayerInfo.PlayerInfoData> entries =
                    (List<PacketPlayOutPlayerInfo.PlayerInfoData>) ENTRIES.get(packet);
                PacketPlayOutPlayerInfo.PlayerInfoData original = entries.get(0);
                entries.set(0, packet.new PlayerInfoData(profile,
                    original.b(), original.c(), original.d()));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Failed to update TAB profile", error);
            }
        }
        ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(packet);
    }
}
