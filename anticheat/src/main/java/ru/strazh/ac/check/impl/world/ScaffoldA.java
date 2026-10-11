package ru.strazh.ac.check.impl.world;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.MoveUpdate;
import ru.strazh.ac.util.McMath;
import ru.strazh.ac.util.Text;
import ru.strazh.ac.util.Vec3;

/**
 * Установка блока, не соответствующая взгляду. Клиент ставит блок туда, куда
 * упирается луч прицела этого тика: точка клика (блок + курсор) лежит на луче,
 * а глаз находится снаружи кликнутой грани. Scaffold/AirPlace/Surround без
 * ротаций и «тихие» ротации, выставленные не в тот тик, это нарушают.
 * Ротацию берём из пакета движения того же тика (он идёт после установки).
 */
@CheckInfo(name = "Scaffold", type = "A", category = Category.WORLD,
        description = "Установка блока не туда, куда смотрит игрок (Scaffold, Surround, AirPlace)", maxVl = 10, decay = 1.0)
public final class ScaffoldA extends Check implements PacketCheck, MovementCheck, TickCheck {

    private static final double[] EYES = {1.62, 1.27, 0.4};
    private boolean pending;
    private double hx, hy, hz;
    private BlockFace face;
    private double fx, fy, fz;
    private float yawBefore, pitchBefore;
    private double maxAngle;

    public ScaffoldA(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        maxAngle = cfg("max-angle", 6.0);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT || !data.client().sendsTickEnd) return;
        WrapperPlayClientPlayerBlockPlacement w = new WrapperPlayClientPlayerBlockPlacement(event);
        BlockFace f = w.getFace();
        if (f == null || f == BlockFace.OTHER || data.inVehicleRecently(5) || data.client().bedrock) return;
        Vector3i b = w.getBlockPosition();
        Vector3f c = w.getCursorPosition();
        if (c.getX() < -0.001 || c.getX() > 1.001 || c.getY() < -0.001 || c.getY() > 1.001 || c.getZ() < -0.001 || c.getZ() > 1.001) {
            event.setCancelled(true);
            flag("курсор вне блока " + c.getX() + "," + c.getY() + "," + c.getZ());
            return;
        }
        var m = data.movement();
        hx = b.getX() + c.getX();
        hy = b.getY() + c.getY();
        hz = b.getZ() + c.getZ();
        face = f;
        fx = m.x;
        fy = m.y;
        fz = m.z;
        yawBefore = m.yaw;
        pitchBefore = m.pitch;
        pending = true;
    }

    @Override
    public void onMovement(MoveUpdate u) {
        if (!pending || u.teleport()) return;
        pending = false;
        evaluate(u.yaw(), u.pitch());
    }

    @Override
    public void onClientTickEnd() {
        if (pending) {
            pending = false;
            evaluate(data.movement().yaw, data.movement().pitch);
        }
    }

    private void evaluate(float yaw, float pitch) {
        double scale = data.attributes().scale;
        double bestAngle = Double.MAX_VALUE;
        boolean faceVisible = false;
        for (double eh : EYES) {
            double ex = fx, ey = fy + eh * scale, ez = fz;
            double dx = hx - ex, dy = hy - ey, dz = hz - ez;
            // Глаз должен быть снаружи грани: (глаз - точка) · нормаль > 0.
            double side = -(dx * face.getModX() + dy * face.getModY() + dz * face.getModZ());
            if (side > -0.01) faceVisible = true;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.3) {
                bestAngle = 0;
                continue;
            }
            for (int i = 0; i < 2; i++) {
                Vec3 look = McMath.viewVectorPrecise(i == 0 ? pitch : pitchBefore, i == 0 ? yaw : yawBefore);
                double cos = (look.x() * dx + look.y() * dy + look.z() * dz) / len;
                bestAngle = Math.min(bestAngle, Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos)))));
            }
        }
        if (!faceVisible) {
            if (increaseBuffer(1.0) > 1.0) flag("грань " + face + " не видна из глаз");
        } else if (bestAngle > maxAngle) {
            if (increaseBuffer(1.0) > 2.0) flag("угол до точки клика " + Text.fmt(bestAngle, 1) + "°");
        } else {
            decreaseBuffer(0.1);
        }
    }
}
