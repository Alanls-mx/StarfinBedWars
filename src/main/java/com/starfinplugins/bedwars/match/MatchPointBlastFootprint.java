package com.starfinplugins.bedwars.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Compact TNT-like circular crater. The X pattern belongs exclusively
 * to the dragon's FLIGHT route, not to the explosion's block footprint.
 */
public final class MatchPointBlastFootprint {
    private MatchPointBlastFootprint() { }

    public static List<Offset> offsets(int requestedRadius) {
        int radius = Math.max(1, Math.min(6, requestedRadius));
        List<Offset> blocks = new ArrayList<Offset>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) blocks.add(new Offset(x, z));
            }
        }
        Collections.sort(blocks, new Comparator<Offset>() {
            @Override public int compare(Offset left, Offset right) {
                int a = left.x * left.x + left.z * left.z;
                int b = right.x * right.x + right.z * right.z;
                if (a != b) return Integer.compare(a, b);
                if (left.x != right.x) return Integer.compare(left.x, right.x);
                return Integer.compare(left.z, right.z);
            }
        });
        return Collections.unmodifiableList(blocks);
    }

    public static final class Offset {
        public final int x;
        public final int z;
        private Offset(int x, int z) { this.x = x; this.z = z; }
    }
}
