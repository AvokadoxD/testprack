package ru.strazh.ac.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Глобальные настройки плагина (config.yml). */
public final class StrazhConfig {

    private final StrazhPlugin plugin;
    private FileConfiguration config;
    private final Map<String, CheckSettings> checkSettings = new ConcurrentHashMap<>();

    // ---- сообщения/алерты ----
    public String prefix;
    public String alertFormat;
    public String alertHover;
    public boolean alertConsole;
    public long alertCooldownMs;
    public String discordWebhook;
    public String kickMessage;

    // ---- наказания ----
    public final Map<Category, List<String>> punishCommands = new EnumMap<>(Category.class);
    public List<String> defaultPunishCommands;
    public int minPlaytimeSecondsForPunish;
    public boolean resetVlAfterPunish;

    // ---- логи ----
    public boolean logEnabled;
    public int logKeepDays;

    // ---- транзакции/лаг ----
    public int transactionTimeoutSeconds;
    public double maxPongHoldMs;
    public int maxTicksWithoutPong;

    // ---- откаты ----
    public boolean setbackEnabled;
    public int maxSetbacksPerMinute;

    // ---- смягчение (mitigation) ----
    public boolean mitigationEnabled;
    public double mitigationCancelChance;

    // ---- исключения ----
    public String bypassPermission;
    public String exemptTag;
    public boolean categoryTags;
    public int joinExemptTicks;
    public boolean exemptBedrock;
    public boolean bedrockSoftMode;

    // ---- транспорт / машины ----
    public Set<String> carTags = new HashSet<>();
    public boolean exemptNonVanillaVehicles;
    public int dismountGraceTicks;
    public int vehicleTeleportGraceTicks;

    // ---- ловушки ----
    public boolean honeypotEnabled;

    // ---- прочее ----
    public boolean debug;

    public StrazhConfig(StrazhPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        checkSettings.clear();

        prefix = config.getString("prefix", "&8[&cСтраж&8] ");
        alertFormat = config.getString("alerts.format",
                "{prefix}&f{player} &7провалил &c{check} &8(&7{category}&8) &7x&c{vl}");
        alertHover = config.getString("alerts.hover",
                "&7Проверка: &f{check}\n&7Описание: &f{description}\n&7Детали: &f{details}\n&7Пинг: &f{ping} мс &7| TPS: &f{tps}\n&7Клиент: &f{brand} &7({version})\n&eНажмите, чтобы телепортироваться");
        alertConsole = config.getBoolean("alerts.console", true);
        alertCooldownMs = config.getLong("alerts.cooldown-ms", 0L);
        discordWebhook = config.getString("alerts.discord-webhook", "");
        kickMessage = config.getString("messages.kick", "&cВы были отключены античитом.\n&7Причина: &f{check}");

        defaultPunishCommands = config.getStringList("punishments.default");
        if (defaultPunishCommands.isEmpty()) {
            defaultPunishCommands = List.of("strazh kick {player} {check}");
        }
        punishCommands.clear();
        for (Category c : Category.values()) {
            List<String> list = config.getStringList("punishments." + c.key());
            if (!list.isEmpty()) punishCommands.put(c, list);
        }
        minPlaytimeSecondsForPunish = config.getInt("punishments.min-playtime-seconds", 0);
        resetVlAfterPunish = config.getBoolean("punishments.reset-vl-after-punish", true);

        logEnabled = config.getBoolean("logging.enabled", true);
        logKeepDays = config.getInt("logging.keep-days", 14);

        transactionTimeoutSeconds = config.getInt("transactions.timeout-seconds", 30);
        maxPongHoldMs = config.getDouble("transactions.max-pong-hold-ms", 2000.0);
        maxTicksWithoutPong = config.getInt("transactions.max-ticks-without-pong", 40);

        setbackEnabled = config.getBoolean("setback.enabled", true);
        maxSetbacksPerMinute = config.getInt("setback.max-per-minute", 60);

        mitigationEnabled = config.getBoolean("mitigation.enabled", true);
        mitigationCancelChance = config.getDouble("mitigation.cancel-chance", 0.5);

        bypassPermission = config.getString("exemptions.bypass-permission", "strazh.bypass");
        exemptTag = config.getString("exemptions.scoreboard-tag", "strazh_exempt");
        categoryTags = config.getBoolean("exemptions.category-tags", true);
        joinExemptTicks = config.getInt("exemptions.join-ticks", 60);
        exemptBedrock = config.getBoolean("exemptions.bedrock.enabled", true);
        bedrockSoftMode = config.getBoolean("exemptions.bedrock.soft-mode", true);

        carTags = new HashSet<>();
        for (String t : config.getStringList("vehicles.seat-tags")) {
            carTags.add(t.toLowerCase(Locale.ROOT));
        }
        if (carTags.isEmpty()) {
            carTags.addAll(List.of("car_seat", "car_main", "moped_main"));
        }
        exemptNonVanillaVehicles = config.getBoolean("vehicles.exempt-display-vehicles", true);
        dismountGraceTicks = config.getInt("vehicles.dismount-grace-ticks", 40);
        vehicleTeleportGraceTicks = config.getInt("vehicles.teleport-grace-ticks", 10);

        honeypotEnabled = config.getBoolean("honeypot.enabled", true);

        debug = config.getBoolean("debug", false);
    }

    /** Настройки проверки (создаются при первом обращении). */
    public CheckSettings settings(CheckInfo info) {
        String id = Check.idOf(info);
        return checkSettings.computeIfAbsent(id, k -> {
            ConfigurationSection s = config.getConfigurationSection("checks." + k);
            return new CheckSettings(k, info, s);
        });
    }

    public List<String> punishCommands(Category category, CheckSettings s) {
        if (s.commands() != null) return s.commands();
        return punishCommands.getOrDefault(category, defaultPunishCommands);
    }

    /** Дописывает в config.yml отсутствующие ключи проверок со значениями по умолчанию. */
    public void saveMissingDefaults() {
        boolean changed = false;
        List<String> ids = new ArrayList<>(checkSettings.keySet());
        ids.sort(String::compareTo);
        for (String id : ids) {
            CheckSettings s = checkSettings.get(id);
            for (Map.Entry<String, Object> e : s.missing().entrySet()) {
                String path = "checks." + id + "." + e.getKey();
                if (!config.contains(path)) {
                    config.set(path, e.getValue());
                    changed = true;
                }
            }
        }
        if (changed) {
            try {
                File f = new File(plugin.getDataFolder(), "config.yml");
                if (config instanceof YamlConfiguration y) {
                    y.save(f);
                } else {
                    plugin.saveConfig();
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Не удалось сохранить config.yml: " + ex.getMessage());
            }
        }
    }

    public FileConfiguration raw() {
        return config;
    }
}
