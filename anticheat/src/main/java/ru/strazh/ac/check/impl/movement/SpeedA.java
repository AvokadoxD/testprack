package ru.strazh.ac.check.impl.movement;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.SimulationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.sim.SimulationResult;
import ru.strazh.ac.sim.SimulationResult.Uncertainty;
import ru.strazh.ac.util.Text;

/**
 * Горизонтальная скорость выше физически возможной: инерция прошлого тика с
 * трением + максимальное ускорение (+0.2 при спринт-прыжке, + скорость от
 * сервера, + толкание). Ловит Speed/BHop/Strafe-ускорение/LongJump/Collide-speed.
 */
@CheckInfo(name = "Speed", type = "A", category = Category.MOVEMENT,
        description = "Горизонтальная скорость выше возможной (Speed, BHop, LongJump)", maxVl = 20, decay = 2.0, setback = true)
public final class SpeedA extends Check implements SimulationCheck {

    private double threshold;

    public SpeedA(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        threshold = cfg("threshold", 0.005);
    }

    @Override
    public void onSimulation(SimulationResult r) {
        if (MoveCheckUtil.unreliable(r) || r.allowedXZ == Double.MAX_VALUE) return;
        double thr = threshold;
        if (r.has(Uncertainty.LIQUID) || r.has(Uncertainty.WEB) || r.has(Uncertainty.BLOCK_CHANGE)) thr += 0.05;
        double off = r.offsetXZ;
        if (off > thr) {
            double add = off > 0.2 ? 3.0 : 1.0;
            if (increaseBuffer(add) > 3.0) {
                flagSetback(Math.min(3.0, 0.5 + off * 5), "скорость " + Text.fmt(r.actual.horizontalLength(), 3)
                        + " > " + Text.fmt(r.allowedXZ, 3) + (r.prevServerGround ? " земля" : " воздух"));
                buffer = 2.0;
            }
        } else {
            decreaseBuffer(0.1);
        }
    }
}
