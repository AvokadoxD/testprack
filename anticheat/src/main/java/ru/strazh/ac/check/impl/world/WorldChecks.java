package ru.strazh.ac.check.impl.world;

import ru.strazh.ac.check.Check;

import java.util.List;

/** Проверки категории «Мир». */
public final class WorldChecks {

    private WorldChecks() {
    }

    public static final List<Class<? extends Check>> ALL = List.of(
            BlockReachA.class, ScaffoldA.class, AirPlaceA.class, FastBreakA.class, NukerB.class
    );
}
