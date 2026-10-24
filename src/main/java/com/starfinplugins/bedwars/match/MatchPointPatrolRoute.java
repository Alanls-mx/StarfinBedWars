package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Diagonal X patrol inside the map's MAIN selection (pos1/pos2).
 *
 * Unlike a serpentine grid sweep, the dragon crosses the whole arena along
 * one diagonal, travels along the far edge, and crosses back via the other
 * diagonal. Every repeat reverses that order and changes the inset, making
 * the flight visibly alternate without grid-like movement or teleporting.
 */
public final class MatchPointPatrolRoute {
    private final List<double[]> points;

    public MatchPointPatrolRoute(ArenaRegion region, int ceilingHeight,
                                 double preferredY, int requestedSpacing) {
        if (region == null) throw new IllegalArgumentException("Regiao MAIN ausente.");
        int spacing = Math.max(20, Math.min(64, requestedSpacing));
        double minX = region.getMinimumX() + 0.5D;
        double maxX = region.getMaximumX() + 0.5D;
        double minZ = region.getMinimumZ() + 0.5D;
        double maxZ = region.getMaximumZ() + 0.5D;
        double margin = 12.0D;
        double x0 = maxX - minX > margin * 2.0D ? minX + margin : (minX + maxX) / 2.0D;
        double x1 = maxX - minX > margin * 2.0D ? maxX - margin : (minX + maxX) / 2.0D;
        double z0 = maxZ - minZ > margin * 2.0D ? minZ + margin : (minZ + maxZ) / 2.0D;
        double z1 = maxZ - minZ > margin * 2.0D ? maxZ - margin : (minZ + maxZ) / 2.0D;

        double bottomY = Math.max(3.0D, region.getMinimumY() + 3.0D);
        double topY = Math.min(245.0D,
            Math.min(ceilingHeight - 7.0D, region.getMaximumY() - 3.0D));
        double y = topY < bottomY ? Math.max(3.0D, Math.min(245.0D, topY))
            : Math.max(bottomY, Math.min(topY, preferredY));

        List<double[]> flight = new ArrayList<double[]>();
        double[] insets = new double[] {0.0D, 0.12D, 0.06D};
        for (int phase = 0; phase < insets.length; phase++) {
            double inset = insets[phase];
            double left = lerp(x0, x1, inset);
            double right = lerp(x0, x1, 1.0D - inset);
            double north = lerp(z0, z1, inset);
            double south = lerp(z0, z1, 1.0D - inset);
            double[] nw = new double[] {left, y, north};
            double[] ne = new double[] {right, y, north};
            double[] sw = new double[] {left, y, south};
            double[] se = new double[] {right, y, south};

            // Forward X, then reverse X in alternating phases. The final
            // short connection returns to the next pattern's start point.
            double[][] route = phase % 2 == 0
                ? new double[][] {nw, se, ne, sw, nw}
                : new double[][] {nw, sw, ne, se, nw};
            for (int i = 0; i + 1 < route.length; i++) {
                interpolate(flight, route[i], route[i + 1], spacing);
            }
        }

        if (flight.isEmpty()) flight.add(new double[] {(x0+x1)/2, y, (z0+z1)/2});
        this.points = Collections.unmodifiableList(flight);
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
    }

    private static void interpolate(List<double[]> points,
                                    double[] start, double[] end, int spacing) {
        double dx = end[0] - start[0], dz = end[2] - start[2];
        int pieces = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / spacing));
        if (points.isEmpty()) points.add(start.clone());
        else {
            double[] last = points.get(points.size() - 1);
            if (last[0] != start[0] || last[2] != start[2]) {
                int bridgePieces = Math.max(1, (int) Math.ceil(
                    Math.sqrt(Math.pow(start[0] - last[0], 2)
                        + Math.pow(start[2] - last[2], 2)) / spacing));
                for (int n = 1; n <= bridgePieces; n++) {
                    double t = n / (double) bridgePieces;
                    points.add(new double[] {
                        lerp(last[0], start[0], t), start[1], lerp(last[2], start[2], t)
                    });
                }
            }
        }
        for (int n = 1; n <= pieces; n++) {
            double t = n / (double) pieces;
            points.add(new double[] {
                lerp(start[0], end[0], t), start[1], lerp(start[2], end[2], t)
            });
        }
    }

    public int size() { return points.size(); }

    public double[] at(int index) {
        double[] point = points.get(Math.floorMod(index, points.size()));
        return point.clone();
    }
}
