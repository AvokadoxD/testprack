package ru.strazh.ac.check.impl.combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.util.DoubleWindow;
import ru.strazh.ac.util.Stats;
import ru.strazh.ac.util.Text;

/**
 * Автокликер по взмахам руки (ЛКМ), без копания блоков:
 * <ul>
 *   <li>CPS выше предела несколько секунд подряд;</li>
 *   <li>слишком ровные интервалы в тиках (робот);</li>
 *   <li>на высоком CPS нет ни одного «двойного клика в тике» — так работают
 *       кликеры, жмущие строго раз в тик (у человека при 15+ CPS два клика в
 *       одном 50-мс окне неизбежно случаются).</li>
 * </ul>
 */
@CheckInfo(name = "AutoClicker", type = "A", category = Category.COMBAT,
        description = "Автокликер: превышение CPS и роботизированные интервалы", maxVl = 15, decay = 1.5)
public final class AutoClickerA extends Check implements PacketCheck, TickCheck {

    private int maxCps;
    private final DoubleWindow intervals = new DoubleWindow(80);
    private final int[] perTick = new int[20];
    private int tickIndex;
    private int swingsThisTick;
    private long lastSwingTick = -1;
    private int highCpsSeconds;
    private int doubleTicks, highRateClicks;

    public AutoClickerA(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        maxCps = cfg("max-cps", 25);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.ANIMATION) return;
        var a = data.actions();
        long tick = data.movement().clientTick;
        // Копание, установка и использование тоже машут рукой — не считаем.
        if (tick - a.lastDigTick < 3 || tick - a.lastPlaceTick < 2 || tick - a.lastUseItemTick < 2) return;
        swingsThisTick++;
        if (lastSwingTick >= 0) {
            long gap = tick - lastSwingTick;
            if (gap <= 10) intervals.add(gap);
        }
        lastSwingTick = tick;
    }

    @Override
    public void onClientTickEnd() {
        if (!data.client().sendsTickEnd) return;
        perTick[tickIndex] = swingsThisTick;
        tickIndex = (tickIndex + 1) % perTick.length;
        if (swingsThisTick >= 2) doubleTicks++;
        swingsThisTick = 0;
        if (tickIndex != 0) return;
        // Раз в секунду (20 тиков).
        int cps = 0;
        for (int c : perTick) cps += c;
        if (cps > maxCps) {
            if (++highCpsSeconds >= 3) {
                flag("cps=" + cps + " (" + highCpsSeconds + " с подряд)");
            }
        } else {
            highCpsSeconds = 0;
        }
        if (cps >= 15) {
            highRateClicks += cps;
            if (highRateClicks >= 150) {
                if (doubleTicks == 0) {
                    flag(1.5, "нет двойных кликов за " + highRateClicks + " кликов на 15+ CPS");
                }
                highRateClicks = 0;
                doubleTicks = 0;
            }
        } else if (cps < 5) {
            highRateClicks = 0;
            doubleTicks = 0;
        }
        if (intervals.isFull()) {
            double[] v = intervals.toArray();
            double mean = Stats.mean(v, v.length);
            double sd = Stats.stdDev(v, v.length);
            int distinct = Stats.distinct(v, v.length, 0.5);
            if (mean <= 3.0 && (sd < 0.25 || distinct == 1)) {
                if (increaseBuffer(1.0) > 2.0) {
                    flag("интервалы mean=" + Text.fmt(mean, 2) + " sd=" + Text.fmt(sd, 3) + " разных=" + distinct);
                }
            } else {
                decreaseBuffer(0.5);
            }
            intervals.clear();
        }
    }
}
