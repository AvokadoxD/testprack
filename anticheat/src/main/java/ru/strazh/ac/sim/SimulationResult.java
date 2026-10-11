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

    // ---- для проверок движения ----
    /** Максимально допустимая горизонтальная скорость в этом тике (без замедлений предметом/приседом). */
    public double allowedXZ = Double.MAX_VALUE;
    /** То же с учётом замедления от использования предмета/приседа. */
    public double slowedAllowedXZ = Double.MAX_VALUE;
    /** Ожидаемое вертикальное смещение в воздухе (NaN — не проверяется). */
    public double predictedDy = Double.NaN;
    /** Был ли игрок на земле в прошлом тике (по коллизиям). */
    public boolean prevServerGround;
    /** Касание земли в конце тика (приземление). */
    public boolean landing;
    /** Подъём с земли за тик и допустимые значения. */
    public double jumpPower = 0.42, stepAllowed = 0.6;
    /** Клиент заявил onGround, а под ногами нет опоры (спуф земли / ходьба по воде). */
    public boolean groundSpoof;
    /** Опора — жидкость (Jesus). */
    public boolean groundSpoofOnLiquid;
    /** Сервер видит опору, а клиент упорно сообщает, что в воздухе (NoGround NoFall). */
    public boolean noGroundSpoof;
    /** Игрок вошёл в твёрдый блок. */
    public boolean phase;
    /** Использует предмет / крадётся (по серверу и вводу). */
    public boolean usingItem, sneaking;
    /** Горизонтальное столкновение со стеной (по коллизиям). */
    public boolean wallCollision;
    /** Блок над головой. */
    public boolean ceiling;

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
