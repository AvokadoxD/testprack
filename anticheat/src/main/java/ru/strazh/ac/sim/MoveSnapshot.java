package ru.strazh.ac.sim;

import ru.strazh.ac.player.tracker.TeleportTracker;
import ru.strazh.ac.player.tracker.VelocityTracker;

import java.util.List;

/**
 * Неизменяемый снимок одного пакета движения со всем контекстом,
 * разрешённым в event loop по транзакциям. Передаётся в главный поток
 * для симуляции движения с доступом к миру.
 *
 * @param velocities        скорости от сервера, которые могли примениться в этом тике
 * @param velocityPending   есть отправленная, но не подтверждённая скорость
 * @param teleportEcho      не null — пакет является эхом подтверждённого телепорта
 * @param teleportPending   сервер ждёт подтверждения телепорта (сервер игнорирует пакет)
 * @param abilitiesUncertain способности меняются (окно транзакции)
 */
public record MoveSnapshot(
        long clientTick,
        long nanoTime,
        double x, double y, double z,
        double lastX, double lastY, double lastZ,
        float yaw, float pitch, float lastYaw, float lastPitch,
        boolean positionChanged, boolean rotationChanged,
        boolean onGround, boolean lastOnGround, boolean horizontalCollision,
        int ticksWithoutPosition,
        // ввод
        boolean hasInput,
        boolean forward, boolean backward, boolean left, boolean right,
        boolean jump, boolean shift, boolean sprintKey,
        boolean sprinting, boolean sneaking,
        boolean usingItem, boolean useOffhand,
        long lastGlideRequestTick,
        // сервер
        List<VelocityTracker.Entry> velocities,
        boolean velocityPending,
        TeleportTracker.Pending teleportEcho,
        boolean teleportPending,
        boolean inVehicle,
        boolean flying, boolean allowFlight, int gameMode, float flySpeed, boolean abilitiesUncertain,
        boolean sendsTickEnd
) {

    public double deltaX() {
        return x - lastX;
    }

    public double deltaY() {
        return y - lastY;
    }

    public double deltaZ() {
        return z - lastZ;
    }

    public double deltaXZ() {
        double dx = x - lastX, dz = z - lastZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Направление ввода вперёд/назад: +1, 0, -1. */
    public int forwardInput() {
        return (forward ? 1 : 0) - (backward ? 1 : 0);
    }

    /** Направление ввода влево/вправо: +1 влево, -1 вправо. */
    public int strafeInput() {
        return (left ? 1 : 0) - (right ? 1 : 0);
    }
}
