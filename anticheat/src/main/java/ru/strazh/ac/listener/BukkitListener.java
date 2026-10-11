package ru.strazh.ac.listener;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.check.hook.BukkitCheck;
import ru.strazh.ac.player.PlayerData;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Bukkit-события: жизненный цикл игрока, транспорт, раздача событий проверкам. */
public final class BukkitListener implements Listener {

    private final StrazhPlugin plugin;

    public BukkitListener(StrazhPlugin plugin) {
        this.plugin = plugin;
    }

    // =====================================================================
    // Вход / выход
    // =====================================================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        PlayerData data = plugin.players().get(p);
        if (data == null) {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(p);
            if (user != null) data = plugin.players().getOrCreate(user);
        }
        if (data == null) return;
        data.setPlayer(p);
        data.joinServerTick = Bukkit.getCurrentTick();
        data.client().joinMillis = System.currentTimeMillis();
        data.client().version = PacketEvents.getAPI().getPlayerManager().getClientVersion(p);
        String brand = p.getClientBrandName();
        if (brand != null && !brand.isBlank()) data.client().brand = brand;
        data.client().bedrock = isBedrock(p.getUniqueId());
        refreshCaches(data);
        // Небольшое исключение на время загрузки мира.
        long joinMs = plugin.config().joinExemptTicks * 50L;
        if (joinMs > 0) data.exemptions().exempt("movement", joinMs);
        if (p.hasPermission("strazh.alerts") && plugin.getConfig().getBoolean("alerts.auto-enable", true)) {
            plugin.alerts().enable(p);
        }
        data.sim().lastValid = p.getLocation();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        plugin.alerts().disable(p);
        plugin.punishments().forget(p.getUniqueId());
        PlayerData data = plugin.players().get(p);
        if (data != null) {
            plugin.honeypots().remove(data);
            plugin.players().remove(data);
        }
    }

    private boolean isBedrock(UUID uuid) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            try {
                Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                return (boolean) api.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, uuid);
            } catch (Throwable ignored) {
            }
        }
        return uuid.getMostSignificantBits() == 0L;
    }

    /** Обновляет кэши права обхода и тэгов (главный поток). */
    public void refreshCaches(PlayerData data) {
        Player p = data.player();
        if (p == null) return;
        data.exemptions().bypassPermission = p.hasPermission(plugin.config().bypassPermission);
        Set<String> tags = p.getScoreboardTags();
        data.exemptions().tags = tags.isEmpty() ? Set.of() : Set.copyOf(tags);
        data.vehicle().bukkitInVehicle = p.isInsideVehicle();
        if (p.isInsideVehicle()) {
            Entity v = p.getVehicle();
            data.vehicle().onCustomVehicle = v != null && plugin.vehicleCompat().isCustomVehicle(v);
        } else {
            data.vehicle().onCustomVehicle = false;
        }
    }

    // =====================================================================
    // Транспорт (включая Skript-машины)
    // =====================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        PlayerData data = plugin.players().get(p);
        if (data == null) return;
        Entity vehicle = event.getMount();
        data.vehicle().bukkitInVehicle = true;
        data.vehicle().lastMountMillis = System.currentTimeMillis();
        data.vehicle().vehicleType = vehicle.getType().name();
        data.vehicle().onCustomVehicle = plugin.vehicleCompat().isCustomVehicle(vehicle);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        PlayerData data = plugin.players().get(p);
        if (data == null) return;
        // Skript-машины отменяют высадку на скорости — тогда игрок остаётся в транспорте.
        if (event.isCancelled()) return;
        data.vehicle().bukkitInVehicle = false;
        data.vehicle().lastDismountMillis = System.currentTimeMillis();
        boolean custom = data.vehicle().onCustomVehicle || plugin.vehicleCompat().isCustomVehicle(event.getDismounted());
        data.vehicle().onCustomVehicle = false;
        // После высадки машина телепортирует игрока к двери и может толкнуть его —
        // даём льготный период на проверки движения.
        long grace = plugin.config().dismountGraceTicks * 50L + (long) Math.min(1000, data.ping());
        data.exemptions().exempt("movement", custom ? grace * 2 : grace);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerData data = plugin.players().get(event.getPlayer());
        if (data == null) return;
        if (event.getPlayer().isInsideVehicle() || System.currentTimeMillis() - data.vehicle().lastDismountMillis < 2000) {
            data.vehicle().lastVehicleTeleportMillis = System.currentTimeMillis();
        }
        if (event.getTo().getWorld() != null) {
            data.sim().lastValid = event.getTo().clone();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        PlayerData data = plugin.players().get(event.getEntity());
        if (data != null) data.dead = true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerData data = plugin.players().get(event.getPlayer());
        if (data == null) return;
        data.dead = false;
        data.sim().lastValid = event.getRespawnLocation().clone();
        data.exemptions().exempt("movement", 2000);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        PlayerData data = plugin.players().get(event.getPlayer());
        if (data == null) return;
        data.sim().lastValid = event.getPlayer().getLocation();
        data.exemptions().exempt("movement", 2000);
    }

    // =====================================================================
    // Смягчение боя: часть ударов подозреваемого отменяется
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMitigate(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p) || !plugin.config().mitigationEnabled) return;
        PlayerData data = plugin.players().get(p);
        if (data == null) return;
        if (plugin.honeypots().isHoneypot(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        double s = data.combatSuspicion();
        if (s < 2.0) return;
        double chance = Math.min(0.9, plugin.config().mitigationCancelChance * (s - 1.0) / 4.0);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            event.setCancelled(true);
        }
    }

    // =====================================================================
    // Раздача событий проверкам
    // =====================================================================

    private void dispatch(Player p, Event event) {
        PlayerData data = plugin.players().get(p);
        if (data == null || data.checks() == null) return;
        data.drainMainQueue();
        for (BukkitCheck c : data.checks().bukkit) {
            try {
                c.onBukkitEvent(event);
            } catch (Throwable t) {
                plugin.getLogger().warning("Ошибка проверки " + c.getClass().getSimpleName() + ": " + t);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageBlock(BlockDamageEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            dispatch(p, event);
        }
        if (event.getEntity() instanceof Player victim && victim != event.getDamager()) {
            dispatch(victim, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p) {
            dispatch(p, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player p) {
            dispatch(p, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player p) {
            dispatch(p, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player p) {
            dispatch(p, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBoost(PlayerElytraBoostEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerArmSwingEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        dispatch(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        dispatch(event.getPlayer(), event);
    }
}
