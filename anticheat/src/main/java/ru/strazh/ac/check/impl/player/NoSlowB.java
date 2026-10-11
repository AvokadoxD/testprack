package ru.strazh.ac.check.impl.player;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.BukkitCheck;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Пакетные обходы замедления при использовании предмета:
 * <ul>
 *   <li>повторный «использовать» (часто левой рукой), пока предмет уже
 *       используется — ванильный клиент глушит нажатия, пока ест/натягивает;</li>
 *   <li>«отпустить» и снова «использовать» в одном тике.</li>
 * </ul>
 */
@CheckInfo(name = "NoSlow", type = "B", category = Category.PLAYER,
        description = "Пакетный обход замедления (повторное использование во время использования)", maxVl = 10, decay = 1.0)
public final class NoSlowB extends Check implements PacketCheck, TickCheck, BukkitCheck {

    private boolean releasedThisTick;

    public NoSlowB(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            if (new WrapperPlayClientPlayerDigging(event).getAction() == DiggingAction.RELEASE_USE_ITEM) releasedThisTick = true;
        } else if (event.getPacketType() == PacketType.Play.Client.USE_ITEM && releasedThisTick && data.client().sendsTickEnd) {
            if (increaseBuffer(1.0) > 2.0) flag("release+use в одном тике");
        }
    }

    @Override
    public void onClientTickEnd() {
        releasedThisTick = false;
    }

    @Override
    public void onBukkitEvent(Event event) {
        if (!(event instanceof PlayerInteractEvent e)) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!p.hasActiveItem() || p.getActiveItemRemainingTime() <= 3 || p.isInsideVehicle()) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getHand() != null && e.getHand() != p.getActiveItemHand()) return;
        if (increaseBuffer(1.0) > 2.0) {
            flag("use во время использования " + p.getActiveItem().getType().name().toLowerCase()
                    + " (" + e.getHand() + ")");
        }
    }
}
