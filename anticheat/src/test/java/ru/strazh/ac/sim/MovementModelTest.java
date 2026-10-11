package ru.strazh.ac.sim;

import org.junit.jupiter.api.Test;
import ru.strazh.ac.sim.engine.MovementModel;

import static org.junit.jupiter.api.Assertions.*;

class MovementModelTest {

    /** Ванильная траектория прыжка с земли. */
    @Test
    void jumpArcMatchesVanilla() {
        double[] vanilla = {0.41999998688698, 0.33319999363422, 0.24813599859094, 0.16477328182606,
                0.08307781780646, 0.00301626150904, -0.07544406518948, -0.15233518685055};
        double dy = MovementModel.jumpPower(0.41999998688698, 1.0, 0);
        assertEquals(vanilla[0], dy, 1e-9);
        for (int i = 1; i < vanilla.length; i++) {
            dy = MovementModel.nextFallDy(dy, 0.08);
            assertEquals(vanilla[i], dy, 1e-6, "тик " + i);
        }
    }

    /** Установившаяся скорость спринта по земле (~0.2806) не превышает границу. */
    @Test
    void sprintingOnGroundStaysUnderBound() {
        double v = 0;
        double accel = MovementModel.inputAccel(true, 0.6, 0.13, true);
        for (int i = 0; i < 40; i++) {
            double bound = MovementModel.maxHorizontal(v, MovementModel.frictionFactor(true, 0.6), accel, false);
            double vanilla = v * 0.6 * 0.91 + 0.13 * 0.98;
            assertTrue(vanilla <= bound + 1e-9);
            v = vanilla;
        }
        assertEquals(0.2806, v, 0.001);
        // Speed-чит 0.35 на земле выше границы.
        assertTrue(0.35 > MovementModel.maxHorizontal(v, 0.546, accel, false));
    }

    /** Спринт-прыжок: тик прыжка и следующий тик в воздухе укладываются в границу. */
    @Test
    void sprintJumpWithinBound() {
        double v = 0.2806;
        double accel = MovementModel.inputAccel(true, 0.6, 0.13, true);
        double jumpTick = v * 0.546 + 0.2 + 0.13 * 0.98;
        double bound = MovementModel.maxHorizontal(v, 0.546, accel, true);
        assertTrue(jumpTick <= bound);
        double airTick = jumpTick * 0.546 + 0.025999999 * 0.98;
        double airBound = MovementModel.maxHorizontal(jumpTick, 0.546, MovementModel.inputAccel(false, 0.6, 0.13, true), false);
        assertTrue(airTick <= airBound);
        // BHop-ускорение: +0.05 в воздухе сверх физики.
        assertTrue(airTick + 0.05 > airBound + 0.003);
    }

    @Test
    void slowFallingGravity() {
        double dy = MovementModel.nextFallDy(-0.5, 0.01);
        assertEquals((-0.5 - 0.01) * 0.98, dy, 1e-12);
    }
}
