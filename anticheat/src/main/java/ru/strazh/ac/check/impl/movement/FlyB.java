package ru.strazh.ac.check.impl.movement;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.SimulationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.sim.SimulationResult;
import ru.strazh.ac.util.Text;

/** Падение быстрее гравитации (FastFall, ReverseStep, «телепорт вниз»). */
@CheckInfo(name = "Fly", type = "B", category = Category.MOVEMENT,
        description = "Падение быстрее гравитации (FastFall, ReverseStep)", maxVl = 15, decay = 2.0, setback = true)
public final class FlyB extends Check implements SimulationCheck {

    public FlyB(PlayerData data) {
        super(data);
    }

    @Override
    public void onSimulation(SimulationResult r) {
        if (MoveCheckUtil.verticalUnreliable(r) || r.predictedDy > 0 || r.ceiling) return;
        double off = r.offsetY;
        if (off < -0.05) {
            if (increaseBuffer(1.0) > 2.0) {
                flagSetback(1.0, "dy=" + Text.fmt(r.actual.y(), 4) + " ожид=" + Text.fmt(r.predictedDy, 4));
                buffer = 1.0;
            }
        } else {
            decreaseBuffer(0.1);
        }
    }
}
