package ru.strazh.ac.sim;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.check.hook.SimulationCheck;
import ru.strazh.ac.player.PlayerData;

import java.util.logging.Level;

/** Запуск симуляции, раздача результатов проверкам и откаты (главный поток). */
public final class SimulationService {

    private final StrazhPlugin plugin;
    private volatile SimulationEngine engine;

    public SimulationService(StrazhPlugin plugin, SimulationEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    public void setEngine(SimulationEngine engine) {
        this.engine = engine;
    }

    public SimulationEngine engine() {
        return engine;
    }

    /** Обработка одного снимка движения (главный поток, по порядку пакетов). */
    public void handle(PlayerData data, MoveSnapshot snapshot) {
        Player player = data.player();
        if (player == null || !player.isOnline() || data.checks() == null) return;
        SimulationResult result;
        try {
            result = engine.simulate(data, snapshot);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Ошибка симуляции движения " + data.name(), t);
            return;
        }
        if (result == null) return;
        for (SimulationCheck c : data.checks().simulation) {
            try {
                c.onSimulation(result);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Ошибка проверки " + c.getClass().getSimpleName(), t);
            }
        }
        // Честный тик — запоминаем позицию для отката.
        if (snapshot.positionChanged() && !data.hasSetbackRequest()
                && (!result.skip || snapshot.teleportEcho() != null || result.skipReason.equals("транспорт"))) {
            data.sim().lastValid = new org.bukkit.Location(player.getWorld(), snapshot.x(), snapshot.y(), snapshot.z(),
                    snapshot.yaw(), snapshot.pitch());
        }
    }

    /** Выполняет запрошенный откат позиции (главный поток). */
    public void processSetback(PlayerData data) {
        String reason = data.pollSetbackRequest();
        if (reason == null) return;
        Player player = data.player();
        if (player == null || !player.isOnline() || player.isInsideVehicle()) return;
        SimState s = data.sim();
        Location target = s.lastValid;
        if (target == null || target.getWorld() == null || !target.getWorld().equals(player.getWorld())) return;
        long now = System.currentTimeMillis();
        if (now - s.setbackMinuteStart > 60_000) {
            s.setbackMinuteStart = now;
            s.setbacksThisMinute = 0;
        }
        if (s.setbacksThisMinute >= plugin.config().maxSetbacksPerMinute) return;
        if (now - s.lastSetbackMillis < 50) return;
        s.setbacksThisMinute++;
        s.lastSetbackMillis = now;
        Location to = target.clone();
        to.setYaw(player.getLocation().getYaw());
        to.setPitch(player.getLocation().getPitch());
        player.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (plugin.config().debug) {
            plugin.getLogger().info("Откат " + data.name() + " (" + reason + ")");
        }
    }
}
