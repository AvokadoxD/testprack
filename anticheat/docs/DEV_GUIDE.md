# StrazhAC — руководство разработчика проверок

Этот документ — контракт для всех, кто пишет проверки. Прочитай целиком перед началом.

## 1. Архитектура и потоки

```
Netty event loop игрока (PacketEvents)            Главный поток сервера
──────────────────────────────────────            ──────────────────────────────
PacketProcessor.onPacketReceive                   TickListener.onTickStart:
  ├─ обновление трекеров (movement, actions,        ├─ data.drainMainQueue()  ← снимки движения
  │   transactions, teleports, rotations...)       │     → SimulationService → SimulationCheck
  ├─ PacketCheck.onPacketReceive (все пакеты)      ├─ откаты (setback)
  ├─ RotationCheck.onRotation                      └─ ServerTickCheck.onServerTick
  ├─ CombatCheck.onAttackPacket / onAttackResolved TickListener.onTickEnd: пинг-транзакция каждому
  ├─ MovementCheck.onMovement                      BukkitListener: события → BukkitCheck
  └─ TickCheck.onClientTickEnd                       (перед вызовом очередь игрока уже обработана)
OutgoingProcessor.onPacketSend → PacketCheck.onPacketSend
```

* **Event loop канала игрока однопоточный**: приём и отправка пакетов одного игрока
  идут в одном потоке, поэтому трекеры (`data.movement()`, `actions()`, `velocity()`,
  `teleports()`, `rotations()`, `entities()`, `abilities()`, `transactions()`) читаются
  и пишутся без блокировок. **Не трогай эти трекеры из главного потока.**
* **Мир (блоки) читать можно ТОЛЬКО в главном потоке.** Чтение блоков из Netty в Paper
  может заблокировать поток или загрузить чанк. Если пакетной проверке нужен мир —
  поставь работу в очередь: `data.queueMain(d -> { ... })`. Очередь выполняется строго в
  порядке пакетов (в начале каждого тика и перед раздачей Bukkit-событий).
* Bukkit-API игрока (`player.getLocation()`, эффекты, инвентарь) — только главный поток.
  Исключения (потокобезопасно): `player.sendMessage`, `player.getPing()`, `getUniqueId()`.

## 2. Транзакции и компенсация задержки

`TransactionManager`: каждый серверный тик (и сразу после важных пакетов) клиенту
отправляется Ping с нашим id. Клиент отвечает Pong при обработке, строго по порядку.

* `tx.lastSent()` — последний пинг, записанный в канал (в порядке байтов на проводе).
* `tx.lastConfirmed()` — последний пинг, на который клиент ответил.
* Серверный пакет X, записанный между пингами N и N+1: до Pong(N) клиент X не применил,
  после Pong(N+1) — точно применил. В `OutgoingProcessor` это `pre = lastSent()`,
  `post = nextPostSeq()`. Используй `tx.runOnConfirm(post, runnable)` для применения
  состояния, которое клиент получит вместе с пакетом.
* Уже реализовано: скорость/взрывы (`VelocityTracker`), телепорты (`TeleportTracker`,
  эхо телепорта помечается `MoveUpdate.teleport()`), навязанные сервером повороты камеры
  (`RotationTracker`, флаг `RotationUpdate.serverForced()`), сущности для reach
  (`EntityTracker`, `TrackedEntity.possibleBox`), способности/режим игры (`AbilityState`),
  атрибуты (`AttributeState`: entity/block interaction range, scale, step height...),
  транспорт (`VehicleState`), открытые окна.

## 3. Как написать проверку

```java
package ru.strazh.ac.check.impl.combat;

@CheckInfo(name = "Reach", type = "A", category = Category.COMBAT,
        description = "Удар дальше допустимой дистанции", maxVl = 10, decay = 2.0)
public final class ReachA extends Check implements CombatCheck {

    private double threshold;

    public ReachA(PlayerData data) {   // ТОЛЬКО сохранить data. data может быть null (прототип)!
        super(data);
    }

    @Override
    protected void onReload() {        // параметры из конфига → поля
        threshold = cfg("threshold", 0.0005);
    }

    @Override
    public void onAttackResolved(AttackUpdate a) {
        if (a.uncertain || a.distance < 0) return;
        double excess = a.distance - a.maxRange;
        if (excess > threshold) {
            if (increaseBuffer(1.0) > 1.0) {
                flag(1.0, "dist=" + Text.fmt(a.distance) + " max=" + Text.fmt(a.maxRange));
            }
        } else {
            decreaseBuffer(0.05);
        }
    }
}
```

* Аннотация `@CheckInfo`: `name`, `type` (A, B, C...), `category`, `description` (по-русски,
  коротко — это видят модераторы), `maxVl` (наказание), `decay` (VL/мин), `alertVl`,
  `punish`, `setback`, `experimental` (true → по умолчанию только алерты).
* Хуки (реализуй нужные интерфейсы из `ru.strazh.ac.check.hook`):
  `PacketCheck`, `RotationCheck`, `MovementCheck`, `CombatCheck`, `TickCheck`
  (Netty), `SimulationCheck`, `BukkitCheck`, `ServerTickCheck` (главный поток).
* `flag(weight, details)` — засчитать нарушение. Возвращает false, если игрок в
  исключении / проверка выключена. `flagSetback(...)` — флаг + откат (если в конфиге
  `setback: true`). Используй **буферы** (`increaseBuffer`, `decreaseBuffer`) — одиночный
  выброс не должен сразу давать флаг, если проверка эвристическая.
