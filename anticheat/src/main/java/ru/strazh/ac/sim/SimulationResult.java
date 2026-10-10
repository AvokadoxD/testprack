package ru.strazh.ac.sim;

import ru.strazh.ac.util.Vec3;

import java.util.EnumSet;
import java.util.Set;

/** Результат симуляции одного тика движения (главный поток). */
public final class SimulationResult {

    /** Причины неопределённости, при которых проверки должны быть мягче. */
    public enum Uncertainty {
        TELEPORT, VEHICLE, FLYING, GLIDING, RIPTIDE, LEVITATION, LIQUID, CLIMBABLE, WEB, POWDER_SNOW,
        BUBBLE_COLUMN, PISTON, ENTITY_COLLISION, SHULKER, BOUNCE, BLOCK_CHANGE, UNLOADED_CHUNK, VELOCITY,
        ABILITIES, JOIN, LAG, SKIPPED_POSITION, WORLD_BORDER, SCAFFOLDING, HONEY, SOUL_SPEED, STEP, EDGE,
        NO_INPUT, EXEMPT
    }

    public final MoveSnapshot snapshot;
    /** Фактическое смещение по пакету. */
    public Vec3 actual = Vec3.ZERO;
    /** Наиболее близкое предсказанное смещение. */
    public Vec3 predicted = Vec3.ZERO;
    /** Расстояние между фактическим и предсказанным смещением. */
    public double offset;
    /** Расхождение по вертикали (факт - предсказание). */
    public double offsetY;
    /** Расхождение по горизонтали (длина факт - длина предсказания, >0 — быстрее). */
    public double offsetXZ;
    /** Стоит ли игрок на земле по расчёту сервера (после коллизий). */
    public boolean serverOnGround;
    /** Касается ли бокс игрока блоков снизу (с допуском) — для NoFall/GroundSpoof. */
    public boolean nearGround;
    /** Была ли применена скорость от сервера в этом тике. */
    public boolean velocityApplied;
    /** Ожидалась ли обязательная скорость, которая не была применена. */
    public boolean velocityIgnored;
    /** Вертикальная скорость прыжка, если симуляция признала прыжок. */
    public boolean jumped;
    /** Высота шага (step), если был подъём на ступеньку. */
    public double stepHeight;
    /** Пропуск проверки целиком (транспорт, наблюдатель, неизвестный мир и т.д.). */
    public boolean skip;
    /** Причина пропуска для отладки. */
    public String skipReason = "";
    public final Set<Uncertainty> uncertainties = EnumSet.noneOf(Uncertainty.class);
    /** Дистанция падения по расчёту сервера. */
    public double fallDistance;
    /** Мир известен и чанки загружены. */
    public boolean worldLoaded = true;
    /** Наличие блоков-жидкостей/лестниц и т.п. вокруг. */
    public boolean inLiquid, onClimbable, inWeb, onIce, onSlime, onHoney, onSoulSand, nearPiston;

    public SimulationResult(MoveSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public boolean has(Uncertainty u) {
        return uncertainties.contains(u);
    }

    public boolean isCertain() {
        return !skip && uncertainties.isEmpty();
    }
}
