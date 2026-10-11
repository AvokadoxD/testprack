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
        var cfg = plugin.config();
        int delaySec = cfg.punishDelayMax <= 0 ? 0 : cfg.punishDelayMin
                + java.util.concurrent.ThreadLocalRandom.current().nextInt(cfg.punishDelayMax - cfg.punishDelayMin + 1);
        if (delaySec <= 0) {
            Bukkit.getScheduler().runTask(plugin, () -> execute(data, check, vl));
        } else {
            // Отложенное наказание: разработчику чита сложнее понять, какой модуль палится.
            lastPunish.put(data.uuid(), now + delaySec * 1000L);
            plugin.alerts().broadcast("&7" + data.name() + " &8— наказание за &c" + check.displayName()
                    + " &8через &f" + delaySec + " с");
            Bukkit.getScheduler().runTaskLater(plugin, () -> execute(data, check, vl), delaySec * 20L);
        }
    }

    private void execute(PlayerData data, Check check, double vl) {
        Player player = data.player();
        boolean online = player != null && player.isOnline();
        // Отложенное наказание выполняется и после выхода игрока (бан по нику/UUID работает офлайн).
        if (!online && plugin.config().punishDelayMax <= 0) return;
        String name = data.name();
        String uuid = data.uuid().toString();
        var cfg = plugin.config();
        long playedSeconds = (System.currentTimeMillis() - data.client().joinMillis) / 1000;
        if (playedSeconds < cfg.minPlaytimeSecondsForPunish) return;
        List<String> commands = new ArrayList<>();
        for (String c : cfg.punishCommands(check.category(), check.settings())) {
            commands.add(c.replace("{player}", name)
                    .replace("{uuid}", uuid)
                    .replace("{check}", check.displayName())
                    .replace("{check_id}", check.id())
                    .replace("{category}", check.category().display())
                    .replace("{vl}", Text.fmt(vl, 1)));
        }
        List<String> toRun = commands;
        if (online) {
            StrazhPunishEvent event = new StrazhPunishEvent(player, check.displayName(), vl, commands);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return;
            toRun = event.getCommands();
        }
        for (String cmd : toRun) {
            String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            } catch (Exception ex) {
                plugin.getLogger().warning("Ошибка команды наказания '" + c + "': " + ex.getMessage());
            }
        }
        plugin.alerts().broadcast("&c" + name + " &7наказан за &c" + check.displayName()
                + " &8(x" + Text.fmt(vl, 1) + ")");
        plugin.violationLog().log("PUNISH " + name + " " + check.displayName() + " vl=" + Text.fmt(vl, 1)
                + " cmds=" + toRun);
        if (cfg.resetVlAfterPunish) {
            check.resetVl();
        }
    }

    public void forget(UUID uuid) {
        lastPunish.remove(uuid);
    }
}
