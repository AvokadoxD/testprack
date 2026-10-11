package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

/** Клиент сообщает о полёте, хотя сервер полёт не разрешал (Fly «Vanilla»/Abilities). */
@CheckInfo(name = "BadPackets", type = "D", category = Category.PACKET,
        description = "Полёт без разрешения сервера (пакет способностей)", maxVl = 5, decay = 1.0)
public final class BadPacketsD extends Check implements PacketCheck {

    public BadPacketsD(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_ABILITIES) return;
        WrapperPlayClientPlayerAbilities w = new WrapperPlayClientPlayerAbilities(event);
        var ab = data.abilities();
        if (w.isFlying() && !ab.allowFlight && !ab.pendingChange && !ab.isCreativeOrSpectator()
                && data.movement().clientTick - ab.lastChangeTick > 20) {
            flagSetback(1.0, "flying без allowFlight");
        }
    }
}
