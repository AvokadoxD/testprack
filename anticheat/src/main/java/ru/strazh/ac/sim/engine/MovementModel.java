package ru.strazh.ac.sim.engine;

/**
 * Чистая физика игрока Minecraft 1.21 для «модели границ» (без доступа к миру).
 *
 * <p>Горизонталь: смещение тика = скорость после трения прошлого тика + ускорение
 * от ввода (+0.2 при спринт-прыжке). Это верхняя граница: стены и замедления
 * могут только уменьшить фактическое смещение.
 * <p>Вертикаль: смещение тика = (прошлое смещение − гравитация) · 0.98, либо сила
 * прыжка с земли, либо скорость от сервера.
 */
public final class MovementModel {

    /** Множитель в формуле наземного ускорения: speed · 0.21600002 / slip³. */
    public static final double GROUND_ACCEL = 0.21600002;
    public static final double AIR_ACCEL = 0.02;
    public static final double AIR_ACCEL_SPRINT = 0.025999999;
    public static final double SPRINT_JUMP_BOOST = 0.2;
    public static final double DRAG_Y = 0.98;
    public static final double FRICTION_AIR = 0.91;

    private MovementModel() {
    }

    /** Множитель трения в конце тика: (на земле ? скользкость блока : 1) · 0.91. */
    public static double frictionFactor(boolean onGroundAtTickStart, double slipperiness) {
        return (onGroundAtTickStart ? slipperiness : 1.0) * FRICTION_AIR;
    }

    /**
     * Максимальное ускорение от ввода за тик.
     * @param moveSpeed атрибут скорости с учётом спринта (0.1 · 1.3 при спринте)
     */
    public static double inputAccel(boolean onGroundAtTickStart, double slipperiness, double moveSpeed, boolean sprinting) {
        if (onGroundAtTickStart) {
            double s = Math.max(slipperiness, 0.1);
            return moveSpeed * (GROUND_ACCEL / (s * s * s));
        }
        return sprinting ? AIR_ACCEL_SPRINT : AIR_ACCEL;
    }

    /**
     * Верхняя граница горизонтального смещения за тик.
     * @param lastDxz          фактическое горизонтальное смещение прошлого тика
     * @param lastFriction     множитель трения прошлого тика
     * @param accel            ускорение от ввода (уже с замедлениями)
     * @param sprintJump       в этом тике был спринт-прыжок
     */
    public static double maxHorizontal(double lastDxz, double lastFriction, double accel, boolean sprintJump) {
        double carry = lastDxz * lastFriction;
        // Нулевание скоростей < 0.003 только уменьшает — граница не меняется.
        return carry + accel + (sprintJump ? SPRINT_JUMP_BOOST : 0.0);
    }

    /** Ожидаемое вертикальное смещение тика в воздухе без прыжка и скорости от сервера. */
    public static double nextFallDy(double lastDy, double gravity) {
        double v = lastDy;
        if (Math.abs(v) < 0.003) v = 0.0;
        return (v - gravity) * DRAG_Y;
    }

    /**
     * Вертикаль под левитацией: скорость стремится к 0.05 · (уровень + 1) с
     * коэффициентом 0.2, гравитация не действует (LivingEntity.travelInAir).
     */
    public static double nextLevitationDy(double lastDy, int amplifier) {
        double v = lastDy;
        if (Math.abs(v) < 0.003) v = 0.0;
        return (v + (0.05 * (amplifier + 1) - v) * 0.2) * DRAG_Y;
    }

    /** Сила прыжка: атрибут jump_strength · множитель блока + 0.1 · уровень прыгучести. */
    public static double jumpPower(double jumpStrength, double blockJumpFactor, int jumpBoostLevel) {
        return jumpStrength * blockJumpFactor + (jumpBoostLevel > 0 ? 0.1 * jumpBoostLevel : 0.0);
    }

    /** Скорость в воде за тик после трения (0.8, спринт 0.9, грация дельфина 0.96). */
    public static double waterFriction(boolean sprinting, boolean dolphinsGrace) {
        if (dolphinsGrace) return 0.96;
        return sprinting ? 0.9 : 0.8;
    }
}
