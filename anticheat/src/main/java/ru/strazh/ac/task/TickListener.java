package ru.strazh.ac.task;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.check.hook.ServerTickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.util.Text;

/**
 * Серверный тик: в начале — обработка очередей пакетов в главном потоке,
 * откаты и тиковые проверки; в конце — отправка транзакции каждому игроку.
 */
public final class TickListener implements Listener {

    private final StrazhPlugin plugin;

    public TickListener(StrazhPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTickStart(ServerTickStartEvent event) {
        int tick = event.getTickNumber();
        boolean slow = tick % 20 == 0;
        for (PlayerData data : plugin.players().all()) {
            Player p = data.player();
            if (p == null) continue;
            data.drainMainQueue();
            plugin.simulation().processSetback(data);
            if (data.checks() != null) {
                for (ServerTickCheck c : data.checks().serverTick) {
                    try {
                        c.onServerTick(tick);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Ошибка проверки " + c.getClass().getSimpleName() + ": " + t);
                    }
                }
            }
            if (slow) {
                plugin.bukkitListener().refreshCaches(data);
                checkTimeout(data, p);
            }
        }
        plugin.sizeResolver().tick();
        plugin.honeypots().tick(tick);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(ServerTickEndEvent event) {
        for (PlayerData data : plugin.players().all()) {
            if (data.player() != null) {
                data.transactions().send();
            }
        }
    }

    private void checkTimeout(PlayerData data, Player p) {
        double pendingMs = data.transactions().oldestPendingAgeMs();
        int limit = plugin.config().transactionTimeoutSeconds;
        if (limit > 0 && pendingMs > limit * 1000.0) {
            plugin.getLogger().info(p.getName() + " не отвечает на транзакции " + Math.round(pendingMs) + " мс — отключение");
            p.kick(Component.text().append(Text.color("&cТайм-аут соединения (Strazh)")).build());
        }
    }
}
