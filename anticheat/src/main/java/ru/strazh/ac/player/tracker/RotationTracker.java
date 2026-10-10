package ru.strazh.ac.player.tracker;

import java.util.ArrayDeque;

/**
 * Повороты камеры, навязанные сервером (PlayerRotation, FacePlayer).
 *
 * <p>Ванильный клиент 1.21.2+ при обработке PlayerRotation сразу применяет углы
 * и отправляет «эхо» — пакет Look(yaw, pitch, onGround=false, hColl=false).
 * Кастомные машины Skript шлют PlayerRotation каждый тик, поэтому эхо нужно
 * распознавать: это не движение мыши и не признак «в воздухе». Следующий
 * после эха пакет ротации уже содержит только движение мыши.
 *
 * <p>Если эхо не пришло (клиент проигнорировал поворот — NoRotate/NoServerRotation),
 * растёт счётчик {@link #missedEchoes}, а ближайшие ротации помечаются как
 * «загрязнённые» сервером.
 */
public final class RotationTracker {

    public static final class Forced {
        public final long preSeq, postSeq;
        public final float yaw, pitch;
        public final boolean relYaw, relPitch;
        /** Ожидается ли эхо (у PlayerRotation — да, у FacePlayer — нет). */
        public final boolean expectEcho;
        public boolean echoed;

        public Forced(long preSeq, long postSeq, float yaw, boolean relYaw, float pitch, boolean relPitch, boolean expectEcho) {
            this.preSeq = preSeq;
            this.postSeq = postSeq;
            this.yaw = yaw;
            this.relYaw = relYaw;
            this.pitch = pitch;
            this.relPitch = relPitch;
            this.expectEcho = expectEcho;
        }
    }

    private final ArrayDeque<Forced> pending = new ArrayDeque<>();
    /** Клиентский тик, до которого ротации считаются «загрязнёнными» сервером. */
    public long taintedUntilTick = -1;
    public long lastForcedTick = -1000;
    /** Сколько навязанных поворотов клиент не подтвердил эхом. */
    public int missedEchoes;
    public long lastMissedEchoTick = -1000;
    public int matchedEchoes;

    public void add(Forced f) {
        pending.add(f);
        if (pending.size() > 128) pending.pollFirst();
    }

    /**
     * Является ли пакет ротации эхом навязанного поворота: клиент уже обработал
     * пинг до поворота (pre), но ещё не ответил на пинг после него (post).
     */
    public Forced matchEcho(long lastConfirmed) {
        for (Forced f : pending) {
            if (f.expectEcho && !f.echoed && lastConfirmed >= f.preSeq && lastConfirmed < f.postSeq) {
                f.echoed = true;
                matchedEchoes++;
                return f;
            }
        }
        return null;
    }

    /** Вызывается после каждого подтверждённого Pong (event loop). */
    public void onPong(long lastConfirmed, long clientTick) {
        while (!pending.isEmpty() && pending.peekFirst().postSeq <= lastConfirmed) {
            Forced f = pending.pollFirst();
            lastForcedTick = clientTick;
            if (!f.echoed) {
                if (f.expectEcho) {
                    missedEchoes++;
                    lastMissedEchoTick = clientTick;
                }
                // Поворот применён без распознанного эха — исключаем ближайшие ротации.
                taintedUntilTick = Math.max(taintedUntilTick, clientTick + 1);
            }
        }
    }

    /**
     * Мог ли нераспознанный навязанный поворот повлиять на текущий пакет ротации.
     */
    public boolean possiblyApplied(long lastConfirmed, long clientTick) {
        for (Forced f : pending) {
            if (!f.echoed && lastConfirmed >= f.preSeq) {
                return true;
            }
        }
        return clientTick <= taintedUntilTick;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public void clear() {
        pending.clear();
    }
}
