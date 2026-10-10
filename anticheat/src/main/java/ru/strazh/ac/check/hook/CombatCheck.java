package ru.strazh.ac.check.hook;

import ru.strazh.ac.player.update.AttackUpdate;

/**
 * Проверка ударов (event loop).
 * <p>{@link #onAttackPacket} — сразу при получении пакета атаки.
 * <p>{@link #onAttackResolved} — на следующем пакете движения, когда известна
 * ротация, использованная клиентом для выбора цели, и рассчитаны дистанция,
 * попадание луча в хитбокс и т.д.
 */
public interface CombatCheck {

    default void onAttackPacket(AttackUpdate attack) {
    }

    default void onAttackResolved(AttackUpdate attack) {
    }
}
