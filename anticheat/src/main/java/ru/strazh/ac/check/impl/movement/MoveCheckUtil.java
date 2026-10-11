package ru.strazh.ac.check.impl.movement;

import ru.strazh.ac.sim.SimulationResult;
import ru.strazh.ac.sim.SimulationResult.Uncertainty;

/** Общие условия для проверок движения. */
final class MoveCheckUtil {

    private MoveCheckUtil() {
    }

    /** Ситуации, в которых модель движения ненадёжна вообще. */
    static boolean unreliable(SimulationResult r) {
        return r.skip || r.has(Uncertainty.ABILITIES) || r.has(Uncertainty.PISTON) || r.has(Uncertainty.SKIPPED_POSITION)
                || r.has(Uncertainty.UNLOADED_CHUNK) || r.has(Uncertainty.TELEPORT);
    }

    /** Окружение, где вертикальная модель не применяется. */
    static boolean verticalUnreliable(SimulationResult r) {
        return unreliable(r) || Double.isNaN(r.predictedDy) || r.has(Uncertainty.BLOCK_CHANGE)
                || r.has(Uncertainty.ENTITY_COLLISION) || r.has(Uncertainty.VELOCITY) || r.has(Uncertainty.LIQUID)
                || r.has(Uncertainty.CLIMBABLE) || r.has(Uncertainty.WEB) || r.has(Uncertainty.BOUNCE)
                || r.has(Uncertainty.BUBBLE_COLUMN) || r.has(Uncertainty.HONEY) || r.has(Uncertainty.POWDER_SNOW)
                || r.has(Uncertainty.SCAFFOLDING);
    }
}
