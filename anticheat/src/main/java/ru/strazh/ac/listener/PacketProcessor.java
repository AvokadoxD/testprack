package ru.strazh.ac.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.event.UserLoginEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTeleportConfirm;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.check.CheckSet;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.RotationCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.tracker.ActionTracker;
import ru.strazh.ac.player.tracker.MovementTracker;
import ru.strazh.ac.player.tracker.TeleportTracker;
import ru.strazh.ac.player.tracker.TrackedEntity;
import ru.strazh.ac.player.update.AttackUpdate;
import ru.strazh.ac.player.update.MoveUpdate;
import ru.strazh.ac.player.update.RotationUpdate;
import ru.strazh.ac.sim.MoveSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Главный обработчик пакетов PacketEvents. Все методы вызываются в event loop
 * канала игрока, поэтому состояние трекеров игрока не требует синхронизации.
 */
public final class PacketProcessor implements PacketListener {

    private final StrazhPlugin plugin;
    private final OutgoingProcessor outgoing;

    public PacketProcessor(StrazhPlugin plugin) {
        this.plugin = plugin;
        this.outgoing = new OutgoingProcessor(plugin);
    }

    @Override
    public void onUserLogin(UserLoginEvent event) {
        plugin.players().getOrCreate(event.getUser());
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        plugin.players().remove(event.getUser());
    }

