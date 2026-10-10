package ru.strazh.ac.player;

/**
 * Транспорт игрока. Часть полей ведётся в event loop по пакетам SetPassengers,
 * часть — в главном потоке по Bukkit-событиям (тэги Skript-машин).
 */
public final class VehicleState {

    // ---- event loop ----
    /** id транспорта по данным клиента (после подтверждения транзакцией), -1 — пешком. */
    public int clientVehicleId = -1;
    /** Сервер посадил/высадил, но клиент ещё не подтвердил. */
    public boolean changePending;
    public long lastRidingTick = -1000;

    // ---- главный поток ----
    public volatile boolean bukkitInVehicle;
    /** Транспорт — сиденье кастомной машины (тэги car_seat/car_main/moped_main...). */
    public volatile boolean onCustomVehicle;
    public volatile String vehicleType = "";
    public volatile long lastDismountMillis;
    public volatile long lastMountMillis;
    /** Сервер телепортировал игрока вместе с транспортом/после высадки. */
    public volatile long lastVehicleTeleportMillis;

    public boolean inVehicle() {
        return clientVehicleId != -1 || bukkitInVehicle || changePending;
    }
}
