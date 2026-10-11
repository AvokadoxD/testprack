package ru.strazh.ac.check.impl.combat;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.AttackUpdate;

/**
 * Удары по разным сущностям в одном тике. Цель под прицелом вычисляется клиентом
 * один раз в начале тика, поэтому все удары тика — по одной сущности.
 */
@CheckInfo(name = "MultiAura", type = "A", category = Category.COMBAT,
        description = "Удары по нескольким целям за один тик (Multi/Switch-аура)", maxVl = 5, decay = 0.5)
public final class MultiAuraA extends Check implements CombatCheck, TickCheck {

    private int firstTarget = Integer.MIN_VALUE;

    public MultiAuraA(PlayerData data) {
        super(data);
    }

    @Override
    public void onAttackPacket(AttackUpdate a) {
        if (firstTarget == Integer.MIN_VALUE) {
            firstTarget = a.entityId;
        } else if (firstTarget != a.entityId && data.client().sendsTickEnd) {
            data.addCombatSuspicion(2.0);
            flag("цели " + firstTarget + " и " + a.entityId);
        }
    }

    @Override
    public void onClientTickEnd() {
        firstTarget = Integer.MIN_VALUE;
    }
}
