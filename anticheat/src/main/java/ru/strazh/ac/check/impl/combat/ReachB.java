package ru.strazh.ac.check.impl.combat;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.AttackUpdate;
import ru.strazh.ac.util.Text;

/**
 * BackTrack: удар допустим только по старым позициям цели, которые клиент
 * давно должен был сменить новыми (придерживает входящие пакеты, чтобы бить
 * «прошлое» положение цели).
 */
@CheckInfo(name = "Reach", type = "B", category = Category.COMBAT,
        description = "Удар по устаревшей позиции цели (BackTrack, задержка входящих пакетов)", maxVl = 12, decay = 1.0)
public final class ReachB extends Check implements CombatCheck {

    public ReachB(PlayerData data) {
        super(data);
    }

    @Override
    public void onAttackResolved(AttackUpdate a) {
        if (a.uncertain || a.distance < 0 || a.distanceUnbounded < 0 || data.abilities().gameMode == 1) return;
        boolean onlyStale = a.distance > a.maxRange + 0.03 && a.distanceUnbounded <= a.maxRange + 0.03;
        if (onlyStale) {
            if (increaseBuffer(1.0) > 3.0) {
                flag("dist=" + Text.fmt(a.distance) + " по старым=" + Text.fmt(a.distanceUnbounded)
                        + " пинг=" + Math.round(data.transactions().smoothedRttMs()) + "/" + Math.round(data.transactions().baselineRttMs()));
            }
        } else {
            decreaseBuffer(0.2);
        }
    }
}
