package ru.strazh.ac.player;

import com.github.retrooper.packetevents.protocol.player.User;
import org.bukkit.entity.Player;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckSet;
import ru.strazh.ac.player.tracker.ActionTracker;
import ru.strazh.ac.player.tracker.EntityTracker;
import ru.strazh.ac.player.tracker.MovementTracker;
import ru.strazh.ac.player.tracker.RotationTracker;
import ru.strazh.ac.player.tracker.TeleportTracker;
import ru.strazh.ac.player.tracker.VelocityTracker;
import ru.strazh.ac.player.update.AttackUpdate;
import ru.strazh.ac.sim.SimState;

import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Все данные античита по одному игроку.
 *
 * <p>Потоки: трекеры пакетов (movement, actions, velocity, teleports, rotations,
 * entities, abilities, transactions) принадлежат event loop канала игрока.
 * Симуляция и Bukkit-состояние — главному потоку. Передача работы в главный
 * поток — через {@link #queueMain(Consumer)} в строгом порядке пакетов.
 */
public final class PlayerData {

    private final StrazhPlugin plugin;
    private final User user;
    private final UUID uuid;
    private volatile Player player;
    private volatile CheckSet checks;

    private final ClientInfo client = new ClientInfo();
    private final TransactionManager transactions;
    private final MovementTracker movement = new MovementTracker();
    private final ActionTracker actions = new ActionTracker();
    private final VelocityTracker velocity = new VelocityTracker();
    private final TeleportTracker teleports = new TeleportTracker();
    private final RotationTracker rotations = new RotationTracker();
    private final EntityTracker entities = new EntityTracker();
    private final AbilityState abilities = new AbilityState();
    private final AttributeState attributes = new AttributeState();
    private final VehicleState vehicle = new VehicleState();
    private final Exemptions exemptions = new Exemptions();
    private final SimState sim = new SimState();

    /** Удары, ждущие следующего пакета движения для расчёта (event loop). */
    public final ArrayDeque<AttackUpdate> pendingAttacks = new ArrayDeque<>();

    private final ConcurrentLinkedQueue<Consumer<PlayerData>> mainQueue = new ConcurrentLinkedQueue<>();
    private boolean draining;

    /** Запрошен откат позиции (причина) — выполняется в главном потоке. */
    private volatile String setbackRequest;

    // ---- смягчение боя (mitigation) ----
    private volatile double combatSuspicion;
    private volatile long combatSuspicionTime;

    // ---- keepalive (для сравнения с пингом транзакций) ----
    public final java.util.Map<Long, Long> keepAliveSent = new java.util.concurrent.ConcurrentHashMap<>();
    public volatile double keepAlivePingMs = -1;
    public volatile long lastKeepAliveResponseNanos;
    public volatile int invalidKeepAlives;

    // ---- телепорты и навязанные повороты ----
    public volatile int invalidTeleportConfirms;
    /** Распознанных эхо-пакетов навязанного поворота камеры. */
    public volatile int rotationEchoes;

    // ---- прочее ----
    public volatile int entityId = -1;
    public volatile long lastInvalidTransactionMillis;
    public volatile String lastInvalidTransaction = "";
    /** Тик сервера при входе. */
    public volatile int joinServerTick;
    /** Игрок полностью загрузил мир (PlayerLoaded / первые пакеты движения). */
    public volatile boolean loaded;

    public PlayerData(StrazhPlugin plugin, User user) {
        this.plugin = plugin;
        this.user = user;
        this.uuid = user == null ? UUID.randomUUID() : user.getUUID();
        this.transactions = new TransactionManager(this);
    }

    /** Для юнит-тестов: данные без плагина и сетевого пользователя. */
    public static PlayerData forTest(java.util.function.Function<PlayerData, java.util.List<Check>> checks) {
        PlayerData d = new PlayerData(null, null);
        java.util.List<Check> list = checks.apply(d);
        for (Check c : list) {
            c.applySettings(new ru.strazh.ac.config.CheckSettings(c.id(), c.info(), null));
        }
        d.checks = new CheckSet(list);
        return d;
    }

    public void initChecks() {
        this.checks = plugin.checkRegistry().create(this);
    }

    // ------------------------------------------------------------------
    // Очередь главного потока
    // ------------------------------------------------------------------

    /** Поставить работу в главный поток (сохраняется порядок пакетов). */
    public void queueMain(Consumer<PlayerData> work) {
        mainQueue.add(work);
    }

    /**
     * Выполнить всю накопленную работу (только главный поток). Безопасно
     * вызывать из обработчиков событий: повторный вход игнорируется.
     */
    public void drainMainQueue() {
        if (draining) return;
        draining = true;
        try {
            Consumer<PlayerData> work;
            int guard = 0;
            while ((work = mainQueue.poll()) != null && guard++ < 4096) {
                try {
                    work.accept(this);
                } catch (Throwable t) {
                    if (plugin != null) plugin.getLogger().log(Level.WARNING, "Ошибка обработки игрока " + name(), t);
                    else t.printStackTrace();
                }
            }
        } finally {
            draining = false;
        }
    }

    public int mainQueueSize() {
        return mainQueue.size();
    }

    // ------------------------------------------------------------------
    // Откаты и смягчение
    // ------------------------------------------------------------------

    public void requestSetback(String reason) {
        if (plugin == null || plugin.config().setbackEnabled) {
            setbackRequest = reason;
        }
    }

    public String pollSetbackRequest() {
        String r = setbackRequest;
        setbackRequest = null;
        return r;
    }

    /** Повышает подозрение в бою (используется для отмены части ударов читера). */
    public void addCombatSuspicion(double amount) {
        decayCombatSuspicion();
        combatSuspicion = Math.min(10.0, combatSuspicion + amount);
    }

    public double combatSuspicion() {
        decayCombatSuspicion();
        return combatSuspicion;
    }

    private void decayCombatSuspicion() {
        long now = System.currentTimeMillis();
        double seconds = (now - combatSuspicionTime) / 1000.0;
        combatSuspicionTime = now;
        if (seconds > 0) {
            combatSuspicion = Math.max(0.0, combatSuspicion - seconds * 0.25);
        }
    }

    // ------------------------------------------------------------------
    // Исключения
    // ------------------------------------------------------------------

    public boolean isExempt(Check check) {
        if (plugin == null) return exemptions.isExempt(check, "strazh_exempt", true);
        return exemptions.isExempt(check, plugin.config().exemptTag, plugin.config().categoryTags);
    }

    public boolean isFullyExempt() {
        return exemptions.isFullyExempt(plugin == null ? "strazh_exempt" : plugin.config().exemptTag);
    }

    /** Игрок в транспорте сейчас или был в нём в последние ticks клиентских тиков (event loop). */
    public boolean inVehicleRecently(int ticks) {
        return vehicle.inVehicle() || movement.clientTick - vehicle.lastRidingTick <= ticks;
    }

    /** Время с высадки из транспорта, мс (потокобезопасно). */
    public long millisSinceDismount() {
        return System.currentTimeMillis() - vehicle.lastDismountMillis;
    }

    void onInvalidTransaction(long seq, long confirmed, long sent) {
        lastInvalidTransactionMillis = System.currentTimeMillis();
        lastInvalidTransaction = "pong=" + seq + " confirmed=" + confirmed + " sent=" + sent;
    }

    // ------------------------------------------------------------------
    // Доступ
    // ------------------------------------------------------------------

    public StrazhPlugin plugin() {
        return plugin;
    }

    public User user() {
        return user;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        Player p = player;
        if (p != null) return p.getName();
        return user != null ? user.getName() : "test";
    }

    public Player player() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public CheckSet checks() {
        return checks;
    }

    public ClientInfo client() {
        return client;
    }

    public TransactionManager transactions() {
        return transactions;
    }

    public MovementTracker movement() {
        return movement;
    }

    public ActionTracker actions() {
        return actions;
    }

    public VelocityTracker velocity() {
        return velocity;
    }

    public TeleportTracker teleports() {
        return teleports;
    }

    public RotationTracker rotations() {
        return rotations;
    }

    public EntityTracker entities() {
        return entities;
    }

    public AbilityState abilities() {
        return abilities;
    }

    public AttributeState attributes() {
        return attributes;
    }

    public VehicleState vehicle() {
        return vehicle;
    }

    public Exemptions exemptions() {
        return exemptions;
    }

    public SimState sim() {
        return sim;
    }

    /** Пинг по транзакциям (мс), а если данных нет — по keepalive сервера. */
    public double ping() {
        double t = transactions.smoothedRttMs();
        if (t >= 0) return t;
        Player p = player;
        return p != null ? p.getPing() : 0;
    }
}
