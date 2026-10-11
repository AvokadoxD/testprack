package ru.strazh.ac.check.impl.world;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.util.AABB;
import ru.strazh.ac.util.Text;

/**
 * Установка/копание блока дальше атрибута block_interaction_range (4.5) от глаз
 * до ближайшей точки блока (Reach для блоков, Scaffold Expand, Nuker, CrystalAura
 * на дальних блоках).
 */
@CheckInfo(name = "BlockReach", type = "A", category = Category.WORLD,
        description = "Взаимодействие с блоком дальше допустимого", maxVl = 10, decay = 1.0)
public final class BlockReachA extends Check implements PacketCheck {

    private static final double[] EYES = {1.62, 1.27, 0.4};
    private double threshold;

    public BlockReachA(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        threshold = cfg("threshold", 0.1);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        Vector3i pos;
        String what;
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement w = new WrapperPlayClientPlayerBlockPlacement(event);
            if (w.getFace() == com.github.retrooper.packetevents.protocol.world.BlockFace.OTHER) return;
            pos = w.getBlockPosition();
            what = "place";
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging w = new WrapperPlayClientPlayerDigging(event);
            if (w.getAction() != DiggingAction.START_DIGGING) return;
            pos = w.getBlockPosition();
            what = "dig";
        } else {
            return;
        }
        if (data.inVehicleRecently(5) || !data.movement().hasPosition || data.client().bedrock) return;
        var m = data.movement();
        double scale = data.attributes().scale;
        AABB block = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        double best = Double.MAX_VALUE;
        for (double eh : EYES) {
            best = Math.min(best, Math.sqrt(block.distanceSquared(m.x, m.y + eh * scale, m.z)));
        }
        double max = data.attributes().blockRange;
        if (best > max + threshold) {
            event.setCancelled(true);
            if (increaseBuffer(1.0) > 1.0) {
                flag(what + " dist=" + Text.fmt(best, 2) + " max=" + Text.fmt(max, 1));
            }
        } else {
            decreaseBuffer(0.05);
        }
    }
}
