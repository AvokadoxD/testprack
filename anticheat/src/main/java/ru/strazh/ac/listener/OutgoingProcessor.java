package ru.strazh.ac.listener;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerExplosion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRespawn;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.player.AbilityState;
import ru.strazh.ac.player.AttributeState;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.TransactionManager;
import ru.strazh.ac.player.tracker.EntityTracker;
import ru.strazh.ac.player.tracker.RotationTracker;
import ru.strazh.ac.player.tracker.TeleportTracker;
import ru.strazh.ac.player.tracker.TrackedEntity;
import ru.strazh.ac.player.tracker.VelocityTracker;
import ru.strazh.ac.util.Vec3;

import java.util.Locale;
import java.util.UUID;

/**
 * Обработка исходящих пакетов (event loop). Каждое изменение состояния,
 * которое клиент применяет при получении пакета, привязывается к окну
 * транзакций: pre = последний записанный пинг, post = следующий.
 */
final class OutgoingProcessor {

    private final StrazhPlugin plugin;

    OutgoingProcessor(StrazhPlugin plugin) {
        this.plugin = plugin;
    }

    void handle(PlayerData data, PacketType.Play.Server type, PacketSendEvent event) {
        TransactionManager tx = data.transactions();
        switch (type) {
            case PING -> {
                WrapperPlayServerPing w = new WrapperPlayServerPing(event);
                if (tx.isOurs(w.getId())) {
                    tx.onPingWritten(w.getId());
                }
            }
            case KEEP_ALIVE -> {
                WrapperPlayServerKeepAlive w = new WrapperPlayServerKeepAlive(event);
                if (data.keepAliveSent.size() > 64) data.keepAliveSent.clear();
                data.keepAliveSent.put(w.getId(), System.nanoTime());
            }
            case ENTITY_VELOCITY -> {
                WrapperPlayServerEntityVelocity w = new WrapperPlayServerEntityVelocity(event);
                if (w.getEntityId() == data.entityId) {
                    Vector3d v = w.getVelocity();
                    addVelocity(data, new Vec3(v.getX(), v.getY(), v.getZ()), false);
                } else {
                    // Скорость других сущностей не влияет на их позицию для клиента
                    // (позиции приходят пакетами движения), игнорируем.
                }
            }
            case EXPLOSION -> {
                WrapperPlayServerExplosion w = new WrapperPlayServerExplosion(event);
                Vector3d kb = w.getKnockback();
                if (kb != null && (kb.getX() != 0 || kb.getY() != 0 || kb.getZ() != 0)) {
                    addVelocity(data, new Vec3(kb.getX(), kb.getY(), kb.getZ()), true);
                }
            }
            case PLAYER_POSITION_AND_LOOK -> handleTeleport(data, event);
            case PLAYER_ROTATION -> {
                WrapperPlayServerPlayerRotation w = new WrapperPlayServerPlayerRotation(event);
                data.rotations().add(new RotationTracker.Forced(tx.lastSent(), tx.nextPostSeq(),
                        w.getYaw(), w.isRelativeYaw(), w.getPitch(), w.isRelativePitch(), true));
                // Пинг сразу после поворота: окно, в котором ждём эхо, будет коротким.
                tx.send();
            }
            case FACE_PLAYER -> data.rotations().add(new RotationTracker.Forced(tx.lastSent(), tx.nextPostSeq(),
                    0, true, 0, true, false));
            case JOIN_GAME -> {
                WrapperPlayServerJoinGame w = new WrapperPlayServerJoinGame(event);
                data.entityId = w.getEntityId();
                data.entities().clear();
                data.teleports().clear();
                data.rotations().clear();
                setGameMode(data, w.getGameMode());
            }
            case RESPAWN -> {
                WrapperPlayServerRespawn w = new WrapperPlayServerRespawn(event);
                GameMode gm = w.getGameMode();
                long post = tx.nextPostSeq();
                data.abilities().pendingChange = true;
                data.loaded = false;
                tx.runOnConfirm(post, () -> {
                    data.loaded = false;
                    data.actions().inventoryOpen = false;
                    data.actions().containerOpen = false;
                    data.actions().openWindowId = -1;
                    data.entities().clear();
                    data.vehicle().clientVehicleId = -1;
                    data.vehicle().changePending = false;
                    setGameMode(data, gm);
                    data.abilities().pendingChange = false;
                    data.abilities().lastChangeTick = data.movement().clientTick;
                });
            }
            case CHANGE_GAME_STATE -> {
                WrapperPlayServerChangeGameState w = new WrapperPlayServerChangeGameState(event);
                if (w.getReason() == WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE) {
                    int mode = (int) w.getValue();
                    data.abilities().pendingChange = true;
                    tx.runOnConfirm(tx.nextPostSeq(), () -> {
                        data.abilities().gameMode = mode;
                        data.abilities().pendingChange = false;
                        data.abilities().lastChangeTick = data.movement().clientTick;
                    });
                }
            }
            case PLAYER_ABILITIES -> {
                WrapperPlayServerPlayerAbilities w = new WrapperPlayServerPlayerAbilities(event);
                boolean flying = w.isFlying(), allow = w.isFlightAllowed(), creative = w.isInCreativeMode(),
                        god = w.isInGodMode();
                float flySpeed = w.getFlySpeed();
                AbilityState ab = data.abilities();
                ab.pendingChange = true;
                // Пока изменение в пути, разрешаем объединение старых и новых способностей.
                ab.allowFlight = ab.allowFlight || allow;
                tx.runOnConfirm(tx.nextPostSeq(), () -> {
                    ab.flying = flying;
                    ab.allowFlight = allow;
                    ab.creative = creative;
                    ab.invulnerable = god;
                    ab.flySpeed = flySpeed;
                    ab.pendingChange = false;
                    ab.lastChangeTick = data.movement().clientTick;
                });
            }
            case SPAWN_ENTITY -> handleSpawn(data, event);
            case ENTITY_RELATIVE_MOVE -> {
                WrapperPlayServerEntityRelativeMove w = new WrapperPlayServerEntityRelativeMove(event);
                TrackedEntity e = data.entities().get(w.getEntityId());
                if (e != null) e.moveRelative(w.getDeltaX(), w.getDeltaY(), w.getDeltaZ(), tx.lastSent(), tx.nextPostSeq());
            }
            case ENTITY_RELATIVE_MOVE_AND_ROTATION -> {
                WrapperPlayServerEntityRelativeMoveAndRotation w = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
                TrackedEntity e = data.entities().get(w.getEntityId());
                if (e != null) e.moveRelative(w.getDeltaX(), w.getDeltaY(), w.getDeltaZ(), tx.lastSent(), tx.nextPostSeq());
            }
            case ENTITY_TELEPORT -> {
                WrapperPlayServerEntityTeleport w = new WrapperPlayServerEntityTeleport(event);
                TrackedEntity e = data.entities().get(w.getEntityId());
                if (e != null) {
                    Vector3d p = w.getPosition();
                    RelativeFlag f = w.getRelativeFlags();
                    double x = p.getX(), y = p.getY(), z = p.getZ();
                    if (f != null) {
                        if (f.has(RelativeFlag.X)) x += e.serverX;
                        if (f.has(RelativeFlag.Y)) y += e.serverY;
                        if (f.has(RelativeFlag.Z)) z += e.serverZ;
                    }
                    e.moveTo(x, y, z, tx.lastSent(), tx.nextPostSeq());
                }
            }
            case ENTITY_POSITION_SYNC -> {
                WrapperPlayServerEntityPositionSync w = new WrapperPlayServerEntityPositionSync(event);
                TrackedEntity e = data.entities().get(w.getId());
                if (e != null) {
                    Vector3d p = w.getValues().getPosition();
                    e.moveTo(p.getX(), p.getY(), p.getZ(), tx.lastSent(), tx.nextPostSeq());
                }
            }
            case DESTROY_ENTITIES -> {
                WrapperPlayServerDestroyEntities w = new WrapperPlayServerDestroyEntities(event);
                int[] ids = w.getEntityIds();
                tx.runOnConfirm(tx.nextPostSeq(), () -> {
                    for (int id : ids) data.entities().remove(id);
                });
            }
            case SET_PASSENGERS -> handlePassengers(data, event);
            case UPDATE_ATTRIBUTES -> handleAttributes(data, event);
            case OPEN_WINDOW -> {
                WrapperPlayServerOpenWindow w = new WrapperPlayServerOpenWindow(event);
                int id = w.getContainerId();
                tx.runOnConfirm(tx.nextPostSeq(), () -> {
                    data.actions().containerOpen = true;
                    data.actions().inventoryOpen = false;
                    data.actions().openWindowId = id;
                    data.actions().lastWindowOpenTick = data.movement().clientTick;
                });
            }
            case CLOSE_WINDOW -> {
                WrapperPlayServerCloseWindow w = new WrapperPlayServerCloseWindow(event);
                tx.runOnConfirm(tx.nextPostSeq(), () -> {
                    data.actions().containerOpen = false;
                    data.actions().inventoryOpen = false;
                    data.actions().openWindowId = -1;
                    data.actions().lastWindowCloseTick = data.movement().clientTick;
                });
            }
            default -> {
            }
        }
    }

