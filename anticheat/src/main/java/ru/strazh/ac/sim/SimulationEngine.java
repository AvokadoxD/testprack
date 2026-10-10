package ru.strazh.ac.sim;

import ru.strazh.ac.player.PlayerData;

/** Симуляция движения игрока (главный поток, доступ к миру). */
public interface SimulationEngine {

    /**
     * Симулирует один пакет движения. Вызывается строго по порядку пакетов.
     * Реализация хранит своё состояние в {@link SimState} игрока.
     */
    SimulationResult simulate(PlayerData data, MoveSnapshot snapshot);
}
