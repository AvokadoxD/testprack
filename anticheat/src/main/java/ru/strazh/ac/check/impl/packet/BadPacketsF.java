package ru.strazh.ac.check.impl.packet;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.MovementCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.MoveUpdate;

/**
 * Больше одного пакета движения за клиентский тик (1.21.2+). Ванильный клиент
 * шлёт не больше одного (эхо телепорта/поворота не считаются). Ловит пакетные
 * Criticals/NoFall, обходы Velocity с дублирующим Full и «таймер внутри тика».
 */
@CheckInfo(name = "BadPackets", type = "F", category = Category.PACKET,
        description = "Несколько пакетов движения за один тик (пакетные криты, NoFall, Velocity)", maxVl = 10, decay = 1.0)
public final class BadPacketsF extends Check implements MovementCheck, TickCheck {

    private int moves;

    public BadPacketsF(PlayerData data) {
        super(data);
    }

    @Override
    public void onMovement(MoveUpdate u) {
        if (u.teleport() || !data.client().sendsTickEnd) return;
        moves++;
        if (moves == 2) {
            flag("moves/tick=2 dy=" + ru.strazh.ac.util.Text.fmt(u.deltaY(), 4));
        }
    }

    @Override
    public void onClientTickEnd() {
        moves = 0;
    }
}
