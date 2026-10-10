package ru.strazh.ac.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * RTT соединения по данным ядра ОС (TCP_INFO), если сервер использует epoll.
 * Это время подделать с клиента невозможно, в отличие от ответов на KeepAlive и
 * Ping: сравнение трёх «часов» выдаёт Blink/FakeLag/BackTrack/PingSpoof.
 * Доступ через отражение, чтобы не зависеть от нативного транспорта при сборке.
 */
public final class NetUtil {

    private static volatile boolean disabled;
    private static volatile Class<?> epollClass;
    private static volatile MethodHandle tcpInfo;
    private static volatile MethodHandle rtt;

    private NetUtil() {
    }

    /** @return RTT по ядру в мс, или -1, если недоступно */
    public static double kernelRttMs(Object channel) {
        if (disabled || channel == null) return -1;
        try {
            Class<?> c = channel.getClass();
            if (epollClass == null) {
                if (!c.getName().equals("io.netty.channel.epoll.EpollSocketChannel")) {
                    return -1;
                }
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> infoClass = Class.forName("io.netty.channel.epoll.EpollTcpInfo", false, c.getClassLoader());
                tcpInfo = lookup.findVirtual(c, "tcpInfo", MethodType.methodType(infoClass));
                rtt = lookup.findVirtual(infoClass, "rtt", MethodType.methodType(long.class));
                epollClass = c;
            } else if (c != epollClass) {
                return -1;
            }
            Object info = tcpInfo.invoke(channel);
            long micros = (long) rtt.invoke(info);
            return micros / 1000.0;
        } catch (Throwable t) {
            disabled = true;
            return -1;
        }
    }
}
