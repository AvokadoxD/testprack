package ru.strazh.ac.check.impl.packet;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.TransactionManager;
import ru.strazh.ac.util.Text;

/**
 * Удержание ответов на транзакции при продолжающихся тиках (Blink, FakeLag,
 * дизейблеры, «заморозка» отдачи). Пинги и тики идут по одному TCP-потоку:
 * при настоящем лаге застревает всё сразу, а у читера тики продолжают
 * приходить, пока ответы на пинги придерживаются.
 */
@CheckInfo(name = "Transaction", type = "B", category = Category.PACKET,
        description = "Ответы на транзакции задерживаются, а тики идут (Blink, FakeLag)", maxVl = 10, decay = 1.0, setback = true)
public final class TransactionB extends Check implements TickCheck {

    private double maxHoldMs;
    private int minTicks;

    public TransactionB(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        maxHoldMs = cfg("max-hold-ms", 1000.0);
        minTicks = cfg("min-ticks", 12);
    }

    @Override
    public void onClientTickEnd() {
        TransactionManager tx = data.transactions();
        if (tx.inFlightCount() == 0) {
            decreaseBuffer(0.02);
            return;
        }
        double base = Math.max(0, tx.baselineRttMs());
        double age = tx.oldestPendingAgeMs();
        int ticks = tx.clientTicksSincePong();
        int allowedTicks = minTicks + (int) Math.ceil(base / 50.0);
        if (age > maxHoldMs + base && ticks > allowedTicks) {
            if (increaseBuffer(1.0) > 4.0) {
                flagSetback(1.0, "pong ждём " + Math.round(age) + " мс, тиков " + ticks + " (база " + Math.round(base) + ")");
                resetBuffer();
            }
        } else {
            decreaseBuffer(0.05);
        }
    }
}
