package com.starfinplugins.bedwars.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;

public final class SafeLocationResolver {
    private SafeLocationResolver() { }

    public static Location findSurface(Location preferred, int searchRadius) {
        if (preferred == null || preferred.getWorld() == null || searchRadius < 0) {
            throw new IllegalArgumentException("Localizacao ou raio de busca invalido.");
        }
        if (hasSafeFloor(preferred)) return preferred.clone();
        World world = preferred.getWorld();
        int centerX = preferred.getBlockX();
        int centerZ = preferred.getBlockZ();
        for (int radius = 0; radius <= searchRadius; radius++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    if (Math.max(Math.abs(x - centerX), Math.abs(z - centerZ)) != radius) continue;
                    int y = world.getHighestBlockYAt(x, z);
                    if (y > 0 && y < world.getMaxHeight() - 2
                            && world.getBlockAt(x, y, z).getType().isSolid()
                            && openForPlayer(world.getBlockAt(x, y + 1, z).getType())
                            && openForPlayer(world.getBlockAt(x, y + 2, z).getType())) {
                        return new Location(world, x + 0.5, y + 1.0, z + 0.5,
                            preferred.getYaw(), preferred.getPitch());
                    }
                }
            }
        }
        return null;
    }

    public static boolean hasSafeFloor(Location location) {
        if (location == null || location.getWorld() == null) return false;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        World world = location.getWorld();
        return y >= 1 && y < world.getMaxHeight() - 2
            && world.getBlockAt(x, y - 1, z).getType().isSolid()
            && openForPlayer(world.getBlockAt(x, y, z).getType())
            && openForPlayer(world.getBlockAt(x, y + 1, z).getType());
    }

    private static boolean openForPlayer(Material material) {
        return material == Material.AIR || material == Material.SNOW
            || material == Material.LONG_GRASS || material == Material.DEAD_BUSH
            || material == Material.RED_ROSE || material == Material.YELLOW_FLOWER;
    }
}
