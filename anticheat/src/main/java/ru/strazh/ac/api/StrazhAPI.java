package ru.strazh.ac.api;

import org.bukkit.entity.Player;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.player.PlayerData;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Публичный API для других плагинов и Skript (через skript-reflect):
 *
 * <pre>
 * import:
 *     ru.strazh.ac.api.StrazhAPI
 * ...
 * StrazhAPI.exempt(player, "movement", 3000)   # исключить движение на 3 секунды
 * StrazhAPI.exempt(player, "all", 0)           # исключить всё до снятия
 * StrazhAPI.unexempt(player, "all")
 * </pre>
 *
 * <p>Также можно без Java: добавить игроку тэг скорборда {@code strazh_exempt}
 * (полное исключение) или {@code strazh_exempt_movement}, {@code strazh_exempt_combat}
 * и т.д. (по категориям), либо {@code strazh_exempt_<id проверки>}.
 */
public final class StrazhAPI {

    private StrazhAPI() {
    }

    private static PlayerData data(Player player) {
        StrazhPlugin plugin = StrazhPlugin.get();
        return plugin == null || player == null ? null : plugin.players().get(player);
    }

    /**
     * Временно исключить игрока из проверок.
     * @param key        "all", категория ("movement", "combat", "world", "player", "packet", "misc") или id проверки ("reach_a")
     * @param durationMs длительность в мс; 0 или меньше — до вызова {@link #unexempt}
     */
    public static void exempt(Player player, String key, long durationMs) {
        PlayerData d = data(player);
        if (d != null) d.exemptions().exempt(key.toLowerCase(Locale.ROOT), durationMs);
    }

    public static void unexempt(Player player, String key) {
        PlayerData d = data(player);
        if (d == null) return;
        if (key.equalsIgnoreCase("all")) d.exemptions().clear();
        else d.exemptions().unexempt(key.toLowerCase(Locale.ROOT));
    }

    /** Исключён ли игрок по ключу ("all" — полностью). */
    public static boolean isExempt(Player player, String key) {
        PlayerData d = data(player);
        if (d == null) return false;
        if (key.equalsIgnoreCase("all")) return d.isFullyExempt();
        return d.exemptions().active().containsKey(key.toLowerCase(Locale.ROOT));
    }

    /** Текущие уровни нарушений по проверкам (только ненулевые). */
    public static Map<String, Double> getViolations(Player player) {
        Map<String, Double> out = new LinkedHashMap<>();
        PlayerData d = data(player);
        if (d == null || d.checks() == null) return out;
        for (Check c : d.checks().all) {
            double vl = c.vl();
            if (vl > 0) out.put(c.id(), vl);
        }
        return out;
    }

    public static void resetViolations(Player player) {
        PlayerData d = data(player);
        if (d == null || d.checks() == null) return;
        for (Check c : d.checks().all) c.resetVl();
    }

    /** Пинг по транзакциям, мс. */
    public static double getTransactionPing(Player player) {
        PlayerData d = data(player);
        return d == null ? -1 : d.transactions().smoothedRttMs();
    }

    /** Бренд клиента (как его сообщил клиент). */
    public static String getClientBrand(Player player) {
        PlayerData d = data(player);
        return d == null ? "unknown" : d.client().brand;
    }
}
