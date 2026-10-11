package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Пакеты копания без начала: STOP/ABORT по блоку, который не начинали копать.
 * Так работают обходы Velocity «Grim/Funtime» (STOP у ног как «заполнитель тика»),
 * PacketMine/InstantRebreak и некоторые дизейблеры.
 */
@CheckInfo(name = "BadPackets", type = "E", category = Category.PACKET,
        description = "Завершение/отмена копания без начала (обходы Velocity, PacketMine)", maxVl = 8, decay = 1.0)
public final class BadPacketsE extends Check implements PacketCheck {

    private Vector3i started;

    public BadPacketsE(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
        WrapperPlayClientPlayerDigging w = new WrapperPlayClientPlayerDigging(event);
        Vector3i pos = w.getBlockPosition();
        switch (w.getAction()) {
            case START_DIGGING -> started = pos;
            case FINISHED_DIGGING, CANCELLED_DIGGING -> {
                int gm = data.abilities().gameMode;
                if (gm == 1 || gm == 3 || data.abilities().creative) return;
                if (started == null || !started.equals(pos)) {
                    if (increaseBuffer(1.0) > 1.0) {
                        flag(w.getAction() + " без START " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                    }
                    if (w.getAction() == com.github.retrooper.packetevents.protocol.player.DiggingAction.FINISHED_DIGGING) {
                        event.setCancelled(true);
                    }
                } else {
                    decreaseBuffer(0.25);
                }
                if (w.getAction() == com.github.retrooper.packetevents.protocol.player.DiggingAction.FINISHED_DIGGING
                        || w.getAction() == com.github.retrooper.packetevents.protocol.player.DiggingAction.CANCELLED_DIGGING) {
                    started = null;
                }
            }
            default -> {
            }
        }
    }
}
