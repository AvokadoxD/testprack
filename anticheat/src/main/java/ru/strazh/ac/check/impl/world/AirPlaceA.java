package ru.strazh.ac.check.impl.world;

import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockPlaceEvent;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.BukkitCheck;
import ru.strazh.ac.player.PlayerData;

/** Блок поставлен «об воздух»: ванильно кликнуть можно только по существующему блоку. */
@CheckInfo(name = "AirPlace", type = "A", category = Category.WORLD,
        description = "Установка блока без опоры (об воздух)", maxVl = 8, decay = 1.0)
public final class AirPlaceA extends Check implements BukkitCheck {

    public AirPlaceA(PlayerData data) {
        super(data);
    }

    @Override
    public void onBukkitEvent(Event event) {
        if (!(event instanceof BlockPlaceEvent e) || data.client().bedrock) return;
        Material against = e.getBlockAgainst().getType();
        if (against.isAir() && !e.getBlockPlaced().equals(e.getBlockAgainst())) {
            e.setCancelled(true);
            flag("опора: " + against.name().toLowerCase());
        }
    }
}
