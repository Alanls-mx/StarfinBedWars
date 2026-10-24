package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Whole-map patrol based on arena MAIN (the actual /sbw setarena pos1/pos2).
 * Visits a serpentine grid covering the entire map, not just the team spawns.
 * Every destination lies inside the selected cuboid with room for dragon wings.
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
        double padding = 12.0D;
        double fromX = (maxX - minX) > padding * 2.0D
            ? minX + padding : (minX + maxX) / 2.0D;
        double toX = (maxX - minX) > padding * 2.0D
            ? maxX - padding : (minX + maxX) / 2.0D;
        double fromZ = (maxZ - minZ) > padding * 2.0D
            ? minZ + padding : (minZ + maxZ) / 2.0D;
        double toZ = (maxZ - minZ) > padding * 2.0D
            ? maxZ - padding : (minZ + maxZ) / 2.0D;

        // The flight altitude remains close to the mapped islands but never
        // exceeds pos1/pos2's vertical range or the configured world ceiling.
        double bottomY = Math.max(3.0D, region.getMinimumY() + 3.0D);
        double topY = Math.min(245.0D,
            Math.min(ceilingHeight - 7.0D, region.getMaximumY() - 3.0D));
        double y = topY < bottomY ? Math.max(3.0D, Math.min(245.0D, topY))
            : Math.max(bottomY, Math.min(topY, preferredY));

        List<Double> xs = steps(fromX, toX, spacing);
        List<Double> zs = steps(fromZ, toZ, spacing);
        List<double[]> result = new ArrayList<double[]>();
        for (int row = 0; row < zs.size(); row++) {
            if ((row & 1) == 0) {
                for (double x : xs) result.add(new double[] {x, y, zs.get(row)});
            } else {
                for (int col = xs.size() - 1; col >= 0; col--) {
                    result.add(new double[] {xs.get(col), y, zs.get(row)});
                }
            }
        }
        this.points = Collections.unmodifiableList(result);
    }

    private static List<Double> steps(double first, double last, int spacing) {
        List<Double> out = new ArrayList<Double>();
        out.add(first);
        if (last - first > 0.001D) {
            int segments = (int) Math.ceil((last - first) / spacing);
            for (int i = 1; i <= segments; i++) {
                out.add(first + (last - first) * i / segments);
            }
        }
        return out;
    }

    public int size() { return points.size(); }

    public double[] at(int index) {
        double[] point = points.get(Math.floorMod(index, points.size()));
        return point.clone();
    }
}