    private void addVelocity(PlayerData data, Vec3 v, boolean additive) {
        TransactionManager tx = data.transactions();
        VelocityTracker.Entry entry = new VelocityTracker.Entry(v, additive, tx.lastSent(), tx.nextPostSeq());
        data.velocity().add(entry);
        data.velocity().lastVelocity = v;
        data.velocity().lastVelocityTick = data.movement().clientTick;
        // Скорость важна для симуляции — отправим пинг сразу после пакета,
        // чтобы окно неопределённости было как можно короче.
        tx.send();
    }

    private void handleTeleport(PlayerData data, PacketSendEvent event) {
        WrapperPlayServerPlayerPositionAndLook w = new WrapperPlayServerPlayerPositionAndLook(event);
        TransactionManager tx = data.transactions();
        RelativeFlag f = w.getRelativeFlags();
        boolean relX = f != null && f.has(RelativeFlag.X);
        boolean relY = f != null && f.has(RelativeFlag.Y);
        boolean relZ = f != null && f.has(RelativeFlag.Z);
        boolean relYaw = f != null && f.has(RelativeFlag.YAW);
        boolean relPitch = f != null && f.has(RelativeFlag.PITCH);
        boolean relDelta = f != null && (f.has(RelativeFlag.DELTA_X) || f.has(RelativeFlag.DELTA_Y) || f.has(RelativeFlag.DELTA_Z));
        Vector3d dm = w.getDeltaMovement();
        Vec3 delta = dm == null ? Vec3.ZERO : new Vec3(dm.getX(), dm.getY(), dm.getZ());
        long post = tx.nextPostSeq();
        data.teleports().add(new TeleportTracker.Pending(w.getTeleportId(), w.getX(), w.getY(), w.getZ(),
                w.getYaw(), w.getPitch(), relX, relY, relZ, relYaw, relPitch, delta, relDelta, post));
        // Ротацию телепорта отдельно не учитываем: эхо телепорта помечается как teleport.
        tx.send();
    }

