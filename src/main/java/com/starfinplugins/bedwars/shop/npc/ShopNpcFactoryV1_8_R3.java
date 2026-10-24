package com.starfinplugins.bedwars.shop.npc;

import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityTypes;
import net.minecraft.server.v1_8_R3.EntityVillager;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntity;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityHeadRotation;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftVillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/** Spawns a silent NPC whose packet type remains the vanilla 1.8 villager. */
public final class ShopNpcFactoryV1_8_R3 {
    private static boolean registered;

    private ShopNpcFactoryV1_8_R3() { }

    public static Villager spawn(Location location) {
        if (location == null || !(location.getWorld() instanceof CraftWorld)) {
            throw new IllegalArgumentException("NPC exige mundo CraftBukkit v1_8_R3.");
        }
        registerType();
        CraftWorld world = (CraftWorld) location.getWorld();
        ShopNpcVillager npc = new ShopNpcVillager(world.getHandle());
        npc.setLocation(location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch());
        Villager spawned = world.addEntity(npc, SpawnReason.CUSTOM);
        if (spawned == null || !spawned.isValid()) {
            throw new IllegalStateException("Servidor recusou o NPC de loja.");
        }
        return spawned;
    }

    @SuppressWarnings("unchecked")
    private static synchronized void registerType() {
        if (registered) return;
        try {
            Field idsField = EntityTypes.class.getDeclaredField("f");
            Field namesField = EntityTypes.class.getDeclaredField("d");
            idsField.setAccessible(true);
            namesField.setAccessible(true);
            Map<Class<? extends Entity>, Integer> ids =
                (Map<Class<? extends Entity>, Integer>) idsField.get(null);
            Map<Class<? extends Entity>, String> names =
                (Map<Class<? extends Entity>, String>) namesField.get(null);
            Integer id = ids.get(EntityVillager.class);
            String name = names.get(EntityVillager.class);
            if (id == null || name == null) throw new IllegalStateException("Villager vanilla sem registro.");
            ids.put(ShopNpcVillager.class, id);
            names.put(ShopNpcVillager.class, name);
            registered = true;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Nao foi possivel registrar o tipo de pacote do NPC.", error);
        }
    }

    @SuppressWarnings("unchecked")
    public static synchronized void unregisterType() {
        if (!registered) return;
        try {
            for (String fieldName : new String[] {"f", "d"}) {
                Field field = EntityTypes.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                ((Map<Class<? extends Entity>, ?>) field.get(null)).remove(ShopNpcVillager.class);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Nao foi possivel limpar o registro do NPC.", error);
        } finally {
            registered = false;
        }
    }

    public static void facePlayer(Villager villager, Player target) {
        if (villager == null || target == null || !villager.isValid()
                || villager.getWorld() != target.getWorld()
                || !(((CraftVillager) villager).getHandle() instanceof ShopNpcVillager)) return;
        Location from = villager.getLocation();
        Location to = target.getEyeLocation();
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY() - 1.4D;
        double dz = to.getZ() - from.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        ShopNpcVillager npc = (ShopNpcVillager) ((CraftVillager) villager).getHandle();
        npc.yaw = yaw;
        npc.pitch = pitch;
        npc.aI = yaw;
        npc.aK = yaw;
        byte yawByte = (byte) (yaw * 256.0F / 360.0F);
        byte pitchByte = (byte) (pitch * 256.0F / 360.0F);
        PacketPlayOutEntity.PacketPlayOutEntityLook look =
            new PacketPlayOutEntity.PacketPlayOutEntityLook(npc.getId(), yawByte, pitchByte, true);
        PacketPlayOutEntityHeadRotation head = new PacketPlayOutEntityHeadRotation(npc, yawByte);
        for (Player viewer : villager.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(from) > 48.0D * 48.0D) continue;
            ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(look);
            ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(head);
        }
    }
}
