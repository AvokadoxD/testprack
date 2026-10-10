package ru.strazh.ac.check.hook;

import org.bukkit.event.Event;

/**
 * Проверка Bukkit-событий игрока (главный поток). Перед вызовом очередь
 * пакетов игрока уже обработана, так что состояние актуально.
 */
public interface BukkitCheck {

    void onBukkitEvent(Event event);
}
