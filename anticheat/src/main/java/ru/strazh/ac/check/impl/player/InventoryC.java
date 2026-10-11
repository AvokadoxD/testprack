package ru.strazh.ac.check.impl.player;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Невозможные клики:
 * <ul>
 *   <li>PICKUP_ALL (двойной клик) с пустым курсором — ванильно двойной клик
 *       собирает предметы только с предметом на курсоре; свап-движок Expensive
 *       так «пересинхронизирует» инвентарь;</li>
 *   <li>3+ клика по разным слотам между двумя тиками без перетаскивания
 *       (человек не успевает за один кадр) — ChestStealer/InvManager/AutoArmor.</li>
 * </ul>
 */
@CheckInfo(name = "Inventory", type = "C", category = Category.PLAYER,
        description = "Невозможные клики в инвентаре (пустой двойной клик, много кликов за кадр)", maxVl = 10, decay = 1.0)
public final class InventoryC extends Check implements PacketCheck, TickCheck {

    private final TickWindow window = new TickWindow();
    private int burst;
    private int lastSlot = Integer.MIN_VALUE;

    public InventoryC(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPacketType() instanceof PacketType.Play.Client t)) return;
        boolean between = window.onPacket(t);
        if (t != PacketType.Play.Client.CLICK_WINDOW) return;
        WrapperPlayClientClickWindow w = new WrapperPlayClientClickWindow(event);
        WrapperPlayClientClickWindow.WindowClickType type = w.getWindowClickType();
        if (type == WrapperPlayClientClickWindow.WindowClickType.PICKUP_ALL && carriedEmpty(w)) {
            flag("PICKUP_ALL без предмета, слот " + w.getSlot());
        }
        // Shift+двойной клик ванильно шлёт пачку QUICK_MOVE за кадр, перетаскивание — QUICK_CRAFT.
        if (type == WrapperPlayClientClickWindow.WindowClickType.QUICK_CRAFT
                || type == WrapperPlayClientClickWindow.WindowClickType.QUICK_MOVE
                || type == WrapperPlayClientClickWindow.WindowClickType.PICKUP_ALL
                || !data.client().sendsTickEnd || !between) return;
        if (w.getSlot() != lastSlot) {
            burst++;
            lastSlot = w.getSlot();
        }
        if (burst == 3) {
            if (increaseBuffer(1.0) > 2.0) flag("3+ разных слота за кадр (" + type + ")");
        }
    }

    private static boolean carriedEmpty(WrapperPlayClientClickWindow w) {
        try {
            var hashed = w.getCarriedHashedStack();
            if (hashed != null) return hashed.isEmpty();
        } catch (Throwable ignored) {
        }
        try {
            var stack = w.getCarriedItemStack();
            return stack == null || stack.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onClientTickEnd() {
        if (burst > 0 && burst < 3) decreaseBuffer(0.05);
        burst = 0;
        lastSlot = Integer.MIN_VALUE;
    }
}
