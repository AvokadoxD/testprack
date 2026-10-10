package ru.strazh.ac.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Перед выполнением команд наказания (главный поток). Команды можно изменить. */
public final class StrazhPunishEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String check;
    private final double vl;
    private final List<String> commands;
    private boolean cancelled;

    public StrazhPunishEvent(Player player, String check, double vl, List<String> commands) {
        this.player = player;
        this.check = check;
        this.vl = vl;
        this.commands = new ArrayList<>(commands);
    }

    public Player getPlayer() {
        return player;
    }

    public String getCheck() {
        return check;
    }

    public double getViolationLevel() {
        return vl;
    }

    /** Изменяемый список команд (уже с подставленными плейсхолдерами). */
    public List<String> getCommands() {
        return commands;
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
