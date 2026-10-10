package ru.strazh.ac.util;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MathTest {

    @Test
    void sinTableMatchesMath() {
        for (double a = -10; a < 10; a += 0.37) {
            assertEquals(Math.sin(a), McMath.sin(a), 2e-4);
            assertEquals(Math.cos(a), McMath.cos(a), 2e-4);
        }
    }

    @Test
    void wrapDegrees() {
        assertEquals(-170f, McMath.wrapDegrees(190f), 1e-5);
        assertEquals(10f, McMath.wrapDegrees(370f), 1e-5);
        assertEquals(-180f, McMath.wrapDegrees(-180f), 1e-5);
    }

    @Test
    void gcdOfSensitivityQuantizedDeltas() {
        double f = 0.5 * 0.6 + 0.2;
        double step = f * f * f * 8.0 * 0.15;
        double g = Stats.gcdFloat(step * 37, step * 12, 1e-4);
        assertEquals(step, g, 1e-6);
    }

    @Test
    void kurtosisOfGaussianNearZero() {
        Random r = new Random(1);
        double[] v = new double[20000];
        for (int i = 0; i < v.length; i++) v[i] = r.nextGaussian();
        assertEquals(0.0, Stats.kurtosis(v, v.length), 0.15);
        assertEquals(0.0, Stats.skewness(v, v.length), 0.1);
        assertEquals(1.0, Stats.stdDev(v, v.length), 0.03);
    }

    @Test
    void rayIntersectsBox() {
        AABB box = new AABB(2, 0, -0.5, 3, 2, 0.5);
        assertEquals(2.0, box.rayIntersect(0, 1, 0, 1, 0, 0, 10), 1e-9);
        assertEquals(-1.0, box.rayIntersect(0, 1, 0, 0, 0, 1, 10), 1e-9);
        assertEquals(-1.0, box.rayIntersect(0, 1, 0, 1, 0, 0, 1.5), 1e-9);
    }

    @Test
    void viewVectorMatchesYawPitchConvention() {
        Vec3 south = McMath.viewVectorPrecise(0, 0);
        assertEquals(1.0, south.z(), 1e-9);
        Vec3 west = McMath.viewVectorPrecise(0, 90);
        assertEquals(-1.0, west.x(), 1e-9);
        assertEquals(90.0, McMath.wrapDegrees(McMath.yawTo(0, 0, -5, 0)), 1e-9);
        assertEquals(-45.0, McMath.pitchTo(0, 0, 0, 1, 1, 0), 1e-9);
    }
}
