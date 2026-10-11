package ru.strazh.ac.check.impl.player;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Действия при открытом экране инвентаря. Пока открыт экран, ванильный клиент
 * не обрабатывает клавиши: не бьёт, не использует и не ставит, не копает, не
 * меняет слот, не машет рукой, не начинает спринт и не двигается (при открытии
 * все клавиши отпускаются). Ловит GuiMove/InventoryWalk, свап-движки
 * (ClickPearl, AutoTotem, ElytraSwap, AutoPotion), NoSlow через инвентарь.
 *
 * <p>Экран считается открытым: контейнер, открытый сервером (подтверждён
 * транзакцией), или собственный инвентарь после клика в окне 0, сделанного
 * между тиками (из кадра), — до Close.
 */
@CheckInfo(name = "Inventory", type = "A", category = Category.PLAYER,
        description = "Действия и движение при открытом инвентаре (GuiMove, свапы, ClickPearl)", maxVl = 10, decay = 1.0)
public final class InventoryA extends Check implements PacketCheck {

    private final TickWindow window = new TickWindow();
    private boolean playerInventoryOpen;
    private int keysHeldOpens;
    private long containerCheckedTick = -1;

    public InventoryA(PlayerData data) {
        super(data);
    }

    private boolean movementKeysHeld() {
        var a = data.actions();
        return a.hasInputPacket && (a.inputForward || a.inputBackward || a.inputLeft || a.inputRight || a.inputJump);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPacketType() instanceof PacketType.Play.Client t)) return;
        boolean between = window.onPacket(t);
        var act = data.actions();
        switch (t) {
            case CLICK_WINDOW -> {
                if (act.openWindowId == 0 && between && data.client().sendsTickEnd) {
                    if (!playerInventoryOpen && movementKeysHeld() && !data.inVehicleRecently(5)) {
                        // Открытие экрана ванильно отпускает все клавиши (PlayerInput со всеми false
                        // приходит до первого клика) — а здесь клавиши движения всё ещё зажаты.
                        if (++keysHeldOpens >= 2) {
                            flag("клавиши движения зажаты при открытом инвентаре");
                            keysHeldOpens = 0;
                        }
                    }
                    playerInventoryOpen = true;
                }
                return;
            }
            case CLOSE_WINDOW -> {
                playerInventoryOpen = false;
                return;
            }
            default -> {
            }
        }
        if (!act.inventoryOpen) playerInventoryOpen = false;
        boolean open = act.containerOpen || playerInventoryOpen;
        if (!open || data.inVehicleRecently(5)) return;
        // Контейнер открыт уже 3+ тика, а клавиши движения так и не отпущены.
        long tick = data.movement().clientTick;
        if (act.containerOpen && t == PacketType.Play.Client.CLIENT_TICK_END && tick - act.lastWindowOpenTick >= 3
                && containerCheckedTick != act.lastWindowOpenTick && data.client().sendsTickEnd) {
            containerCheckedTick = act.lastWindowOpenTick;
            if (movementKeysHeld()) {
                flag("клавиши движения не отпущены при открытом контейнере");
            }
        }
        String what = switch (t) {
            case INTERACT_ENTITY -> "interact";
            case USE_ITEM -> "use";
            case PLAYER_BLOCK_PLACEMENT -> "place";
            case HELD_ITEM_CHANGE -> "slot";
            case ANIMATION -> "swing";
            case PLAYER_DIGGING -> {
                var a = new WrapperPlayClientPlayerDigging(event).getAction();
                yield switch (a) {
                    case START_DIGGING, DROP_ITEM, DROP_ITEM_STACK, SWAP_ITEM_WITH_OFFHAND, RELEASE_USE_ITEM -> "dig:" + a;
                    default -> null;
                };
            }
            case ENTITY_ACTION -> new WrapperPlayClientEntityAction(event).getAction()
                    == WrapperPlayClientEntityAction.Action.START_SPRINTING ? "sprint" : null;
            case PLAYER_INPUT -> {
                var w = new WrapperPlayClientPlayerInput(event);
                yield w.isForward() || w.isBackward() || w.isLeft() || w.isRight() || w.isJump() ? "move-keys" : null;
            }
            default -> null;
        };
        if (what != null) {
            if (t == PacketType.Play.Client.USE_ITEM || t == PacketType.Play.Client.INTERACT_ENTITY
                    || t == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
                event.setCancelled(true);
            }
            flag(what + (act.containerOpen ? " (контейнер)" : " (инвентарь)"));
        }
    }
}
