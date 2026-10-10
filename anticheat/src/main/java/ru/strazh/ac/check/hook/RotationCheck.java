package ru.strazh.ac.check.hook;

import ru.strazh.ac.player.update.RotationUpdate;

/** Проверка ротаций (event loop). Вызывается на каждый пакет с изменением ротации. */
public interface RotationCheck {

    void onRotation(RotationUpdate update);
}