* Регистрация: добавь класс в список своей категории
  (`check/impl/<категория>/<Категория>Checks.java`). **Не редактируй** чужие списки и
  `Checks.java`.
* Отмена пакета: в `onPacketReceive` можно `event.setCancelled(true)` (например, удар
  через стену или невозможный пакет). Отменяй только при полной уверенности.
* Детали флага — коротко и по делу (`dist=3.41 max=3.00`), без переводов строк.

## 4. Ложные срабатывания — обязательные защиты

Античит ценен только без ложных срабатываний. Каждая проверка обязана учитывать:

1. **Транспорт и машины Skript.** Игрок сидит на `item_display`-сиденье (тэги `car_seat`,
   `car_main`, `car_id_*`), сервер каждый тик телепортирует сиденье (до ~3 блоков/тик),
   поворачивает камеру пакетом `PlayerRotation` (`RotationUpdate.serverForced()`), при
   авариях дёргает pitch, выставляет скорость пешеходам и пассажирам при ДТП/взрыве,
   высаживает и телепортирует к двери. Правило: при `data.vehicle().inVehicle()` или
   `data.inVehicleRecently(N)` проверки движения/аима не работают; после высадки
   действует льготный период (исключение категории movement выставляет BukkitListener).
   Отменённые сервером Q (гудок), F (фары), ПКМ ключом — нормальные пакеты.
   gun.sk стреляет ПКМ-лучами — не ограничивай ПКМ-клики разумного темпа.
2. **Навязанные повороты камеры**: `RotationUpdate.serverForced()` / `teleport()` → такой
   поворот не анализировать (и следующий тоже — флаг держится тик).
3. **Телепорты**: `MoveUpdate.teleport()` (эхо) и `teleportPending()` → пропуск.
4. **Скорость от сервера** (удары, взрывы, машины, ветряные заряды, булава): только через
   симуляцию, которая учитывает `VelocityTracker`.
5. **Лаги**: низкий TPS (`Bukkit.getTPS()` из главного потока или кэш), большой/скачущий
   пинг (`data.transactions().rttStdDev()`), пачки пакетов после фриза. Таймер-проверки
   должны допускать «догон» после лага.
6. **Режим игры/способности**: творчество, наблюдатель, полёт (`data.abilities()`).
7. **Bedrock (Geyser)**: `data.client().bedrock` — другая физика/ввод (ViolationManager
   в мягком режиме не наказывает Bedrock по движению и бою, но лучше пропускать).
8. **Атрибуты 1.21**: scale, step_height, entity/block_interaction_range,
   jump_strength, gravity, movement_speed — их меняют плагины. Используй `data.attributes()`.
9. **Новые механики 1.21.x**: ветряной заряд, булава (smash attack), элитры + фейерверки,
   тризубец (riptide), левитация, медленное падение, шалкеры, пузырьковые
   колонны, мёд, слизь, паутина, порошковый снег, строительные леса, лианы/лестницы.
10. **Игрок с правом обхода / тэгом** — уже обрабатывается в `ViolationManager`.

## 5. Полезные данные

* `data.movement()`: x/y/z, last*, deltaX/Y/Z/XZ, yaw/pitch, deltaYaw/Pitch, onGround,
  clientTick (номер клиентского тика), ticksSinceTeleport, airTicks/groundTicks.
* `data.actions()`: ввод (PlayerInput), sprinting/sneaking, usingItem, heldSlot,
  счётчики «за тик» (attacksThisTick, swingsThisTick, placesThisTick, slotChangesThisTick,
  windowClicksThisTick, useItemsThisTick), тики последних действий, containerOpen.
* `data.transactions()`: smoothedRttMs, rttStdDev, oldestPendingAgeMs, clientTicksSincePong,
  invalidPongs.
* `data.keepAlivePingMs`, `data.invalidKeepAlives`, `data.invalidTeleportConfirms`.
* `data.client()`: version, brand, bedrock, sendsTickEnd, channels.
* `data.entities()`: отслеживаемые сущности (`TrackedEntity`), их возможные хитбоксы.
* `AttackUpdate`: distance, rayHit, aimErrorYaw/Pitch, targetBox, maxRange, uncertain,
  indexInTick, swungBefore, yawBefore/After...
* `ru.strazh.ac.util`: `McMath` (таблица sin/cos клиента, wrapDegrees, viewVector, yawTo),
  `Stats` (mean, stdDev, skewness, kurtosis, outliers, distinct, entropy, mode, gcd),
  `DoubleWindow` (кольцевой буфер), `AABB` (лучи, коллизии), `Vec3`, `Text.fmt`.

## 6. Сборка и тесты

```
cd anticheat
gradle compileJava -PlocalLibs=<папка с paper-api и packetevents jar>   # офлайн
gradle test -PlocalLibs=...
```

Юнит-тесты (`src/test/java`): `PlayerData.forTest(d -> List.of(new MyCheck(d)))` создаёт
данные без сервера; `TestHooks.setHandler((check, details) -> ...)` перехватывает флаги.
Хуки вызывай напрямую (`onRotation(new RotationUpdate(...))`, `onAttackResolved(...)`,
`onSimulation(...)`). Держи логику проверок в чистых методах, чтобы её можно было тестировать.

## 7. Прочие правила

* Не копируй код GPL-проектов (Grim и др.) — только идеи и формулы своими словами.
* Без аллокаций в горячих путях, где это просто (переиспользуй буферы).
* Комментарии и описания — на русском, кратко.
* Никаких сетевых запросов, файлов и потоков внутри проверок.
