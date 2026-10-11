package ru.strazh.ac.check.impl.aim;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.check.hook.RotationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.AttackUpdate;
import ru.strazh.ac.player.update.RotationUpdate;
import ru.strazh.ac.util.McMath;
import ru.strazh.ac.util.Text;

/**
 * «Снап в момент удара»: аура резко доворачивает на цель ровно в тике удара и
 * сразу уводит взгляд обратно (FunTime/Snap/HolyWorld-ротации). Человек,
 * наоборот, замедляет мышь перед кликом. Считаем долю таких ударов.
 */
@CheckInfo(name = "Aim", type = "C", category = Category.COMBAT,
        description = "Резкий доворот на цель в момент удара и возврат (Snap-аура)", maxVl = 10, decay = 1.0)
public final class AimC extends Check implements RotationCheck, CombatCheck {

    private final float[] recent = new float[4];
    private int idx;
    private float lastYawDelta;
    private boolean awaitingReturn;
    private float snapDelta;
    private int attacks, snaps;

    public AimC(PlayerData data) {
        super(data);
    }

    @Override
    public void onRotation(RotationUpdate u) {
        if (!u.isMouseInput() || u.inVehicle()) return;
        float d = (float) McMath.angleDiff(u.yaw(), u.lastYaw());
        if (awaitingReturn) {
            awaitingReturn = false;
            // Возврат: заметный поворот в обратную сторону сразу после удара.
            if (Math.abs(d) > 4f && Math.signum(d) != Math.signum(snapDelta)) {
                snaps++;
            }
        }
        recent[idx] = Math.abs(d);
        idx = (idx + 1) % recent.length;
        lastYawDelta = d;
    }

    @Override
    public void onAttackResolved(AttackUpdate a) {
        if (a.uncertain || a.inVehicle || data.rotations().possiblyApplied(data.transactions().lastConfirmed(), a.clientTick)) return;
        attacks++;
        float atk = Math.abs(lastYawDelta);
        float sum = 0;
        int n = 0;
        for (float r : recent) {
            sum += r;
            n++;
        }
        // recent включает и сам тик удара — исключим его из среднего.
        float before = n > 1 ? (sum - atk) / (n - 1) : 0;
        if (atk > 10f && atk > 3f * Math.max(1f, before) && a.rayHit) {
            awaitingReturn = true;
            snapDelta = lastYawDelta;
        }
        if (attacks >= 20) {
            double ratio = (double) snaps / attacks;
            if (ratio >= 0.4) {
                flag(1.5, "снапов " + snaps + "/" + attacks + " (" + Text.fmt(ratio * 100, 0) + "%)");
            }
            attacks = 0;
            snaps = 0;
        }
    }
}
