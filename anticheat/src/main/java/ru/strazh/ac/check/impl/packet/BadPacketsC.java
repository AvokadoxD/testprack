package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Удар/взаимодействие с самим собой или с сущностью, которую сервер клиенту не
 * отправлял (предсказание id в CrystalAura/AnchorAura: удар по кристаллу до его
 * появления у клиента).
 */
@CheckInfo(name = "BadPackets", type = "C", category = Category.PACKET,
        description = "Удар по себе или по сущности, неизвестной клиенту (предсказание id)", maxVl = 10, decay = 1.0)
public final class BadPacketsC extends Check implements PacketCheck {

    public BadPacketsC(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity w = new WrapperPlayClientInteractEntity(event);
        int id = w.getEntityId();
        if (id == data.entityId) {
            event.setCancelled(true);
            flag(2.0, "self-interact");
            return;
        }
        if (w.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
        if (data.entities().get(id) == null && data.entities().size() > 0 && data.movement().clientTick > 100) {
            event.setCancelled(true);
            if (increaseBuffer(1.0) > 1.0) {
                flag("unknown-id=" + id);
            }
        } else {
            decreaseBuffer(0.02);
        }
    }
}
