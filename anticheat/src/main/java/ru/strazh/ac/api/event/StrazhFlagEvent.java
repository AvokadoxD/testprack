package ru.strazh.ac.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Игрок провалил проверку. Может вызываться асинхронно (из сетевого потока),
 * см. {@link #isAsynchronous()}. Отмена события не засчитывает нарушение.
 */
public final class StrazhFlagEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String check;
    private final String type;
    private final String category;
    private final double vl;
    private final String details;
    private boolean cancelled;

    public StrazhFlagEvent(boolean async, Player player, String check, String type, String category, double vl, String details) {
        super(async);
        this.player = player;
        this.check = check;
        this.type = type;
        this.category = category;
        this.vl = vl;
        this.details = details;
    }

    public Player getPlayer() {
        return player;
    }

    /** Имя проверки, например "Reach". */
    public String getCheck() {
        return check;
    }

    /** Подтип, например "A". */
    public String getType() {
        return type;
    }

    /** Категория: COMBAT, MOVEMENT, WORLD, PLAYER, PACKET, MISC. */
    public String getCategory() {
        return category;
    }

    public double getViolationLevel() {
        return vl;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
