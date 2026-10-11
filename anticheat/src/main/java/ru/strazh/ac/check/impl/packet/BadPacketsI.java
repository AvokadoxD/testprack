package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Больше одного PlayerInput за тик: клиент шлёт состояние клавиш один раз за тик
 * и только при изменении. Два и больше — WaterSpeed (присед туда-обратно),
 * спуф ввода, «легитные» режимы аур, переключающие клавиши.
 */
@CheckInfo(name = "BadPackets", type = "I", category = Category.PACKET,
        description = "Несколько пакетов ввода (клавиш) за один тик", maxVl = 10, decay = 1.0)
public final class BadPacketsI extends Check implements PacketCheck, TickCheck {

    private int inputs;

    public BadPacketsI(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_INPUT || !data.client().sendsTickEnd) return;
        if (++inputs == 2) {
            flag("inputs/tick=2");
        }
    }

    @Override
    public void onClientTickEnd() {
        inputs = 0;
    }
}
