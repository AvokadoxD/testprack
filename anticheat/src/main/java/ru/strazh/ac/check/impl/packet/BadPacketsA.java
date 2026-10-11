package ru.strazh.ac.check.impl.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

/** Невозможные значения в пакете движения: NaN/бесконечность, pitch вне [-90; 90], огромные координаты. */
@CheckInfo(name = "BadPackets", type = "A", category = Category.PACKET,
        description = "Невозможные координаты или углы в пакете движения", maxVl = 3, decay = 0.5)
public final class BadPacketsA extends Check implements PacketCheck {

    public BadPacketsA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        WrapperPlayClientPlayerFlying w = event.getLastUsedWrapper() instanceof WrapperPlayClientPlayerFlying f
                ? f : new WrapperPlayClientPlayerFlying(event);
        Location l = w.getLocation();
        String bad = null;
        if (w.hasPositionChanged()) {
            if (!Double.isFinite(l.getX()) || !Double.isFinite(l.getY()) || !Double.isFinite(l.getZ())) bad = "nan-pos";
            else if (Math.abs(l.getX()) > 3.0E7 || Math.abs(l.getZ()) > 3.0E7 || Math.abs(l.getY()) > 2.0E7) bad = "far-pos";
        }
        if (bad == null && w.hasRotationChanged()) {
            if (!Float.isFinite(l.getYaw()) || !Float.isFinite(l.getPitch())) bad = "nan-rot";
            else if (Math.abs(l.getPitch()) > 90.0001f) bad = "pitch=" + l.getPitch();
        }
        if (bad != null) {
            event.setCancelled(true);
            flag(bad);
        }
    }
}
