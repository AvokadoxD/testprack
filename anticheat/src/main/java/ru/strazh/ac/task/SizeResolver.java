package ru.strazh.ac.task;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import ru.strazh.ac.player.tracker.TrackedEntity;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Уточняет размеры хитбоксов сущностей через Bukkit (главный поток):
 * слаймы, детёныши, масштаб и т.д.
 */
public final class SizeResolver {

    private final ConcurrentLinkedQueue<TrackedEntity> queue = new ConcurrentLinkedQueue<>();

    public void request(TrackedEntity e) {
        if (queue.size() < 10_000) queue.add(e);
    }

    /** Обрабатывает запросы (главный поток). */
    public void tick() {
        int budget = 256;
        TrackedEntity e;
        while (budget-- > 0 && (e = queue.poll()) != null) {
            if (e.uuid == null) continue;
            Entity entity = Bukkit.getEntity(e.uuid);
            if (entity == null) continue;
            double scale = 1.0;
            if (entity instanceof org.bukkit.entity.LivingEntity living) {
                var attr = living.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                if (attr != null && attr.getValue() > 0.01) scale = attr.getValue();
            }
            // Размеры Bukkit уже учитывают масштаб — храним базовые, масштаб учитывается отдельно.
            e.width = entity.getWidth() / scale;
            e.height = entity.getHeight() / scale;
            e.exactSize = true;
        }
    }
}
