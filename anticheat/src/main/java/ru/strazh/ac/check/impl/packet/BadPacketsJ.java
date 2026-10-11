package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Подтверждение телепорта, которого сервер не отправлял (PacketFly, дизейблеры,
 * подделка подтверждений для обхода откатов).
 */
@CheckInfo(name = "BadPackets", type = "J", category = Category.PACKET,
        description = "Подтверждение несуществующего телепорта (PacketFly)", maxVl = 5, decay = 0.5)
public final class BadPacketsJ extends Check implements PacketCheck {

    private int seen;

    public BadPacketsJ(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.TELEPORT_CONFIRM) return;
        int invalid = data.invalidTeleportConfirms;
        if (invalid > seen) {
            seen = invalid;
            event.setCancelled(true);
            flag("unknown teleport id");
        }
    }
}
