package ru.strazh.ac.check.impl.combat;

import ru.strazh.ac.check.Check;

import java.util.List;

/** Проверки категории «Бой». Новые классы добавляются в этот список. */
public final class CombatChecks {

    private CombatChecks() {
    }

    public static final List<Class<? extends Check>> ALL = List.of(
            ReachA.class, ReachB.class, HitboxA.class, MultiAuraA.class, NoSwingA.class, WallHitA.class,
            CriticalsA.class, AutoClickerA.class, KillAuraA.class, CrystalAuraA.class
    );
}
