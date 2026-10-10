package ru.strazh.ac.check;

import ru.strazh.ac.check.impl.aim.AimChecks;
import ru.strazh.ac.check.impl.combat.CombatChecks;
import ru.strazh.ac.check.impl.misc.MiscChecks;
import ru.strazh.ac.check.impl.movement.MovementChecks;
import ru.strazh.ac.check.impl.packet.PacketChecks;
import ru.strazh.ac.check.impl.player.PlayerChecks;
import ru.strazh.ac.check.impl.world.WorldChecks;

import java.util.ArrayList;
import java.util.List;

/** Перечень классов всех проверок (собирается из списков категорий). */
public final class Checks {

    private Checks() {
    }

    public static final List<Class<? extends Check>> ALL;

    static {
        List<Class<? extends Check>> all = new ArrayList<>();
        all.addAll(CombatChecks.ALL);
        all.addAll(AimChecks.ALL);
        all.addAll(MovementChecks.ALL);
        all.addAll(WorldChecks.ALL);
        all.addAll(PlayerChecks.ALL);
        all.addAll(PacketChecks.ALL);
        all.addAll(MiscChecks.ALL);
        ALL = List.copyOf(all);
    }
}
