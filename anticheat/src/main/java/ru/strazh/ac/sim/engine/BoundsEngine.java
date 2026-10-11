package ru.strazh.ac.sim.engine;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.tracker.VelocityTracker;
import ru.strazh.ac.sim.MoveSnapshot;
import ru.strazh.ac.sim.SimulationEngine;
import ru.strazh.ac.sim.SimulationResult;
import ru.strazh.ac.sim.SimulationResult.Uncertainty;
import ru.strazh.ac.util.AABB;
import ru.strazh.ac.util.Vec3;

/**
 * Движок движения на «модели границ»: для каждого пакета движения считает
 * верхнюю границу горизонтальной скорости и ожидаемое вертикальное смещение по
 * физике 1.21, проверяет опору под ногами и вход в блоки. Неопределённые
 * ситуации (транспорт, полёт, элитры, тризубец, левитация, жидкости, поршни,
 * толкание сущностями, свежие изменения блоков, эффекты/атрибуты в пути)
 * помечаются, и проверки в них не флагают.
 */
public final class BoundsEngine implements SimulationEngine {

    private static final class State {
        World world;
        boolean init;
        long lastTick;
        double lastDxz;
        double lastDy;
        double lastFriction = MovementModel.FRICTION_AIR;
        boolean serverGround;
        float slipperiness = 0.6f;
        boolean wasInLiquid, wasInWeb, wasClimbing;
        int noGroundTicks;
        long resetTick;
        double lastDx, lastDz;
        int glideTicks;
        boolean justReset;
    }

    private final StrazhPlugin plugin;

