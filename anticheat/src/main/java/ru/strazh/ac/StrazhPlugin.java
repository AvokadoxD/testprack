package ru.strazh.ac;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.strazh.ac.alert.AlertManager;
import ru.strazh.ac.check.CheckRegistry;
import ru.strazh.ac.command.StrazhCommand;
import ru.strazh.ac.compat.VehicleCompat;
import ru.strazh.ac.config.StrazhConfig;
import ru.strazh.ac.honeypot.HoneypotManager;
import ru.strazh.ac.listener.BukkitListener;
import ru.strazh.ac.listener.PacketProcessor;
import ru.strazh.ac.log.ViolationLogger;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.PlayerManager;
import ru.strazh.ac.punish.PunishmentManager;
import ru.strazh.ac.punish.ViolationManager;
import ru.strazh.ac.sim.SimulationEngine;
import ru.strazh.ac.sim.SimulationService;
import ru.strazh.ac.sim.Engines;
import ru.strazh.ac.task.SizeResolver;
import ru.strazh.ac.task.TickListener;

/** StrazhAC — серверный античит для Paper 1.21.11. */
public final class StrazhPlugin extends JavaPlugin {

    private static volatile StrazhPlugin instance;

    private StrazhConfig config;
    private PlayerManager players;
    private CheckRegistry checkRegistry;
    private ViolationManager violations;
    private AlertManager alerts;
    private PunishmentManager punishments;
    private ViolationLogger violationLog;
    private SimulationService simulation;
    private SizeResolver sizeResolver;
    private VehicleCompat vehicleCompat;
    private HoneypotManager honeypots;
    private BukkitListener bukkitListener;
    private PacketListenerCommon packetListener;

    public static StrazhPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        if (Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            getLogger().severe("Не найден плагин PacketEvents! Установите packetevents-spigot 2.14.0+ — StrazhAC отключён.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        config = new StrazhConfig(this);
        config.load();
        players = new PlayerManager(this);
        checkRegistry = new CheckRegistry(this);
        violations = new ViolationManager(this);
        alerts = new AlertManager(this);
        punishments = new PunishmentManager(this);
        violationLog = new ViolationLogger(this);
        sizeResolver = new SizeResolver();
        vehicleCompat = new VehicleCompat(this);
        SimulationEngine engine = Engines.create(this);
        simulation = new SimulationService(this, engine);
        honeypots = Engines.createHoneypots(this);

        // Прототипы проверок дописывают в config.yml параметры по умолчанию.
        checkRegistry.prototypes();
        config.saveMissingDefaults();

        packetListener = PacketEvents.getAPI().getEventManager()
                .registerListener(new PacketProcessor(this), PacketListenerPriority.LOW);

        bukkitListener = new BukkitListener(this);
        Bukkit.getPluginManager().registerEvents(bukkitListener, this);
        Bukkit.getPluginManager().registerEvents(new TickListener(this), this);

        PluginCommand cmd = getCommand("strazh");
        if (cmd != null) {
            StrazhCommand executor = new StrazhCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> alerts.flushWebhook(), 40L, 40L);

        // /reload или поздняя загрузка: подхватить уже подключённых игроков.
        for (Player p : Bukkit.getOnlinePlayers()) {
            var user = PacketEvents.getAPI().getPlayerManager().getUser(p);
            if (user == null) continue;
            PlayerData data = players.getOrCreate(user);
            if (data != null) {
                data.setPlayer(p);
                data.entityId = p.getEntityId();
                bukkitListener.refreshCaches(data);
                data.sim().lastValid = p.getLocation();
            }
        }
        getLogger().info("StrazhAC запущен: проверок " + checkRegistry.count() + ", симуляция: "
                + engine.getClass().getSimpleName());
    }

    @Override
    public void onDisable() {
        if (packetListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            } catch (Throwable ignored) {
            }
        }
        if (honeypots != null) honeypots.shutdown();
        if (violationLog != null) violationLog.shutdown();
        instance = null;
    }

    /** /strazh reload. */
    public void reloadAll() {
        config.load();
        players.reloadChecks();
        checkRegistry.prototypes();
        config.saveMissingDefaults();
    }

    public StrazhConfig config() {
        return config;
    }

    public PlayerManager players() {
        return players;
    }

    public CheckRegistry checkRegistry() {
        return checkRegistry;
    }

    public ViolationManager violations() {
        return violations;
    }

    public AlertManager alerts() {
        return alerts;
    }

    public PunishmentManager punishments() {
        return punishments;
    }

    public ViolationLogger violationLog() {
        return violationLog;
    }

    public SimulationService simulation() {
        return simulation;
    }

    public SizeResolver sizeResolver() {
        return sizeResolver;
    }

    public VehicleCompat vehicleCompat() {
        return vehicleCompat;
    }

    public HoneypotManager honeypots() {
        return honeypots;
    }

    public BukkitListener bukkitListener() {
        return bukkitListener;
    }
}
