package com.starfinplugins.bedwars.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A deliberately small TNT-like X footprint. The four diagonal arms are
 * visited center-outward and not as a square raster, so an impact leaves
 * four intersecting scars rather than a flat swept plane.
 */
public final class MatchPointXBlast {
    private MatchPointXBlast() { }

    public static List<Offset> offsets(int requestedRadius) {
        int radius = Math.max(1, Math.min(6, requestedRadius));
        List<Offset> result = new ArrayList<Offset>();
        result.add(new Offset(0, 0));
        for (int step = 1; step <= radius; step++) {
            result.add(new Offset(step, step));
            result.add(new Offset(-step, -step));
            result.add(new Offset(step, -step));
            result.add(new Offset(-step, step));
        }
        return Collections.unmodifiableList(result);
    }

    public static final class Offset {
        public final int x;
        public final int z;
        private Offset(int x, int z) { this.x = x; this.z = z; }
    }
}