    public BoundsEngine(StrazhPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public SimulationResult simulate(PlayerData data, MoveSnapshot s) {
        SimulationResult r = new SimulationResult(s);
        Player p = data.player();
        State st = data.sim().engineState instanceof State existing ? existing : new State();
        data.sim().engineState = st;
        if (p == null) return skip(r, "нет игрока");

        // ---- полный пропуск ----
        String skip = skipReason(data, p, s);
        if ("элитры".equals(skip) && st.init && st.world == p.getWorld() && s.teleportEcho() == null && !s.teleportPending()) {
            elytra(data, p, s, st, r);
            return skip(r, skip);
        }
        st.glideTicks = 0;
        if (skip != null) {
            reset(st, s, p.getWorld());
            if (s.teleportEcho() != null) {
                r.uncertainties.add(Uncertainty.TELEPORT);
            } else if (s.positionChanged()) {
                // Инерция продолжается от реального движения (после элитр, транспорта и т.п.).
                st.lastDxz = s.deltaXZ();
                st.lastDy = s.deltaY();
                st.lastDx = s.deltaX();
                st.lastDz = s.deltaZ();
            }
            return skip(r, skip);
        }
        if (!s.positionChanged()) {
            st.lastTick = s.clientTick();
            return skip(r, "без позиции");
        }
        if (!st.init || st.world != p.getWorld()) {
            reset(st, s, p.getWorld());
            return skip(r, "инициализация");
        }
        long gap = s.clientTick() - st.lastTick;
        if (s.sendsTickEnd() && gap > 1) {
            // Клиент не слал позицию несколько тиков (почти не двигался) — инерция почти нулевая.
            st.lastDxz = Math.min(st.lastDxz, 0.03);
            st.lastDy = 0;
            r.uncertainties.add(Uncertainty.SKIPPED_POSITION);
        }
        st.lastTick = s.clientTick();

        double scale = attr(p, Attribute.SCALE, 1.0);
        double width = 0.6 * scale;
        // Высота по текущей позе (присед 1.5, ползком/плавание 0.6) — с учётом масштаба.
        double height = Math.max(0.6 * scale, p.getHeight());
        AABB before = AABB.ofEntity(s.lastX(), s.lastY(), s.lastZ(), width, height);
        AABB now = AABB.ofEntity(s.x(), s.y(), s.z(), width, height);
        WorldScan env = WorldScan.scan(p.getWorld(), before.union(now), now);
        if (env.unloaded) {
            reset(st, s, p.getWorld());
            r.worldLoaded = false;
            r.uncertainties.add(Uncertainty.UNLOADED_CHUNK);
            return skip(r, "чанк не загружен");
        }

        double dx = s.deltaX(), dz = s.deltaZ(), dy = s.deltaY();
        double dxz = Math.sqrt(dx * dx + dz * dz);
        r.actual = new Vec3(dx, dy, dz);
        r.inLiquid = env.liquid;
        r.onClimbable = env.climbable;
        r.inWeb = env.web;
        r.onIce = env.ice;
        r.onSlime = env.slime;
        r.onHoney = env.honey;
        r.onSoulSand = env.soul;
        r.nearPiston = env.piston;
        r.serverOnGround = env.groundBelow;
        r.nearGround = env.groundBelow;
        r.prevServerGround = st.serverGround;
        r.wallCollision = env.wall;
        r.ceiling = env.ceiling;
        r.landing = env.groundBelow && !st.serverGround;

        // ---- неопределённости ----
        long nowMs = System.currentTimeMillis();
        double ping = Math.max(50, data.ping());
        if (nowMs - data.lastEffectChangeMillis < ping + 400) r.uncertainties.add(Uncertainty.ABILITIES);
        if (data.movement().clientTick - data.attributes().lastChangeTick < 10) r.uncertainties.add(Uncertainty.ABILITIES);
        if (nowMs - data.lastBlockChangeMillis < ping + 500) r.uncertainties.add(Uncertainty.BLOCK_CHANGE);
        if (env.piston) r.uncertainties.add(Uncertainty.PISTON);
        if (env.liquid || st.wasInLiquid) r.uncertainties.add(Uncertainty.LIQUID);
        if (env.climbable || st.wasClimbing) r.uncertainties.add(Uncertainty.CLIMBABLE);
        if (env.web || env.berry) r.uncertainties.add(Uncertainty.WEB);
        if (env.powderSnow) r.uncertainties.add(Uncertainty.POWDER_SNOW);
        if (env.bubble) r.uncertainties.add(Uncertainty.BUBBLE_COLUMN);
        if (env.scaffolding) r.uncertainties.add(Uncertainty.SCAFFOLDING);
        if (env.honey) r.uncertainties.add(Uncertainty.HONEY);
        if (env.slime || env.bed) r.uncertainties.add(Uncertainty.BOUNCE);
        if (s.abilitiesUncertain()) r.uncertainties.add(Uncertainty.ABILITIES);
        boolean entityPush = false;
        for (Entity e : p.getNearbyEntities(0.8, 0.8, 0.8)) {
            if (e.isValid() && !(e instanceof org.bukkit.entity.Item) && !(e instanceof org.bukkit.entity.ExperienceOrb)
                    && !(e instanceof org.bukkit.entity.Projectile)) {
                entityPush = true;
                break;
            }
        }
        if (entityPush) r.uncertainties.add(Uncertainty.ENTITY_COLLISION);

        // ---- параметры игрока (главный поток) ----
        boolean bukkitSprint = p.isSprinting();
        boolean sprinting = s.sprinting() || bukkitSprint;
        double speedAttr = attr(p, Attribute.MOVEMENT_SPEED, 0.1);
        double base = bukkitSprint ? speedAttr / 1.3 : speedAttr;
        double moveSpeed = base * (sprinting ? 1.3 : 1.0);
        // Порядок пакетов важен: сервер мог уже обработать следующий Use, поэтому
        // «использует» = клиент начал использование до этого пакета И предмет реально используется.
        boolean using = s.usingItem() && (p.isHandRaised() || p.hasActiveItem());
        boolean sneaking = s.shift();
        r.usingItem = using;
        r.sneaking = sneaking;
        double gravity = attr(p, Attribute.GRAVITY, 0.08);
        PotionEffect slowFall = p.getPotionEffect(PotionEffectType.SLOW_FALLING);
        if (slowFall != null && st.lastDy <= 0) gravity = Math.min(gravity, 0.01);
        PotionEffect jumpBoost = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
        double jumpPower = MovementModel.jumpPower(attr(p, Attribute.JUMP_STRENGTH, 0.42), 1.0,
                jumpBoost == null ? 0 : jumpBoost.getAmplifier() + 1);
        double stepHeight = attr(p, Attribute.STEP_HEIGHT, 0.6);
        r.jumpPower = jumpPower;
        r.stepAllowed = stepHeight;
        int depthStrider = enchant(p.getInventory().getBoots(), Enchantment.DEPTH_STRIDER);
        int soulSpeed = enchant(p.getInventory().getBoots(), Enchantment.SOUL_SPEED);
        if (soulSpeed > 0 && env.soul) r.uncertainties.add(Uncertainty.SOUL_SPEED);

        // ---- скорость от сервера ----
        double velXZ = -1, velY = Double.NaN;
        boolean velAdditive = false;
        for (VelocityTracker.Entry e : s.velocities()) {
            double h = e.velocity.horizontalLength();
            if (h > velXZ) {
                velXZ = h;
                velAdditive = e.additive;
            }
            velY = Double.isNaN(velY) ? e.velocity.y() : Math.max(velY, e.velocity.y());
        }
        if (velXZ >= 0 || s.velocityPending()) r.uncertainties.add(Uncertainty.VELOCITY);

        // ---- вертикаль ----
        boolean jumped = st.serverGround && dy > 0 && Math.abs(dy - jumpPower) < 0.01;
        r.jumped = jumped;
        boolean verticalChecked = !env.liquid && !st.wasInLiquid && !env.climbable && !st.wasClimbing && !env.web
                && !env.powderSnow && !env.bubble && !env.honey && !env.scaffolding && !env.slime && !env.bed
                && !entityPush && p.getPotionEffect(PotionEffectType.LEVITATION) == null;
        if (verticalChecked) {
            double fall = MovementModel.nextFallDy(st.serverGround ? 0.0 : st.lastDy, gravity);
            double best = fall;
            double bestErr = Math.abs(dy - fall);
            if (st.serverGround) {
                // С земли: прыжок, шаг на ступеньку, стояние (0).
                double[] options = {jumpPower, 0.0};
                for (double o : options) {
                    double err = Math.abs(dy - o);
                    if (err < bestErr) {
                        best = o;
                        bestErr = err;
                    }
                }
                if (dy > 0 && dy <= stepHeight + 1.0E-3 && env.groundBelow) {
                    best = dy;
                    bestErr = 0;
                    r.stepHeight = dy;
                }
            }
            if (!Double.isNaN(velY)) {
                double vOpt = velAdditive ? fall + velY : velY;
                if (Math.abs(dy - vOpt) < bestErr) {
                    best = vOpt;
                    bestErr = Math.abs(dy - vOpt);
                }
            }
            double offset = dy - best;
            // Потолок обрезает подъём, приземление — падение: в эту сторону отклонение допустимо.
            if (offset < 0 && best > 0 && env.ceiling) offset = 0;
            if (offset > 0 && best < 0 && env.groundBelow && dy <= 0) offset = 0;
            if (offset < 0 && env.groundBelow) offset = 0;
            r.predictedDy = best;
            r.offsetY = offset;
        } else {
            r.offsetY = 0;
            // Лестницы/лианы: подъём не быстрее ванильных 0.1176 (или остатка прыжка).
            if (env.climbable && st.wasClimbing && !st.serverGround && !env.liquid && !env.scaffolding && !env.bubble
                    && Double.isNaN(velY) && !s.velocityPending() && !entityPush) {
                double allowedClimb = Math.max(0.1176000022888184, MovementModel.nextFallDy(st.lastDy, gravity));
                r.fastClimb = dy > allowedClimb + 0.005;
            }
        }

        // ---- горизонталь ----
        boolean groundStart = st.serverGround;
        double accel = MovementModel.inputAccel(groundStart, st.slipperiness, moveSpeed, sprinting);
        boolean sprintJump = sprinting && dy > 0 && groundStart && !env.groundBelow;
        double allowed;
        if (env.liquid || st.wasInLiquid) {
            boolean dolphin = p.getPotionEffect(PotionEffectType.DOLPHINS_GRACE) != null;
            double f = MovementModel.waterFriction(sprinting, dolphin);
            allowed = st.lastDxz * Math.max(f, depthStrider > 0 ? 0.92 : f) + 0.04 + depthStrider * 0.04 + (dolphin ? 0.1 : 0);
            if (sprintJump) allowed += MovementModel.SPRINT_JUMP_BOOST;
        } else {
            allowed = MovementModel.maxHorizontal(st.lastDxz, st.lastFriction, accel, sprintJump);
        }
        if (velXZ >= 0) {
            double withVel = (velAdditive ? st.lastDxz * st.lastFriction + velXZ : velXZ) + accel
                    + (sprintJump ? MovementModel.SPRINT_JUMP_BOOST : 0);
            allowed = Math.max(allowed, withVel);
        }
        if (entityPush) allowed += 0.1;
        if (r.has(Uncertainty.SOUL_SPEED)) allowed *= 1.6;
        // Точность float, нулевание 0.003 и мелкие расхождения клиента.
        allowed = allowed * 1.005 + 0.003;
        r.allowedXZ = allowed;
        double slowFactor = 1.0;
        if (using) slowFactor *= 0.2;
        if (sneaking && groundStart) {
            double sneak = attr(p, Attribute.SNEAKING_SPEED, 0.3);
            slowFactor *= sneak;
        }
        if (slowFactor < 1.0 && !(env.liquid || st.wasInLiquid)) {
            double slowAccel = accel * slowFactor;
            double slowed = MovementModel.maxHorizontal(st.lastDxz, st.lastFriction, slowAccel, sprintJump && !using);
            if (velXZ >= 0) slowed = Math.max(slowed, velXZ + slowAccel);
            if (entityPush) slowed += 0.1;
            r.slowedAllowedXZ = slowed * 1.01 + 0.01;
        } else {
            r.slowedAllowedXZ = allowed;
        }
        r.offsetXZ = dxz - allowed;
        r.offset = Math.max(r.offsetXZ, Math.abs(r.offsetY));

        // ---- направление ускорения относительно клавиш (только 1.21.2+, где клавиши известны) ----
        boolean strafeOk = s.hasInput() && s.sendsTickEnd() && gap == 1 && !st.justReset
                && !env.liquid && !st.wasInLiquid && !env.climbable && !st.wasClimbing && !env.web && !st.wasInWeb
                && !env.wall && !s.horizontalCollision() && !env.soul && !env.honey && !env.powderSnow && !env.scaffolding
                && !env.slime && !entityPush && s.velocities().isEmpty() && !s.velocityPending()
                && !(sneaking && groundStart) && s.clientTick() - s.lastAttackTick() > 1
                && !r.has(Uncertainty.ABILITIES) && !r.has(Uncertainty.BLOCK_CHANGE) && !r.has(Uncertainty.PISTON);
        if (strafeOk) {
            double cx = st.lastDx * st.lastFriction, cz = st.lastDz * st.lastFriction;
            if (Math.abs(cx) < 0.003) cx = 0;
            if (Math.abs(cz) < 0.003) cz = 0;
            if (sprintJump) {
                float yr = s.yaw() * ru.strazh.ac.util.McMath.DEG_TO_RAD;
                cx -= ru.strazh.ac.util.McMath.sin(yr) * MovementModel.SPRINT_JUMP_BOOST;
                cz += ru.strazh.ac.util.McMath.cos(yr) * MovementModel.SPRINT_JUMP_BOOST;
            }
            double ax = dx - cx, az = dz - cz;
            double acc = Math.sqrt(ax * ax + az * az);
            Vec3 exp = ru.strazh.ac.util.McMath.inputVector(s.strafeInput(), 0, s.forwardInput(), 1f, s.yaw());
            double expLen = exp.horizontalLength();
            r.strafeChecked = true;
            r.strafeAccel = acc;
            if (expLen < 1.0E-6) {
                r.strafeError = acc > 0.02 ? 180.0 : 0.0;
            } else if (acc < 0.015) {
                r.strafeError = 0.0;
            } else {
                double cos = (ax * exp.x() + az * exp.z()) / (acc * expLen);
                r.strafeError = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
            }
        }

        // ---- опора ----
        boolean unsureGround = env.contextualBelow || r.has(Uncertainty.BLOCK_CHANGE) || r.has(Uncertainty.PISTON)
                || entityPush || r.has(Uncertainty.SCAFFOLDING);
        if (s.onGround() && !env.groundBelow && !unsureGround && !p.isInsideVehicle()) {
            r.groundSpoof = true;
            r.groundSpoofOnLiquid = env.liquidBelow;
        }
        if (!s.onGround() && env.groundBelow && Math.abs(dy) < 1.0E-6 && st.serverGround && !unsureGround) {
            st.noGroundTicks++;
        } else {
            st.noGroundTicks = 0;
        }
        r.noGroundSpoof = st.noGroundTicks >= 4;

        // ---- вход в блок ----
        // Для прохода сквозь блоки берём минимальную позу (ползком): смена позы
        // на сервере запаздывает, а горизонтальные проходы в стены ловятся и так.
        AABB lowNow = AABB.ofEntity(s.x(), s.y(), s.z(), width, 0.6 * scale);
        AABB lowBefore = AABB.ofEntity(s.lastX(), s.lastY(), s.lastZ(), width, 0.6 * scale);
        if (!r.has(Uncertainty.BLOCK_CHANGE) && !r.has(Uncertainty.PISTON) && !entityPush
                && env.newlyInsideSolid(lowNow, lowBefore)) {
            r.phase = true;
        }

        // ---- состояние на следующий тик ----
        st.lastFriction = (env.liquid ? 0.8 : MovementModel.frictionFactor(groundStart, st.slipperiness));
        st.lastDxz = dxz;
        st.lastDy = dy;
        st.lastDx = dx;
        st.lastDz = dz;
        st.justReset = false;
        st.serverGround = env.groundBelow || (s.onGround() && unsureGround);
        st.slipperiness = env.slipperiness;
        st.wasInLiquid = env.liquid;
        st.wasInWeb = env.web;
        st.wasClimbing = env.climbable;
        return r;
    }

    private String skipReason(PlayerData data, Player p, MoveSnapshot s) {
        if (p.isDead() || data.dead) return "мёртв";
        if (data.client().bedrock) return "bedrock";
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return "режим " + gm;
        if (p.isFlying() || s.flying() || p.getAllowFlight() && s.abilitiesUncertain()) return "полёт";
        if (s.inVehicle() || p.isInsideVehicle() || data.vehicle().bukkitInVehicle) return "транспорт";
        if (data.millisSinceDismount() < plugin.config().dismountGraceTicks * 50L + 500) return "высадка";
        if (System.currentTimeMillis() - data.vehicle().lastVehicleTeleportMillis < 1500) return "телепорт транспорта";
        if (s.teleportEcho() != null) return "телепорт";
        if (s.teleportPending()) return "ждём подтверждения телепорта";
        if (p.isGliding() || System.currentTimeMillis() - data.lastGlideToggleMillis < 1000) return "элитры";
        if (p.isRiptiding() || System.currentTimeMillis() - data.lastRiptideMillis < 3000) return "тризубец";
        if (System.currentTimeMillis() - data.lastElytraBoostMillis < 3000) return "фейерверк";
        if (p.getPotionEffect(PotionEffectType.LEVITATION) != null) return "левитация";
        if (data.isFullyExempt()) return "исключение";
        return null;
    }

    private static void reset(State st, MoveSnapshot s, World world) {
        st.init = true;
        st.world = world;
        st.lastTick = s.clientTick();
        st.lastDxz = s.teleportEcho() != null ? s.teleportEcho().deltaMovement.horizontalLength() : 0;
        st.lastDy = s.teleportEcho() != null ? s.teleportEcho().deltaMovement.y() : 0;
        st.lastDx = s.teleportEcho() != null ? s.teleportEcho().deltaMovement.x() : 0;
        st.lastDz = s.teleportEcho() != null ? s.teleportEcho().deltaMovement.z() : 0;
        st.justReset = true;
        st.lastFriction = MovementModel.FRICTION_AIR;
        st.serverGround = s.onGround();
        st.noGroundTicks = 0;
        st.wasInLiquid = true; // после сброса первый тик мягкий
        st.wasClimbing = false;
        st.resetTick = s.clientTick();
    }

    /**
     * Полёт на элитрах без фейерверков детерминирован: скорость тика =
     * updateFallFlyingMovement(прошлая скорость) с ротацией этого тика.
     */
    private void elytra(PlayerData data, Player p, MoveSnapshot s, State st, SimulationResult r) {
        long now = System.currentTimeMillis();
        boolean prevValid = st.glideTicks >= 2 && s.clientTick() - st.lastTick == 1;
        st.glideTicks++;
        st.lastTick = s.clientTick();
        if (!s.positionChanged()) return;
        double dx = s.deltaX(), dy = s.deltaY(), dz = s.deltaZ();
        double ping = Math.max(50, data.ping());
        boolean clean = prevValid && p.isGliding() && now - data.lastGlideToggleMillis > 500
                && now - data.lastElytraBoostMillis > 3500 + ping && now - data.lastRiptideMillis > 3000
                && now - data.lastEffectChangeMillis > ping + 400 && now - data.lastDamageMillis > 500
                && s.velocities().isEmpty() && !s.velocityPending() && !data.vehicle().inVehicle();
        if (clean) {
            double scale = attr(p, Attribute.SCALE, 1.0);
            AABB before = AABB.ofEntity(s.lastX(), s.lastY(), s.lastZ(), 0.6 * scale, 0.6 * scale);
            AABB box = AABB.ofEntity(s.x(), s.y(), s.z(), 0.6 * scale, 0.6 * scale);
            WorldScan env = WorldScan.scan(p.getWorld(), before.union(box), box);
            boolean free = !env.unloaded && !env.liquid && !env.wall && !env.groundBelow && !env.ceiling
                    && !env.climbable && !env.web && !env.bubble && p.getNearbyEntities(1.5, 1.5, 1.5).isEmpty();
            if (free) {
                double vx = st.lastDx, vy = st.lastDy, vz = st.lastDz;
                if (Math.abs(vx) < 0.003) vx = 0;
                if (Math.abs(vy) < 0.003) vy = 0;
                if (Math.abs(vz) < 0.003) vz = 0;
                double g = attr(p, Attribute.GRAVITY, 0.08);
                if (vy <= 0 && p.getPotionEffect(PotionEffectType.SLOW_FALLING) != null) g = Math.min(g, 0.01);
                Vec3 look = ru.strazh.ac.util.McMath.viewVector(s.pitch(), s.yaw());
                float f = s.pitch() * ru.strazh.ac.util.McMath.DEG_TO_RAD;
                double d = Math.sqrt(look.x() * look.x() + look.z() * look.z());
                double e = Math.sqrt(vx * vx + vz * vz);
                double h = Math.cos(f) * Math.cos(f);
                vy += g * (-1.0 + h * 0.75);
                if (vy < 0.0 && d > 0.0) {
                    double i = vy * -0.1 * h;
                    vx += look.x() * i / d;
                    vy += i;
                    vz += look.z() * i / d;
                }
                if (f < 0.0F && d > 0.0) {
                    double i = e * (-ru.strazh.ac.util.McMath.sin(f)) * 0.04;
                    vx += -look.x() * i / d;
                    vy += i * 3.2;
                    vz += -look.z() * i / d;
                }
                if (d > 0.0) {
                    vx += (look.x() / d * e - vx) * 0.1;
                    vz += (look.z() / d * e - vz) * 0.1;
                }
                vx *= 0.99F;
                vy *= 0.98F;
                vz *= 0.99F;
                Vec3 predicted = new Vec3(vx, vy, vz);
                r.elytraChecked = true;
                r.elytraPredicted = predicted;
                r.elytraOffset = predicted.distance(new Vec3(dx, dy, dz));
                r.actual = new Vec3(dx, dy, dz);
            }
        }
        st.lastDx = dx;
        st.lastDy = dy;
        st.lastDz = dz;
        st.lastDxz = Math.sqrt(dx * dx + dz * dz);
        st.serverGround = false;
        st.lastFriction = MovementModel.FRICTION_AIR;
    }

    private static SimulationResult skip(SimulationResult r, String reason) {
        r.skip = true;
        r.skipReason = reason;
        return r;
    }

    private static double attr(Player p, Attribute a, double def) {
        AttributeInstance i = p.getAttribute(a);
        return i == null ? def : i.getValue();
    }

    private static int enchant(ItemStack item, Enchantment e) {
        return item == null ? 0 : item.getEnchantmentLevel(e);
    }

    /** Позиция для отката после успешного тика. */
    public static Location location(World w, MoveSnapshot s) {
        return new Location(w, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
    }
}
