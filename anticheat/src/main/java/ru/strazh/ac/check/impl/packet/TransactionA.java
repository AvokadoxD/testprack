package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Ответы на транзакции не по порядку или с пропусками. Клиент обязан отвечать
 * на каждый пинг строго по очереди; «OldGrim»-велосити и дизейблеры отменяют
 * часть пингов, чтобы сервер не узнал о применённой отдаче.
 */
@CheckInfo(name = "Transaction", type = "A", category = Category.PACKET,
        description = "Пропуск или перестановка ответов на транзакции (дизейблер, отмена отдачи)", maxVl = 5, decay = 0.5)
public final class TransactionA extends Check implements PacketCheck {

    private int seen;

    public TransactionA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PONG) return;
        int invalid = data.transactions().invalidPongs();
        if (invalid > seen) {
            seen = invalid;
            flagSetback(1.0, data.lastInvalidTransaction);
        }
    }
}
