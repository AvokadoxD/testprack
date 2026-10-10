package ru.strazh.ac.player;

import ru.strazh.ac.check.Check;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Исключения игрока: право обхода, тэги скорборда (для Skript), временные
 * исключения через API/команду. Потокобезопасно.
 *
 * <p>Ключи временных исключений: "all", ключ категории ("movement", "combat"...)
 * или id проверки ("reach_a").
 */
public final class Exemptions {

    private final Map<String, Long> timed = new ConcurrentHashMap<>();
    /** Кэш права обхода (обновляется в главном потоке). */
    public volatile boolean bypassPermission;
    /** Кэш тэгов скорборда игрока (обновляется в главном потоке). */
    public volatile Set<String> tags = Set.of();

    public void exempt(String key, long durationMs) {
        long until = durationMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + durationMs;
        timed.merge(key, until, Math::max);
    }

    public void unexempt(String key) {
        timed.remove(key);
    }

    public void clear() {
        timed.clear();
    }

    private boolean timedActive(String key) {
        Long until = timed.get(key);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            timed.remove(key, until);
            return false;
        }
        return true;
    }

    public boolean isExempt(Check check, String exemptTag, boolean categoryTags) {
        if (bypassPermission) return true;
        Set<String> t = tags;
        if (!t.isEmpty()) {
            if (t.contains(exemptTag)) return true;
            if (categoryTags && (t.contains(exemptTag + "_" + check.category().key())
                    || t.contains(exemptTag + "_" + check.id()))) {
                return true;
            }
        }
        if (timed.isEmpty()) return false;
        return timedActive("all") || timedActive(check.category().key()) || timedActive(check.id());
    }

    /** Полное исключение (все проверки). */
    public boolean isFullyExempt(String exemptTag) {
        return bypassPermission || tags.contains(exemptTag) || timedActive("all");
    }

    public Map<String, Long> active() {
        return Map.copyOf(timed);
    }
}
