package ru.strazh.ac.check.impl.aim;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.RotationCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.tracker.MovementTracker;
import ru.strazh.ac.player.tracker.TrackedEntity;
import ru.strazh.ac.player.update.RotationUpdate;
import ru.strazh.ac.util.AABB;
import ru.strazh.ac.util.McMath;
import ru.strazh.ac.util.Text;
import ru.strazh.ac.util.Vec3;

/**
 * Аимбот для ПКМ-оружия (лучевые пушки на Skript, луки, арбалеты): в тике выстрела
 * взгляд резко доворачивает на игрока, а в следующем тике уходит обратно. Так
 * выглядят тихий и «снап»-аимбот: камера человека остаётся на месте, сервер видит
 * рывок туда-обратно ровно вокруг выстрела. Считается доля таких выстрелов среди
 * выстрелов, луч которых попал в игрока.
 */
@CheckInfo(name = "Aim", type = "F", category = Category.COMBAT,
        description = "Доворот на игрока в момент выстрела ПКМ и возврат (аимбот/тихий аим для оружия)", maxVl = 10, decay = 1.0)
public final class AimF extends Check implements PacketCheck, RotationCheck, TickCheck {

    private static final double[] EYE_HEIGHTS = {1.62, 1.27, 0.4};

    private final float[] recent = new float[4];
    private int idx;
    /** 0 — ждём выстрел, 1 — снап в тике выстрела, 2 — ждём возврат в следующем тике. */
    private int state;
    private int waitTicks;
    private float snapDelta;
    private int shots, snaps;
    private double range, minSnap;
    private int sample;

    public AimF(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        range = cfg("range", 96.0);
        minSnap = cfg("min-snap-degrees", 10.0);
        sample = Math.max(5, cfg("sample-shots", 15));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.USE_ITEM) return;
        if (data.client().bedrock || data.inVehicleRecently(10) || !data.movement().hasPosition) return;
        if (data.rotations().possiblyApplied(data.transactions().lastConfirmed(), data.movement().clientTick)) return;
        WrapperPlayClientUseItem w = new WrapperPlayClientUseItem(event);
        MovementTracker m = data.movement();
        // До 1.21 в пакете нет углов — берём последние отправленные.
        float yaw = data.client().sendsTickEnd ? w.getYaw() : m.yaw;
        float pitch = data.client().sendsTickEnd ? w.getPitch() : m.pitch;
        if (!hitsPlayer(m, yaw, pitch)) return;
        shots++;
        if (state == 0) {
            float d = (float) McMath.angleDiff(yaw, m.yaw);
            float sum = 0;
            for (float r : recent) sum += r;
            float before = sum / recent.length;
            if (Math.abs(d) > minSnap && Math.abs(d) > 3f * Math.max(1f, before)) {
                state = 1;
                waitTicks = 0;
                snapDelta = d;
            }
        }
        if (shots >= sample) {
            double ratio = (double) snaps / shots;
            if (ratio >= 0.4) {
                flag(1.5, "снапов при выстреле " + snaps + "/" + shots + " (" + Text.fmt(ratio * 100, 0) + "%)");
            }
            shots = 0;
            snaps = 0;
        }
    }

    @Override
    public void onRotation(RotationUpdate u) {
        if (u.inVehicle()) return;
        if (!u.isMouseInput()) {
            state = 0;
            return;
        }
        float d = (float) McMath.angleDiff(u.yaw(), u.lastYaw());
        if (state == 1) {
            state = 2; // ротация самого тика выстрела
        } else if (state == 2) {
            state = 0;
            if (Math.abs(d) > 4f && Math.signum(d) != Math.signum(snapDelta)) {
                snaps++;
            }
        }
        recent[idx] = Math.abs(d);
        idx = (idx + 1) % recent.length;
    }

    @Override
    public void onClientTickEnd() {
        if (state == 1) {
            state = 0; // в тике выстрела ротация не пришла — снапа не было
        } else if (state == 2 && ++waitTicks > 1) {
            state = 0;
        }
    }

    private boolean hitsPlayer(MovementTracker m, float yaw, float pitch) {
        Vec3 dir = McMath.viewVectorPrecise(pitch, yaw);
        long confirmed = data.transactions().lastConfirmed();
        double scale = data.attributes().scale;
        for (TrackedEntity t : data.entities().all()) {
            if (!t.isPlayer || t.vehicleId != -1) continue;
            AABB box = t.possibleBox(confirmed, m.clientTick, 4);
            if (box == null) continue;
            box = box.expand(0.1);
            for (double eh : EYE_HEIGHTS) {
                if (box.rayIntersect(m.x, m.y + eh * scale, m.z, dir.x(), dir.y(), dir.z(), range) >= 0) return true;
            }
        }
        return false;
    }
}
