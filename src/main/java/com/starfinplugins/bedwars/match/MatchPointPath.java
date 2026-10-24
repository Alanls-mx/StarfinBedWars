package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.ArenaRegion;

/** Deterministic, clamped flying loop inside the playable island world. */
public final class MatchPointPath {
    private final double centerX;
    private final double centerZ;
    private final double radiusX;
    private final double radiusZ;
    private final double altitude;

    public MatchPointPath(ArenaRegion bounds, int ceilingHeight, int configuredAltitude) {
        if (bounds == null) throw new IllegalArgumentException("Limites da arena ausentes.");
        centerX = (bounds.getMinimumX() + (double) bounds.getMaximumX() + 1.0D) / 2.0D;
        centerZ = (bounds.getMinimumZ() + (double) bounds.getMaximumZ() + 1.0D) / 2.0D;
        radiusX = Math.max(0.0D, (bounds.getMaximumX() - bounds.getMinimumX()) / 2.0D - 4.0D);
        radiusZ = Math.max(0.0D, (bounds.getMaximumZ() - bounds.getMinimumZ()) / 2.0D - 4.0D);
        int maxY = Math.min(254, Math.min(ceilingHeight - 3, bounds.getMaximumY() - 2));
        int minY = Math.max(2, bounds.getMinimumY() + 3);
        altitude = maxY < minY ? Math.max(2, Math.min(253, maxY)) :
            Math.max(minY, Math.min(maxY, configuredAltitude));
    }

    public double[] at(double radians) {
        return new double[] { centerX + Math.cos(radians) * radiusX,
            altitude, centerZ + Math.sin(radians) * radiusZ };
    }
}
