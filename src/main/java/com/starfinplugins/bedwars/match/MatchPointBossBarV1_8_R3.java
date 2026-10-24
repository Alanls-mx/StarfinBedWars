package com.starfinplugins.bedwars.match;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.v1_8_R3.EntityWither;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntityLiving;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * Client-only, invisible Wither keeps the Minecraft 1.8 boss bar visible to
 * each player regardless of the real dragon's view/track distance. No Wither
 * is added to a Bukkit world, so this cannot damage the arena or make noise.
 *
 * The fake entity is individually positioned below the viewer, not at the
 * dragon's distant location. Resend position while the player moves.
 */
public final class MatchPointBossBarV1_8_R3 {
    private final Map<UUID, View> viewers = new HashMap<UUID, View>();

    public void show(Player player, String title) {
        if (player == null || !player.isOnline()) return;
        View old = viewers.get(player.getUniqueId());
        if (old != null && old.world == player.getWorld()) {
            move(player, old);
            return;
        }
        if (old != null) hide(player);
        Location pos = hiddenPosition(player);
        EntityWither wither = new EntityWither(((CraftWorld) player.getWorld()).getHandle());
        wither.setCustomName(title);
        wither.setCustomNameVisible(false);
        wither.setInvisible(true);
        wither.setPosition(pos.getX(), pos.getY(), pos.getZ());
        wither.setHealth(wither.getMaxHealth());
        send(player, new PacketPlayOutSpawnEntityLiving(wither));
        viewers.put(player.getUniqueId(), new View(wither, player.getWorld()));
    }

    public void update(Player player, String title) {
        if (player == null || !player.isOnline()) return;
        View view = viewers.get(player.getUniqueId());
        if (view == null || view.world != player.getWorld()) show(player, title);
        else move(player, view);
    }

    private void move(Player player, View view) {
        Location pos = hiddenPosition(player);
        view.wither.setPosition(pos.getX(), pos.getY(), pos.getZ());
        send(player, new PacketPlayOutEntityTeleport(view.wither));
    }

    private Location hiddenPosition(Player player) {
        // 25 blocks below the camera keeps the client-only boss entity close
        // enough for its health bar, without a visible body obstructing play.
        Location position = player.getLocation();
        return position.subtract(0.0D, 25.0D, 0.0D);
    }

    public void hide(Player player) {
        if (player == null) return;
        View removed = viewers.remove(player.getUniqueId());
        if (removed != null && player.isOnline()) {
            send(player, new PacketPlayOutEntityDestroy(removed.wither.getId()));
        }
    }

    /** Called for disconnected players whose client session no longer exists. */
    public void forget(UUID playerId) { viewers.remove(playerId); }

    public void clear() {
        // Active players receive destroy packets; offline viewers are simply
        // discarded because their client already destroyed its world state.
        for (UUID playerId : new HashSet<UUID>(viewers.keySet())) {
            Player player = org.bukkit.Bukkit.getPlayer(playerId);
            if (player != null) hide(player);
            else viewers.remove(playerId);
        }
    }

    public Set<UUID> getViewerIds() { return new HashSet<UUID>(viewers.keySet()); }

    private void send(Player player, net.minecraft.server.v1_8_R3.Packet<?> packet) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    private static final class View {
        private final EntityWither wither;
        private final World world;
        private View(EntityWither wither, World world) {
            this.wither = wither;
            this.world = world;
        }
    }
}
