package ru.strazh.ac.check.impl.movement;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.SimulationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.sim.SimulationResult;

/** Вход внутрь твёрдого блока (Phase, NoClip, VClip, PearlPhase). */
@CheckInfo(name = "Phase", type = "A", category = Category.MOVEMENT,
        description = "Прохождение сквозь блоки (Phase, NoClip, VClip)", maxVl = 8, decay = 1.0, setback = true)
public final class PhaseA extends Check implements SimulationCheck {

    public PhaseA(PlayerData data) {
        super(data);
    }

    @Override
    public void onSimulation(SimulationResult r) {
        if (MoveCheckUtil.unreliable(r) || !r.phase) return;
        flagSetback(1.0, "вход в блок");
    }
}
