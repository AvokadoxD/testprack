package ru.strazh.ac.check.impl.player;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * AutoTotem: новый тотем в левую руку почти сразу после срабатывания старого
 * (статус сущности 35). Человеку нужно ≥ 6–10 тиков, автототему — 0–2.
 */
@CheckInfo(name = "Inventory", type = "E", category = Category.PLAYER,
        description = "Мгновенная замена тотема после срабатывания (AutoTotem)", maxVl = 6, decay = 0.5)
public final class InventoryE extends Check implements PacketCheck {

    private long popTick = -1000;

    public InventoryE(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_STATUS) return;
        WrapperPlayServerEntityStatus w = new WrapperPlayServerEntityStatus(event);
        if (w.getEntityId() != data.entityId || w.getStatus() != 35) return;
        var tx = data.transactions();
        tx.runOnConfirm(tx.nextPostSeq(), () -> popTick = data.movement().clientTick);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        long since = data.movement().clientTick - popTick;
        if (since > 2) return;
        boolean offhandSwap = false;
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow w = new WrapperPlayClientClickWindow(event);
            offhandSwap = (w.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.SWAP && w.getButton() == 40)
                    || (w.getWindowId() == 0 && w.getSlot() == 45);
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            offhandSwap = new WrapperPlayClientPlayerDigging(event).getAction() == DiggingAction.SWAP_ITEM_WITH_OFFHAND;
        }
        if (offhandSwap) {
            popTick = -1000;
            if (increaseBuffer(1.0) > 1.0) {
                flag("замена тотема через " + since + " тик.");
            }
        }
    }
}
