package ru.strazh.ac.check.impl.aim;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.RotationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.RotationUpdate;
import ru.strazh.ac.util.Text;

/**
 * Отпечатки ротаций семейства Expensive и его паст (Exosware, Rich, Zenith...):
 * <ul>
 *   <li>«+3.1°»: модуль шага yaw растёт ровно на ~3.1° со сменой знака;</li>
 *   <li>минимальный шаг ~1°: при удержании цели yaw дрожит ±1.0–1.1° без
 *       меньших изменений.</li>
 * </ul>
 */
@CheckInfo(name = "Aim", type = "E", category = Category.COMBAT,
        description = "Отпечаток ротаций паст Expensive (+3.1° зигзаг, дрожание ±1°)", maxVl = 8, decay = 1.0)
public final class AimE extends Check implements RotationCheck {

    private float lastAbs, lastDelta;
    private int zigzag;
    private int ditherRun;

    public AimE(PlayerData data) {
        super(data);
    }

    @Override
    public void onRotation(RotationUpdate u) {
        if (!u.isMouseInput() || u.inVehicle()) return;
        boolean combat = u.clientTick() - data.actions().lastAttackTick < 40;
        float d = u.deltaYaw();
        float abs = Math.abs(d);
        if (combat && abs > 2f && lastAbs > 0f && Math.abs((abs - lastAbs) - 3.1f) < 0.06f
                && Math.signum(d) != Math.signum(lastDelta)) {
            if (++zigzag >= 3) {
                flag("+3.1° зигзаг x" + zigzag);
                zigzag = 0;
            }
        } else if (abs > 0.0001f) {
            zigzag = Math.max(0, zigzag - 1);
        }
        if (combat && abs >= 0.99f && abs <= 1.12f && Math.signum(d) != Math.signum(lastDelta) && lastAbs >= 0.99f && lastAbs <= 1.12f) {
            if (++ditherRun >= 8) {
                if (increaseBuffer(1.0) > 1.0) flag("дрожание ±" + Text.fmt(abs, 2) + "° x" + ditherRun);
                ditherRun = 0;
            }
        } else if (abs > 0.0001f) {
            ditherRun = 0;
        }
        lastAbs = abs;
        lastDelta = d;
    }
}
