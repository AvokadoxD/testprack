package ru.strazh.ac.player.tracker;

import ru.strazh.ac.util.Vec3;

import java.util.ArrayDeque;

/** Серверные телепорты игрока и их подтверждения (event loop). */
public final class TeleportTracker {

    public static final class Pending {
        public final int teleportId;
        public final double x, y, z;
        public final float yaw, pitch;
        public final boolean relX, relY, relZ, relYaw, relPitch;
        public final Vec3 deltaMovement;
        public final boolean relDelta;
        public final long postSeq;
        public final long sentNanos = System.nanoTime();
        public boolean confirmed;

        public Pending(int teleportId, double x, double y, double z, float yaw, float pitch,
                       boolean relX, boolean relY, boolean relZ, boolean relYaw, boolean relPitch,
                       Vec3 deltaMovement, boolean relDelta, long postSeq) {
            this.teleportId = teleportId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.relYaw = relYaw;
            this.relPitch = relPitch;
            this.deltaMovement = deltaMovement;
            this.relDelta = relDelta;
            this.postSeq = postSeq;
        }

        public boolean anyRelativePosition() {
            return relX || relY || relZ;
        }
    }

    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    /** Подтверждённый телепорт, эхо-позицию которого ждём в следующем пакете движения. */
    public Pending awaitingEcho;
    public long lastTeleportTick = -1000;
    public int unconfirmedTimeouts;

    public void add(Pending p) {
        pending.add(p);
        if (pending.size() > 64) pending.pollFirst();
    }

    /**
     * Клиент подтвердил телепорт. Возвращает запись или null, если id неизвестен
     * (подтверждение несуществующего телепорта — признак PacketFly/дизейблера).
     */
    public Pending confirm(int id) {
        while (!pending.isEmpty()) {
            Pending p = pending.pollFirst();
            if (p.teleportId == id) {
                p.confirmed = true;
                awaitingEcho = p;
                return p;
            }
            // Пропущенные (более старые) телепорты клиент уже не подтвердит:
            // ванильный клиент подтверждает каждый по порядку.
        }
        return null;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public Pending peek() {
        return pending.peekFirst();
    }

    public int pendingCount() {
        return pending.size();
    }

    public void clear() {
        pending.clear();
        awaitingEcho = null;
    }
}
