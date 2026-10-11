package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Клиент не ответил на поворот камеры сервером (NoRotate/NoServerRotation).
 * Ванильный 1.21.2+ сразу отвечает пакетом Look с новыми углами.
 */
@CheckInfo(name = "NoRotate", type = "A", category = Category.PACKET,
        description = "Игнорирование поворота камеры сервером (NoRotate, NoServerRotation)", maxVl = 8, decay = 1.0)
public final class NoRotateA extends Check implements PacketCheck {

    private int seen;

    public NoRotateA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PONG) return;
        int missed = data.rotations().missedEchoes;
        if (missed <= seen) {
            if (data.rotations().matchedEchoes > 0) decreaseBuffer(0.01);
            return;
        }
        int delta = missed - seen;
        seen = missed;
        if (!data.client().isNewerThanOrEquals(ClientVersion.V_1_21_2) || data.dead || !data.loaded) return;
        if (increaseBuffer(delta) > 2.0) {
            flag("нет эха поворота x" + delta);
        }
    }
}
