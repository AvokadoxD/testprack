package ru.strazh.ac.check;

import java.util.function.BiPredicate;

/** Точка подмены обработки флагов для юнит-тестов (без запущенного сервера). */
public final class TestHooks {

    private static volatile BiPredicate<Check, String> handler = (c, d) -> {
        c.addVl(1.0);
        return true;
    };

    private TestHooks() {
    }

    public static void setHandler(BiPredicate<Check, String> h) {
        handler = h;
    }

    static boolean onFlag(Check check, double weight, String details) {
        return handler.test(check, details);
    }
}