    private void handleSpawn(PlayerData data, PacketSendEvent event) {
        WrapperPlayServerSpawnEntity w = new WrapperPlayServerSpawnEntity(event);
        TransactionManager tx = data.transactions();
        EntityType type = w.getEntityType();
        String name = type == null ? "unknown" : type.getName().getKey().toLowerCase(Locale.ROOT);
        double[] size = EntityTracker.defaultSize(name);
        boolean isPlayer = type == EntityTypes.PLAYER;
        boolean living = type != null && type.isInstanceOf(EntityTypes.LIVINGENTITY);
        Vector3d p = w.getPosition();
        UUID uuid = w.getUUID().orElse(null);
        TrackedEntity e = new TrackedEntity(w.getEntityId(), uuid, name, isPlayer, living,
                size[0], size[1], p.getX(), p.getY(), p.getZ(), tx.lastSent(), tx.nextPostSeq());
        e.exactSize = size[2] > 0;
        if (data.entities().size() < 4096) {
            data.entities().put(e);
        }
        if (uuid != null && !e.exactSize) {
            plugin.sizeResolver().request(e);
        }
    }

    private void handlePassengers(PlayerData data, PacketSendEvent event) {
        WrapperPlayServerSetPassengers w = new WrapperPlayServerSetPassengers(event);
        TransactionManager tx = data.transactions();
        int vehicle = w.getEntityId();
        int[] passengers = w.getPassengers();
        boolean containsSelf = false;
        for (int id : passengers) {
            if (id == data.entityId) {
                containsSelf = true;
            } else {
                TrackedEntity e = data.entities().get(id);
                if (e != null) e.vehicleId = vehicle;
            }
        }
        // Пассажиры, которых больше нет в списке, слезли.
        for (TrackedEntity e : data.entities().all()) {
            if (e.vehicleId == vehicle) {
                boolean still = false;
                for (int id : passengers) {
                    if (id == e.id) {
                        still = true;
                        break;
                    }
                }
                if (!still) e.vehicleId = -1;
            }
        }
        final boolean mounted = containsSelf;
        if (mounted || data.vehicle().clientVehicleId == vehicle) {
            data.vehicle().changePending = true;
            tx.runOnConfirm(tx.nextPostSeq(), () -> {
                if (mounted) {
                    data.vehicle().clientVehicleId = vehicle;
                } else if (data.vehicle().clientVehicleId == vehicle) {
                    data.vehicle().clientVehicleId = -1;
                }
                data.vehicle().changePending = false;
                data.vehicle().lastRidingTick = data.movement().clientTick;
            });
        }
    }

