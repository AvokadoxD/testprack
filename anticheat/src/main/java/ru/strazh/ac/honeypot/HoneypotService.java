package ru.strazh.ac.honeypot;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.util.McMath;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ловушка для аур: фейковый игрок, существующий только в пакетах и только для
 * подозреваемого. Он держится строго за спиной игрока (по отправленной им
 * ротации), поэтому человек не может навести на него прицел, а аура,
 * перебирающая цели в радиусе, бьёт его. Ловушка проходит типичные AntiBot-фильтры
 * приватных клиентов: есть в табе с реалистичным пингом, настоящий подписанный
 * скин, правильная схема UUID, id из обычного счётчика сущностей, снаряжение
 * выставлено при появлении и не меняется, существует дольше 30 тиков.
 */
public final class HoneypotService extends HoneypotManager {

    private static final String[] PARTS = {"Dark", "Fox", "Wolf", "Kira", "Max", "Nik", "Lex", "Sky", "Neo", "Zed",
            "Rex", "Ash", "Vlad", "Dima", "Artem", "Egor", "Misha", "Ilya", "Kot", "Bear", "Pro", "Ghost", "Ice", "Fire"};

    private static final class Bot {
        final UUID owner;
        final int entityId;
        final UUID uuid;
        final String name;
        final int spawnTick;
        final int despawnTick;
        boolean spawned;

        Bot(UUID owner, int entityId, UUID uuid, String name, int spawnTick, int despawnTick) {
            this.owner = owner;
            this.entityId = entityId;
            this.uuid = uuid;
            this.name = name;
            this.spawnTick = spawnTick;
            this.despawnTick = despawnTick;
        }
    }

    private final Map<UUID, Bot> bots = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> cooldownUntil = new ConcurrentHashMap<>();

    public HoneypotService(StrazhPlugin plugin) {
        super(plugin);
    }

    /** Является ли сущность ловушкой этого игрока (любой поток). */
    public boolean isBotFor(PlayerData data, int entityId) {
        Bot b = bots.get(data.uuid());
        return b != null && b.entityId == entityId;
    }

    @Override
    public void remove(PlayerData data) {
        bots.remove(data.uuid());
        cooldownUntil.remove(data.uuid());
    }

    @Override
    public void tick(int tick) {
        if (!plugin.config().honeypotEnabled) return;
        double minSuspicion = plugin.getConfig().getDouble("honeypot.min-suspicion", 1.0);
        double randomChance = plugin.getConfig().getDouble("honeypot.random-chance", 0.0);
        for (PlayerData data : plugin.players().all()) {
            Player p = data.player();
            if (p == null) continue;
            Bot bot = bots.get(data.uuid());
            if (bot != null) {
                update(data, p, bot, tick);
                continue;
            }
            if (tick % 10 != 0 || tick < cooldownUntil.getOrDefault(data.uuid(), 0)) continue;
            if (!eligible(data, p)) continue;
            boolean inCombat = System.nanoTime() - data.actions().lastAttackNanos < 3_000_000_000L;
            if (!inCombat) continue;
            boolean suspicious = data.combatSuspicion() >= minSuspicion;
            if (suspicious || ThreadLocalRandom.current().nextDouble() < randomChance) {
                create(data, p, tick);
            }
        }
    }

    private boolean eligible(PlayerData data, Player p) {
        return !p.isDead() && !p.isInsideVehicle() && data.loaded && !data.client().bedrock
                && (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE)
                && !data.isFullyExempt();
    }

