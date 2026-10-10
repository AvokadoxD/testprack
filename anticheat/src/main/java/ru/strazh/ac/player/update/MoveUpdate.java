package ru.strazh.ac.player.update;

/**
 * Один пакет движения (Flying/Position/Rotation/PositionRotation).
 *
 * @param teleport       эхо подтверждённого серверного телепорта (не является движением)
 * @param teleportPending сервер ждёт подтверждения телепорта: такие пакеты сервер игнорирует
 * @param inVehicle      игрок сидит в транспорте
 */
public record MoveUpdate(
        long clientTick,
        double x, double y, double z,
        double lastX, double lastY, double lastZ,
        float yaw, float pitch,
        boolean positionChanged,
        boolean rotationChanged,
        boolean onGround,
        boolean lastOnGround,
        boolean horizontalCollision,
        boolean teleport,
        boolean teleportPending,
        boolean inVehicle
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
}
