package ru.strazh.ac.check.impl.world;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.MoveUpdate;
import ru.strazh.ac.util.McMath;
import ru.strazh.ac.util.Vec3;

/**
 * Взаимодействие сквозь блоки: луч взгляда тика первым упирается в другой
 * твёрдый блок, а не в тот, по которому кликнули/который начали копать
 * (ChestAura, GhostHand, Nuker сквозь стены). Проверка мира — в главном потоке.
 */
@CheckInfo(name = "GhostHand", type = "A", category = Category.WORLD,
        description = "Клик/копание сквозь блоки (GhostHand, ChestAura, Nuker сквозь стены)", maxVl = 10, decay = 1.0)
public final class GhostHandA extends Check implements PacketCheck, MovementCheck, TickCheck {

    private boolean pending;
    private int bx, by, bz;
    private double fx, fy, fz;
    private String what;

    public GhostHandA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!data.client().sendsTickEnd || data.client().bedrock || data.inVehicleRecently(5)) return;
        Vector3i b;
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement w = new WrapperPlayClientPlayerBlockPlacement(event);
            if (w.getFace() == null || w.getFace() == BlockFace.OTHER) return;
            b = w.getBlockPosition();
            what = "клик";
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging w = new WrapperPlayClientPlayerDigging(event);
            if (w.getAction() != DiggingAction.START_DIGGING) return;
            b = w.getBlockPosition();
            what = "копание";
        } else {
            return;
        }
        var m = data.movement();
        bx = b.getX();
        by = b.getY();
        bz = b.getZ();
        fx = m.x;
        fy = m.y;
        fz = m.z;
        pending = true;
    }

    @Override
    public void onMovement(MoveUpdate u) {
        if (!pending || u.teleport()) return;
        pending = false;
        schedule(u.yaw(), u.pitch());
    }

    @Override
    public void onClientTickEnd() {
        if (pending) {
            pending = false;
            schedule(data.movement().yaw, data.movement().pitch);
        }
    }

    private void schedule(float yaw, float pitch) {
        final int x = bx, y = by, z = bz;
        final double ex = fx, ez = fz, feet = fy, scale = data.attributes().scale;
        final String kind = what;
        data.queueMain(d -> {
            // Высота глаз может быть любой из поз (стоя/крадучись/ползком): чисто хотя бы для одной — не флагаем.
            String bad = null;
            for (double eh : new double[]{1.62, 1.27, 0.4}) {
                String r = check(x, y, z, ex, feet + eh * scale, ez, yaw, pitch, kind);
                if (r == null) {
                    decreaseBuffer(0.1);
                    return;
                }
                bad = r;
            }
            if (!bad.isEmpty() && increaseBuffer(1.0) > 1.5) {
                flag(bad);
                buffer = 1.0;
            }
        });
    }

    /** @return null — чисто, "" — не судим, иначе описание нарушения */
    private String check(int x, int y, int z, double ex, double ey, double ez, float yaw, float pitch, String kind) {
        Player p = data.player();
        if (p == null || System.currentTimeMillis() - data.lastBlockChangeMillis < 1000) return null;
        World w = p.getWorld();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        if (w.getBlockAt((int) Math.floor(ex), (int) Math.floor(ey), (int) Math.floor(ez)).getType().isOccluding()) {
            return ""; // глаз внутри блока при этой высоте — эта поза невозможна
        }
        Vec3 dir = McMath.viewVectorPrecise(pitch, yaw);
        double range = data.attributes().blockRange + 1.0;
        RayTraceResult hit = w.rayTraceBlocks(new Location(w, ex, ey, ez), new Vector(dir.x(), dir.y(), dir.z()),
                range, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) {
            return null; // луч не нашёл блока (неполные формы) — не судим
        }
        Block first = hit.getHitBlock();
        if (first.getX() == x && first.getY() == y && first.getZ() == z) {
            return null;
        }
        // Луч упёрся в другой блок, а целевой блок лежит дальше по тому же лучу.
        double distFirst = hit.getHitPosition().distance(new Vector(ex, ey, ez));
        double dTarget = new ru.strazh.ac.util.AABB(x, y, z, x + 1, y + 1, z + 1)
                .rayIntersect(ex, ey, ez, dir.x(), dir.y(), dir.z(), range);
        if (dTarget > distFirst + 0.2 && first.getType().isOccluding()) {
            return kind + " сквозь " + first.getType().name().toLowerCase();
        }
        return null;
    }
}
