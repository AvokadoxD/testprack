package ru.strazh.ac.check.impl.combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.AttackUpdate;

/** Удар без взмаха рукой в том же тике (NoSwing, серверный NoSwing у аур). */
@CheckInfo(name = "NoSwing", type = "A", category = Category.COMBAT,
        description = "Удар без взмаха рукой", maxVl = 8, decay = 1.0)
public final class NoSwingA extends Check implements CombatCheck, PacketCheck, TickCheck {

    private int attacks, swingsAfter;

    public NoSwingA(PlayerData data) {
        super(data);
    }

    @Override
    public void onAttackPacket(AttackUpdate a) {
        attacks++;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ANIMATION && attacks > 0) swingsAfter++;
    }

    @Override
    public void onClientTickEnd() {
        if (data.client().sendsTickEnd && attacks > 0 && swingsAfter == 0) {
            if (increaseBuffer(1.0) > 1.0) flag("ударов " + attacks + ", взмахов 0");
        } else if (attacks > 0) {
            decreaseBuffer(0.25);
        }
        attacks = 0;
        swingsAfter = 0;
    }
}
