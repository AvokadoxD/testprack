package ru.strazh.ac.check.impl.combat;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.AttackUpdate;
import ru.strazh.ac.player.update.MoveUpdate;
import ru.strazh.ac.util.Text;

/**
 * Пакетные/мини-криты: игрок «отрывается» от земли на сотые доли блока
 * (0.0625, 0.0125, 1e-5...) и сразу бьёт, чтобы сервер посчитал удар
 * критическим. Настоящий прыжок начинается со скорости ~0.42, падение — с
 * отрицательной; подпрыгнуть на 0.01–0.15 и ударить ванильно невозможно.
 */
@CheckInfo(name = "Criticals", type = "A", category = Category.COMBAT,
        description = "Ложные криты (микро-подскок перед ударом)", maxVl = 8, decay = 1.0)
public final class CriticalsA extends Check implements CombatCheck, MovementCheck {

    private boolean airborne;
    private double takeoffY;
    private double takeoffDy;
    private double peak;
    private int airMoves;

    public CriticalsA(PlayerData data) {
        super(data);
    }

    @Override
    public void onMovement(MoveUpdate u) {
        if (u.teleport() || u.inVehicle()) {
            airborne = false;
            return;
        }
        if (!u.onGround() && u.lastOnGround() && u.positionChanged()) {
            airborne = true;
            takeoffY = u.lastY();
            takeoffDy = u.deltaY();
            peak = u.y() - takeoffY;
            airMoves = 1;
        } else if (!u.onGround() && airborne) {
            airMoves++;
            peak = Math.max(peak, u.y() - takeoffY);
        } else if (u.onGround()) {
            airborne = false;
        }
    }

    @Override
    public void onAttackPacket(AttackUpdate a) {
        if (!airborne || data.movement().onGround || a.inVehicle) return;
        if (airMoves >= 2 && airMoves <= 3 && takeoffDy > 0 && takeoffDy < 0.15 && peak < 0.15
                && data.movement().deltaY < 0 && !data.movement().horizontalCollision) {
            if (increaseBuffer(1.0) > 2.0) {
                flag("подскок " + Text.fmt(takeoffDy, 4) + " пик " + Text.fmt(peak, 4) + " тиков " + airMoves);
            }
        } else {
            decreaseBuffer(0.05);
        }
    }
}
