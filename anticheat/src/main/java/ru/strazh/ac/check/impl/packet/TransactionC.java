package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.TransactionManager;
import ru.strazh.ac.util.Text;

/**
 * Расхождение «часов»: ответы на транзакции систематически медленнее KeepAlive
 * (Blink пропускает KeepAlive) или медленнее RTT ядра ОС, которое клиент
 * подделать не может (FakeLag/BackTrack/PingSpoof). У ванильного клиента
 * разница не больше одного тика (~50 мс) плюс джиттер.
 */
@CheckInfo(name = "Transaction", type = "C", category = Category.PACKET,
        description = "Ответы на транзакции медленнее KeepAlive/RTT ядра (Blink, FakeLag, PingSpoof)", maxVl = 10, decay = 1.0, experimental = true)
public final class TransactionC extends Check implements PacketCheck {

    private double threshold;
    private long lastCheck;

    public TransactionC(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        threshold = cfg("threshold-ms", 250.0);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PONG) return;
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1000) return;
        lastCheck = now;
        TransactionManager tx = data.transactions();
        double pong = tx.smoothedRttMs();
        if (pong < 0) return;
        double kernelGap = tx.pongOverKernelMs();
        double ka = data.keepAlivePingMs;
        String reason = null;
        if (kernelGap >= 0 && tx.kernelRttMs() >= 0 && kernelGap > threshold) {
            reason = "pong-kernel=" + Math.round(kernelGap) + " мс";
        } else if (ka >= 0 && pong - ka > threshold + 50
                && System.nanoTime() - data.lastKeepAliveResponseNanos < 20_000_000_000L) {
            reason = "pong-keepalive=" + Math.round(pong - ka) + " мс";
        }
        if (reason != null) {
            if (increaseBuffer(1.0) > 5.0) {
                flag(reason + " pong=" + Text.fmt(pong, 0));
                buffer = 3.0;
            }
        } else {
            decreaseBuffer(0.5);
        }
    }
}
