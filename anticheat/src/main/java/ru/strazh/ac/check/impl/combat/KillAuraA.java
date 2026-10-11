package ru.strazh.ac.check.impl.combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.AttackUpdate;
import ru.strazh.ac.util.Text;

/**
 * Статистический «портрет» ауры по 30 ударам. Ауры бьют по кулдауну (почти все
 * интервалы одинаковые, 10–12 тиков), почти только критами, не машут мимо и
 * сбрасывают спринт ровно на тик вокруг каждого удара. Человек промахивается,
 * сбивается с ритма и не держит идеальную дисциплину. Флаг — если совпали
 * хотя бы 3 из 4 признаков.
 */
@CheckInfo(name = "KillAura", type = "A", category = Category.COMBAT,
        description = "Статистика ударов как у ауры (ритм, криты, нет промахов, сброс спринта)", maxVl = 8, decay = 0.5)
public final class KillAuraA extends Check implements CombatCheck, PacketCheck, TickCheck {

    private static final int WINDOW = 30;
    private final int[] intervals = new int[WINDOW];
    private int count;
    private long lastAttackTick = -1;
    private int crits, sprintResets, airSwings;
    private boolean attackedThisTick, swungThisTick;
    private long lastSprintStop = -100, lastSprintStart = -100;
    private long pendingResetCheckTick = -1;

    public KillAuraA(PlayerData data) {
        super(data);
    }

    @Override
    public void onAttackPacket(AttackUpdate a) {
        if (a.inVehicle) return;
        attackedThisTick = true;
        long tick = a.clientTick;
        if (lastAttackTick >= 0) {
            long gap = tick - lastAttackTick;
            if (gap >= 1 && gap <= 40) {
                intervals[count % WINDOW] = (int) gap;
                count++;
                var m = data.movement();
                if (!m.onGround && m.deltaY < 0) crits++;
                if (tick - lastSprintStop <= 1) pendingResetCheckTick = tick;
            }
        }
        lastAttackTick = tick;
        if (count >= WINDOW) evaluate();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            swungThisTick = true;
        } else if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            var a = new WrapperPlayClientEntityAction(event).getAction();
            long tick = data.movement().clientTick;
            if (a == WrapperPlayClientEntityAction.Action.STOP_SPRINTING) lastSprintStop = tick;
            if (a == WrapperPlayClientEntityAction.Action.START_SPRINTING) {
                lastSprintStart = tick;
                if (pendingResetCheckTick >= 0 && tick - pendingResetCheckTick <= 1) {
                    sprintResets++;
                    pendingResetCheckTick = -1;
                }
            }
        }
    }

    @Override
    public void onClientTickEnd() {
        long tick = data.movement().clientTick;
        var act = data.actions();
        boolean busy = tick - act.lastDigTick < 3 || tick - act.lastPlaceTick < 2 || tick - act.lastUseItemTick < 2;
        // Взмах без удара в бою (последний удар недавно) — промах.
        if (swungThisTick && !attackedThisTick && !busy && lastAttackTick >= 0 && tick - lastAttackTick < 60) {
            airSwings++;
        }
        swungThisTick = false;
        attackedThisTick = false;
        if (pendingResetCheckTick >= 0 && tick - pendingResetCheckTick > 2) pendingResetCheckTick = -1;
    }

    private void evaluate() {
        int[] freq = new int[41];
        for (int v : intervals) freq[v]++;
        int mode = 0, modeCount = 0;
        for (int i = 1; i < freq.length; i++) {
            if (freq[i] > modeCount) {
                modeCount = freq[i];
                mode = i;
            }
        }
        double cadence = (double) modeCount / WINDOW;
        double critRatio = (double) crits / WINDOW;
        double resetRatio = (double) sprintResets / WINDOW;
        int features = 0;
        if (cadence >= 0.7 && mode >= 9) features++;
        if (critRatio >= 0.9) features++;
        if (airSwings == 0) features++;
        if (resetRatio >= 0.8) features++;
        if (features >= 3) {
            data.addCombatSuspicion(2.0);
            flag("ритм " + Text.fmt(cadence * 100, 0) + "% по " + mode + " т., криты " + Text.fmt(critRatio * 100, 0)
                    + "%, промахов " + airSwings + ", сброс спринта " + Text.fmt(resetRatio * 100, 0) + "%");
        }
        count = 0;
        crits = 0;
        sprintResets = 0;
        airSwings = 0;
    }
}
