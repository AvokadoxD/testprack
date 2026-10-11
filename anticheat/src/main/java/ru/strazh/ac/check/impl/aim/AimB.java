package ru.strazh.ac.check.impl.aim;

import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.RotationCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.player.update.RotationUpdate;
import ru.strazh.ac.util.Text;

/**
 * Скачок yaw больше 320° за пакет: клиент никогда не нормализует yaw (он
 * накапливается), а читы с «обёрнутыми» углами шлют 359 → 1.
 */
@CheckInfo(name = "Aim", type = "B", category = Category.COMBAT,
        description = "Нормализованный (обёрнутый) yaw — признак программной ротации", maxVl = 5, decay = 1.0)
public final class AimB extends Check implements RotationCheck {

    public AimB(PlayerData data) {
        super(data);
    }

    @Override
    public void onRotation(RotationUpdate u) {
        if (!u.isMouseInput() || u.inVehicle() || data.inVehicleRecently(10)) return;
        if (Math.abs(u.deltaYaw()) > 320f) {
            flag("Δyaw=" + Text.fmt(u.deltaYaw(), 1));
        }
    }
}
