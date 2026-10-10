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

    /**
     * id наших пингов — псевдослучайные 32-битные значения (а не последовательность
     * из узкого диапазона): читы определяют античит по диапазону id транзакций.
     * Соответствие id -> seq хранится до подтверждения.
     */
    private final java.util.Map<Integer, Long> idToSeq = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.SplittableRandom random = new java.util.SplittableRandom();

    public boolean isOurs(int id) {
        return idToSeq.containsKey(id);
    }

    private int newId(long seq) {
        int id;
        do {
            id = random.nextInt();
            // Избегаем значений, которые используют ваниль и известные античиты/плагины.
        } while ((id > -32768 && id < 32768) || idToSeq.containsKey(id));
        idToSeq.put(id, seq);
        return id;
    }

    private final PlayerData data;
    private final Object createLock = new Object();
    private long createdSeq;

    // ---- состояние event loop ----
    private long lastSentSeq;
    private long lastConfirmedSeq;
    private final ArrayDeque<long[]> inFlight = new ArrayDeque<>(); // {seq, sentNanos}
    private final ArrayDeque<Task> tasks = new ArrayDeque<>();
    private final DoubleWindow rttWindow = new DoubleWindow(40);
    /** Длинное окно RTT для базового (минимального) пинга. */
    private final DoubleWindow rttLongWindow = new DoubleWindow(400);
    private volatile double baselineRttMs = -1;
    private int baselineCounter;
    /** RTT по данным ядра ОС (TCP_INFO), мс; -1 — недоступно. */
    private volatile double kernelRttMs = -1;
    private final DoubleWindow kernelWindow = new DoubleWindow(40);
    /** Разница RTT транзакций и ядра (сглаженная), мс. */
    private volatile double pongOverKernelMs = -1;
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
            WrapperPlayServerPing ping = new WrapperPlayServerPing(newId(seq));
            ChannelHelper.runInEventLoop(channel, () -> user.sendPacket(ping));
        }
    }

    /** Наш пинг реально записан в канал (event loop). */
    public void onPingWritten(int id) {
        Long boxed = idToSeq.get(id);
        if (boxed == null) return;
        long seq = boxed;
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
        Long boxed = idToSeq.remove(id);
        if (boxed == null) {
            return false;
        }
        long seq = boxed;
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
            if (e[0] != seq) {
                // Пропущенные пинги больше не ждём.
                idToSeq.values().remove(e[0]);
            }
            if (e[0] == seq) {
                double rtt = (now - e[1]) / 1_000_000.0;
                lastRttMs = rtt;
                rttWindow.add(rtt);
                rttLongWindow.add(rtt);
                if (baselineCounter % 5 == 0) {
                    double k = ru.strazh.ac.util.NetUtil.kernelRttMs(data.user() == null ? null : data.user().getChannel());
                    if (k >= 0) {
                        kernelRttMs = k;
                        kernelWindow.add(rtt - k);
                        pongOverKernelMs = kernelWindow.mean();
                    }
                }
                if (++baselineCounter % 20 == 0 || baselineRttMs < 0) {
                    // 10-й перцентиль за ~20 секунд: устойчив к искусственному раздуванию пинга в бою.
                    double[] arr = rttLongWindow.toArray();
                    java.util.Arrays.sort(arr);
                    baselineRttMs = arr[Math.min(arr.length - 1, arr.length / 10)];
                }
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

    /** RTT по ядру ОС, мс (-1 — недоступно: не epoll или нет данных). */
    public double kernelRttMs() {
        return kernelRttMs;
    }

    /**
     * Насколько ответы на транзакции в среднем медленнее, чем RTT по ядру, мс.
     * У ванильного клиента это ~0–60 мс (ожидание клиентского тика).
     * Систематически больше — клиент придерживает ответы (Blink/FakeLag/BackTrack).
     */
    public double pongOverKernelMs() {
        return pongOverKernelMs;
    }

    /** Базовый RTT (низкий перцентиль за длинное окно), мс; -1 — нет данных. */
    public double baselineRttMs() {
        return baselineRttMs;
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
