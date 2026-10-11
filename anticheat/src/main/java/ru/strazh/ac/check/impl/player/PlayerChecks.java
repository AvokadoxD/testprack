package ru.strazh.ac.check.impl.player;

import ru.strazh.ac.check.Check;

import java.util.List;

/** Проверки категории «Игрок (инвентарь, использование предметов)». */
public final class PlayerChecks {

    private PlayerChecks() {
    }

    public static final List<Class<? extends Check>> ALL = List.of(
            InventoryA.class, InventoryB.class, InventoryC.class, InventoryD.class, InventoryE.class,
            NoSlowB.class, FastUseA.class
    );
}
