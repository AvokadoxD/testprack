package ru.strazh.ac.sim;

import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.honeypot.HoneypotManager;

/** Фабрика реализаций симуляции и ловушек. */
public final class Engines {

    private Engines() {
    }

    public static SimulationEngine create(StrazhPlugin plugin) {
        return new NoopEngine();
    }

    public static HoneypotManager createHoneypots(StrazhPlugin plugin) {
        return new HoneypotManager(plugin);
    }
}
