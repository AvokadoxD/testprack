package ru.strazh.ac.punish;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.api.event.StrazhFlagEvent;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.config.CheckSettings;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.util.Text;

/** Единая точка обработки флагов проверок (любой поток). */
public final class ViolationManager {

    private final StrazhPlugin plugin;

    public ViolationManager(StrazhPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(Check check, double weight, String details) {
        PlayerData data = check.data();
        CheckSettings s = check.settings();
        if (s == null || !s.enabled()) return false;
        if (data.isExempt(check)) return false;
        Player player = data.player();
        if (player == null) return false;
        if (data.client().bedrock && plugin.config().exemptBedrock && plugin.config().bedrockSoftMode
                && (check.category() == Category.MOVEMENT || check.category() == Category.COMBAT)) {
            // Bedrock-клиенты (Geyser) имеют другую физику и ввод: только лог.
            plugin.violationLog().log("[bedrock] " + data.name() + " " + check.displayName() + " " + details);
            return false;
        }
        double vl = check.addVl(weight);
        StrazhFlagEvent event = new StrazhFlagEvent(!Bukkit.isPrimaryThread(), player, check.name(), check.type(),
                check.category().name(), vl, details == null ? "" : details);
        try {
            Bukkit.getPluginManager().callEvent(event);
        } catch (IllegalStateException ignored) {
            // вызов из неподходящего потока — просто не уведомляем API
        }
        if (event.isCancelled()) {
            check.removeVl(weight);
            return false;
        }
        if (check.category() == Category.COMBAT) {
            data.addCombatSuspicion(Math.min(2.0, weight));
        }
        if (vl >= s.alertVl()) {
            plugin.alerts().alert(data, check, vl, details);
        }
        plugin.violationLog().log(data.name() + " " + check.displayName() + " vl=" + Text.fmt(vl, 2)
                + " ping=" + Math.round(data.ping()) + " " + (details == null ? "" : details));
        if (s.punish() && vl >= s.maxVl()) {
            plugin.punishments().punish(data, check, vl);
        }
        return true;
    }
}