    private void handleAttributes(PlayerData data, PacketSendEvent event) {
        WrapperPlayServerUpdateAttributes w = new WrapperPlayServerUpdateAttributes(event);
        TransactionManager tx = data.transactions();
        if (w.getEntityId() == data.entityId) {
            AttributeState a = data.attributes();
            for (WrapperPlayServerUpdateAttributes.Property prop : w.getProperties()) {
                String key = attributeKey(prop);
                if (key == null) continue;
                double value = prop.calcValue();
                String k = key.toLowerCase(Locale.ROOT);
                Runnable apply = null;
                if (k.endsWith("entity_interaction_range")) {
                    // Пока не подтверждено — разрешаем большее из значений.
                    a.entityRange = Math.max(a.entityRange, value);
                    apply = () -> a.entityRange = value;
                } else if (k.endsWith("block_interaction_range")) {
                    a.blockRange = Math.max(a.blockRange, value);
                    apply = () -> a.blockRange = value;
                } else if (k.endsWith("scale")) {
                    a.scale = Math.max(a.scale, value);
                    apply = () -> a.scale = value;
                } else if (k.endsWith("step_height")) {
                    a.stepHeight = Math.max(a.stepHeight, value);
                    apply = () -> a.stepHeight = value;
                } else if (k.endsWith("gravity")) {
                    apply = () -> a.gravity = value;
                } else if (k.endsWith("jump_strength")) {
                    a.jumpStrength = Math.max(a.jumpStrength, value);
                    apply = () -> a.jumpStrength = value;
                } else if (k.endsWith("safe_fall_distance")) {
                    apply = () -> a.safeFallDistance = value;
                } else if (k.endsWith("movement_speed")) {
                    apply = () -> a.movementSpeed = value;
                } else if (k.endsWith("water_movement_efficiency")) {
                    apply = () -> a.waterMovementEfficiency = value;
                } else if (k.endsWith("movement_efficiency")) {
                    apply = () -> a.movementEfficiency = value;
                } else if (k.endsWith("sneaking_speed")) {
                    apply = () -> a.sneakingSpeed = value;
                }
                if (apply != null) {
                    Runnable r = apply;
                    a.lastChangeTick = data.movement().clientTick;
                    tx.runOnConfirm(tx.nextPostSeq(), () -> {
                        r.run();
                        a.lastChangeTick = data.movement().clientTick;
                    });
                }
            }
        } else {
            TrackedEntity e = data.entities().get(w.getEntityId());
            if (e == null) return;
            for (WrapperPlayServerUpdateAttributes.Property prop : w.getProperties()) {
                String key = attributeKey(prop);
                if (key != null && key.toLowerCase(Locale.ROOT).endsWith("scale")) {
                    double value = prop.calcValue();
                    e.scale = Math.max(e.scale, value);
                    tx.runOnConfirm(tx.nextPostSeq(), () -> e.scale = value);
                }
            }
        }
    }

    private static String attributeKey(WrapperPlayServerUpdateAttributes.Property prop) {
        var attr = prop.getAttribute();
        return attr == null || attr.getName() == null ? null : attr.getName().getKey();
    }

    private static void setGameMode(PlayerData data, GameMode gm) {
        if (gm == null) return;
        int id = switch (gm) {
            case SURVIVAL -> 0;
            case CREATIVE -> 1;
            case ADVENTURE -> 2;
            case SPECTATOR -> 3;
        };
        data.abilities().gameMode = id;
    }
}
