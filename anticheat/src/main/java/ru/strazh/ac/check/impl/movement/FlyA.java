package ru.strazh.ac.check.impl.movement;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.SimulationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.sim.SimulationResult;
import ru.strazh.ac.util.Text;

/**
 * Вертикаль выше физики: зависание, подъём в воздухе, прыжок выше силы прыжка,
 * подъём на ступеньку выше step_height, лазание по стенам (Fly, Glide, HighJump,
 * Step, Spider, AirJump).
 */
@CheckInfo(name = "Fly", type = "A", category = Category.MOVEMENT,
        description = "Подъём/зависание выше физики (Fly, HighJump, Step, Spider, AirJump)", maxVl = 15, decay = 2.0, setback = true)
public final class FlyA extends Check implements SimulationCheck {

    private double threshold;

    public FlyA(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        threshold = cfg("threshold", 0.01);
    }

    @Override
    public void onSimulation(SimulationResult r) {
        if (MoveCheckUtil.verticalUnreliable(r)) return;
        double off = r.offsetY;
        if (off > threshold) {
            double dy = r.actual.y();
            String kind;
            if (r.prevServerGround && dy > 0) kind = dy > r.jumpPower ? "прыжок выше " + Text.fmt(r.jumpPower, 2) : "ступенька";
            else if (r.wallCollision && dy > 0) kind = "лазание по стене";
            else kind = dy >= 0 ? "подъём/зависание" : "медленное падение";
            if (increaseBuffer(off > 0.1 ? 2.0 : 1.0) > 1.5) {
                flagSetback(Math.min(3.0, 0.5 + off * 4), kind + " dy=" + Text.fmt(dy, 4) + " ожид=" + Text.fmt(r.predictedDy, 4));
                buffer = 1.0;
            }
        } else {
            decreaseBuffer(0.05);
        }
    }
}
