package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Ответ на телепорт с позицией, не совпадающей с телепортом. Ванильный клиент
 * подтверждает телепорт и сразу шлёт ровно ту позицию, куда его телепортировали;
 * PacketFly/Phase-обходы откатов шлют свою.
 */
@CheckInfo(name = "BadPackets", type = "L", category = Category.PACKET,
        description = "Ответ на телепорт не в точку телепорта (PacketFly, обход откатов)", maxVl = 6, decay = 0.5)
public final class BadPacketsL extends Check implements PacketCheck {

    private int seen;

    public BadPacketsL(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) return;
        int n = data.teleportEchoMismatches;
        if (n > seen) {
            seen = n;
            if (!data.inVehicleRecently(10) && increaseBuffer(1.0) > 1.0) {
                flagSetback(1.0, "эхо телепорта не совпало");
            }
        }
    }
}
