package ru.strazh.ac.player.update;

/**
 * Изменение ротации в одном пакете движения.
 *
 * @param deltaYaw      изменение yaw (без нормализации — так, как прислал клиент)
 * @param deltaPitch    изменение pitch
 * @param serverForced  поворот мог быть вызван сервером (PlayerRotation/телепорт/FacePlayer),
 *                      аим-проверки должны пропускать такие обновления
 * @param inVehicle     игрок в транспорте (камеру могут поворачивать машины)
 * @param teleport      пакет — эхо серверного телепорта
 */
public record RotationUpdate(
        long clientTick,
        float yaw, float pitch,
        float lastYaw, float lastPitch,
        float deltaYaw, float deltaPitch,
        float lastDeltaYaw, float lastDeltaPitch,
        boolean serverForced,
        boolean inVehicle,
        boolean teleport,
        boolean positionChanged
) {

    public float absDeltaYaw() {
        return Math.abs(deltaYaw);
    }

    public float absDeltaPitch() {
        return Math.abs(deltaPitch);
    }

    /** Можно ли использовать этот поворот для анализа движения мыши. */
    public boolean isMouseInput() {
        return !serverForced && !teleport;
    }
}
