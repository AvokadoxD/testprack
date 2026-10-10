package ru.strazh.ac.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;

/** Цвета и форматирование сообщений (коды &). */
public final class Text {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private Text() {
    }

    public static Component color(String s) {
        return AMP.deserialize(s == null ? "" : s);
    }

    public static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    public static String fmt(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}
