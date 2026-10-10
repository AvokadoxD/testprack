package ru.strazh.ac.sim;

import org.bukkit.entity.Player;
import ru.strazh.ac.player.PlayerData;

/** Заглушка симулятора: только запоминает последнюю позицию для откатов. */
public final class NoopEngine implements SimulationEngine {

    @Override
    public SimulationResult simulate(PlayerData data, MoveSnapshot s) {
        SimulationResult r = new SimulationResult(s);
        r.skip = true;
        r.skipReason = "engine disabled";
        Player p = data.player();
        if (p != null && s.positionChanged() && s.teleportEcho() == null) {
            data.sim().lastValid = new org.bukkit.Location(p.getWorld(), s.x(), s.y(), s.z());
        }
        return r;
    }
}
