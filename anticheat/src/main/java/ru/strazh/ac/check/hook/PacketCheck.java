package ru.strazh.ac.check.hook;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;

/**
 * Проверка, получающая все пакеты игрока (event loop). Вызывается ПОСЛЕ
 * обновления трекеров по этому пакету, поэтому data.movement() и т.п.
 * уже содержат новые значения, а last* — предыдущие.
 */
public interface PacketCheck {

    default void onPacketReceive(PacketReceiveEvent event) {
    }

    default void onPacketSend(PacketSendEvent event) {
    }
}
