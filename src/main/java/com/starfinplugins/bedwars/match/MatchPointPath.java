package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.Position;
import java.util.Collections;
import java.util.List;

/**
 * A rounded patrol around the BedWars team islands. In two-team maps the
 * islands may be aligned on X or Z; use an additional cross-axis span so the
 * path does not collapse into a tiny back-and-forth turn.
 *
 * Coordinates refer to the arena template. World instances have identical
 * block coordinates but different Bukkit world names.
 */
public final class MatchPointPath {
    private final double centerX;
    private final double centerZ;
    private final double radiusX;
    private final double radiusZ;
    private final double altitude;
    private final double minY;
    private final double maxY;

    public MatchPointPath(ArenaRegion bounds, int ceilingHeight, int configuredAltitude) {
        this(bounds, ceilingHeight, configuredAltitude, Collections.<Position>emptyList());
    }

    public MatchPointPath(ArenaRegion bounds, int ceilingHeight, int configuredAltitude,
                          List<Position> islandSpawns) {
        if (bounds == null) throw new IllegalArgumentException("Limites da arena ausentes.");

        double boundsCenterX = (bounds.getMinimumX() + bounds.getMaximumX() + 1.0D) / 2.0D;
        double boundsCenterZ = (bounds.getMinimumZ() + bounds.getMaximumZ() + 1.0D) / 2.0D;
        double minIslandX = Double.POSITIVE_INFINITY, maxIslandX = Double.NEGATIVE_INFINITY;
        double minIslandZ = Double.POSITIVE_INFINITY, maxIslandZ = Double.NEGATIVE_INFINITY;
        int count = 0;
        if (islandSpawns != null) {
            for (Position spawn : islandSpawns) {
                if (spawn == null) continue;
                minIslandX = Math.min(minIslandX, spawn.getX());
                maxIslandX = Math.max(maxIslandX, spawn.getX());
                minIslandZ = Math.min(minIslandZ, spawn.getZ());
                maxIslandZ = Math.max(maxIslandZ, spawn.getZ());
                count++;
            }
        }

        boolean islandsKnown = count >= 2;
        double desiredCenterX = islandsKnown
            ? (minIslandX + maxIslandX) / 2.0D : boundsCenterX;
        double desiredCenterZ = islandsKnown
            ? (minIslandZ + maxIslandZ) / 2.0D : boundsCenterZ;
        // Allow for the large body/wings and prevent the route from touching
        // the player's world-border wall. Narrow test arenas use the center.
        double horizontalMargin = 12.0D;
        centerX = clampCenter(desiredCenterX, bounds.getMinimumX(),
            bounds.getMaximumX() + 1.0D, horizontalMargin);
        centerZ = clampCenter(desiredCenterZ, bounds.getMinimumZ(),
            bounds.getMaximumZ() + 1.0D, horizontalMargin);
        double availableX = Math.max(0.0D, Math.min(
            centerX - bounds.getMinimumX() - horizontalMargin,
            bounds.getMaximumX() + 1.0D - centerX - horizontalMargin));
        double availableZ = Math.max(0.0D, Math.min(
            centerZ - bounds.getMinimumZ() - horizontalMargin,
            bounds.getMaximumZ() + 1.0D - centerZ - horizontalMargin));

        double islandRadiusX = islandsKnown ? (maxIslandX - minIslandX) / 2.0D : 0.0D;
        double islandRadiusZ = islandsKnown ? (maxIslandZ - minIslandZ) / 2.0D : 0.0D;
        double islandRadius = Math.max(islandRadiusX, islandRadiusZ);

        // Three-dimensional body needs generous turns. The previous code set
        // the missing axis to 4 blocks for two collinear islands, creating
        // a nearly straight line that made the dragon writhe in place.
        double crossSpan = Math.max(16.0D, islandRadius * 0.55D);
        double fallbackX = (bounds.getMaximumX() - bounds.getMinimumX()) * 0.30D;
        double fallbackZ = (bounds.getMaximumZ() - bounds.getMinimumZ()) * 0.30D;
        double desiredRadiusX = islandsKnown
            ? Math.max(islandRadiusX, crossSpan) : Math.max(0.0D, fallbackX);
        double desiredRadiusZ = islandsKnown
            ? Math.max(islandRadiusZ, crossSpan) : Math.max(0.0D, fallbackZ);
        // On four-corner maps (e.g. islands at +/-90,+/-90), simply using
        // half the coordinate extent creates an ellipse that misses every
        // corner island. Expand the ellipse to pass near actual team spawns.
        double islandScale = 1.0D;
        if (islandsKnown && islandSpawns != null) {
            for (Position spawn : islandSpawns) {
                if (spawn == null) continue;
                double nx = (spawn.getX() - centerX) / desiredRadiusX;
                double nz = (spawn.getZ() - centerZ) / desiredRadiusZ;
                islandScale = Math.max(islandScale, Math.sqrt(nx * nx + nz * nz));
            }
        }
        radiusX = Math.min(availableX, desiredRadiusX * islandScale);
        radiusZ = Math.min(availableZ, desiredRadiusZ * islandScale);

        int allowedMax = Math.min(247,
            Math.min(ceilingHeight - 9, bounds.getMaximumY() - 8));
        int allowedMin = Math.max(8, bounds.getMinimumY() + 9);
        minY = Math.min(allowedMin, allowedMax);
        maxY = Math.max(allowedMin, allowedMax);
        altitude = Math.max(minY, Math.min(maxY, configuredAltitude));
    }

    private static double clampCenter(double wanted, double lower, double upper, double margin) {
        if (upper - lower < 2.0D * margin) return (lower + upper) / 2.0D;
        return Math.max(lower + margin, Math.min(upper - margin, wanted));
    }

    public double[] at(double radians) {
        return new double[] {
            centerX + Math.cos(radians) * radiusX,
            Math.max(minY, Math.min(maxY,
                altitude + Math.sin(radians * 2.0D) * 2.5D)),
            centerZ + Math.sin(radians) * radiusZ
        };
    }
}
