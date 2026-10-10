package ru.strazh.ac.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /strazh — управление античитом. */
public final class StrazhCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "alerts", "verbose", "info", "vl", "exempt", "unexempt", "resetvl", "checks", "top", "reload", "kick", "help");

    private final StrazhPlugin plugin;

    public StrazhCommand(StrazhPlugin plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String text) {
        s.sendMessage(Text.color(plugin.config().prefix + text));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("strazh.command." + sub) && !sender.hasPermission("strazh.admin")) {
            msg(sender, "&cНедостаточно прав.");
            return true;
        }
        switch (sub) {
            case "alerts" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "&cТолько для игроков.");
                    return true;
                }
                boolean on = plugin.alerts().toggle(p);
                msg(sender, on ? "&aАлерты включены." : "&7Алерты выключены.");
            }
            case "verbose" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "&cТолько для игроков.");
                    return true;
                }
                if (!plugin.alerts().hasAlerts(p)) plugin.alerts().enable(p);
                boolean on = plugin.alerts().toggleVerbose(p);
                msg(sender, on ? "&aПодробный режим: все флаги без задержки." : "&7Подробный режим выключен.");
            }
            case "info", "vl" -> info(sender, args);
            case "exempt" -> exempt(sender, args);
            case "unexempt" -> {
                PlayerData d = target(sender, args, 1);
                if (d == null) return true;
                String key = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "all";
                if (key.equals("all")) d.exemptions().clear();
                else d.exemptions().unexempt(key);
                msg(sender, "&aИсключение снято: &f" + d.name() + " &7(" + key + ")");
            }
            case "resetvl" -> {
                PlayerData d = target(sender, args, 1);
                if (d == null) return true;
                for (Check c : d.checks().all) c.resetVl();
                msg(sender, "&aVL сброшены: &f" + d.name());
            }
            case "checks" -> {
                Map<Category, List<String>> byCat = new java.util.EnumMap<>(Category.class);
                for (Check c : plugin.checkRegistry().prototypes()) {
                    byCat.computeIfAbsent(c.category(), k -> new ArrayList<>())
                            .add((c.isEnabled() ? "&a" : "&c") + c.displayName());
                }
                msg(sender, "&fПроверок: &c" + plugin.checkRegistry().count());
                for (var e : byCat.entrySet()) {
                    sender.sendMessage(Text.color(" &7" + e.getKey().display() + ": " + String.join("&7, ", e.getValue())));
                }
            }
            case "top" -> {
                List<Object[]> rows = new ArrayList<>();
                for (PlayerData d : plugin.players().all()) {
                    if (d.checks() == null || d.player() == null) continue;
                    double total = 0;
                    for (Check c : d.checks().all) total += c.vl();
                    if (total > 0.5) rows.add(new Object[]{d.name(), total});
                }
                rows.sort(Comparator.comparingDouble((Object[] r) -> (double) r[1]).reversed());
                msg(sender, "&fСамые подозрительные игроки:");
                for (int i = 0; i < Math.min(10, rows.size()); i++) {
                    sender.sendMessage(Text.color(" &7" + (i + 1) + ". &f" + rows.get(i)[0] + " &8— &c" + Text.fmt((double) rows.get(i)[1], 1)));
                }
                if (rows.isEmpty()) sender.sendMessage(Text.color(" &7Нарушений нет."));
            }
            case "reload" -> {
                plugin.reloadAll();
                msg(sender, "&aКонфигурация перезагружена.");
            }
            case "kick" -> {
                if (args.length < 2) {
                    msg(sender, "&7/strazh kick <игрок> [причина]");
                    return true;
                }
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null) {
                    msg(sender, "&cИгрок не найден.");
                    return true;
                }
                String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "нечестная игра";
                p.kick(Text.color(plugin.config().kickMessage.replace("{check}", reason).replace("\\n", "\n")));
            }
            default -> {
                msg(sender, "&fStrazhAC &7" + plugin.getPluginMeta().getVersion());
                sender.sendMessage(Text.color(" &c/strazh alerts &7— вкл/выкл алерты"));
                sender.sendMessage(Text.color(" &c/strazh verbose &7— все флаги без задержки"));
                sender.sendMessage(Text.color(" &c/strazh info <игрок> &7— клиент, пинг, нарушения"));
                sender.sendMessage(Text.color(" &c/strazh exempt <игрок> <секунд|0> [all|категория|проверка] &7— исключение"));
                sender.sendMessage(Text.color(" &c/strazh unexempt <игрок> [ключ] &7— снять исключение"));
                sender.sendMessage(Text.color(" &c/strazh resetvl <игрок> &7— сбросить нарушения"));
                sender.sendMessage(Text.color(" &c/strazh checks &7— список проверок"));
                sender.sendMessage(Text.color(" &c/strazh top &7— самые подозрительные"));
                sender.sendMessage(Text.color(" &c/strazh reload &7— перезагрузить конфиг"));
            }
        }
        return true;
    }

    private PlayerData target(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            msg(sender, "&cУкажите игрока.");
            return null;
        }
        Player p = Bukkit.getPlayerExact(args[index]);
        PlayerData d = p == null ? null : plugin.players().get(p);
        if (d == null) {
            msg(sender, "&cИгрок не найден.");
        }
        return d;
    }

    private void info(CommandSender sender, String[] args) {
        PlayerData d = target(sender, args, 1);
        if (d == null) return;
        Player p = d.player();
        msg(sender, "&f" + d.name());
        sender.sendMessage(Text.color(" &7Клиент: &f" + d.client().brand + " &8(" + d.client().version.getReleaseName()
                + (d.client().bedrock ? ", Bedrock" : "") + ")"));
        sender.sendMessage(Text.color(" &7Пинг: &fkeepalive " + (p == null ? "?" : p.getPing()) + " мс, транзакции "
                + Math.round(d.transactions().smoothedRttMs()) + " мс (±" + Math.round(d.transactions().rttStdDev()) + ")"));
        sender.sendMessage(Text.color(" &7Транспорт: &f" + (d.vehicle().inVehicle() ? d.vehicle().vehicleType
                + (d.vehicle().onCustomVehicle ? " (кастомный)" : "") : "нет")));
        if (!d.client().channels.isEmpty()) {
            List<String> ch = new ArrayList<>(d.client().channels);
            sender.sendMessage(Text.color(" &7Каналы: &f" + String.join(", ", ch.subList(0, Math.min(12, ch.size())))));
        }
        var ex = d.exemptions().active();
        if (!ex.isEmpty() || d.exemptions().bypassPermission) {
            sender.sendMessage(Text.color(" &7Исключения: &f" + ex.keySet() + (d.exemptions().bypassPermission ? " +право обхода" : "")));
        }
        List<Check> flagged = new ArrayList<>();
        for (Check c : d.checks().all) {
            if (c.vl() >= 0.05) flagged.add(c);
        }
        flagged.sort(Comparator.comparingDouble(Check::vl).reversed());
        if (flagged.isEmpty()) {
            sender.sendMessage(Text.color(" &aНарушений нет."));
        } else {
            for (Check c : flagged) {
                sender.sendMessage(Text.color(" &c" + c.displayName() + " &8— &f" + Text.fmt(c.vl(), 1)
                        + " &8/ " + Text.fmt(c.settings().maxVl(), 0)));
            }
        }
    }

    private void exempt(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg(sender, "&7/strazh exempt <игрок> <секунд, 0 = навсегда> [all|movement|combat|world|player|packet|misc|id_проверки]");
            return;
        }
        PlayerData d = target(sender, args, 1);
        if (d == null) return;
        long seconds;
        try {
            seconds = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            msg(sender, "&cНеверное число секунд.");
            return;
        }
        String key = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "all";
        d.exemptions().exempt(key, seconds * 1000L);
        msg(sender, "&aИсключение: &f" + d.name() + " &7(" + key + ", " + (seconds <= 0 ? "навсегда" : seconds + " с") + ")");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("checks") && !args[0].equalsIgnoreCase("top")
                && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("alerts")) {
            String pre = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pre)).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("exempt")) {
            List<String> keys = new ArrayList<>(List.of("all", "movement", "combat", "world", "player", "packet", "misc"));
            return keys.stream().filter(s -> s.startsWith(args[3].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unused")
    private static Component empty() {
        return Component.empty();
    }
}
