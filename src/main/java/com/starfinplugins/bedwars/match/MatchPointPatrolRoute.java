package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.Position;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Alternating X flight which favors real BedWars islands and their central
 * crossing. MAIN/pos1/pos2 is a SAFETY BOUNDARY, not a reason to spend the
 * entire fight over empty corners of the world.
 */
public final class MatchPointPatrolRoute {
    private final List<double[]> points;
    private final boolean hasIslands;

    public MatchPointPatrolRoute(ArenaRegion region, int ceilingHeight,
                                  double preferredY, int requestedSpacing) {
        this(region, ceilingHeight, preferredY, requestedSpacing,
            Collections.<Position>emptyList());
    }

    public MatchPointPatrolRoute(ArenaRegion region, int ceilingHeight,
                                  double preferredY, int requestedSpacing,
                                  List<Position> islandSpawns) {
        if (region == null) throw new IllegalArgumentException("Regiao MAIN ausente.");
        final int spacing = Math.max(12, Math.min(40, requestedSpacing));
        final double minimumX = region.getMinimumX() + 0.5D;
        final double maximumX = region.getMaximumX() + 0.5D;
        final double minimumZ = region.getMinimumZ() + 0.5D;
        final double maximumZ = region.getMaximumZ() + 0.5D;
        final double margin = 12.0D;

        final double safeMinX = maximumX - minimumX > margin * 2
            ? minimumX + margin : (minimumX + maximumX) / 2.0D;
        final double safeMaxX = maximumX - minimumX > margin * 2
            ? maximumX - margin : (minimumX + maximumX) / 2.0D;
        final double safeMinZ = maximumZ - minimumZ > margin * 2
            ? minimumZ + margin : (minimumZ + maximumZ) / 2.0D;
        final double safeMaxZ = maximumZ - minimumZ > margin * 2
            ? maximumZ - margin : (minimumZ + maximumZ) / 2.0D;

        double bottomY = Math.max(3, region.getMinimumY() + 3);
        double topY = Math.min(245, Math.min(ceilingHeight - 7, region.getMaximumY() - 3));
        final double y = topY < bottomY ? Math.max(3, Math.min(245, topY))
            : clamp(preferredY, bottomY, topY);

        List<Position> islands = new ArrayList<Position>();
        if (islandSpawns != null) for (Position spawn : islandSpawns) {
            if (spawn != null && spawn.getX() >= minimumX && spawn.getX() <= maximumX
                    && spawn.getZ() >= minimumZ && spawn.getZ() <= maximumZ) {
                islands.add(spawn);
            }
        }
        hasIslands = !islands.isEmpty();

        double centerX = (safeMinX + safeMaxX) / 2.0D;
        double centerZ = (safeMinZ + safeMaxZ) / 2.0D;
        double x0 = safeMinX, x1 = safeMaxX;
        double z0 = safeMinZ, z1 = safeMaxZ;

        if (islands.size() >= 2) {
            double lowX = Double.POSITIVE_INFINITY, highX = Double.NEGATIVE_INFINITY;
            double lowZ = Double.POSITIVE_INFINITY, highZ = Double.NEGATIVE_INFINITY;
            double sumX = 0, sumZ = 0;
            for (Position spawn : islands) {
                lowX = Math.min(lowX, spawn.getX());
                highX = Math.max(highX, spawn.getX());
                lowZ = Math.min(lowZ, spawn.getZ());
                highZ = Math.max(highZ, spawn.getZ());
                sumX += spawn.getX();
                sumZ += spawn.getZ();
            }
            centerX = clamp(sumX / islands.size(), safeMinX, safeMaxX);
            centerZ = clamp(sumZ / islands.size(), safeMinZ, safeMaxZ);
            // Favor actual island coordinates, allowing only a small amount
            // of overshoot so TNT impacts occur above constructed platforms.
            double xPad = Math.max(3.0D, Math.min(8.0D, (highX - lowX) * 0.04D));
            double zPad = Math.max(3.0D, Math.min(8.0D, (highZ - lowZ) * 0.04D));
            // Keep two diagonals even for bases aligned along one axis.
            double crossSpan = Math.max(5.0D, Math.min(12.0D,
                Math.max(highX - lowX, highZ - lowZ) * 0.05D));
            x0 = clamp(Math.min(lowX - xPad, centerX - crossSpan), safeMinX, safeMaxX);
            x1 = clamp(Math.max(highX + xPad, centerX + crossSpan), safeMinX, safeMaxX);
            z0 = clamp(Math.min(lowZ - zPad, centerZ - crossSpan), safeMinZ, safeMaxZ);
            z1 = clamp(Math.max(highZ + zPad, centerZ + crossSpan), safeMinZ, safeMaxZ);
        }

        List<double[]> route = new ArrayList<double[]>();
        for (int phase = 0; phase < 4; phase++) {
            // Alternating X across the occupied island cluster (not pos1/pos2
            // corners). The central crossing is visited on both diagonals.
            double factor = phase % 2 == 0 ? 1.0D : 0.80D;
            double left = lerp(centerX, x0, factor);
            double right = lerp(centerX, x1, factor);
            double north = lerp(centerZ, z0, factor);
            double south = lerp(centerZ, z1, factor);
            double[] nw = point(left, y, north);
            double[] ne = point(right, y, north);
            double[] sw = point(left, y, south);
            double[] se = point(right, y, south);
            double[] middle = point(centerX, y, centerZ);
            double[][] x = phase % 2 == 0
                ? new double[][] {nw, middle, se, ne, middle, sw, nw}
                : new double[][] {ne, middle, sw, se, middle, nw, ne};
            for (int i = 0; i + 1 < x.length; i++) append(route, x[i], x[i + 1], spacing);

            // Physical island waypoints: a large dragon has a long turn radius,
            // and merely flying near a 14-block waypoint does NOT place its
            // 3-block blast on the island. Explicitly visit configured bases.
            if (!islands.isEmpty()) {
                for (int i = 0; i < islands.size(); i++) {
                    Position spawn = islands.get((phase + i) % islands.size());
                    append(route, route.get(route.size() - 1),
                        point(clamp(spawn.getX(), safeMinX, safeMaxX), y,
                            clamp(spawn.getZ(), safeMinZ, safeMaxZ)), spacing);
                }
                append(route, route.get(route.size() - 1), middle, spacing);
            }
        }
        if (route.isEmpty()) route.add(point(centerX, y, centerZ));
        append(route, route.get(route.size() - 1), route.get(0), spacing);
        points = Collections.unmodifiableList(route);
    }

    private static double[] point(double x, double y, double z) { return new double[] {x, y, z}; }
    private static double lerp(double a, double b, double fraction) { return a + (b - a) * fraction; }
    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
    private static void append(List<double[]> route, double[] start, double[] end, int spacing) {
        if (route.isEmpty()) route.add(start.clone());
        else {
            double[] last = route.get(route.size() - 1);
            if (Math.hypot(last[0] - start[0], last[2] - start[2]) > 0.01D) {
                append(route, last, start, spacing);
            }
        }
        double dx = end[0] - start[0], dz = end[2] - start[2];
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / spacing));
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            route.add(point(lerp(start[0], end[0], t),
                lerp(start[1], end[1], t), lerp(start[2], end[2], t)));
        }
    }

    public boolean hasIslands() { return hasIslands; }
    public int size() { return points.size(); }
    public double[] at(int index) { return points.get(Math.floorMod(index, points.size())).clone(); }
}
