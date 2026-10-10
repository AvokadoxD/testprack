package ru.strazh.ac.player;

/** Способности и режим игры по данным клиента (event loop, применяются по транзакциям). */
public final class AbilityState {

    public boolean flying;
    public boolean allowFlight;
    public boolean creative;
    public boolean invulnerable;
    public float flySpeed = 0.05f;
    public float walkSpeed = 0.1f;
    /** 0 выживание, 1 творчество, 2 приключение, 3 наблюдатель, -1 неизвестно. */
    public int gameMode = -1;
    /** Изменение способностей отправлено, но не подтверждено клиентом. */
    public boolean pendingChange;
    public long lastChangeTick = -1000;
    /** Клиентский тик, когда клиент последний раз сообщил, что летит. */
    public long lastClientFlyingTick = -1000;

    public boolean isSpectator() {
        return gameMode == 3;
    }

    public boolean isCreativeOrSpectator() {
        return gameMode == 1 || gameMode == 3 || creative;
    }
}
