package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.MoveUpdate;

/**
 * Порядок пакетов внутри тика (1.21.2+). Ванильный клиент: [слот] [действия]
 * [PlayerInput] [спринт] [движение] [ClientTickEnd]. Клики/закрытие окон —
 * только между тиками. Действие после пакета движения в том же тике значит,
 * что пакет отправлен не из обработки ввода: «пост»-ауры, свапы, пакетные
 * модули по событию после движения.
 */
@CheckInfo(name = "PacketOrder", type = "A", category = Category.PACKET,
        description = "Действие после пакета движения внутри тика (пост-аура, пакетные модули)", maxVl = 10, decay = 1.0)
public final class PacketOrderA extends Check implements PacketCheck, MovementCheck, TickCheck {

    private boolean moved;

    public PacketOrderA(PlayerData data) {
        super(data);
    }

    @Override
    public void onMovement(MoveUpdate u) {
        if (!u.teleport()) moved = true;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!moved || !(event.getPacketType() instanceof PacketType.Play.Client t)) return;
        if (!data.client().sendsTickEnd || data.inVehicleRecently(5)) return;
        String what = switch (t) {
            case INTERACT_ENTITY -> "interact";
            case USE_ITEM -> "use";
            case PLAYER_BLOCK_PLACEMENT -> "place";
            case PLAYER_DIGGING -> "dig";
            case HELD_ITEM_CHANGE -> "slot";
            case ANIMATION -> "swing";
            case PLAYER_INPUT -> "input";
            case CLICK_WINDOW -> "click";
            case CLOSE_WINDOW -> "close";
            case ENTITY_ACTION -> {
                var a = new WrapperPlayClientEntityAction(event).getAction();
                yield a == WrapperPlayClientEntityAction.Action.START_SPRINTING
                        || a == WrapperPlayClientEntityAction.Action.STOP_SPRINTING ? "sprint" : null;
            }
            default -> null;
        };
        if (what != null) {
            flag(what + " после движения");
        }
    }

    @Override
    public void onClientTickEnd() {
        moved = false;
    }
}
