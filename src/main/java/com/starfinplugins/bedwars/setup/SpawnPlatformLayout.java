package com.starfinplugins.bedwars.setup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SpawnPlatformLayout {
    private final List<BlockCoordinate> blocks;
    private final int minimumX;
    private final int maximumX;
    private final int minimumZ;
    private final int maximumZ;

    public SpawnPlatformLayout(int centerX, int baseY, int centerZ, boolean solo) {
        this(centerX, baseY, centerZ, solo, 0.0F);
    }

    public SpawnPlatformLayout(int centerX, int baseY, int centerZ, boolean solo, float yaw) {
        this(centerX, baseY, centerZ, solo ? 3 : 5, solo ? 3 : 5);
    }

    public static SpawnPlatformLayout forTeamCapacity(int centerX, int baseY, int centerZ,
                                                       int capacity) {
        int width = widthForTeamCapacity(capacity);
        return new SpawnPlatformLayout(centerX, baseY, centerZ, width, width);
    }

    public static int widthForTeamCapacity(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacidade da equipe invalida.");
        if (capacity == 1) return 3;
        if (capacity == 2) return 5;
        if (capacity <= 4) return 7;
        int interior = Math.max(7, (int) Math.ceil(Math.sqrt(capacity * 4.0D)));
        if ((interior & 1) == 0) interior++;
        return interior + 2;
    }

    public SpawnPlatformLayout(int centerX, int baseY, int centerZ, int width, int depth) {
        if (width < 3 || depth < 3) throw new IllegalArgumentException("A sala deve ter no minimo 3x3.");
        minimumX = centerX - width / 2;
        minimumZ = centerZ - depth / 2;
        maximumX = minimumX + width - 1;
        maximumZ = minimumZ + depth - 1;
        if (baseY < 0 || baseY + 4 > 255
                || Math.abs((long) minimumX) > 30000000L
                || Math.abs((long) maximumX) > 30000000L
                || Math.abs((long) minimumZ) > 30000000L
                || Math.abs((long) maximumZ) > 30000000L) {
            throw new IllegalArgumentException("A plataforma ultrapassa os limites do mundo.");
        }
        List<BlockCoordinate> coordinates = new ArrayList<BlockCoordinate>();
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                coordinates.add(new BlockCoordinate(x, baseY, z, Part.BASE));
            }
        }
        for (int y = baseY + 1; y <= baseY + 3; y++) {
            for (int x = minimumX; x <= maximumX; x++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    if (x == minimumX || x == maximumX
                            || z == minimumZ || z == maximumZ) {
                        coordinates.add(new BlockCoordinate(x, y, z, Part.WALL));
                    }
                }
            }
        }
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                coordinates.add(new BlockCoordinate(x, baseY + 4, z, Part.ROOF));
            }
        }
        blocks = Collections.unmodifiableList(coordinates);
    }

    public List<BlockCoordinate> getBlocks() { return blocks; }
    public int getMinimumX() { return minimumX; }
    public int getMaximumX() { return maximumX; }
    public int getMinimumZ() { return minimumZ; }
    public int getMaximumZ() { return maximumZ; }

    public enum Part {
        BASE, WALL, ROOF
    }

    public static final class BlockCoordinate {
        private final int x;
        private final int y;
        private final int z;
        private final Part part;

        private BlockCoordinate(int x, int y, int z, Part part) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.part = part;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
        public Part getPart() { return part; }
    }
}
