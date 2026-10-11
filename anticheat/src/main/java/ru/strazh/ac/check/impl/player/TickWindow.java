package ru.strazh.ac.check.impl.player;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;

/**
 * Определяет, пришёл ли пакет «между тиками» (после ClientTickEnd и до первого
 * пакета следующего тика). У ванильного клиента 1.21.2+ клики и закрытие окон
 * идут из кадров отрисовки — то есть только между тиками.
 */
final class TickWindow {

    private boolean betweenTicks = true;

    /** Обновить состояние по пакету; вернуть true, если пакет пришёл между тиками. */
    boolean onPacket(PacketType.Play.Client t) {
        boolean between = betweenTicks;
        switch (t) {
            case CLIENT_TICK_END -> betweenTicks = true;
            case PLAYER_FLYING, PLAYER_POSITION, PLAYER_ROTATION, PLAYER_POSITION_AND_ROTATION,
                 PLAYER_INPUT, ENTITY_ACTION, INTERACT_ENTITY, USE_ITEM, PLAYER_BLOCK_PLACEMENT,
                 PLAYER_DIGGING, ANIMATION, HELD_ITEM_CHANGE, VEHICLE_MOVE -> betweenTicks = false;
            default -> {
            }
        }
        return between;
    }
}
