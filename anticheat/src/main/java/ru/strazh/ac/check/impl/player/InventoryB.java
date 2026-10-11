package ru.strazh.ac.check.impl.player;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Удар в первом тике после клика/закрытия окна. Пока открыт экран, кулдаун
 * атаки клиента 10000, и он сбрасывается только в конце обработки ввода
 * первого тика после закрытия — удар в этом тике невозможен. Ловит
 * ShieldBreaker, свап-движки «GRIM/same-tick», авто-свап оружия.
 */
@CheckInfo(name = "Inventory", type = "B", category = Category.PLAYER,
        description = "Удар сразу после клика/закрытия инвентаря (свап оружия, ShieldBreaker)", maxVl = 8, decay = 1.0)
public final class InventoryB extends Check implements PacketCheck {

    private final TickWindow window = new TickWindow();
    private boolean screenEventBetweenTicks;
    private boolean tickHadContent;

    public InventoryB(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPacketType() instanceof PacketType.Play.Client t) || !data.client().sendsTickEnd) return;
        boolean between = window.onPacket(t);
        switch (t) {
            case CLICK_WINDOW, CLOSE_WINDOW -> {
                if (between) screenEventBetweenTicks = true;
            }
            case CLIENT_TICK_END -> {
                // Окно «следующего тика» закончилось.
                if (tickHadContent) screenEventBetweenTicks = false;
                tickHadContent = false;
            }
            case INTERACT_ENTITY -> {
                tickHadContent = true;
                if (screenEventBetweenTicks && new WrapperPlayClientInteractEntity(event).getAction()
                        == WrapperPlayClientInteractEntity.InteractAction.ATTACK && !data.inVehicleRecently(5)) {
                    event.setCancelled(true);
                    flag("удар в тике после клика/закрытия окна");
                }
            }
            default -> {
                if (!between) tickHadContent = true;
            }
        }
    }
}
