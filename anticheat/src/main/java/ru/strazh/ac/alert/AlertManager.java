package ru.strazh.ac.alert;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.util.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Уведомления персонала о нарушениях. Потокобезопасно. */
public final class AlertManager {

    private final StrazhPlugin plugin;
    /** Игроки с включёнными алертами. */
    private final Map<UUID, Player> receivers = new ConcurrentHashMap<>();
    /** Игроки, получающие подробный вывод (verbose) — все флаги без кулдауна. */
    private final Set<UUID> verbose = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> webhookQueue = new ConcurrentLinkedQueue<>();
    private HttpClient http;

    public AlertManager(StrazhPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean toggle(Player p) {
        if (receivers.remove(p.getUniqueId()) != null) {
            return false;
        }
        receivers.put(p.getUniqueId(), p);
        return true;
    }

    public void enable(Player p) {
        receivers.put(p.getUniqueId(), p);
    }

    public void disable(Player p) {
        receivers.remove(p.getUniqueId());
        verbose.remove(p.getUniqueId());
    }

    public boolean toggleVerbose(Player p) {
        if (!verbose.remove(p.getUniqueId())) {
            verbose.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    public boolean hasAlerts(Player p) {
        return receivers.containsKey(p.getUniqueId());
    }

    public void alert(PlayerData data, Check check, double vl, String details) {
        var cfg = plugin.config();
        String key = data.uuid() + ":" + check.id();
        long now = System.currentTimeMillis();
        boolean cooled = true;
        if (cfg.alertCooldownMs > 0) {
            Long last = cooldowns.get(key);
            cooled = last == null || now - last >= cfg.alertCooldownMs;
            if (cooled) cooldowns.put(key, now);
        }
        String name = data.name();
        String line = cfg.alertFormat
                .replace("{prefix}", cfg.prefix)
                .replace("{player}", name)
                .replace("{check}", check.displayName())
                .replace("{category}", check.category().display())
                .replace("{vl}", Text.fmt(vl, 1))
                .replace("{details}", details == null ? "" : details);
        String hover = cfg.alertHover
                .replace("{check}", check.displayName())
                .replace("{description}", check.info().description())
                .replace("{details}", details == null ? "-" : details)
                .replace("{ping}", String.valueOf(Math.round(data.ping())))
                .replace("{tps}", Text.fmt(Bukkit.getTPS()[0], 1))
                .replace("{brand}", data.client().brand)
                .replace("{version}", data.client().version.getReleaseName())
                .replace("{vl}", Text.fmt(vl, 1));
        Component msg = Text.color(line)
                .hoverEvent(HoverEvent.showText(Text.color(hover)))
                .clickEvent(ClickEvent.runCommand("/tp " + name));
        for (Player p : receivers.values()) {
            if (!p.isOnline()) {
                receivers.remove(p.getUniqueId());
                continue;
            }
            if (cooled || verbose.contains(p.getUniqueId())) {
                p.sendMessage(msg);
            }
        }
        if (cooled && cfg.alertConsole) {
            plugin.getLogger().info(name + " провалил " + check.displayName() + " (x" + Text.fmt(vl, 1) + ") " + details);
        }
        if (cooled && cfg.discordWebhook != null && !cfg.discordWebhook.isBlank()) {
            webhookQueue.add("**" + name + "** провалил **" + check.displayName() + "** x" + Text.fmt(vl, 1)
                    + " — " + (details == null ? "" : details.replace("`", "'")));
        }
    }

    /** Сообщение всем получателям алертов (например, о наказании). */
    public void broadcast(String legacy) {
        Component c = Text.color(plugin.config().prefix + legacy);
        for (Player p : receivers.values()) {
            if (p.isOnline()) p.sendMessage(c);
        }
    }

    /** Отправка накопленных сообщений в Discord (вызывается раз в несколько секунд асинхронно). */
    public void flushWebhook() {
        String url = plugin.config().discordWebhook;
        if (url == null || url.isBlank() || webhookQueue.isEmpty()) {
            webhookQueue.clear();
            return;
        }
        StringBuilder sb = new StringBuilder();
        String s;
        while ((s = webhookQueue.poll()) != null && sb.length() < 1800) {
            sb.append(s).append('\n');
        }
        if (sb.isEmpty()) return;
        try {
            if (http == null) {
                http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            }
            String json = "{\"username\":\"Страж\",\"content\":\"" + escapeJson(sb.toString()) + "\"}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            http.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            plugin.getLogger().fine("Discord webhook: " + ex.getMessage());
        }
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> {
                }
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    public void clearCooldowns(UUID uuid) {
        cooldowns.keySet().removeIf(k -> k.startsWith(uuid.toString()));
    }
}
