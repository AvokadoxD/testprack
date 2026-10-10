package ru.strazh.ac.player.tracker;

import com.github.retrooper.packetevents.protocol.player.InteractionHand;

/** Состояние действий игрока по пакетам (event loop). */
public final class ActionTracker {

    // --- ввод (PlayerInput, 1.21.2+) ---
    public boolean inputForward, inputBackward, inputLeft, inputRight, inputJump, inputShift, inputSprint;
    public boolean hasInputPacket;
    public long lastInputChangeTick;

    // --- спринт/присед ---
    public boolean sprinting;
    public boolean sneaking;
    public long lastSprintToggleTick = -1000;
    public long lastSneakToggleTick = -1000;
    public int sprintTogglesThisTick;
    public int sneakTogglesThisTick;

    // --- использование предметов ---
    public boolean usingItem;
    public InteractionHand useHand = InteractionHand.MAIN_HAND;
    public long useStartTick = -1000;
    public long lastReleaseTick = -1000;

    // --- слоты / инвентарь ---
    public int heldSlot;
    public long lastSlotChangeTick = -1000;
    public int slotChangesThisTick;
    /** id открытого контейнера (0 — инвентарь игрока или ничего). */
    public int openWindowId = -1;
    public boolean containerOpen;
    public long lastWindowOpenTick = -1000;
    public long lastWindowClickTick = -1000;
    public long lastWindowCloseTick = -1000;
    public int windowClicksThisTick;

    // --- бой ---
    public long lastAttackTick = -1000;
    public long lastSwingTick = -1000;
    public long lastAttackNanos;
    public int attacksThisTick;
    public int lastAttackedEntity = -1;
    public int swingsThisTick;
    public long lastInteractTick = -1000;

    // --- блоки ---
    public long lastPlaceTick = -1000;
    public int placesThisTick;
    public long lastDigTick = -1000;
    public long lastUseItemTick = -1000;
    public int useItemsThisTick;

    // --- элитры ---
    public long lastGlideRequestTick = -1000;

    /** Сброс счётчиков «за тик» на границе клиентского тика. */
    public void onTickEnd() {
        sprintTogglesThisTick = 0;
        sneakTogglesThisTick = 0;
        slotChangesThisTick = 0;
        attacksThisTick = 0;
        swingsThisTick = 0;
        placesThisTick = 0;
        useItemsThisTick = 0;
        windowClicksThisTick = 0;
    }
}
