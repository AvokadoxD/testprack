package ru.strazh.ac.listener;

import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.tracker.TrackedEntity;
import ru.strazh.ac.player.update.AttackUpdate;
import ru.strazh.ac.util.AABB;
import ru.strazh.ac.util.McMath;
import ru.strazh.ac.util.Vec3;

/** Расчёт геометрии удара: дистанция, попадание луча, ошибка прицела (event loop). */
public final class CombatProcessor {

    /** Возможные высоты глаз игрока: стоя, крадучись, ползком/плавая/на элитрах. */
    private static final double[] EYE_HEIGHTS = {1.62, 1.27, 0.4};

    private CombatProcessor() {
    }

    public static void resolve(PlayerData data, AttackUpdate a, float yawAfter, float pitchAfter) {
        a.resolved = true;
        a.yawAfter = yawAfter;
        a.pitchAfter = pitchAfter;
        a.maxRange = data.attributes().entityRange;
        TrackedEntity t = a.target;
        if (t == null || a.inVehicle || t.vehicleId != -1) {
            a.uncertain = true;
            return;
        }
        if (!t.exactSize && !t.isPlayer) {
            a.uncertain = true;
        }
        long confirmed = data.transactions().lastConfirmed();
        int interp = t.living || t.isPlayer ? 3 : 1;
        long clientTick = data.movement().clientTick;
        AABB unbounded = t.possibleBox(confirmed, clientTick, interp + 1);
        double baseline = data.transactions().baselineRttMs();
        double maxAgeMs = Math.max(300.0, (baseline < 0 ? 300.0 : baseline) + 150.0);
        AABB box = t.possibleBox(confirmed, clientTick, interp + 1, (long) (maxAgeMs * 1_000_000L));
        if (box == null) box = unbounded;
        if (box == null) {
            a.uncertain = true;
            return;
        }
        double scale = data.attributes().scale;
        a.targetBox = box;
        double best = Double.MAX_VALUE;
        boolean hit = false;
        double bestYawErr = Double.MAX_VALUE, bestPitchErr = Double.MAX_VALUE;
        Vec3 center = box.center();
        for (double eh : EYE_HEIGHTS) {
            double ex = a.eyeX, ey = a.feetY + eh * scale, ez = a.eyeZ;
            double d = Math.sqrt(box.distanceSquared(ex, ey, ez));
            if (d < best) best = d;
            for (int i = 0; i < 2; i++) {
                float yaw = i == 0 ? a.yawBefore : yawAfter;
                float pitch = i == 0 ? a.pitchBefore : pitchAfter;
                Vec3 dir = McMath.viewVectorPrecise(pitch, yaw);
                double t0 = box.rayIntersect(ex, ey, ez, dir.x(), dir.y(), dir.z(), a.maxRange + 3.0);
                if (t0 >= 0) hit = true;
                double needYaw = McMath.yawTo(ex, ez, center.x(), center.z());
                double needPitch = McMath.pitchTo(ex, ey, ez, center.x(), center.y(), center.z());
                bestYawErr = Math.min(bestYawErr, Math.abs(McMath.angleDiff(yaw, needYaw)));
                bestPitchErr = Math.min(bestPitchErr, Math.abs(needPitch - pitch));
            }
        }
        a.distance = best;
        if (unbounded != null) {
            double bu = Double.MAX_VALUE;
            for (double eh : EYE_HEIGHTS) {
                bu = Math.min(bu, Math.sqrt(unbounded.distanceSquared(a.eyeX, a.feetY + eh * scale, a.eyeZ)));
            }
            a.distanceUnbounded = bu;
        }
        a.rayHit = hit;
        a.aimErrorYaw = bestYawErr;
        a.aimErrorPitch = bestPitchErr;
    }
}
