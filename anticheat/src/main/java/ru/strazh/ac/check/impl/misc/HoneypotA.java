package ru.strazh.ac.check.impl.misc;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.honeypot.HoneypotService;
import ru.strazh.ac.player.PlayerData;

/** Удар по ловушке — фейковому игроку за спиной, на которого человек навести прицел не может. */
@CheckInfo(name = "Honeypot", type = "A", category = Category.COMBAT,
        description = "Удар по невидимой для прицела ловушке за спиной (KillAura)", maxVl = 4, decay = 0.25)
public final class HoneypotA extends Check implements PacketCheck {

    public HoneypotA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        StrazhPlugin plugin = StrazhPlugin.get();
        if (plugin == null || !(plugin.honeypots() instanceof HoneypotService hp)) return;
        WrapperPlayClientInteractEntity w = new WrapperPlayClientInteractEntity(event);
        if (!hp.isBotFor(data, w.getEntityId())) return;
        event.setCancelled(true);
        if (w.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            data.addCombatSuspicion(3.0);
            flag(2.0, "удар по ловушке за спиной");
        }
    }
}
