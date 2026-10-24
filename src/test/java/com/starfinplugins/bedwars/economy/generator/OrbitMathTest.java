package com.starfinplugins.bedwars.economy.generator;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class OrbitMathTest {
    @Test public void fourIconsHaveConstantRadiusAndQuarterTurnSpacing() {
        for (int tick = 0; tick < 240; tick += 7) {
            for (int index = 0; index < 4; index++) {
                double x = OrbitMath.x(tick, index, 4, 6.0D, 0.75D);
                double z = OrbitMath.z(tick, index, 4, 6.0D, 0.75D);
                assertEquals(0.75D, Math.hypot(x, z), 0.0000001D);
                assertEquals(Math.PI / 2.0D,
                    OrbitMath.angle(tick, index + 1, 4, 6.0D)
                        - OrbitMath.angle(tick, index, 4, 6.0D), 0.0000001D);
            }
        }
    }

    @Test public void completesOneRotationAtConfiguredSpeed() {
        assertEquals(OrbitMath.x(0, 0, 1, 6.0D, 0.75D),
            OrbitMath.x(60, 0, 1, 6.0D, 0.75D), 0.0000001D);
        assertEquals(0.0D, OrbitMath.z(0, 0, 1, 6.0D, 0.75D), 0.0000001D);
        assertEquals(0.75D, OrbitMath.z(15, 0, 1, 6.0D, 0.75D), 0.0000001D);
    }

    @Test public void floatingReturnsToBaseAndRespectsAmplitude() {
        assertEquals(0.0D, OrbitMath.floatOffset(0, 0.12D, 60), 0.0000001D);
        assertEquals(0.12D, OrbitMath.floatOffset(15, 0.12D, 60), 0.0000001D);
        assertEquals(0.0D, OrbitMath.floatOffset(30, 0.12D, 60), 0.0000001D);
        assertEquals(-0.12D, OrbitMath.floatOffset(45, 0.12D, 60), 0.0000001D);
        assertEquals(0.0D, OrbitMath.floatOffset(60, 0.12D, 60), 0.0000001D);
    }
}
