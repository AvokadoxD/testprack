package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.MoveUpdate;
import ru.strazh.ac.util.McMath;

/**
 * Углы в пакете UseItem не совпадают с ротацией этого же тика. В 1.21 сервер
 * использует углы UseItem для броска (жемчуг, зелья, заряды ветра), а между
 * UseItem и пакетом движения одного тика кадров нет — у ванильного клиента они
 * одинаковы. Ловит «тихие» броски AutoPotion/ClickPearl/HighJump/Spider.
 */
@CheckInfo(name = "BadPackets", type = "H", category = Category.PACKET,
        description = "Углы использования предмета не совпадают с ротацией тика (тихий бросок)", maxVl = 8, decay = 1.0)
public final class BadPacketsH extends Check implements PacketCheck, MovementCheck, TickCheck {

    private boolean pending;
    private float useYaw, usePitch;

    public BadPacketsH(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.USE_ITEM) return;
        if (!data.client().sendsTickEnd) return;
        WrapperPlayClientUseItem w = new WrapperPlayClientUseItem(event);
        useYaw = w.getYaw();
        usePitch = w.getPitch();
        pending = true;
    }

    @Override
    public void onMovement(MoveUpdate u) {
        if (!pending || u.teleport()) return;
        pending = false;
        compare(u.yaw(), u.pitch());
    }

    @Override
    public void onClientTickEnd() {
        if (pending) {
            pending = false;
            // Движения в тике не было — ротация не менялась с прошлого пакета.
            compare(data.movement().yaw, data.movement().pitch);
        }
    }

    private void compare(float yaw, float pitch) {
        if (data.inVehicleRecently(3) || !Float.isFinite(useYaw)) return;
        double dy = Math.abs(McMath.angleDiff(yaw, useYaw));
        double dp = Math.abs(pitch - usePitch);
        if (dy > 0.01 || dp > 0.01) {
            if (increaseBuffer(1.0) > 1.0) {
                flag("use=" + Math.round(useYaw) + "/" + Math.round(usePitch) + " tick=" + Math.round(yaw) + "/" + Math.round(pitch));
            }
        } else {
            decreaseBuffer(0.1);
        }
    }
}
