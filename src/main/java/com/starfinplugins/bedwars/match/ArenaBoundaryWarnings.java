package com.starfinplugins.bedwars.match;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Match-scoped cooldowns keep movement warnings out of the chat spam loop. */
public final class ArenaBoundaryWarnings {
    public enum Type { HEIGHT, EDGE }

    private final Map<UUID, long[]> lastNoticeAt = new HashMap<UUID, long[]>();

    public boolean shouldWarn(UUID playerId, Type type, long now, long cooldownMillis) {
        if (playerId == null || type == null) return false;
        long[] timestamps = lastNoticeAt.get(playerId);
        if (timestamps == null) {
            timestamps = new long[] { Long.MIN_VALUE, Long.MIN_VALUE };
            lastNoticeAt.put(playerId, timestamps);
        }
        int index = type.ordinal();
        long last = timestamps[index];
        if (last != Long.MIN_VALUE && now >= last && now - last < cooldownMillis) return false;
        timestamps[index] = now;
        return true;
    }

    public void forget(UUID playerId) { lastNoticeAt.remove(playerId); }
    public void clear() { lastNoticeAt.clear(); }

    /** Warn within a short approach distance as well as on blocked attempts. */
    public static boolean nearHorizontalEdge(double x, double z,
                                             int minX, int maxX, int minZ, int maxZ,
                                             double margin) {
        return x < minX + margin || x > maxX + 1.0D - margin
            || z < minZ + margin || z > maxZ + 1.0D - margin;
    }
}
