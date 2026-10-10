package ru.strazh.ac.player;

import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import ru.strazh.ac.util.DoubleWindow;

import java.util.ArrayDeque;

/**
 * Система транзакций (ping/pong) для компенсации задержки.
 *
 * <p>Каждый тик сервера игроку отправляется пакет Ping с уникальным id. Клиент
 * обрабатывает входящие пакеты строго по порядку и отвечает Pong в момент
 * обработки пинга. Поэтому, если серверный пакет X (скорость, телепорт,
 * движение сущности) записан в канал между пингами N и N+1, то:
 * <ul>
 *   <li>до получения Pong(N) клиент точно ещё не применил X;</li>
 *   <li>после получения Pong(N+1) клиент точно уже применил X.</li>
 * </ul>
 *
 * <p>Номера последовательности (seq) монотонны. Пинги создаются под замком и
 * ставятся в очередь event loop канала, поэтому порядок записи в канал совпадает
 * с порядком seq. Учёт «отправлено» ведётся в момент реальной записи
 * (PacketSendEvent), то есть в порядке байтов на проводе.
 *
 * <p>Методы on* вызываются только из event loop канала игрока.
 */
public final class TransactionManager {

    /** Диапазон id наших пингов: отрицательные значения, не пересекающиеся с ванилью. */
    public static final int ID_BASE = -1_879_048_192;
    public static final int ID_RANGE = 1 << 20;

    private final PlayerData data;
    private final Object createLock = new Object();
    private long createdSeq;

    // ---- состояние event loop ----
    private long lastSentSeq;
    private long lastConfirmedSeq;
    private final ArrayDeque<long[]> inFlight = new ArrayDeque<>(); // {seq, sentNanos}
    private final ArrayDeque<Task> tasks = new ArrayDeque<>();
    private final DoubleWindow rttWindow = new DoubleWindow(40);
    private volatile double lastRttMs;
    private volatile double smoothedRttMs = -1;
    private volatile long oldestInFlightNanos;
    private long lastPongNanos = System.nanoTime();
    /** Клиентских тиков, полученных с момента последнего Pong. */
    private int clientTicksSincePong;
    private int invalidPongs;

    private record Task(long seq, Runnable runnable) {
    }

    TransactionManager(PlayerData data) {
        this.data = data;
    }

    public static boolean isOurs(int id) {
        return id >= ID_BASE && id < ID_BASE + ID_RANGE;
    }

    private static int toId(long seq) {
        return ID_BASE + (int) (seq % ID_RANGE);
    }

    /** Восстанавливает seq по id относительно опорного seq (обработка переполнения диапазона). */
    private static long toSeq(int id, long reference) {
        long base = reference - (reference % ID_RANGE);
        long candidate = base + (id - ID_BASE);
        if (candidate < reference - ID_RANGE / 2) candidate += ID_RANGE;
        if (candidate > reference + ID_RANGE / 2) candidate -= ID_RANGE;
        return candidate;
    }

    /** Отправляет новый пинг. Можно вызывать из любого потока. */
    public void send() {
        User user = data.user();
        if (user == null) return;
        Object channel = user.getChannel();
        if (channel == null || !ChannelHelper.isOpen(channel)) {
            return;
        }
        synchronized (createLock) {
            long seq = ++createdSeq;
            WrapperPlayServerPing ping = new WrapperPlayServerPing(toId(seq));
            ChannelHelper.runInEventLoop(channel, () -> user.sendPacket(ping));
        }
    }

    /** Наш пинг реально записан в канал (event loop). */
    public void onPingWritten(int id) {
        long seq = toSeq(id, lastSentSeq + 1);
        if (seq > lastSentSeq) {
            lastSentSeq = seq;
            if (inFlight.isEmpty()) {
                oldestInFlightNanos = System.nanoTime();
            }
            inFlight.add(new long[]{seq, System.nanoTime()});
        }
    }

