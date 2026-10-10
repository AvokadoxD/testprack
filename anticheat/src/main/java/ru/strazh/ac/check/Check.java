package ru.strazh.ac.check;

import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.config.CheckSettings;
import ru.strazh.ac.player.PlayerData;

import java.util.Locale;

/**
 * Базовый класс проверки. Экземпляр создаётся на каждого игрока.
 *
 * <p>Правила для наследников:
 * <ul>
 *   <li>Конструктор принимает только {@link PlayerData} и не обращается к нему
 *       (для генерации конфига создаётся прототип с data == null).</li>
 *   <li>Параметры читаются в {@link #onReload()} через cfg*(ключ, значение по умолчанию)
 *       и сохраняются в поля.</li>
 *   <li>Пакетные хуки вызываются в event loop канала игрока, Bukkit-хуки и
 *       симуляция — в главном потоке.</li>
 * </ul>
 */
public abstract class Check {

    protected final PlayerData data;
    private final CheckInfo info;
    private final String id;
    private volatile CheckSettings settings;

    private double vl;
    private long lastDecayMs = System.currentTimeMillis();
    private long lastFlagMs;
    private int flags;
    /** Буфер для накопления подозрений перед флагом. */
    protected double buffer;

    protected Check(PlayerData data) {
        this.data = data;
        this.info = getClass().getAnnotation(CheckInfo.class);
        if (info == null) {
            throw new IllegalStateException("Нет @CheckInfo у " + getClass().getName());
        }
        this.id = idOf(info);
    }

    public static String idOf(CheckInfo info) {
        return (info.name() + "_" + info.type()).toLowerCase(Locale.ROOT);
    }

    /** Применяет настройки (при создании и /strazh reload). */
    public final void applySettings(CheckSettings s) {
        this.settings = s;
        onReload();
    }

    /** Чтение параметров проверки из конфига в поля. */
    protected void onReload() {
    }

    // ---------- настройки ----------

    protected final double cfg(String key, double def) {
        return settings.getDouble(key, def);
    }

    protected final int cfg(String key, int def) {
        return settings.getInt(key, def);
    }

    protected final boolean cfg(String key, boolean def) {
        return settings.getBoolean(key, def);
    }

    protected final String cfg(String key, String def) {
        return settings.getString(key, def);
    }

    // ---------- флаги ----------

    /** Зафиксировать нарушение с весом 1. */
    protected final boolean flag(String details) {
        return flag(1.0, details);
    }

    /**
     * Зафиксировать нарушение.
     * @return true, если нарушение засчитано (проверка включена, игрок не в исключении,
     * событие API не отменено)
     */
    protected final boolean flag(double weight, String details) {
        if (data == null) {
            return false;
        }
        StrazhPlugin plugin = StrazhPlugin.get();
        if (plugin == null) {
            // Юнит-тесты без сервера.
            return TestHooks.onFlag(this, weight, details);
        }
        return plugin.violations().handle(this, weight, details);
    }

    /** Флаг + откат позиции (если setback включён в конфиге для этой проверки). */
    protected final boolean flagSetback(double weight, String details) {
        boolean flagged = flag(weight, details);
        if (flagged && settings != null && settings.setback()) {
            data.requestSetback(name() + type());
        }
        return flagged;
    }

    protected final double increaseBuffer(double amount, double max) {
        buffer = Math.min(max, buffer + amount);
        return buffer;
    }

    protected final double increaseBuffer(double amount) {
        return increaseBuffer(amount, Double.MAX_VALUE);
    }

    protected final double decreaseBuffer(double amount) {
        buffer = Math.max(0.0, buffer - amount);
        return buffer;
    }

    protected final void resetBuffer() {
        buffer = 0.0;
    }

    /** Вызывается ViolationManager под синхронизацией. Возвращает новый VL. */
    public final synchronized double addVl(double weight) {
        decay();
        vl += weight;
        flags++;
        lastFlagMs = System.currentTimeMillis();
        return vl;
    }

    public final synchronized void removeVl(double weight) {
        vl = Math.max(0.0, vl - weight);
    }

    private void decay() {
        long now = System.currentTimeMillis();
        double minutes = (now - lastDecayMs) / 60_000.0;
        lastDecayMs = now;
        if (minutes > 0 && settings != null) {
            vl = Math.max(0.0, vl - settings.decay() * minutes);
        }
    }

    public final synchronized double vl() {
        decay();
        return vl;
    }

    public final synchronized void resetVl() {
        vl = 0.0;
        buffer = 0.0;
    }

    // ---------- метаданные ----------

    public final String id() {
        return id;
    }

    public final String name() {
        return info.name();
    }

    public final String type() {
        return info.type();
    }

    public final String displayName() {
        return info.name() + " " + info.type();
    }

    public final Category category() {
        return info.category();
    }

    public final CheckInfo info() {
        return info;
    }

    public final CheckSettings settings() {
        return settings;
    }

    public final boolean isEnabled() {
        return settings != null && settings.enabled();
    }

    public final int flagCount() {
        return flags;
    }

    public final long lastFlagMs() {
        return lastFlagMs;
    }

    public final PlayerData data() {
        return data;
    }
}
