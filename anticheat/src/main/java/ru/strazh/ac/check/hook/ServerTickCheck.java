package ru.strazh.ac.check.hook;

/** Вызывается каждый серверный тик (главный поток). */
public interface ServerTickCheck {

    void onServerTick(int currentTick);
}
