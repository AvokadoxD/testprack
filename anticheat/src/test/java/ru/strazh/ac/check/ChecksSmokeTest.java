package ru.strazh.ac.check;

import org.junit.jupiter.api.Test;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.check.hook.RotationCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.config.CheckSettings;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.AttackUpdate;
import ru.strazh.ac.player.update.MoveUpdate;
import ru.strazh.ac.player.update.RotationUpdate;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChecksSmokeTest {

    @Test
    void allChecksHaveUniqueIdsAndBuildAsPrototypes() throws Exception {
        Set<String> ids = new HashSet<>();
        for (Class<? extends Check> type : Checks.ALL) {
            Constructor<? extends Check> c = type.getDeclaredConstructor(PlayerData.class);
            Check check = c.newInstance((PlayerData) null);
            check.applySettings(new CheckSettings(check.id(), check.info(), null));
            assertTrue(ids.add(check.id()), "дубликат id " + check.id());
            assertFalse(check.info().description().isBlank());
        }
        assertTrue(Checks.ALL.size() >= 40, "проверок: " + Checks.ALL.size());
    }

    /** Обычная игра без читов: хождение, повороты мыши, удары по цели в упор — без падений и почти без флагов. */
    @Test
    void legitTrafficDoesNotCrash() {
        List<String> flags = new ArrayList<>();
        TestHooks.setHandler((check, details) -> {
            flags.add(check.id() + ": " + details);
            return true;
        });
        PlayerData d = PlayerData.forTest(pd -> {
            List<Check> list = new ArrayList<>();
            for (Class<? extends Check> type : Checks.ALL) {
                try {
                    list.add(type.getDeclaredConstructor(PlayerData.class).newInstance(pd));
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
            return list;
        });
        Random r = new Random(7);
        float yaw = 0, pitch = 0;
        double x = 0, y = 64, z = 0;
        double sens = 0.5 * 0.6 + 0.2, step = sens * sens * sens * 8 * 0.15;
        for (int tick = 0; tick < 400; tick++) {
            float lastYaw = yaw, lastPitch = pitch;
            yaw += (float) (step * (r.nextInt(21) - 10));
            pitch = (float) Math.max(-90, Math.min(90, pitch + step * (r.nextInt(7) - 3)));
            double lx = x, lz = z;
            x += 0.2;
            d.movement().lastX = lx;
            d.movement().x = x;
            d.movement().y = y;
            d.movement().z = z;
            d.movement().yaw = yaw;
            d.movement().pitch = pitch;
            d.movement().onGround = true;
            d.movement().clientTick = tick;
            RotationUpdate ru = new RotationUpdate(tick, yaw, pitch, lastYaw, lastPitch, yaw - lastYaw, pitch - lastPitch,
                    0, 0, false, false, false, true);
            for (RotationCheck c : d.checks().rotation) c.onRotation(ru);
            MoveUpdate mu = new MoveUpdate(tick, x, y, z, lx, y, lz, yaw, pitch, true, true, true, true, false, false, false, false);
            for (MovementCheck c : d.checks().movement) c.onMovement(mu);
            if (tick % 12 == 0) {
                AttackUpdate a = new AttackUpdate(5, null, tick, x, y, z, lastYaw, lastPitch, false, true, false, 0, false);
                for (CombatCheck c : d.checks().combat) c.onAttackPacket(a);
                a.resolved = true;
                a.yawAfter = yaw;
                a.pitchAfter = pitch;
                a.uncertain = true;
                for (CombatCheck c : d.checks().combat) c.onAttackResolved(a);
            }
            for (TickCheck c : d.checks().tick) c.onClientTickEnd();
        }
        // Удары без пакета взмаха в этом синтетическом трафике не моделируются (NoSwing),
        // остальные проверки не должны срабатывать.
        flags.removeIf(f -> f.startsWith("noswing_a"));
        assertTrue(flags.isEmpty(), "ложные флаги: " + flags);
    }
}
