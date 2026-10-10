package ru.strazh.ac.sim;

import org.bukkit.Location;
import ru.strazh.ac.util.Vec3;

/** Состояние симуляции игрока (только главный поток). */
public final class SimState {

    /** Последняя позиция, прошедшая проверки (для отката). */
    public Location lastValid;
    /** Предсказанная скорость (deltaMovement) после последнего тика. */
    public Vec3 motion = Vec3.ZERO;
    public boolean onGround;
    public double fallDistance;
    public int setbacksThisMinute;
    public long setbackMinuteStart;
    public long lastSetbackMillis;
    /** Произвольные данные реализации симулятора. */
    public Object engineState;
}
