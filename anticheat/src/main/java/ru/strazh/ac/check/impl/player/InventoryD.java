package ru.strazh.ac.check.impl.player;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.util.DoubleWindow;
import ru.strazh.ac.util.Stats;
import ru.strazh.ac.util.Text;

/**
 * ChestStealer/AutoBuy: реакция на открытие контейнера быстрее человеческой и
 * ровные интервалы между кликами. Время открытия берём по подтверждению
 * транзакции (клиент точно уже получил окно).
 */
@CheckInfo(name = "Inventory", type = "D", category = Category.PLAYER,
        description = "Слишком быстрая и ровная работа с контейнером (ChestStealer, AutoBuy)", maxVl = 10, decay = 1.0)
public final class InventoryD extends Check implements PacketCheck {

    private long openedNanos;
    private boolean firstClickPending;
    private long lastClickNanos;
    private final DoubleWindow intervals = new DoubleWindow(10);
    private int fastFirstClicks;

    public InventoryD(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.OPEN_WINDOW) return;
        var tx = data.transactions();
        tx.runOnConfirm(tx.nextPostSeq(), () -> {
            openedNanos = System.nanoTime();
            firstClickPending = true;
            intervals.clear();
            lastClickNanos = 0;
        });
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            firstClickPending = false;
            return;
        }
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW || !data.actions().containerOpen) return;
        WrapperPlayClientClickWindow w = new WrapperPlayClientClickWindow(event);
        if (w.getWindowId() == 0) return;
        long now = System.nanoTime();
        if (firstClickPending) {
            firstClickPending = false;
            double reaction = (now - openedNanos) / 1_000_000.0;
            if (reaction < 60) {
                if (++fastFirstClicks >= 3) {
                    flag("реакция на открытие " + Math.round(reaction) + " мс (x" + fastFirstClicks + ")");
                    fastFirstClicks = 1;
                }
            } else if (reaction > 150) {
                fastFirstClicks = Math.max(0, fastFirstClicks - 1);
            }
        }
        if (w.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.QUICK_MOVE
                || w.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.PICKUP) {
            if (lastClickNanos != 0) {
                double ms = (now - lastClickNanos) / 1_000_000.0;
                if (ms > 2 && ms < 400) intervals.add(ms);
            }
            lastClickNanos = now;
            if (intervals.isFull()) {
                double[] v = intervals.toArray();
                double mean = Stats.mean(v, v.length), sd = Stats.stdDev(v, v.length);
                if (mean < 130 && sd < 9) {
                    flag("клики каждые " + Text.fmt(mean, 0) + "±" + Text.fmt(sd, 1) + " мс");
                }
                intervals.clear();
            }
        }
    }
}
