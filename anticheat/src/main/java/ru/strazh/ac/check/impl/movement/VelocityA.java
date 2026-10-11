package ru.strazh.ac.check.impl.movement;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.SimulationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.tracker.VelocityTracker;
import ru.strazh.ac.sim.SimulationResult;
import ru.strazh.ac.sim.SimulationResult.Uncertainty;
import ru.strazh.ac.util.Text;

import java.util.List;

/**
 * Отдача (AntiKB/Velocity). После подтверждения транзакции клиент обязан был
 * применить скорость от сервера. Вертикальную часть отдачи «jump reset» не
 * уменьшает (прыжок берёт максимум), горизонтальную уменьшает максимум на 0.2 —
 * поэтому по горизонтали флагаем только сильное подавление.
 */
@CheckInfo(name = "Velocity", type = "A", category = Category.COMBAT,
        description = "Игнорирование отдачи (AntiKB, Velocity)", maxVl = 10, decay = 1.0)
public final class VelocityA extends Check implements SimulationCheck {

    public VelocityA(PlayerData data) {
        super(data);
    }

    @Override
    public void onSimulation(SimulationResult r) {
        List<VelocityTracker.Entry> list = r.snapshot.velocities();
        if (list.isEmpty()) return;
        VelocityTracker.Entry newest = null;
        for (VelocityTracker.Entry e : list) {
            if (!e.additive && !e.consumed) newest = e;
        }
        if (newest == null) return;
        for (VelocityTracker.Entry e : list) {
            if (e != newest && !e.additive) e.consumed = true;
        }
        if (r.skip) {
            newest.consumed = true;
            return;
        }
        double vy = newest.velocity.y(), vxz = newest.velocity.horizontalLength();
        double dy = r.actual.y(), dxz = r.actual.horizontalLength();
        boolean verticalTaken = Math.abs(dy - vy) < 0.02 || (dy >= vy * 0.9 && dy <= Math.max(vy, r.jumpPower) + 0.02);
        boolean horizontalTaken = vxz < 0.05 || (dxz >= vxz * 0.55 && dxz <= vxz * 1.6 + 0.3);
        if (verticalTaken || horizontalTaken && vy <= 0.1) {
            newest.consumed = true;
            decreaseBuffer(0.25);
            return;
        }
        if (newest.confirmedTick < 0 || r.snapshot.clientTick() <= newest.confirmedTick) {
            return; // ещё в окне неопределённости — клиент мог не применить
        }
        newest.consumed = true;
        if (MoveCheckUtil.unreliable(r) || r.has(Uncertainty.LIQUID) || r.has(Uncertainty.WEB)
                || r.has(Uncertainty.CLIMBABLE) || r.has(Uncertainty.POWDER_SNOW) || r.has(Uncertainty.BUBBLE_COLUMN)) return;
        String why = null;
        if (vy > 0.1 && !r.ceiling && dy < vy * 0.75) {
            why = "вертикаль " + Text.fmt(dy, 3) + " из " + Text.fmt(vy, 3);
        } else if (vxz > 0.15 && !r.wallCollision && dxz < vxz * 0.3) {
            why = "горизонталь " + Text.fmt(dxz, 3) + " из " + Text.fmt(vxz, 3);
        }
        if (why != null) {
            if (increaseBuffer(1.0) > 1.0) {
                flag(why);
            }
        } else {
            decreaseBuffer(0.25);
        }
    }
}
