package ru.strazh.ac.check.impl.aim;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.RotationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.RotationUpdate;
import ru.strazh.ac.util.Stats;
import ru.strazh.ac.util.Text;

/**
 * Квантование мыши (GCD). Ванильные повороты кратны шагу (s·0.6+0.2)³·8·0.15
 * (не меньше 0.0096°). Многие ауры и «ИИ-ротации» пишут углы напрямую — у пар
 * соседних изменений pitch нет общего делителя. Ложные срабатывания возможны у
 * кинематографической камеры, подзорной трубы, геймпадов, macOS с дробным вводом,
 * поэтому проверка экспериментальная и смотрит только на бой.
 */
@CheckInfo(name = "Aim", type = "A", category = Category.COMBAT,
        description = "Повороты без квантования мыши (GCD) в бою", maxVl = 15, decay = 2.0, experimental = true)
public final class AimA extends Check implements RotationCheck {

    private static final double MIN_STEP = 0.0096 * 0.9;
    private double lastDelta;
    private int samples, bad;

    public AimA(PlayerData data) {
        super(data);
    }

    @Override
    public void onRotation(RotationUpdate u) {
        if (!u.isMouseInput() || u.inVehicle()) {
            lastDelta = 0;
            return;
        }
        if (u.clientTick() - data.actions().lastAttackTick > 40) return;
        double d = Math.abs(u.deltaPitch());
        if (d < 0.05 || d > 20 || Math.abs(u.pitch()) > 85) {
            lastDelta = 0;
            return;
        }
        if (lastDelta > 0) {
            double g = Stats.gcdFloat(d, lastDelta, 1.0E-4);
            samples++;
            if (g < MIN_STEP) bad++;
            if (samples >= 60) {
                double ratio = (double) bad / samples;
                if (ratio > 0.6) {
                    if (increaseBuffer(1.0) > 1.0) flag("без шага мыши " + Text.fmt(ratio * 100, 0) + "%");
                } else {
                    decreaseBuffer(0.5);
                }
                samples = 0;
                bad = 0;
            }
        }
        lastDelta = d;
    }
}
