package com.starfinplugins.bedwars.gameplay;

/** Significant position or action is activity; head rotation alone is ignored. */
public final class AfkActivity {
    public enum Stage { ACTIVE, WARNING, EXPIRED }
    private long lastMillis;
    private double anchorX, anchorY, anchorZ;

    public AfkActivity(long now, double x, double y, double z) {
        lastMillis = now; anchorX = x; anchorY = y; anchorZ = z;
    }
    public boolean moved(long now, double x, double y, double z) {
        double dx = x - anchorX, dy = y - anchorY, dz = z - anchorZ;
        if (dx * dx + dy * dy + dz * dz < 0.25) return false;
        anchorX = x; anchorY = y; anchorZ = z; lastMillis = now;
        return true;
    }
    public void action(long now) { lastMillis = now; }
    public Stage stage(long now, int timeoutSeconds, int warningSeconds) {
        long idle = Math.max(0L, now - lastMillis);
        if (idle >= timeoutSeconds * 1000L) return Stage.EXPIRED;
        if (idle >= (timeoutSeconds - warningSeconds) * 1000L) return Stage.WARNING;
        return Stage.ACTIVE;
    }
}
