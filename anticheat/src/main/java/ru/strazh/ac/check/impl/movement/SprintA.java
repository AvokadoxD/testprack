package ru.strazh.ac.check.impl.movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Начало спринта, когда ванильный клиент не может бежать: голод ≤ 6 (берём ≤ 5
 * с запасом на задержку обновления сытости) или слепота.
 */
@CheckInfo(name = "Sprint", type = "A", category = Category.MOVEMENT,
        description = "Спринт при голоде или слепоте", maxVl = 10, decay = 1.0)
public final class SprintA extends Check implements PacketCheck {

    public SprintA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.ENTITY_ACTION) return;
        if (new WrapperPlayClientEntityAction(event).getAction() != WrapperPlayClientEntityAction.Action.START_SPRINTING) return;
        if (data.inVehicleRecently(5) || data.abilities().isCreativeOrSpectator() || data.abilities().allowFlight) return;
        data.queueMain(d -> {
            Player p = d.player();
            if (p == null || p.isInsideVehicle() || p.getAllowFlight()) return;
            String why = null;
            if (p.getFoodLevel() <= 5) why = "сытость " + p.getFoodLevel();
            else if (p.hasPotionEffect(PotionEffectType.BLINDNESS)
                    && System.currentTimeMillis() - d.lastEffectChangeMillis > d.ping() + 500) why = "слепота";
            if (why != null) {
                if (increaseBuffer(1.0) > 1.0) flag("старт спринта: " + why);
            } else {
                decreaseBuffer(0.1);
            }
        });
    }
}
