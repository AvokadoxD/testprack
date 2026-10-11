package ru.strazh.ac.check.impl.movement;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.SimulationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.sim.SimulationResult;
import ru.strazh.ac.util.Text;

/**
 * Полёт на элитрах не по физике (ElytraFly «Control/Packet/Bounce», вертикальный
 * подъём без фейерверков, ускорения). Без фейерверков, урона и помех ванильное
 * движение на элитрах полностью детерминировано.
 */
@CheckInfo(name = "Elytra", type = "A", category = Category.MOVEMENT,
        description = "Полёт на элитрах не по физике (ElytraFly)", maxVl = 15, decay = 2.0, setback = true)
public final class ElytraA extends Check implements SimulationCheck {

    private double threshold;

    public ElytraA(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        threshold = cfg("threshold", 0.05);
    }

    @Override
    public void onSimulation(SimulationResult r) {
        if (!r.elytraChecked) return;
        double off = r.elytraOffset;
        if (off > threshold) {
            if (increaseBuffer(off > 0.3 ? 2.0 : 1.0) > 3.0) {
                flagSetback(Math.min(3.0, 0.5 + off * 3), "откл. " + Text.fmt(off, 3) + " факт " + r.actual
                        + " ожид " + r.elytraPredicted);
                buffer = 2.0;
            }
        } else {
            decreaseBuffer(0.25);
        }
    }
}
