package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.tracker.MovementTracker;
import ru.strazh.ac.util.Text;

/**
 * Ротация в пакете использования предмета (1.21+) не совпадает с ротацией тика.
 * Ванильный клиент кладёт в UseItem свои текущие углы, а мышь внутри тика не
 * двигается, поэтому они ровно равны углам пакета движения того же тика (или
 * последним отправленным, если ротация не менялась). Тихий аим для ПКМ-оружия,
 * луков, жемчуга и зелий, подменяющий только пакет использования, даёт другие углы.
 */
@CheckInfo(name = "BadPackets", type = "M", category = Category.PACKET,
        description = "Углы в пакете использования предмета не совпадают с углами тика (тихий аим для ПКМ)", maxVl = 8, decay = 0.5)
public final class BadPacketsM extends Check implements PacketCheck, TickCheck {

    private static final float EPSILON = 0.01f;

    private boolean pending;
    private float useYaw, usePitch;

    public BadPacketsM(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.USE_ITEM) return;
        if (!data.client().sendsTickEnd || data.client().bedrock || data.inVehicleRecently(10)) return;
        WrapperPlayClientUseItem w = new WrapperPlayClientUseItem(event);
        if (pending && (Math.abs(w.getYaw() - useYaw) > EPSILON || Math.abs(w.getPitch() - usePitch) > EPSILON)) {
            // Два использования в одном тике с разными углами — ванилла так не умеет.
            flag(1.0, "разные углы в двух UseItem одного тика");
        }
        pending = true;
        useYaw = w.getYaw();
        usePitch = w.getPitch();
    }

    @Override
    public void onClientTickEnd() {
        if (!pending) return;
        pending = false;
        MovementTracker m = data.movement();
        if (!m.hasPosition || data.inVehicleRecently(10)) return;
        float dYaw = Math.abs(useYaw - m.yaw), dPitch = Math.abs(usePitch - m.pitch);
        if (dYaw > EPSILON || dPitch > EPSILON) {
            if (increaseBuffer(1.0) > 1.0) {
                flag(1.0, "use=" + Text.fmt(useYaw, 2) + "/" + Text.fmt(usePitch, 2)
                        + " тик=" + Text.fmt(m.yaw, 2) + "/" + Text.fmt(m.pitch, 2));
            }
        } else {
            decreaseBuffer(0.02);
        }
    }
}
