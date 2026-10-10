package ru.strazh.ac.check;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Описание проверки. Значения — настройки по умолчанию, их можно менять в config.yml. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CheckInfo {

    /** Имя проверки, например "Reach". */
    String name();

    /** Подтип: A, B, C... */
    String type() default "A";

    Category category();

    /** Описание на русском — что ловит. */
    String description();

    /** VL, при котором применяется наказание. */
    double maxVl() default 20.0;

    /** Сколько VL снимается за минуту без нарушений. */
    double decay() default 2.0;

    /** VL, начиная с которого показываются алерты. */
    double alertVl() default 1.0;

    /** Наказывать ли (выполнять команды) при достижении maxVl. */
    boolean punish() default true;

    /** Откатывать ли игрока (setback) при нарушении движения. */
    boolean setback() default false;

    /** Экспериментальная проверка: по умолчанию только алерты, без наказаний. */
    boolean experimental() default false;
}
