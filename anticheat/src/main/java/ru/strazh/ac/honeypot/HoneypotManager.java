package ru.strazh.ac.honeypot;

import org.bukkit.entity.Entity;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.player.PlayerData;

/** Ловушки для аур (фейковые сущности только в пакетах). Полная реализация — в HoneypotService. */
public class HoneypotManager {

    protected final StrazhPlugin plugin;

    public HoneypotManager(StrazhPlugin plugin) {
        this.plugin = plugin;
    }

    /** Является ли Bukkit-сущность ловушкой (у пакетных ловушек нет Bukkit-сущности). */
    public boolean isHoneypot(Entity entity) {
        return false;
    }

    public void remove(PlayerData data) {
    }

    /** Вызывается каждый серверный тик (главный поток). */
    public void tick(int currentTick) {
    }

    public void shutdown() {
    }
}
