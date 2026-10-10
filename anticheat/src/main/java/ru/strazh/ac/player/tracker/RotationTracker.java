package ru.strazh.ac.player.tracker;

import java.util.ArrayDeque;

/**
 * Повороты камеры, навязанные сервером (PlayerRotation, телепорт с ротацией,
 * FacePlayer). Например, кастомные машины Skript каждый тик поворачивают камеру
 * водителя пакетом ClientboundPlayerRotationPacket. Такие изменения ротации не
 * являются движением мыши и не должны попадать в аим-проверки.
 */
public final class RotationTracker {

    public record Forced(long preSeq, long postSeq, float yaw, boolean relYaw, float pitch, boolean relPitch) {
    }

    private final ArrayDeque<Forced> pending = new ArrayDeque<>();
    /** Клиентский тик, до которого ротации считаются «загрязнёнными» сервером. */
    public long taintedUntilTick = -1;
    public long lastForcedTick = -1000;

    public void add(Forced f) {
        pending.add(f);
        if (pending.size() > 128) pending.pollFirst();
    }

    /**
     * Мог ли какой-то навязанный поворот примениться к текущему пакету ротации.
     * Удаляет записи, которые точно применены до прошлого пакета.
     */
    public boolean possiblyApplied(long lastConfirmed, long clientTick) {
        boolean influenced = false;
        while (!pending.isEmpty()) {
            Forced f = pending.peekFirst();
            if (lastConfirmed >= f.preSeq) {
                influenced = true;
                if (lastConfirmed >= f.postSeq) {
                    pending.pollFirst();
                    lastForcedTick = clientTick;
                    // Следующий тик тоже исключаем: применение могло прийтись на
                    // границу тиков.
                    taintedUntilTick = Math.max(taintedUntilTick, clientTick + 1);
                    continue;
                }
            }
            break;
        }
        return influenced || clientTick <= taintedUntilTick;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public void clear() {
        pending.clear();
    }
}
