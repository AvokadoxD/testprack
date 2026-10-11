package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.MoveUpdate;

/**
 * Таймер, привязанный к транзакциям. Между двумя подтверждёнными пингами клиент
 * не может прожить больше тиков, чем прошло реального времени между их
 * отправкой (с запасом на джиттер). Баланс копится: «кредит» — время между
 * отправками пингов, «расход» — 50 мс за тик. Накопленный кредит ограничен
 * (чтобы нельзя было «замедлиться, а потом ускориться»), но не меньше 10 тиков —
 * ровно столько ванильный клиент догоняет после фриза.
 * Для клиентов без ClientTickEnd тиком считается пакет движения.
 */
@CheckInfo(name = "Timer", type = "A", category = Category.MOVEMENT,
        description = "Ускорение игры (Timer, TickBase): больше тиков, чем прошло времени", maxVl = 15, decay = 2.0, setback = true)
public final class TimerA extends Check implements PacketCheck, TickCheck, MovementCheck {

    private double maxBankMs;
    private double thresholdMs;
    private double balance;
    private long lastSentNanos;
    private int ticks;

    public TimerA(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        maxBankMs = cfg("max-bank-ms", 600.0);
        thresholdMs = cfg("threshold-ms", 250.0);
    }

    @Override
    public void onClientTickEnd() {
        if (data.client().sendsTickEnd) ticks++;
    }

    @Override
    public void onMovement(MoveUpdate u) {
        if (!data.client().sendsTickEnd && !u.teleport()) ticks++;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PONG) return;
        long sent = data.transactions().lastConfirmedSentNanos();
        if (sent == 0 || sent == lastSentNanos) return;
        if (lastSentNanos == 0) {
            lastSentNanos = sent;
            ticks = 0;
            return;
        }
        double credit = (sent - lastSentNanos) / 1_000_000.0;
        lastSentNanos = sent;
        balance = Math.min(maxBankMs, balance + credit - ticks * 50.0);
        ticks = 0;
        if (data.inVehicleRecently(0) && !data.client().sendsTickEnd) {
            balance = Math.max(balance, 0);
            return;
        }
        if (balance < -thresholdMs) {
            flagSetback(Math.min(3.0, -balance / 200.0), "баланс " + Math.round(balance) + " мс");
            balance = -thresholdMs / 2;
        }
    }
}
