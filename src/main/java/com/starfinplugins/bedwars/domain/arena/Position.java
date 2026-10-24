package com.starfinplugins.bedwars.domain.arena;

public final class Position {
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public Position(String world, double x, double y, double z, float yaw, float pitch) {
        if (world == null || world.trim().isEmpty()) {
            throw new IllegalArgumentException("world ausente");
        }
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
                || Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
            throw new IllegalArgumentException("coordenada invalida");
        }
        if (y < 0 || y > 255 || Float.isNaN(yaw) || Float.isInfinite(yaw)
                || Float.isNaN(pitch) || Float.isInfinite(pitch)) {
            throw new IllegalArgumentException("altura, yaw ou pitch invalido para Minecraft 1.8.");
        }
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
