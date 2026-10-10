package ru.strazh.ac.check.hook;

/** Вызывается в конце каждого клиентского тика (event loop). */
public interface TickCheck {

    void onClientTickEnd();
}
