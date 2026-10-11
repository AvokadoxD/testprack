package ru.strazh.ac.check.impl.combat;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.AttackUpdate;
import ru.strazh.ac.util.AABB;
import ru.strazh.ac.util.McMath;
import ru.strazh.ac.util.Text;
import ru.strazh.ac.util.Vec3;

/**
 * Удар сквозь блок: луч взгляда упирается в блок раньше, чем доходит до цели.
 * Проверяется в главном потоке (чтение мира), по ротации тика удара.
 */
@CheckInfo(name = "WallHit", type = "A", category = Category.COMBAT,
        description = "Удар сквозь стену/блок", maxVl = 8, decay = 1.0)
public final class WallHitA extends Check implements CombatCheck {

    public WallHitA(PlayerData data) {
        super(data);
    }

    @Override
    public void onAttackResolved(AttackUpdate a) {
        if (a.uncertain || a.targetBox == null || !a.rayHit || a.distance < 0) return;
        final AABB box = a.targetBox;
        final double ex = a.eyeX, ez = a.eyeZ;
        final double feet = a.feetY, scale = data.attributes().scale;
        final float yaw = a.yawAfter, pitch = a.pitchAfter;
        data.queueMain(d -> {
            String bad = null;
            for (double eh : new double[]{1.62, 1.27, 0.4}) {
                String r = check(box, ex, feet + eh * scale, ez, yaw, pitch);
                if (r == null) {
                    decreaseBuffer(0.1);
                    return;
                }
                if (!r.isEmpty()) bad = r;
            }
            if (bad != null && increaseBuffer(1.0) > 1.5) {
                flag(bad);
            }
        });
    }

    /** @return null — чисто, "" — эта высота глаз невозможна, иначе описание нарушения */
    private String check(AABB box, double ex, double ey, double ez, float yaw, float pitch) {
        Player p = data.player();
        if (p == null) return null;
        World w = p.getWorld();
        Vec3 dir = McMath.viewVectorPrecise(pitch, yaw);
        double entityDist = box.rayIntersect(ex, ey, ez, dir.x(), dir.y(), dir.z(), 8.0);
        if (entityDist < 0) return ""; // с этой высоты луч мимо — судит Hitbox
        if (entityDist <= 0.05) return null;
        if (!w.isChunkLoaded((int) Math.floor(ex) >> 4, (int) Math.floor(ez) >> 4)) return null;
        if (w.getBlockAt((int) Math.floor(ex), (int) Math.floor(ey), (int) Math.floor(ez)).getType().isOccluding()) return "";
        RayTraceResult hit = w.rayTraceBlocks(new Location(w, ex, ey, ez), new Vector(dir.x(), dir.y(), dir.z()),
                entityDist, FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitBlock() != null) {
            double blockDist = hit.getHitPosition().distance(new Vector(ex, ey, ez));
            if (blockDist + 0.1 < entityDist) {
                return "блок " + hit.getHitBlock().getType().name().toLowerCase() + " на " + Text.fmt(blockDist, 2)
                        + ", цель на " + Text.fmt(entityDist, 2);
            }
        }
        return null;
    }
}
