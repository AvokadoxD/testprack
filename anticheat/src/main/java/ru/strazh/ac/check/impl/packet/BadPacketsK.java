package ru.strazh.ac.check.impl.packet;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.MoveUpdate;

/**
 * AirStuck/FreeCam/«заморозка»: клиент 1.21.2+ обязан прислать позицию хотя бы
 * раз в 20 тиков, а тики (ClientTickEnd) продолжают идти.
 */
@CheckInfo(name = "BadPackets", type = "K", category = Category.PACKET,
        description = "Нет позиции дольше 20 тиков при идущих тиках (AirStuck, FreeCam)", maxVl = 5, decay = 1.0)
public final class BadPacketsK extends Check implements MovementCheck, TickCheck {

    private int ticksWithoutPosition;

    public BadPacketsK(PlayerData data) {
        super(data);
    }

    @Override
    public void onMovement(MoveUpdate u) {
        if (u.positionChanged() || u.teleport()) ticksWithoutPosition = 0;
    }

    @Override
    public void onClientTickEnd() {
        if (!data.client().sendsTickEnd || !data.loaded || data.dead || data.inVehicleRecently(20)
                || data.abilities().isSpectator()
                || data.teleports().hasPending()) {
            ticksWithoutPosition = 0;
            return;
        }
        if (++ticksWithoutPosition == 30) {
            flagSetback(1.0, "нет позиции 30 тиков");
        }
    }
}
