package com.starfinplugins.bedwars.match;

/** Death height measured from the lowest solid block in the arena template. */
public final class VoidDepthPolicy {
    private VoidDepthPolicy() { }

    public static int deathHeight(int lowestBlockY, int depth, int configuredHeight) {
        if (depth < 0 || depth > 128) throw new IllegalArgumentException("Profundidade invalida.");
        if (lowestBlockY < 0) return Math.max(0, configuredHeight - Math.max(16, depth));
        return Math.max(0, Math.min(configuredHeight, lowestBlockY - depth));
    }
}
