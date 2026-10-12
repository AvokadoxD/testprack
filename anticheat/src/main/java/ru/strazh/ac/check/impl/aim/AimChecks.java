package ru.strazh.ac.check.impl.aim;

import ru.strazh.ac.check.Check;

import java.util.List;

/** Проверки аима. Новые классы добавляются в этот список. */
public final class AimChecks {

    private AimChecks() {
    }

    public static final List<Class<? extends Check>> ALL = List.of(
            AimA.class, AimB.class, AimC.class, AimD.class, AimE.class, AimF.class
    );
}
