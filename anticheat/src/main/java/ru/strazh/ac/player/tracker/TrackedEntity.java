package ru.strazh.ac.player.tracker;

import ru.strazh.ac.util.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сущность, как её видит клиент, с учётом задержки (event loop).
 * Хранит несколько последних позиций с окнами транзакций, чтобы для удара
 * построить объединение всех хитбоксов, которые клиент мог видеть
 * (включая интерполяцию в 3 тика).
 */
public final class TrackedEntity {

    public static final class State {
        public final double x, y, z;
        public final long preSeq, postSeq;
        /** Время отправки позиции клиенту. */
        public final long sentNanos = System.nanoTime();
        /** Клиентский тик, когда postSeq подтверждён (-1 — ещё нет). */
        public long confirmedTick = -1;

        public State(double x, double y, double z, long preSeq, long postSeq) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.preSeq = preSeq;
            this.postSeq = postSeq;
        }
    }

    public final int id;
    public final UUID uuid;
    public final String typeName;
    public final boolean isPlayer;
    public final boolean living;
    public volatile double width;
    public volatile double height;
    /** true — размеры получены с сервера (Bukkit), а не из таблицы. */
    public volatile boolean exactSize;
    /** Атрибут scale (размер хитбокса умножается на него). */
    public volatile double scale = 1.0;
    /** Сущность, на которой едет эта (или -1). */
    public int vehicleId = -1;
    public final long spawnNanos = System.nanoTime();

    private final List<State> states = new ArrayList<>(6);
    /** Последняя позиция по данным сервера. */
    public double serverX, serverY, serverZ;

    public TrackedEntity(int id, UUID uuid, String typeName, boolean isPlayer, boolean living,
                         double width, double height, double x, double y, double z, long preSeq, long postSeq) {
        this.id = id;
        this.uuid = uuid;
        this.typeName = typeName;
        this.isPlayer = isPlayer;
        this.living = living;
        this.width = width;
        this.height = height;
        this.serverX = x;
        this.serverY = y;
        this.serverZ = z;
        states.add(new State(x, y, z, preSeq, postSeq));
    }

    public void moveTo(double x, double y, double z, long preSeq, long postSeq) {
        serverX = x;
        serverY = y;
        serverZ = z;
        states.add(new State(x, y, z, preSeq, postSeq));
        if (states.size() > 24) {
            states.remove(0);
        }
    }

    public void moveRelative(double dx, double dy, double dz, long preSeq, long postSeq) {
        moveTo(serverX + dx, serverY + dy, serverZ + dz, preSeq, postSeq);
    }

    public void onConfirmed(long seq, long clientTick) {
        for (State s : states) {
            if (s.confirmedTick < 0 && s.postSeq <= seq) {
                s.confirmedTick = clientTick;
            }
        }
    }

    /**
     * Объединение хитбоксов, которые клиент мог видеть в данный момент.
     * @param interpolationTicks сколько тиков клиент интерполирует позицию (3 для живых сущностей)
     * @return бокс или null, если клиент ещё не получил ни одной позиции
     */
    public AABB possibleBox(long lastConfirmed, long clientTick, int interpolationTicks) {
        return possibleBox(lastConfirmed, clientTick, interpolationTicks, Long.MAX_VALUE);
    }

    /**
     * То же, но без позиций, отправленных раньше {@code maxAgeNanos} назад
     * (кроме самой свежей из доступных клиенту): ограничение компенсации лага
     * против BackTrack/FakeLag, которые задерживают входящие пакеты.
     */
    public AABB possibleBox(long lastConfirmed, long clientTick, int interpolationTicks, long maxAgeNanos) {
        int base = -1;
        for (int i = states.size() - 1; i >= 0; i--) {
            State s = states.get(i);
            if (s.confirmedTick >= 0 && clientTick - s.confirmedTick > interpolationTicks) {
                base = i;
                break;
            }
        }
        if (base > 8) {
            states.subList(0, base - 1).clear();
            base = 1;
        }
        int start = Math.max(base, 0);
        AABB box = null;
        double w = width * scale, h = height * scale;
        long now = System.nanoTime();
        int last = start;
        for (int i = start; i < states.size(); i++) {
            if (i != start && lastConfirmed < states.get(i).preSeq) {
                break; // клиент ещё не мог получить эту и следующие позиции
            }
            last = i;
        }
        for (int i = start; i <= last; i++) {
            State s = states.get(i);
            if (i != last && maxAgeNanos != Long.MAX_VALUE && now - states.get(i + 1).sentNanos > maxAgeNanos) {
                // Клиент должен был давно получить более новую позицию — старую не учитываем.
                continue;
            }
            AABB b = AABB.ofEntity(s.x, s.y, s.z, w, h);
            box = box == null ? b : box.union(b);
        }
        return box;
    }

    /** Самая свежая позиция, которую клиент мог получить. */
    public State latestPossible(long lastConfirmed) {
        State best = states.get(0);
        for (State s : states) {
            if (lastConfirmed >= s.preSeq) best = s;
        }
        return best;
    }

    public int stateCount() {
        return states.size();
    }
}