    // =====================================================================
    // Входящие
    // =====================================================================

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPacketType() instanceof PacketType.Play.Client type)) {
            return;
        }
        PlayerData data = plugin.players().get(event.getUser());
        if (data == null) {
            data = plugin.players().getOrCreate(event.getUser());
            if (data == null) return;
        }
        CheckSet checks = data.checks();
        if (checks == null) return;
        try {
            handleReceive(data, checks, type, event);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Ошибка обработки пакета " + type + " от " + data.name(), t);
        }
    }

    private void handleReceive(PlayerData data, CheckSet checks, PacketType.Play.Client type, PacketReceiveEvent event) {
        boolean flying = false;
        WrapperPlayClientPlayerFlying flyingWrapper = null;
        switch (type) {
            case PONG -> handlePong(data, event);
            case KEEP_ALIVE -> handleKeepAlive(data, event);
            case TELEPORT_CONFIRM -> handleTeleportConfirm(data, event);
            case PLAYER_FLYING, PLAYER_POSITION, PLAYER_ROTATION, PLAYER_POSITION_AND_ROTATION -> {
                flying = true;
                flyingWrapper = new WrapperPlayClientPlayerFlying(event);
            }
            case CLIENT_TICK_END -> {
                data.client().sendsTickEnd = true;
                // Счётчики тика сбрасываются после проверок пакета (см. ниже).
            }
            case PLAYER_INPUT -> handleInput(data, event);
            case ENTITY_ACTION -> handleEntityAction(data, event);
            case INTERACT_ENTITY -> handleInteract(data, checks, event);
            case ANIMATION -> {
                WrapperPlayClientAnimation w = new WrapperPlayClientAnimation(event);
                ActionTracker a = data.actions();
                a.lastSwingTick = data.movement().clientTick;
                a.swingsThisTick++;
            }
            case PLAYER_DIGGING -> handleDigging(data, event);
            case PLAYER_BLOCK_PLACEMENT -> {
                ActionTracker a = data.actions();
                a.lastPlaceTick = data.movement().clientTick;
                a.placesThisTick++;
            }
            case USE_ITEM -> {
                WrapperPlayClientUseItem w = new WrapperPlayClientUseItem(event);
                ActionTracker a = data.actions();
                a.lastUseItemTick = data.movement().clientTick;
                a.useItemsThisTick++;
                if (!a.usingItem) {
                    a.usingItem = true;
                    a.useHand = w.getHand();
                    a.useStartTick = data.movement().clientTick;
                }
            }
            case HELD_ITEM_CHANGE -> {
                WrapperPlayClientHeldItemChange w = new WrapperPlayClientHeldItemChange(event);
                ActionTracker a = data.actions();
                if (w.getSlot() != a.heldSlot) {
                    a.slotChangesThisTick++;
                    a.lastSlotChangeTick = data.movement().clientTick;
                    if (a.useHand == InteractionHand.MAIN_HAND) {
                        a.usingItem = false;
                    }
                }
                a.heldSlot = w.getSlot();
            }
            case CLICK_WINDOW -> {
                WrapperPlayClientClickWindow w = new WrapperPlayClientClickWindow(event);
                ActionTracker a = data.actions();
                a.lastWindowClickTick = data.movement().clientTick;
                a.windowClicksThisTick++;
                if (w.getWindowId() == 0 && !a.containerOpen) {
                    // Клик в инвентаре игрока означает, что инвентарь открыт.
                    a.openWindowId = 0;
                    a.inventoryOpen = true;
                }
            }
            case CLOSE_WINDOW -> {
                WrapperPlayClientCloseWindow w = new WrapperPlayClientCloseWindow(event);
                ActionTracker a = data.actions();
                a.containerOpen = false;
                a.inventoryOpen = false;
                a.openWindowId = -1;
                a.lastWindowCloseTick = data.movement().clientTick;
            }
            case PLAYER_ABILITIES -> {
                WrapperPlayClientPlayerAbilities w = new WrapperPlayClientPlayerAbilities(event);
                data.abilities().flying = w.isFlying();
                if (w.isFlying()) data.abilities().lastClientFlyingTick = data.movement().clientTick;
            }
            case PLUGIN_MESSAGE -> handlePluginMessage(data, event);
            case PLAYER_LOADED -> data.loaded = true;
            case VEHICLE_MOVE, STEER_BOAT -> data.vehicle().lastRidingTick = data.movement().clientTick;
            default -> {
            }
        }

        RotationUpdate rotationUpdate = null;
        MoveUpdate moveUpdate = null;
        if (flying) {
            Object[] updates = handleFlying(data, flyingWrapper);
            rotationUpdate = (RotationUpdate) updates[0];
            moveUpdate = (MoveUpdate) updates[1];
        }

        // ---- проверки ----
        for (PacketCheck c : checks.packet) {
            c.onPacketReceive(event);
        }
        if (rotationUpdate != null) {
            for (RotationCheck c : checks.rotation) {
                c.onRotation(rotationUpdate);
            }
        }
        if (moveUpdate != null) {
            // Удары, ждавшие этого пакета: теперь известна ротация, с которой клиент выбирал цель.
            resolveAttacks(data, checks, moveUpdate);
            for (MovementCheck c : checks.movement) {
                c.onMovement(moveUpdate);
            }
        }

        // ---- граница клиентского тика ----
        boolean tickBoundary = type == PacketType.Play.Client.CLIENT_TICK_END
                || (flying && !data.client().sendsTickEnd && moveUpdate != null && !moveUpdate.teleport());
        if (tickBoundary) {
            onClientTickEnd(data, checks);
        }
    }

    private void onClientTickEnd(PlayerData data, CheckSet checks) {
        for (TickCheck c : checks.tick) {
            c.onClientTickEnd();
        }
        MovementTracker m = data.movement();
        data.actions().onTickEnd();
        data.transactions().onClientTick();
        m.clientTick++;
        m.movesThisTick = 0;
        if (data.vehicle().inVehicle()) {
            data.vehicle().lastRidingTick = m.clientTick;
        }
        data.velocity().prune(m.clientTick, 40);
    }

    private void handlePong(PlayerData data, PacketReceiveEvent event) {
        WrapperPlayClientPong w = new WrapperPlayClientPong(event);
        if (data.transactions().onPong(w.getId())) {
            event.setCancelled(true);
            long confirmed = data.transactions().lastConfirmed();
            long tick = data.movement().clientTick;
            data.velocity().onConfirmed(confirmed, tick);
            data.entities().onConfirmed(confirmed, tick);
            data.rotations().onPong(confirmed, tick);
        }
    }

    private void handleKeepAlive(PlayerData data, PacketReceiveEvent event) {
        WrapperPlayClientKeepAlive w = new WrapperPlayClientKeepAlive(event);
        Long sent = data.keepAliveSent.remove(w.getId());
        if (sent == null) {
            data.invalidKeepAlives++;
            return;
        }
        long now = System.nanoTime();
        data.keepAlivePingMs = (now - sent) / 1_000_000.0;
        data.lastKeepAliveResponseNanos = now;
    }

    private void handleTeleportConfirm(PlayerData data, PacketReceiveEvent event) {
        WrapperPlayClientTeleportConfirm w = new WrapperPlayClientTeleportConfirm(event);
        TeleportTracker.Pending p = data.teleports().confirm(w.getTeleportId());
        if (p == null) {
            data.invalidTeleportConfirms++;
        } else {
            data.teleports().lastTeleportTick = data.movement().clientTick;
        }
    }

    private void handleInput(PlayerData data, PacketReceiveEvent event) {
        WrapperPlayClientPlayerInput w = new WrapperPlayClientPlayerInput(event);
        ActionTracker a = data.actions();
        a.hasInputPacket = true;
        a.inputForward = w.isForward();
        a.inputBackward = w.isBackward();
        a.inputLeft = w.isLeft();
        a.inputRight = w.isRight();
        a.inputJump = w.isJump();
        a.inputSprint = w.isSprint();
        if (a.inputShift != w.isShift()) {
            a.sneakTogglesThisTick++;
            a.lastSneakToggleTick = data.movement().clientTick;
        }
        a.inputShift = w.isShift();
        // С 1.21.6 присед передаётся только через PlayerInput.
        a.sneaking = w.isShift();
        a.lastInputChangeTick = data.movement().clientTick;
    }

    private void handleEntityAction(PlayerData data, PacketReceiveEvent event) {
        WrapperPlayClientEntityAction w = new WrapperPlayClientEntityAction(event);
        ActionTracker a = data.actions();
        long tick = data.movement().clientTick;
        switch (w.getAction()) {
            case START_SPRINTING -> {
                a.sprinting = true;
                a.sprintTogglesThisTick++;
                a.lastSprintToggleTick = tick;
            }
            case STOP_SPRINTING -> {
                a.sprinting = false;
                a.sprintTogglesThisTick++;
                a.lastSprintToggleTick = tick;
            }
            case START_SNEAKING -> {
                a.sneaking = true;
                a.sneakTogglesThisTick++;
                a.lastSneakToggleTick = tick;
            }
            case STOP_SNEAKING -> {
                a.sneaking = false;
                a.sneakTogglesThisTick++;
                a.lastSneakToggleTick = tick;
            }
            case START_FLYING_WITH_ELYTRA -> a.lastGlideRequestTick = tick;
            default -> {
            }
        }
    }

    private void handleInteract(PlayerData data, CheckSet checks, PacketReceiveEvent event) {
        WrapperPlayClientInteractEntity w = new WrapperPlayClientInteractEntity(event);
        ActionTracker a = data.actions();
        MovementTracker m = data.movement();
        long tick = m.clientTick;
        if (w.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            a.lastInteractTick = tick;
            return;
        }
        int index = a.attacksThisTick;
        a.attacksThisTick++;
        a.lastAttackTick = tick;
        a.lastAttackNanos = System.nanoTime();
        a.lastAttackedEntity = w.getEntityId();
        TrackedEntity target = data.entities().get(w.getEntityId());
        AttackUpdate attack = new AttackUpdate(w.getEntityId(), target, tick, m.x, m.y, m.z, m.yaw, m.pitch,
                a.sneaking, a.sprinting, data.vehicle().inVehicle(), index, a.swingsThisTick > 0);
        if (data.pendingAttacks.size() < 32) {
            data.pendingAttacks.add(attack);
        }
        for (CombatCheck c : checks.combat) {
            c.onAttackPacket(attack);
        }
    }

    private void resolveAttacks(PlayerData data, CheckSet checks, MoveUpdate move) {
        if (data.pendingAttacks.isEmpty()) return;
        AttackUpdate a;
        while ((a = data.pendingAttacks.poll()) != null) {
            CombatProcessor.resolve(data, a, move.yaw(), move.pitch());
            for (CombatCheck c : checks.combat) {
                c.onAttackResolved(a);
            }
        }
    }

    private void handleDigging(PlayerData data, PacketReceiveEvent event) {
        WrapperPlayClientPlayerDigging w = new WrapperPlayClientPlayerDigging(event);
        ActionTracker a = data.actions();
        long tick = data.movement().clientTick;
        switch (w.getAction()) {
            case START_DIGGING, CANCELLED_DIGGING, FINISHED_DIGGING -> a.lastDigTick = tick;
            case RELEASE_USE_ITEM -> {
                a.usingItem = false;
                a.lastReleaseTick = tick;
            }
            default -> {
            }
        }
    }

    private void handlePluginMessage(PlayerData data, PacketReceiveEvent event) {
        WrapperPlayClientPluginMessage w = new WrapperPlayClientPluginMessage(event);
        String channel = w.getChannelName();
        byte[] bytes = w.getData();
        if (channel == null || bytes == null) return;
        if (channel.equals("minecraft:brand") || channel.equals("MC|Brand")) {
            data.client().brand = readMcString(bytes);
        } else if (channel.equals("minecraft:register") || channel.equals("REGISTER")) {
            String all = new String(bytes, StandardCharsets.UTF_8);
            for (String ch : all.split("\0")) {
                if (!ch.isEmpty() && data.client().channels.size() < 256) {
                    data.client().channels.add(ch);
                }
            }
        }
    }

    /** Строка в формате Minecraft: VarInt длины + UTF-8. */
    static String readMcString(byte[] bytes) {
        int len = 0, shift = 0, i = 0;
        while (i < bytes.length && i < 5) {
            int b = bytes[i++] & 0xFF;
            len |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        if (len <= 0 || i + len > bytes.length) {
            return new String(bytes, StandardCharsets.UTF_8).replaceAll("[^\\p{Print}]", "");
        }
        String s = new String(bytes, i, len, StandardCharsets.UTF_8);
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    /**
     * Обработка пакета движения: обновляет трекер, определяет эхо телепорта,
     * формирует обновления ротации/движения и снимок для симуляции.
     *
     * @return {RotationUpdate или null, MoveUpdate}
     */
    private Object[] handleFlying(PlayerData data, WrapperPlayClientPlayerFlying w) {
        MovementTracker m = data.movement();
        boolean pos = w.hasPositionChanged();
        boolean rot = w.hasRotationChanged();
        Location loc = w.getLocation();
        long tick = m.clientTick;
        long confirmed = data.transactions().lastConfirmed();

        // ---- эхо телепорта ----
        TeleportTracker.Pending echo = null;
        TeleportTracker tp = data.teleports();
        if (tp.awaitingEcho != null) {
            TeleportTracker.Pending p = tp.awaitingEcho;
            tp.awaitingEcho = null;
            if (pos && rot) {
                double ex = p.relX ? m.x + p.x : p.x;
                double ey = p.relY ? m.y + p.y : p.y;
                double ez = p.relZ ? m.z + p.z : p.z;
                double tol = p.anyRelativePosition() ? 0.5 : 1.0E-4;
                if (Math.abs(loc.getX() - ex) < tol && Math.abs(loc.getY() - ey) < tol && Math.abs(loc.getZ() - ez) < tol) {
                    echo = p;
                }
            }
        }
        boolean teleport = echo != null;
        boolean teleportPending = tp.hasPending();

        // ---- эхо навязанного сервером поворота (1.21.2+: Look с onGround=false) ----
        if (!teleport && rot && !pos && !w.isOnGround() && !w.isHorizontalCollision()) {
            var forced = data.rotations().matchEcho(confirmed);
            if (forced != null) {
                boolean probe = forced.relYaw && forced.relPitch && forced.yaw == 0f && forced.pitch == 0f;
                return handleRotationEcho(data, loc, tick, probe);
            }
        }

        // ---- позиция ----
        m.lastX = m.x;
        m.lastY = m.y;
        m.lastZ = m.z;
        m.lastDeltaX = m.deltaX;
        m.lastDeltaY = m.deltaY;
        m.lastDeltaZ = m.deltaZ;
        m.lastDeltaXZ = m.deltaXZ;
        m.lastOnGround = m.onGround;
        if (pos) {
            if (!m.hasPosition) {
                m.resetTo(loc.getX(), loc.getY(), loc.getZ(), m.yaw, m.pitch);
            }
            m.x = loc.getX();
            m.y = loc.getY();
            m.z = loc.getZ();
            m.ticksWithoutPosition = 0;
        } else {
            m.ticksWithoutPosition++;
        }
        m.deltaX = m.x - m.lastX;
        m.deltaY = m.y - m.lastY;
        m.deltaZ = m.z - m.lastZ;
        m.deltaXZ = Math.sqrt(m.deltaX * m.deltaX + m.deltaZ * m.deltaZ);
        if (!teleport) {
            // Эхо телепорта всегда приходит с onGround=false — не учитываем его.
            m.onGround = w.isOnGround();
            m.horizontalCollision = w.isHorizontalCollision();
        }
        m.positionChanged = pos;
        m.movesThisTick++;
        if (teleport) {
            m.ticksSinceTeleport = 0;
            m.deltaX = m.deltaY = m.deltaZ = m.deltaXZ = 0;
        } else {
            m.ticksSinceTeleport++;
        }
        if (m.onGround) {
            m.groundTicks++;
            m.airTicks = 0;
        } else {
            m.airTicks++;
            m.groundTicks = 0;
        }

        // ---- ротация ----
        RotationUpdate rotationUpdate = null;
        m.rotationChanged = rot;
        if (rot) {
            m.lastYaw = m.yaw;
            m.lastPitch = m.pitch;
            m.lastDeltaYaw = m.deltaYaw;
            m.lastDeltaPitch = m.deltaPitch;
            m.yaw = loc.getYaw();
            m.pitch = loc.getPitch();
            m.deltaYaw = m.yaw - m.lastYaw;
            m.deltaPitch = m.pitch - m.lastPitch;
            boolean forced = data.rotations().possiblyApplied(confirmed, tick) || teleport;
            rotationUpdate = new RotationUpdate(tick, m.yaw, m.pitch, m.lastYaw, m.lastPitch,
                    m.deltaYaw, m.deltaPitch, m.lastDeltaYaw, m.lastDeltaPitch,
                    forced, data.vehicle().inVehicle(), teleport, pos);
        } else {
            m.deltaYaw = 0;
            m.deltaPitch = 0;
        }

        boolean inVehicle = data.vehicle().inVehicle();
        if (inVehicle) {
            data.vehicle().lastRidingTick = tick;
        }
        if (!data.loaded && pos) {
            data.loaded = true;
        }

        MoveUpdate moveUpdate = new MoveUpdate(tick, m.x, m.y, m.z, m.lastX, m.lastY, m.lastZ, m.yaw, m.pitch,
                pos, rot, m.onGround, m.lastOnGround, m.horizontalCollision, teleport, teleportPending, inVehicle);

        // ---- снимок для симуляции в главном потоке ----
        ActionTracker a = data.actions();
        var ab = data.abilities();
        MoveSnapshot snapshot = new MoveSnapshot(
                tick, System.nanoTime(),
                m.x, m.y, m.z, m.lastX, m.lastY, m.lastZ,
                m.yaw, m.pitch, m.lastYaw, m.lastPitch,
                pos, rot, m.onGround, m.lastOnGround, m.horizontalCollision,
                m.ticksWithoutPosition,
                a.hasInputPacket, a.inputForward, a.inputBackward, a.inputLeft, a.inputRight,
                a.inputJump, a.inputShift, a.inputSprint,
                a.sprinting, a.sneaking, a.usingItem, a.useHand == InteractionHand.OFF_HAND,
                a.lastGlideRequestTick,
                data.velocity().candidates(confirmed, tick, 2),
                data.velocity().hasPending(confirmed),
                echo, teleportPending, inVehicle,
                ab.flying, ab.allowFlight, ab.gameMode, ab.flySpeed, ab.pendingChange,
                data.client().sendsTickEnd);
        data.queueMain(d -> plugin.simulation().handle(d, snapshot));

        return new Object[]{rotationUpdate, moveUpdate};
    }

    /**
     * Эхо навязанного поворота: обновляем только ротацию (следующая дельта будет
     * чистым движением мыши), не трогаем onGround/позицию, не считаем тиком и не
     * отправляем в симуляцию.
     */
    private Object[] handleRotationEcho(PlayerData data, Location loc, long tick, boolean probe) {
        MovementTracker m = data.movement();
        data.lastEchoPrevYaw = m.yaw;
        data.lastEchoPrevPitch = m.pitch;
        data.lastEchoYaw = loc.getYaw();
        data.lastEchoPitch = loc.getPitch();
        data.lastEchoTick = tick;
        data.lastEchoWasProbe = probe;
        data.probeAwaitingMove = probe;
        m.lastYaw = m.yaw;
        m.lastPitch = m.pitch;
        m.yaw = loc.getYaw();
        m.pitch = loc.getPitch();
        float dYaw = m.yaw - m.lastYaw, dPitch = m.pitch - m.lastPitch;
        RotationUpdate update = new RotationUpdate(tick, m.yaw, m.pitch, m.lastYaw, m.lastPitch,
                dYaw, dPitch, m.lastDeltaYaw, m.lastDeltaPitch, true, data.vehicle().inVehicle(), false, false);
        data.rotationEchoes++;
        return new Object[]{update, null};
    }

    // =====================================================================
    // Исходящие
    // =====================================================================

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPacketType() instanceof PacketType.Play.Server type)) {
            return;
        }
        PlayerData data = plugin.players().get(event.getUser());
        if (data == null || data.checks() == null) return;
        try {
            outgoing.handle(data, type, event);
            for (PacketCheck c : data.checks().packet) {
                c.onPacketSend(event);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Ошибка обработки исходящего пакета " + type + " для " + data.name(), t);
        }
    }

    /** Удобный доступ для проверок: тип пакета как Play.Client или null. */
    public static PacketType.Play.Client clientType(PacketTypeCommon type) {
        return type instanceof PacketType.Play.Client c ? c : null;
    }
}
