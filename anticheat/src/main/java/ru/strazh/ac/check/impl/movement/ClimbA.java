package ru.strazh.ac.check.impl.movement;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.SimulationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.sim.SimulationResult;
import ru.strazh.ac.util.Text;

/** Подъём по лестнице/лианам быстрее ванильных 0.1176 блока за тик (FastClimb). */
@CheckInfo(name = "Climb", type = "A", category = Category.MOVEMENT,
        description = "Слишком быстрый подъём по лестнице/лианам (FastClimb)", maxVl = 10, decay = 1.5, setback = true)
public final class ClimbA extends Check implements SimulationCheck {

    public ClimbA(PlayerData data) {
        super(data);
    }

    @Override
    public void onSimulation(SimulationResult r) {
        if (MoveCheckUtil.unreliable(r) || r.has(ru.strazh.ac.sim.SimulationResult.Uncertainty.BLOCK_CHANGE)) return;
        if (r.fastClimb) {
            if (increaseBuffer(1.0) > 2.0) {
                flagSetback(1.0, "подъём " + Text.fmt(r.actual.y(), 4) + " за тик");
                buffer = 1.0;
            }
        } else {
            decreaseBuffer(0.1);
        }
    }
}
