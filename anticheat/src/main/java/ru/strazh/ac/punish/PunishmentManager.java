package ru.strazh.ac.punish;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.api.event.StrazhPunishEvent;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Выполнение команд наказания (в главном потоке). */
public final class PunishmentManager {

    private final StrazhPlugin plugin;
    private final Map<UUID, Long> lastPunish = new ConcurrentHashMap<>();

    public PunishmentManager(StrazhPlugin plugin) {
        this.plugin = plugin;
    }

    public void punish(PlayerData data, Check check, double vl) {
        long now = System.currentTimeMillis();
        Long last = lastPunish.get(data.uuid());
        if (last != null && now - last < 3000) {
            return; // не наказываем дважды подряд за одну серию флагов
        }
        lastPunish.put(data.uuid(), now);
        Bukkit.getScheduler().runTask(plugin, () -> execute(data, check, vl));
    }

    private void execute(PlayerData data, Check check, double vl) {
        Player player = data.player();
        if (player == null || !player.isOnline()) return;
        var cfg = plugin.config();
        long playedSeconds = (System.currentTimeMillis() - data.client().joinMillis) / 1000;
        if (playedSeconds < cfg.minPlaytimeSecondsForPunish) return;
        List<String> commands = new ArrayList<>();
        for (String c : cfg.punishCommands(check.category(), check.settings())) {
            commands.add(c.replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{check}", check.displayName())
                    .replace("{check_id}", check.id())
                    .replace("{category}", check.category().display())
                    .replace("{vl}", Text.fmt(vl, 1)));
        }
        StrazhPunishEvent event = new StrazhPunishEvent(player, check.displayName(), vl, commands);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        for (String cmd : event.getCommands()) {
            String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            } catch (Exception ex) {
                plugin.getLogger().warning("Ошибка команды наказания '" + c + "': " + ex.getMessage());
            }
        }
        plugin.alerts().broadcast("&c" + player.getName() + " &7наказан за &c" + check.displayName()
                + " &8(x" + Text.fmt(vl, 1) + ")");
        plugin.violationLog().log("PUNISH " + player.getName() + " " + check.displayName() + " vl=" + Text.fmt(vl, 1)
                + " cmds=" + event.getCommands());
        if (cfg.resetVlAfterPunish) {
            check.resetVl();
        }
    }

    public void forget(UUID uuid) {
        lastPunish.remove(uuid);
    }
}
