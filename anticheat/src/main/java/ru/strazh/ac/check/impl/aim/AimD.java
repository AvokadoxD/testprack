package ru.strazh.ac.check.impl.aim;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerRotation;
import org.bukkit.entity.Player;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.check.hook.ServerTickCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.MoveUpdate;
import ru.strazh.ac.util.McMath;
import ru.strazh.ac.util.Text;

/**
 * Зонд «тихого» аима. В бою сервер иногда шлёт поворот камеры на 0° (невидимо).
 * Ванильный клиент отвечает эхом с углами КАМЕРЫ, и они лежат на пути между
 * прошлой и следующей ротацией в пакетах движения. Silent-аура сообщает в
 * пакетах движения углы цели, а камера смотрит в другую сторону — эхо
 * «выпрыгивает» из этого пути. Работает и против «человечных»/нейросетевых аур.
 */
@CheckInfo(name = "Aim", type = "D", category = Category.COMBAT,
        description = "Тихий аим: углы камеры не совпадают с отправляемой ротацией (зонд)", maxVl = 6, decay = 0.5)
public final class AimD extends Check implements MovementCheck, TickCheck, ServerTickCheck {

    private double threshold;
    private int minInterval;
    private volatile int nextProbeTick;
    private int lastServerTick;

    public AimD(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        threshold = cfg("threshold-degrees", 8.0);
        minInterval = cfg("min-interval-ticks", 60);
    }

    // ---- отправка зонда (главный поток) ----
    @Override
    public void onServerTick(int tick) {
        lastServerTick = tick;
        if (tick < nextProbeTick) return;
        Player p = data.player();
        if (p == null || p.isDead() || p.isInsideVehicle() || !data.loaded) return;
        if (!data.client().isNewerThanOrEquals(ClientVersion.V_1_21_2) || data.client().bedrock) return;
        // Только в бою: удар в последние ~3 секунды.
        if (System.nanoTime() - data.actions().lastAttackNanos > 3_000_000_000L) return;
        nextProbeTick = tick + minInterval + java.util.concurrent.ThreadLocalRandom.current().nextInt(minInterval);
        data.user().sendPacket(new WrapperPlayServerPlayerRotation(0f, true, 0f, true));
    }

    // ---- оценка ответа (event loop) ----
    @Override
    public void onMovement(MoveUpdate u) {
        if (!data.probeAwaitingMove) return;
        data.probeAwaitingMove = false;
        if (u.teleport()) return;
        if (u.rotationChanged()) evaluate(u.yaw(), u.pitch());
        else evaluate(data.lastEchoPrevYaw, data.lastEchoPrevPitch);
    }

    @Override
    public void onClientTickEnd() {
        if (data.probeAwaitingMove) {
            data.probeAwaitingMove = false;
            evaluate(data.lastEchoPrevYaw, data.lastEchoPrevPitch);
        }
    }

    private void evaluate(float nextYaw, float nextPitch) {
        if (data.vehicle().inVehicle()) return;
        double prevYaw = data.lastEchoPrevYaw, prevPitch = data.lastEchoPrevPitch;
        double camYaw = data.lastEchoYaw, camPitch = data.lastEchoPitch;
        double a = dist(camYaw, camPitch, prevYaw, prevPitch);
        double b = dist(nextYaw, nextPitch, camYaw, camPitch);
        double c = dist(nextYaw, nextPitch, prevYaw, prevPitch);
        double excess = a + b - c;
        if (excess > threshold) {
            data.addCombatSuspicion(2.0);
            if (increaseBuffer(1.0) > 1.0) {
                flag("камера отклонена на " + Text.fmt(excess / 2, 1) + "°");
            }
        } else {
            decreaseBuffer(0.25);
        }
    }

    private static double dist(double y1, double p1, double y2, double p2) {
        double dy = McMath.angleDiff(y1, y2);
        double dp = p1 - p2;
        return Math.sqrt(dy * dy + dp * dp);
    }
}
