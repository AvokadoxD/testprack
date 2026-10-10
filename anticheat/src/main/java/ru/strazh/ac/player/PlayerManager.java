package ru.strazh.ac.player;

import com.github.retrooper.packetevents.protocol.player.User;
import org.bukkit.entity.Player;
import ru.strazh.ac.StrazhPlugin;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Реестр PlayerData. */
public final class PlayerManager {

    private final StrazhPlugin plugin;
    private final Map<UUID, PlayerData> byUuid = new ConcurrentHashMap<>();
    private final Map<User, PlayerData> byUser = new ConcurrentHashMap<>();

    public PlayerManager(StrazhPlugin plugin) {
        this.plugin = plugin;
    }

    /** Создаёт данные для пользователя (event loop при входе) или возвращает существующие. */
    public PlayerData getOrCreate(User user) {
        PlayerData data = byUser.get(user);
        if (data != null) return data;
        UUID uuid = user.getUUID();
        if (uuid == null) return null;
        data = byUuid.get(uuid);
        if (data != null && data.user() != user) {
            // Переподключение с тем же UUID — старая запись устарела.
            remove(data);
            data = null;
        }
        if (data == null) {
            PlayerData created = new PlayerData(plugin, user);
            created.initChecks();
            data = created;
            byUuid.put(uuid, data);
        }
        byUser.put(user, data);
        return data;
    }

    public PlayerData get(User user) {
        return user == null ? null : byUser.get(user);
    }

    public PlayerData get(UUID uuid) {
        return byUuid.get(uuid);
    }

    public PlayerData get(Player player) {
        return byUuid.get(player.getUniqueId());
    }

    public void remove(PlayerData data) {
        byUuid.remove(data.uuid(), data);
        byUser.remove(data.user(), data);
    }

    public void remove(User user) {
        PlayerData d = byUser.remove(user);
        if (d != null) byUuid.remove(d.uuid(), d);
    }

    public Collection<PlayerData> all() {
        return byUuid.values();
    }

    public void reloadChecks() {
        for (PlayerData d : byUuid.values()) {
            if (d.checks() == null) continue;
            for (var c : d.checks().all) {
                c.applySettings(plugin.config().settings(c.info()));
            }
        }
    }
}
