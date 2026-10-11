package ru.strazh.ac.check.impl.movement;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.SimulationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.sim.SimulationResult;
import ru.strazh.ac.util.Text;

/**
 * Направление ускорения не соответствует нажатым клавишам (1.21.2+: клиент
 * сообщает клавиши в PlayerInput). По ванильной физике ускорение тика всегда
 * направлено по вектору клавиш, повёрнутому на yaw этого тика. Нарушают:
 * «тихие» ауры с коррекцией движения (клавиши относительно камеры, а yaw —
 * на цель), TargetStrafe, AutoWalk без клавиш, OmniSprint, Strafe-ускорения.
 */
@CheckInfo(name = "Strafe", type = "A", category = Category.MOVEMENT,
        description = "Движение не по нажатым клавишам (MoveFix тихой ауры, TargetStrafe)", maxVl = 15, decay = 1.5, setback = true)
public final class StrafeA extends Check implements SimulationCheck {

    private double maxError;
    private int bad, total;

    public StrafeA(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        maxError = cfg("max-angle", 25.0);
    }

    @Override
    public void onSimulation(SimulationResult r) {
        if (!r.strafeChecked || r.skip) return;
        // Ускорение в воздухе мало (0.02) — судим только по уверенным случаям.
        if (r.strafeAccel < 0.02 && r.strafeError < 179) return;
        total++;
        if (r.strafeError > maxError) {
            bad++;
            if (increaseBuffer(r.strafeError >= 179 ? 1.0 : 1.5) > 6.0) {
                flagSetback(1.0, (r.strafeError >= 179 ? "ускорение без клавиш " : "отклонение " + Text.fmt(r.strafeError, 0) + "° ")
                        + "a=" + Text.fmt(r.strafeAccel, 3) + " (" + bad + "/" + total + ")");
                buffer = 3.0;
            }
        } else {
            decreaseBuffer(0.5);
        }
        if (total >= 200) {
            total = 0;
            bad = 0;
        }
    }
}