    @SuppressWarnings("deprecation")
    private void create(PlayerData data, Player p, int tick) {
        String name = randomName();
        UUID uuid = Bukkit.getOnlineMode() ? UUID.randomUUID()
                : UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        int id = Bukkit.getUnsafe().nextEntityId();
        Bot bot = new Bot(data.uuid(), id, uuid, name, tick + 40, tick + 40 + 100);
        bots.put(data.uuid(), bot);
        cooldownUntil.put(data.uuid(), tick + 20 * 60);
        // Сначала — запись в табе (за 2 секунды до появления, как у настоящего входа).
        UserProfile profile = new UserProfile(uuid, name, textures(p));
        int latency = 25 + ThreadLocalRandom.current().nextInt(110);
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile, true, latency, GameMode.SURVIVAL, null, null);
        data.user().sendPacket(new WrapperPlayServerPlayerInfoUpdate(EnumSet.of(
                WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER, WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY, WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE),
                List.of(info)));
    }

    private void update(PlayerData data, Player p, Bot bot, int tick) {
        if (tick >= bot.despawnTick || !eligible(data, p)) {
            destroy(data, bot);
            return;
        }
        if (tick < bot.spawnTick) return;
        Location l = p.getLocation();
        // За спиной по последней ОТПРАВЛЕННОЙ ротации, на расстоянии 2.4–2.9.
        float yaw = data.movement().yaw;
        double dist = 2.4 + 0.5 * ((tick % 37) / 37.0);
        double bx = l.getX() + McMath.sin(yaw * McMath.DEG_TO_RAD) * dist;
        double bz = l.getZ() - McMath.cos(yaw * McMath.DEG_TO_RAD) * dist;
        double by = l.getY();
        float faceYaw = yaw;
        if (!bot.spawned) {
            bot.spawned = true;
            data.user().sendPacket(new WrapperPlayServerSpawnEntity(bot.entityId, Optional.of(bot.uuid), EntityTypes.PLAYER,
                    new Vector3d(bx, by, bz), 0f, faceYaw, faceYaw, 0, Optional.empty()));
            List<Equipment> eq = new ArrayList<>();
            eq.add(new Equipment(EquipmentSlot.HELMET, item(ItemTypes.DIAMOND_HELMET)));
            eq.add(new Equipment(EquipmentSlot.CHEST_PLATE, item(ItemTypes.IRON_CHESTPLATE)));
            eq.add(new Equipment(EquipmentSlot.BOOTS, item(ItemTypes.DIAMOND_BOOTS)));
            eq.add(new Equipment(EquipmentSlot.MAIN_HAND, item(ItemTypes.DIAMOND_SWORD)));
            eq.add(new Equipment(EquipmentSlot.OFF_HAND, item(ItemTypes.TOTEM_OF_UNDYING)));
            data.user().sendPacket(new WrapperPlayServerEntityEquipment(bot.entityId, eq));
            return;
        }
        data.user().sendPacket(new WrapperPlayServerEntityTeleport(bot.entityId, new Vector3d(bx, by, bz), faceYaw, 0f, true));
        data.user().sendPacket(new WrapperPlayServerEntityHeadLook(bot.entityId, faceYaw));
    }

    private void destroy(PlayerData data, Bot bot) {
        bots.remove(bot.owner, bot);
        if (bot.spawned) {
            data.user().sendPacket(new WrapperPlayServerDestroyEntities(bot.entityId));
        }
        data.user().sendPacket(new WrapperPlayServerPlayerInfoRemove(bot.uuid));
    }

    @Override
    public void shutdown() {
        for (Bot b : bots.values()) {
            PlayerData d = plugin.players().get(b.owner);
            if (d != null) {
                try {
                    destroy(d, b);
                } catch (Throwable ignored) {
                }
            }
        }
        bots.clear();
    }

    private static ItemStack item(ItemType type) {
        return ItemStack.builder().type(type).amount(1).build();
    }

    /** Подписанный скин случайного другого игрока онлайн (без подписи AntiBot отсеет ловушку). */
    private static List<TextureProperty> textures(Player exclude) {
        List<Player> pool = new ArrayList<>(Bukkit.getOnlinePlayers());
        pool.remove(exclude);
        java.util.Collections.shuffle(pool);
        for (Player other : pool) {
            for (ProfileProperty prop : other.getPlayerProfile().getProperties()) {
                if (prop.getName().equals("textures") && prop.getSignature() != null) {
                    return List.of(new TextureProperty("textures", prop.getValue(), prop.getSignature()));
                }
            }
        }
        return List.of();
    }

    private static String randomName() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 20; attempt++) {
            String n = PARTS[r.nextInt(PARTS.length)] + (r.nextBoolean() ? "_" : "") + PARTS[r.nextInt(PARTS.length)].toLowerCase()
                    + (r.nextInt(3) == 0 ? "" : String.valueOf(r.nextInt(1, 2010)));
            if (n.length() > 16) n = n.substring(0, 16);
            if (Bukkit.getPlayerExact(n) == null) return n;
        }
        return "Player" + r.nextInt(1000, 9999);
    }
}
