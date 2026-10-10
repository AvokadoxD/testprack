package ru.strazh.ac.check.hook;

import ru.strazh.ac.player.update.MoveUpdate;

/** Проверка пакетов движения без доступа к миру (event loop). */
public interface MovementCheck {

    void onMovement(MoveUpdate update);
}
