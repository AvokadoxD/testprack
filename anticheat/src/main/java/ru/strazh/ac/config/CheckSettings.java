package ru.strazh.ac.config;

import org.bukkit.configuration.ConfigurationSection;
import ru.strazh.ac.check.CheckInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Настройки одной проверки (общие для всех игроков). Неизменяемые, кроме
 * реестра значений по умолчанию, который нужен для автодополнения config.yml.
 */
public final class CheckSettings {

    private final String id;
    private final boolean enabled;
    private final boolean punish;
    private final double maxVl;
    private final double decay;
    private final double alertVl;
    private final boolean setback;
    private final List<String> commands;
    private final Map<String, Object> values;
    /** Ключи, которых не было в конфиге: будут дописаны со значениями по умолчанию. */
    private final Map<String, Object> missing = new ConcurrentHashMap<>();

    public CheckSettings(String id, CheckInfo info, ConfigurationSection section) {
        this.id = id;
        boolean defPunish = info.punish() && !info.experimental();
        if (section == null) {
            enabled = true;
            punish = defPunish;
            maxVl = info.maxVl();
            decay = info.decay();
            alertVl = info.alertVl();
            setback = info.setback();
            commands = null;
            values = Map.of();
            missing.put("enabled", true);
            missing.put("punish", defPunish);
            missing.put("max-vl", info.maxVl());
            missing.put("decay", info.decay());
            missing.put("alert-vl", info.alertVl());
            if (info.setback()) missing.put("setback", true);
            return;
        }
        enabled = read(section, "enabled", true);
        punish = read(section, "punish", defPunish);
        maxVl = readD(section, "max-vl", info.maxVl());
        decay = readD(section, "decay", info.decay());
        alertVl = readD(section, "alert-vl", info.alertVl());
        setback = read(section, "setback", info.setback());
        commands = section.isList("commands") ? List.copyOf(section.getStringList("commands")) : null;
        Map<String, Object> v = new ConcurrentHashMap<>();
        for (String key : section.getKeys(false)) {
            Object o = section.get(key);
            if (o != null && !(o instanceof ConfigurationSection)) {
                v.put(key, o);
            }
        }
        values = v;
    }

    private boolean read(ConfigurationSection s, String key, boolean def) {
        if (!s.contains(key)) {
            missing.put(key, def);
            return def;
        }
        return s.getBoolean(key, def);
    }

    private double readD(ConfigurationSection s, String key, double def) {
        if (!s.contains(key)) {
            missing.put(key, def);
            return def;
        }
        return s.getDouble(key, def);
    }

    public double getDouble(String key, double def) {
        Object o = values.get(key);
        if (o instanceof Number n) return n.doubleValue();
        missing.putIfAbsent(key, def);
        return def;
    }

    public int getInt(String key, int def) {
        Object o = values.get(key);
        if (o instanceof Number n) return n.intValue();
        missing.putIfAbsent(key, def);
        return def;
    }

    public boolean getBoolean(String key, boolean def) {
        Object o = values.get(key);
        if (o instanceof Boolean b) return b;
        missing.putIfAbsent(key, def);
        return def;
    }

    public String getString(String key, String def) {
        Object o = values.get(key);
        if (o instanceof String s) return s;
        missing.putIfAbsent(key, def);
        return def;
    }

    public String id() {
        return id;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean punish() {
        return punish;
    }

    public double maxVl() {
        return maxVl;
    }

    public double decay() {
        return decay;
    }

    public double alertVl() {
        return alertVl;
    }

    public boolean setback() {
        return setback;
    }

    /** Команды наказания этой проверки или null — брать из категории. */
    public List<String> commands() {
        return commands;
    }

    public Map<String, Object> missing() {
        return missing;
    }
}
