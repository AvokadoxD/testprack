package ru.strazh.ac.player.tracker;

import ru.strazh.ac.util.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Скорости, выставленные сервером игроку (EntityVelocity, Explosion),
 * с окнами транзакций (event loop).
 */
public final class VelocityTracker {

    public static final class Entry {
        public final Vec3 velocity;
        /** true — добавляется к текущему движению (взрыв), false — заменяет его. */
        public final boolean additive;
        /** Пинг, отправленный до пакета: до его подтверждения скорость не применена. */
        public final long preSeq;
        /** Пинг после пакета: после его подтверждения скорость точно применена. */
        public final long postSeq;
        public final long createdNanos = System.nanoTime();
        /** Использована ли скорость каким-либо тиком движения (по данным симуляции). */
        public volatile boolean consumed;
        /** Клиентский тик, на котором подтверждён postSeq (-1 — ещё нет). */
        public long confirmedTick = -1;

        public Entry(Vec3 velocity, boolean additive, long preSeq, long postSeq) {
            this.velocity = velocity;
            this.additive = additive;
            this.preSeq = preSeq;
            this.postSeq = postSeq;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    public long lastVelocityTick = -1000;
    public Vec3 lastVelocity = Vec3.ZERO;

    public void add(Entry e) {
        entries.add(e);
        if (entries.size() > 32) {
            entries.remove(0);
        }
    }

    /**
     * Скорости, которые могли быть применены к движению текущего тика:
     * клиент мог обработать пакет (preSeq подтверждён), и запись не устарела.
     */
    public List<Entry> candidates(long lastConfirmed, long clientTick, int keepTicks) {
        List<Entry> out = new ArrayList<>(2);
        for (Entry e : entries) {
            if (lastConfirmed < e.preSeq) {
                continue;
            }
            if (e.confirmedTick >= 0 && clientTick - e.confirmedTick > keepTicks) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    /** Есть ли скорость, отправленная, но ещё не подтверждённая клиентом. */
    public boolean hasPending(long lastConfirmed) {
        for (Entry e : entries) {
            if (lastConfirmed < e.postSeq) return true;
        }
        return false;
    }

    public void onConfirmed(long seq, long clientTick) {
        for (Entry e : entries) {
            if (e.confirmedTick < 0 && e.postSeq <= seq) {
                e.confirmedTick = clientTick;
            }
        }
    }

    public void prune(long clientTick, int keepTicks) {
        entries.removeIf(e -> e.confirmedTick >= 0 && clientTick - e.confirmedTick > keepTicks);
    }

    public List<Entry> all() {
        return entries;
    }
}
