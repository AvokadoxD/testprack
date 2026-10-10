package ru.strazh.ac.player;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Сведения о клиенте игрока. */
public final class ClientInfo {

    public volatile ClientVersion version = ClientVersion.UNKNOWN;
    public volatile String brand = "unknown";
    /** Игрок Bedrock через Geyser/Floodgate (другая физика и ввод). */
    public volatile boolean bedrock;
    /** Клиент шлёт ClientTickEnd (1.21.2+), значит границы тиков известны точно. */
    public volatile boolean sendsTickEnd;
    /** Каналы плагин-сообщений, зарегистрированные клиентом. */
    public final Set<String> channels = ConcurrentHashMap.newKeySet();
    public volatile long joinMillis = System.currentTimeMillis();

    public boolean isNewerThanOrEquals(ClientVersion v) {
        ClientVersion cur = version;
        return cur != ClientVersion.UNKNOWN && cur.isNewerThanOrEquals(v);
    }
}
