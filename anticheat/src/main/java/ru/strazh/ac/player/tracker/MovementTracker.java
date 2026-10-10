package ru.strazh.ac.player.tracker;

/**
 * Позиция и ротация игрока по пакетам движения (event loop).
 * Значения «last*» — предыдущий пакет, без суффикса — текущий.
 */
public final class MovementTracker {

    public boolean hasPosition;

    public double x, y, z;
    public double lastX, lastY, lastZ;
    public float yaw, pitch;
    public float lastYaw, lastPitch;
    public boolean onGround, lastOnGround;
    public boolean horizontalCollision;

    public double deltaX, deltaY, deltaZ, deltaXZ;
    public double lastDeltaX, lastDeltaY, lastDeltaZ, lastDeltaXZ;
    public float deltaYaw, deltaPitch;
    public float lastDeltaYaw, lastDeltaPitch;

    /** Изменилась ли позиция в последнем пакете движения. */
    public boolean positionChanged;
    public boolean rotationChanged;

    /** Номер клиентского тика (растёт на каждый тик клиента). */
    public long clientTick;
    /** Пакетов движения подряд без изменения позиции (клиент шлёт позицию раз в 20 тиков). */
    public int ticksWithoutPosition;
    public int ticksSinceTeleport = 1000;
    public int groundTicks, airTicks;
    /** Тиков с последнего пакета движения внутри текущего клиентского тика. */
    public int movesThisTick;

    public void resetTo(double nx, double ny, double nz, float nyaw, float npitch) {
        x = lastX = nx;
        y = lastY = ny;
        z = lastZ = nz;
        yaw = lastYaw = nyaw;
        pitch = lastPitch = npitch;
        deltaX = deltaY = deltaZ = deltaXZ = 0;
        lastDeltaX = lastDeltaY = lastDeltaZ = lastDeltaXZ = 0;
        hasPosition = true;
    }
}
