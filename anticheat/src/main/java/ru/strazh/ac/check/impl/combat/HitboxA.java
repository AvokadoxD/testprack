package ru.strazh.ac.check.impl.combat;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.AttackUpdate;
import ru.strazh.ac.util.Text;

/**
 * Луч взгляда не пересекает хитбокс цели (расширенный HitBox, удар без наведения,
 * «тихая» аура с неверной ротацией). Цель клиент выбирает лучом из глаз с
 * ротацией этого же тика — значит, луч обязан попадать в хитбокс.
 */
@CheckInfo(name = "Hitbox", type = "A", category = Category.COMBAT,
        description = "Удар мимо хитбокса (HitBox, аура без наведения)", maxVl = 10, decay = 1.0)
public final class HitboxA extends Check implements CombatCheck {

    public HitboxA(PlayerData data) {
        super(data);
    }

    @Override
    public void onAttackResolved(AttackUpdate a) {
        if (a.uncertain || a.targetBox == null || a.distance < 0 || a.distance > a.maxRange) return;
        if (data.rotations().possiblyApplied(data.transactions().lastConfirmed(), data.movement().clientTick)) return;
        if (!a.rayHit) {
            data.addCombatSuspicion(0.5);
            if (increaseBuffer(1.0) > 2.0) {
                flag("промах лучом, ошибка " + Text.fmt(a.aimErrorYaw, 1) + "°/" + Text.fmt(a.aimErrorPitch, 1) + "°");
            }
        } else {
            decreaseBuffer(0.1);
        }
    }
}
