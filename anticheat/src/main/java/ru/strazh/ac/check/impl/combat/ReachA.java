package ru.strazh.ac.check.impl.combat;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.AttackUpdate;
import ru.strazh.ac.util.Text;

/**
 * Дистанция удара от глаз до ближайшей точки хитбокса цели больше атрибута
 * entity_interaction_range (3.0). Хитбокс — объединение всех позиций, которые
 * клиент мог видеть (транзакции + интерполяция), но не старше базового пинга + запас.
 */
@CheckInfo(name = "Reach", type = "A", category = Category.COMBAT,
        description = "Удар дальше допустимой дистанции", maxVl = 10, decay = 1.0)
public final class ReachA extends Check implements CombatCheck {

    private double threshold;
    private boolean cancel;

    public ReachA(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        threshold = cfg("threshold", 0.03);
        cancel = cfg("cancel-hits", true);
    }

    @Override
    public void onAttackResolved(AttackUpdate a) {
        if (a.uncertain || a.distance < 0 || data.abilities().gameMode == 1) return;
        double excess = a.distance - a.maxRange;
        if (excess > threshold) {
            if (cancel) data.addCombatSuspicion(1.0);
            double w = excess > 0.5 ? 1.5 : 1.0;
            if (increaseBuffer(1.0) > 1.0) {
                flag(w, "dist=" + Text.fmt(a.distance) + " max=" + Text.fmt(a.maxRange, 2)
                        + (a.target != null ? " " + a.target.typeName : ""));
            }
        } else {
            decreaseBuffer(0.1);
        }
    }
}
