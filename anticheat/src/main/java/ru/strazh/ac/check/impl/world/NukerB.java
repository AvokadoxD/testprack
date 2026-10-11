package ru.strazh.ac.check.impl.world;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.MoveUpdate;
import ru.strazh.ac.util.AABB;
import ru.strazh.ac.util.McMath;
import ru.strazh.ac.util.Vec3;

/**
 * Начало копания блока, в который не смотрит игрок. Клиент начинает копать
 * блок под прицелом этого тика, поэтому луч из глаз с ротацией тика обязан
 * проходить через блок. Nuker, PacketMine и «копание за спиной» нарушают.
 */
@CheckInfo(name = "Nuker", type = "B", category = Category.WORLD,
        description = "Копание блока не под прицелом (Nuker, PacketMine)", maxVl = 10, decay = 1.0)
public final class NukerB extends Check implements PacketCheck, MovementCheck, TickCheck {

    private static final double[] EYES = {1.62, 1.27, 0.4};
    private boolean pending;
    private int bx, by, bz;
    private double fx, fy, fz;
    private float yawBefore, pitchBefore;

    public NukerB(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING || !data.client().sendsTickEnd) return;
        WrapperPlayClientPlayerDigging w = new WrapperPlayClientPlayerDigging(event);
        if (w.getAction() != DiggingAction.START_DIGGING || data.inVehicleRecently(5) || data.client().bedrock) return;
        Vector3i b = w.getBlockPosition();
        var m = data.movement();
        bx = b.getX();
        by = b.getY();
        bz = b.getZ();
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
        AABB block = new AABB(bx, by, bz, bx + 1, by + 1, bz + 1).expand(0.05);
        double scale = data.attributes().scale;
        double range = data.attributes().blockRange + 1.0;
        boolean hit = false;
        for (double eh : EYES) {
            for (int i = 0; i < 2 && !hit; i++) {
                Vec3 dir = McMath.viewVectorPrecise(i == 0 ? pitch : pitchBefore, i == 0 ? yaw : yawBefore);
                if (block.rayIntersect(fx, fy + eh * scale, fz, dir.x(), dir.y(), dir.z(), range) >= 0) hit = true;
            }
        }
        if (!hit) {
            if (increaseBuffer(1.0) > 2.0) {
                flag("блок " + bx + "," + by + "," + bz + " не под прицелом");
                buffer = 1.0;
            }
        } else {
            decreaseBuffer(0.1);
        }
    }
}
