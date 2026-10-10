package ru.strazh.ac.check.hook;

import ru.strazh.ac.sim.SimulationResult;

/** Проверка результатов симуляции движения (главный поток). */
public interface SimulationCheck {

    void onSimulation(SimulationResult result);
}
