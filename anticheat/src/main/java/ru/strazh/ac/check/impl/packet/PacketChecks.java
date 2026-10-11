package ru.strazh.ac.check.impl.packet;

import ru.strazh.ac.check.Check;

import java.util.List;

/** Проверки категории «Пакеты». Новые классы добавляются в этот список. */
public final class PacketChecks {

    private PacketChecks() {
    }

    public static final List<Class<? extends Check>> ALL = List.of(
            BadPacketsA.class, BadPacketsB.class, BadPacketsC.class, BadPacketsD.class, BadPacketsE.class,
            BadPacketsF.class, BadPacketsG.class, BadPacketsH.class, BadPacketsI.class, BadPacketsJ.class,
            BadPacketsK.class, BadPacketsL.class, PacketOrderA.class, TransactionA.class, TransactionB.class, TransactionC.class,
            TimerA.class, NoRotateA.class
    );
}
