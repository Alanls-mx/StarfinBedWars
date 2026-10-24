package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.Position;
import java.util.List;

/** Deterministic, clamped flying loop inside the playable island world. */
public final class MatchPointPath {
    private final double centerX;
    private final double centerZ;
    private final double radiusX;
    private final double radiusZ;
    private final double altitude;

    public MatchPointPath(ArenaRegion bounds, int ceilingHeight, int configuredAltitude) {
        this(bounds, ceilingHeight, configuredAltitude, java.util.Collections.<Position>emptyList());
    }

    /**
     * Aim the dragon flight path through the islands, not along the empty
     * outer boundary of the arena. The configured team spawns are the island
     * landmarks; all coordinates are clamped to the match arena bounds.
     */
    public MatchPointPath(ArenaRegion bounds, int ceilingHeight, int configuredAltitude,
                          List<Position> islandSpawns) {
        if (bounds == null) throw new IllegalArgumentException("Limites da arena ausentes.");
        double centerBoundsX = (bounds.getMinimumX() + (double) bounds.getMaximumX() + 1.0D) / 2.0D;
        double centerBoundsZ = (bounds.getMinimumZ() + (double) bounds.getMaximumZ() + 1.0D) / 2.0D;
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        if (islandSpawns != null) {
            for (Position pos : islandSpawns) {
                if (pos == null) continue;
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
            }
        }
        boolean configured = minX != Double.POSITIVE_INFINITY;
        centerX = configured ? (minX + maxX) / 2.0D : centerBoundsX;
        centerZ = configured ? (minZ + maxZ) / 2.0D : centerBoundsZ;
        double spanX = configured ? Math.max(4.0D, (maxX - minX) / 2.0D)
            : (bounds.getMaximumX() - bounds.getMinimumX()) / 2.0D - 4.0D;
        double spanZ = configured ? Math.max(4.0D, (maxZ - minZ) / 2.0D)
            : (bounds.getMaximumZ() - bounds.getMinimumZ()) / 2.0D - 4.0D;
        radiusX = Math.max(0.0D, Math.min(spanX,
            Math.min(centerX - bounds.getMinimumX() - 3.0D,
                bounds.getMaximumX() + 1.0D - centerX - 3.0D)));
        radiusZ = Math.max(0.0D, Math.min(spanZ,
            Math.min(centerZ - bounds.getMinimumZ() - 3.0D,
                bounds.getMaximumZ() + 1.0D - centerZ - 3.0D)));
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
