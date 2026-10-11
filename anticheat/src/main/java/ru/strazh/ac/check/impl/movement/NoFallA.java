package ru.strazh.ac.check.impl.movement;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.SimulationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.sim.SimulationResult;

/**
 * Подмена земли: клиент сообщает «на земле», а под ногами нет опоры
 * (NoFall «Spoof», Jesus «Solid» — опора жидкость, GroundSpoof для обхода
 * проверок), либо упорно сообщает «в воздухе», стоя на блоке (NoFall «NoGround»).
 */
@CheckInfo(name = "NoFall", type = "A", category = Category.MOVEMENT,
        description = "Подмена состояния «на земле» (NoFall, GroundSpoof, Jesus)", maxVl = 10, decay = 1.5, setback = true)
public final class NoFallA extends Check implements SimulationCheck {

    private int spoofTicks;

    public NoFallA(PlayerData data) {
        super(data);
    }

    @Override
    public void onSimulation(SimulationResult r) {
        if (MoveCheckUtil.unreliable(r)) {
            spoofTicks = 0;
            return;
        }
        if (r.groundSpoof) {
            if (++spoofTicks >= 2) {
                flagSetback(1.0, r.groundSpoofOnLiquid ? "«на земле» над жидкостью (Jesus)" : "«на земле» без опоры");
                spoofTicks = 0;
            }
        } else {
            spoofTicks = 0;
        }
        if (r.noGroundSpoof) {
            if (increaseBuffer(1.0) > 3.0) {
                flag("«в воздухе» стоя на блоке");
                buffer = 0;
            }
        } else {
            decreaseBuffer(0.05);
        }
    }
}
