package ru.strazh.ac.check;

import ru.strazh.ac.check.hook.BukkitCheck;
import ru.strazh.ac.check.hook.CombatCheck;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.RotationCheck;
import ru.strazh.ac.check.hook.ServerTickCheck;
import ru.strazh.ac.check.hook.SimulationCheck;
import ru.strazh.ac.check.hook.TickCheck;

import java.util.ArrayList;
import java.util.List;

/** Набор экземпляров проверок одного игрока, разложенный по типам хуков. */
public final class CheckSet {

    public final Check[] all;
    public final PacketCheck[] packet;
    public final RotationCheck[] rotation;
    public final MovementCheck[] movement;
    public final CombatCheck[] combat;
    public final TickCheck[] tick;
    public final SimulationCheck[] simulation;
    public final BukkitCheck[] bukkit;
    public final ServerTickCheck[] serverTick;

    public CheckSet(List<Check> checks) {
        this.all = checks.toArray(new Check[0]);
        this.packet = filter(checks, PacketCheck.class).toArray(new PacketCheck[0]);
        this.rotation = filter(checks, RotationCheck.class).toArray(new RotationCheck[0]);
        this.movement = filter(checks, MovementCheck.class).toArray(new MovementCheck[0]);
        this.combat = filter(checks, CombatCheck.class).toArray(new CombatCheck[0]);
        this.tick = filter(checks, TickCheck.class).toArray(new TickCheck[0]);
        this.simulation = filter(checks, SimulationCheck.class).toArray(new SimulationCheck[0]);
        this.bukkit = filter(checks, BukkitCheck.class).toArray(new BukkitCheck[0]);
        this.serverTick = filter(checks, ServerTickCheck.class).toArray(new ServerTickCheck[0]);
    }

    private static <T> List<T> filter(List<Check> checks, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Check c : checks) {
            if (type.isInstance(c)) out.add(type.cast(c));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public <T extends Check> T get(Class<T> type) {
        for (Check c : all) {
            if (type.isInstance(c)) return (T) c;
        }
        return null;
    }

    public Check byId(String id) {
        for (Check c : all) {
            if (c.id().equalsIgnoreCase(id)) return c;
        }
        return null;
    }
}
