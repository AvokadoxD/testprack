package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Спринт: больше одного переключения за тик (KeepSprint/WTap/Rage-ауры шлют
 * STOP+атака+START в одном тике) и старт спринта без нажатой «вперёд» по PlayerInput
 * (OmniSprint). Ванильный клиент отправляет состояние спринта один раз за тик
 * перед пакетом движения.
 */
@CheckInfo(name = "BadPackets", type = "G", category = Category.PACKET,
        description = "Невозможное переключение спринта (несколько за тик, без «вперёд»)", maxVl = 10, decay = 1.0)
public final class BadPacketsG extends Check implements PacketCheck, TickCheck {

    private int toggles;

    public BadPacketsG(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.ENTITY_ACTION) return;
        WrapperPlayClientEntityAction.Action a = new WrapperPlayClientEntityAction(event).getAction();
        if (a != WrapperPlayClientEntityAction.Action.START_SPRINTING && a != WrapperPlayClientEntityAction.Action.STOP_SPRINTING) return;
        if (!data.client().sendsTickEnd || data.inVehicleRecently(5)) return;
        toggles++;
        if (toggles == 2) {
            flag("sprint toggles/tick=2");
        }
        var act = data.actions();
        if (a == WrapperPlayClientEntityAction.Action.START_SPRINTING && act.hasInputPacket && !act.inputForward
                && !data.abilities().flying && data.movement().clientTick - act.lastInputChangeTick > 1) {
            if (increaseBuffer(1.0) > 1.0) flag("start sprint без W");
        } else if (a == WrapperPlayClientEntityAction.Action.START_SPRINTING) {
            decreaseBuffer(0.1);
        }
    }

    @Override
    public void onClientTickEnd() {
        toggles = 0;
    }
}
