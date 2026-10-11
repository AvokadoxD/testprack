package ru.strazh.ac.check.impl.combat;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.AttackUpdate;

/**
 * CrystalAura: удар по кристаллу края через 0–1 тик после того, как клиент его
 * получил (подтверждено транзакцией). Человеку нужно ≥ 3–4 тика, чтобы увидеть
 * кристалл и кликнуть; «мгновенный» удар (+ предсказание id — BadPackets C)
 * — признак CrystalAura/AutoCrystal.
 */
@CheckInfo(name = "CrystalAura", type = "A", category = Category.COMBAT,
        description = "Мгновенный удар по кристаллу после появления (CrystalAura)", maxVl = 10, decay = 1.0)
public final class CrystalAuraA extends Check implements CombatCheck {

    private int fast, total;

    public CrystalAuraA(PlayerData data) {
        super(data);
    }

    @Override
    public void onAttackPacket(AttackUpdate a) {
        if (a.target == null || !a.target.typeName.equals("end_crystal")) return;
        long seen = a.target.firstConfirmedTick();
        // Клиент мог увидеть кристалл и раньше подтверждения — тогда реакция ещё больше.
        long reaction = seen < 0 ? 0 : a.clientTick - seen;
        total++;
        if (reaction <= 1) {
            fast++;
            if (increaseBuffer(1.0) > 3.0) {
                flag("удар через " + reaction + " тик. после появления (" + fast + "/" + total + ")");
                buffer = 2.0;
            }
        } else if (reaction >= 3) {
            decreaseBuffer(0.5);
        }
        if (total >= 50) {
            total = 0;
            fast = 0;
        }
    }
}
