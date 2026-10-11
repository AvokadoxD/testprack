package ru.strazh.ac.check.impl.movement;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.SimulationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.sim.SimulationResult;
import ru.strazh.ac.sim.SimulationResult.Uncertainty;
import ru.strazh.ac.util.Text;

/** Нет замедления при использовании предмета (еда, щит, лук) или приседе. */
@CheckInfo(name = "NoSlow", type = "A", category = Category.MOVEMENT,
        description = "Нет замедления при использовании предмета или приседе", maxVl = 15, decay = 2.0, setback = true)
public final class NoSlowA extends Check implements SimulationCheck {

    public NoSlowA(PlayerData data) {
        super(data);
    }

    @Override
    public void onSimulation(SimulationResult r) {
        if (MoveCheckUtil.unreliable(r) || (!r.usingItem && !r.sneaking) || r.has(Uncertainty.LIQUID)
                || r.has(Uncertainty.VELOCITY) || r.slowedAllowedXZ == Double.MAX_VALUE) return;
        double speed = r.actual.horizontalLength();
        double off = speed - r.slowedAllowedXZ;
        if (off > 0.01) {
            if (increaseBuffer(1.0) > 5.0) {
                flagSetback(1.0, (r.usingItem ? "предмет" : "присед") + " скорость " + Text.fmt(speed, 3)
                        + " > " + Text.fmt(r.slowedAllowedXZ, 3));
                buffer = 3.0;
            }
        } else {
            decreaseBuffer(0.25);
        }
    }
}
