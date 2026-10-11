package ru.strazh.ac.check.impl.misc;

import ru.strazh.ac.check.Check;

import java.util.List;

/** Проверки категории «Прочее». */
public final class MiscChecks {

    private MiscChecks() {
    }

    public static final List<Class<? extends Check>> ALL = List.of(
            HoneypotA.class, ClientBrandA.class
    );
}
