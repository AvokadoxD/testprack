package ru.strazh.ac.compat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import ru.strazh.ac.StrazhPlugin;

import java.util.Locale;
import java.util.Set;

/**
 * Совместимость с кастомным транспортом (Skript-машины и т.п.).
 *
 * <p>Кастомные машины сажают игрока на item_display-«сиденье» с тэгами
 * car_seat / car_main / car_id_* и каждый тик телепортируют его, поворачивают
 * камеру пакетом PlayerRotation, а при столкновениях выставляют скорость.
 * Всё это не должно вызывать срабатываний, поэтому езда на таком транспорте
 * полностью исключает проверки движения и аима.
 */
public final class VehicleCompat {

    /** Типы сущностей, которыми ванильный игрок не может управлять — это всегда «кастомный» транспорт. */
    private static final Set<EntityType> NON_VANILLA_VEHICLES = Set.of(
            EntityType.ITEM_DISPLAY, EntityType.BLOCK_DISPLAY, EntityType.TEXT_DISPLAY,
            EntityType.ARMOR_STAND, EntityType.INTERACTION, EntityType.MARKER,
            EntityType.ITEM, EntityType.FALLING_BLOCK, EntityType.AREA_EFFECT_CLOUD
    );

    private final StrazhPlugin plugin;

    public VehicleCompat(StrazhPlugin plugin) {
        this.plugin = plugin;
    }

    /** Является ли транспорт (или любой транспорт под ним) кастомным. */
    public boolean isCustomVehicle(Entity vehicle) {
        int guard = 0;
        Entity e = vehicle;
        while (e != null && guard++ < 8) {
            if (isCustomSingle(e)) return true;
            e = e.getVehicle();
        }
        return false;
    }

    private boolean isCustomSingle(Entity e) {
        if (plugin.config().exemptNonVanillaVehicles && NON_VANILLA_VEHICLES.contains(e.getType())) {
            return true;
        }
        Set<String> tags = e.getScoreboardTags();
        if (tags.isEmpty()) return false;
        Set<String> configured = plugin.config().carTags;
        for (String tag : tags) {
            String t = tag.toLowerCase(Locale.ROOT);
            if (configured.contains(t)) return true;
            if (t.startsWith("car_id_") || t.startsWith("car_seat_") || t.startsWith("moped_id_")
                    || t.startsWith("car_part_")) {
                return true;
            }
        }
        return false;
    }
}
