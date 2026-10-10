package ru.strazh.ac.player;

/** Атрибуты игрока по данным клиента (применяются по транзакциям). */
public final class AttributeState {

    public volatile double entityRange = 3.0;
    public volatile double blockRange = 4.5;
    public volatile double scale = 1.0;
    public volatile double stepHeight = 0.6;
    public volatile double gravity = 0.08;
    public volatile double jumpStrength = 0.42;
    public volatile double safeFallDistance = 3.0;
    public volatile double movementSpeed = 0.1;
    public volatile double movementEfficiency = 0.0;
    public volatile double waterMovementEfficiency = 0.0;
    public volatile double sneakingSpeed = 0.3;
    /** Клиентский тик последнего изменения какого-либо атрибута. */
    public volatile long lastChangeTick = -1000;
}
