package ru.strazh.ac.player.update;

import ru.strazh.ac.player.tracker.TrackedEntity;
import ru.strazh.ac.util.AABB;

/**
 * Удар по сущности. Создаётся при получении пакета атаки и дополняется
 * расчётами на следующем пакете движения.
 */
public final class AttackUpdate {

    public final int entityId;
    /** Цель (может быть null, если сущность не отслеживается — например, не отправлялась клиенту). */
    public final TrackedEntity target;
    public final long clientTick;
    public final long nanoTime = System.nanoTime();
    /** Позиция глаз атакующего в момент удара (позиция последнего пакета). */
    public final double eyeX, eyeZ;
    /** Возможные высоты глаз (стоя/крадучись/плавая) от позиции ног. */
    public final double feetY;
    public final float yawBefore, pitchBefore;
    public final boolean sneaking;
    public final boolean sprinting;
    public final boolean inVehicle;
    /** Номер удара в этом клиентском тике (0 — первый). */
    public final int indexInTick;
    /** Был ли до удара в этом тике пакет взмаха рукой. */
    public final boolean swungBefore;

    // ---- заполняется при разрешении (на следующем пакете движения) ----
    public boolean resolved;
    public float yawAfter, pitchAfter;
    /** Объединение хитбоксов цели, которые клиент мог видеть. */
    public AABB targetBox;
    /** Минимальная дистанция от глаз до хитбокса (по всем высотам глаз). */
    public double distance = -1;
    /** Пересекает ли луч взгляда (до или после) хитбокс цели в пределах дальности. */
    public boolean rayHit;
    /** Минимальная угловая ошибка взгляда относительно центра хитбокса, градусы. */
    public double aimErrorYaw = -1, aimErrorPitch = -1;
    /** Допустимая дальность взаимодействия (атрибут entity_interaction_range). */
    public double maxRange = 3.0;
    /** Расчёт ненадёжен (цель не отслеживается, транспорт, неизвестный размер и т.п.). */
    public boolean uncertain;

    public AttackUpdate(int entityId, TrackedEntity target, long clientTick, double eyeX, double feetY, double eyeZ,
                        float yawBefore, float pitchBefore, boolean sneaking, boolean sprinting, boolean inVehicle,
                        int indexInTick, boolean swungBefore) {
        this.entityId = entityId;
        this.target = target;
        this.clientTick = clientTick;
        this.eyeX = eyeX;
        this.feetY = feetY;
        this.eyeZ = eyeZ;
        this.yawBefore = yawBefore;
        this.pitchBefore = pitchBefore;
        this.sneaking = sneaking;
        this.sprinting = sprinting;
        this.inVehicle = inVehicle;
        this.indexInTick = indexInTick;
        this.swungBefore = swungBefore;
    }
}
