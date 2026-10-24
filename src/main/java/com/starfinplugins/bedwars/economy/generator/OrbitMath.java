package com.starfinplugins.bedwars.economy.generator;

/** Pure geometry shared by the animation and its tests. Angles are measured in ticks. */
public final class OrbitMath {
    private OrbitMath() { }

    public static double angle(long tick, int index, int count, double degreesPerTick) {
        return Math.toRadians(tick * degreesPerTick + index * 360.0D / count);
    }

    public static double x(long tick, int index, int count, double speed, double radius) {
        return Math.cos(angle(tick, index, count, speed)) * radius;
    }

    public static double z(long tick, int index, int count, double speed, double radius) {
        return Math.sin(angle(tick, index, count, speed)) * radius;
    }

    public static double floatOffset(long tick, double amplitude, int periodTicks) {
        return amplitude * Math.sin(2.0D * Math.PI * tick / periodTicks);
    }
}