    /**
     * Клиент ответил Pong. Возвращает true, если это наш пинг (его надо отменить).
     */
    public boolean onPong(int id) {
        if (!isOurs(id)) {
            return false;
        }
        long seq = toSeq(id, lastConfirmedSeq + 1);
        if (seq <= lastConfirmedSeq || seq > lastSentSeq) {
            // Ответ на неотправленный или уже подтверждённый пинг — подделка или
            // переупорядочивание ответов (дизейблеры).
            invalidPongs++;
            data.onInvalidTransaction(seq, lastConfirmedSeq, lastSentSeq);
            return true;
        }
        if (seq != lastConfirmedSeq + 1) {
            // Пропущен ответ: клиент обязан отвечать на каждый пинг по порядку.
            invalidPongs++;
            data.onInvalidTransaction(seq, lastConfirmedSeq, lastSentSeq);
        }
        long now = System.nanoTime();
        while (!inFlight.isEmpty() && inFlight.peekFirst()[0] <= seq) {
            long[] e = inFlight.pollFirst();
            if (e[0] == seq) {
                double rtt = (now - e[1]) / 1_000_000.0;
                lastRttMs = rtt;
                rttWindow.add(rtt);
                smoothedRttMs = smoothedRttMs < 0 ? rtt : smoothedRttMs * 0.9 + rtt * 0.1;
            }
        }
        oldestInFlightNanos = inFlight.isEmpty() ? 0L : inFlight.peekFirst()[1];
        lastConfirmedSeq = seq;
        lastPongNanos = now;
        clientTicksSincePong = 0;
        while (!tasks.isEmpty() && tasks.peekFirst().seq <= seq) {
            Task t = tasks.pollFirst();
            try {
                t.runnable.run();
            } catch (Throwable ex) {
                if (data.plugin() != null) data.plugin().getLogger().warning("Ошибка задачи транзакции: " + ex);
            }
        }
        return true;
    }

    /** Вызывается на каждый клиентский тик (event loop). */
    public void onClientTick() {
        clientTicksSincePong++;
    }

    /**
     * Выполнить задачу, когда клиент подтвердит пинг с указанным seq
     * (event loop). Если уже подтверждён — выполняется сразу.
     */
    public void runOnConfirm(long seq, Runnable r) {
        if (seq <= lastConfirmedSeq) {
            r.run();
            return;
        }
        // seq задач неубывающий в обычном использовании (lastSentSeq + 1),
        // но на всякий случай вставляем по порядку.
        if (tasks.isEmpty() || tasks.peekLast().seq <= seq) {
            tasks.addLast(new Task(seq, r));
        } else {
            ArrayDeque<Task> tmp = new ArrayDeque<>(tasks.size() + 1);
            boolean inserted = false;
            for (Task t : tasks) {
                if (!inserted && t.seq > seq) {
                    tmp.add(new Task(seq, r));
                    inserted = true;
                }
                tmp.add(t);
            }
            if (!inserted) tmp.add(new Task(seq, r));
            tasks.clear();
            tasks.addAll(tmp);
        }
    }

    /** Последний записанный в канал пинг (event loop). */
    public long lastSent() {
        return lastSentSeq;
    }

    /** Последний подтверждённый клиентом пинг (event loop). */
    public long lastConfirmed() {
        return lastConfirmedSeq;
    }

    /**
     * Seq пинга, который будет записан сразу после текущего пакета:
     * после его подтверждения клиент гарантированно применил пакет.
     */
    public long nextPostSeq() {
        return lastSentSeq + 1;
    }

    public boolean isConfirmed(long seq) {
        return seq <= lastConfirmedSeq;
    }

    public double lastRttMs() {
        return lastRttMs;
    }

    /** Сглаженный RTT по транзакциям, мс (или -1, если ещё нет данных). */
    public double smoothedRttMs() {
        return smoothedRttMs;
    }

    public double rttStdDev() {
        return rttWindow.stdDev();
    }

    /** Возраст самого старого неподтверждённого пинга в мс (0 — нет таких). */
    public double oldestPendingAgeMs() {
        long t = oldestInFlightNanos;
        return t == 0L ? 0.0 : (System.nanoTime() - t) / 1_000_000.0;
    }

    public int inFlightCount() {
        return inFlight.size();
    }

    public double msSinceLastPong() {
        return (System.nanoTime() - lastPongNanos) / 1_000_000.0;
    }

    public int clientTicksSincePong() {
        return clientTicksSincePong;
    }

    public int invalidPongs() {
        return invalidPongs;
    }
}
