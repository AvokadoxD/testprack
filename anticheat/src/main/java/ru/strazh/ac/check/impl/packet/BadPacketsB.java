package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Смена слота: повтор того же слота, слот вне 0-8, больше двух смен за тик или
 * «A→B→A» без взаимодействия после — так работают NoSlow/AutoBlock/свап-движки.
 * Ванильный клиент отправляет слот только при изменении: в начале тика и
 * перед взаимодействием.
 */
@CheckInfo(name = "BadPackets", type = "B", category = Category.PACKET,
        description = "Невозможная смена слота (повтор, A→B→A, слишком часто)", maxVl = 10, decay = 1.0)
public final class BadPacketsB extends Check implements PacketCheck, TickCheck {

    private int lastSlot = -1;
    private int changesThisTick;
    private int slotBeforeTick = -1;
    private boolean interactionAfterLastChange = true;

    public BadPacketsB(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPacketType() instanceof PacketType.Play.Client t)) return;
        switch (t) {
            case HELD_ITEM_CHANGE -> {
                int slot = new WrapperPlayClientHeldItemChange(event).getSlot();
                if (slot < 0 || slot > 8) {
                    event.setCancelled(true);
                    flag(2.0, "slot=" + slot);
                    return;
                }
                if (slot == lastSlot) {
                    flag("same-slot=" + slot);
                }
                if (changesThisTick == 0) slotBeforeTick = lastSlot;
                changesThisTick++;
                lastSlot = slot;
                interactionAfterLastChange = false;
                if (changesThisTick > 2) {
                    flag("changes/tick=" + changesThisTick);
                }
            }
            case INTERACT_ENTITY, USE_ITEM, PLAYER_BLOCK_PLACEMENT, PLAYER_DIGGING -> interactionAfterLastChange = true;
            default -> {
            }
        }
    }

    @Override
    public void onClientTickEnd() {
        if (data.client().sendsTickEnd && changesThisTick == 2 && lastSlot == slotBeforeTick && !interactionAfterLastChange) {
            flag("A-B-A без действия, слот " + lastSlot);
        }
        changesThisTick = 0;
    }
}
