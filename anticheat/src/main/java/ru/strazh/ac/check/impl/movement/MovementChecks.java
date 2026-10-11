package ru.strazh.ac.check.impl.movement;

import ru.strazh.ac.check.Check;

import java.util.List;

/** Проверки категории «Движение». */
public final class MovementChecks {

    private MovementChecks() {
    }

    public static final List<Class<? extends Check>> ALL = List.of(
            SpeedA.class, NoSlowA.class, FlyA.class, FlyB.class, NoFallA.class, PhaseA.class, VelocityA.class
    );
}
