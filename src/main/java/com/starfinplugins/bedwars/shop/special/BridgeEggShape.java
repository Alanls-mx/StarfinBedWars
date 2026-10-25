package com.starfinplugins.bedwars.shop.special;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic cross-section for Bridge Eggs.
 * Width is across the egg's horizontal travel and thickness grows downward.
 * The center top block is always first, so a limited block budget prioritizes
 * a usable floor instead of an isolated railing or support block.
 */
public final class BridgeEggShape {
    private BridgeEggShape() { }

    public static List<Offset> footprint(int width, int thickness, double velocityX, double velocityZ) {
        if (width < 1 || width > 5 || (width & 1) == 0
                || thickness < 1 || thickness > 2) {
            throw new IllegalArgumentException("Largura deve ser impar (1, 3 ou 5) e espessura 1 ou 2.");
        }
        boolean movingAlongX = Math.abs(velocityX) >= Math.abs(velocityZ);
        List<Offset> offsets = new ArrayList<Offset>(width * thickness);
        int radius = width / 2;
        for (int level = 0; level < thickness; level++) {
            offsets.add(new Offset(0, -level, 0));
            for (int step = 1; step <= radius; step++) {
                offsets.add(movingAlongX ? new Offset(0, -level, step)
                    : new Offset(step, -level, 0));
                offsets.add(movingAlongX ? new Offset(0, -level, -step)
                    : new Offset(-step, -level, 0));
            }
        }
        return Collections.unmodifiableList(offsets);
    }

    public static final class Offset {
        private final int x;
        private final int y;
        private final int z;

        private Offset(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
    }
}
