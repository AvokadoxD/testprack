package ru.strazh.ac.sim.engine;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;
import ru.strazh.ac.util.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Окружение игрока по блокам мира (только главный поток, без загрузки чанков).
 */
final class WorldScan {

    boolean unloaded;
    boolean liquid, lava, climbable, web, powderSnow, berry, honey, slime, bed, soul, ice, bubble, piston, scaffolding;
    /** Есть твёрдая опора под ногами (в пределах 0.03 блока). */
    boolean groundBelow;
    /** Под ногами только жидкость (для Jesus). */
    boolean liquidBelow;
    /** Опора под ногами — блок, форма которого зависит от контекста (порошковый снег, леса). */
    boolean contextualBelow;
    /** Скользкость блока под ногами (y − 0.5). */
    float slipperiness = 0.6f;
    /** Блок над головой (для прыжков под потолком). */
    boolean ceiling;
    /** Боковое столкновение со стеной. */
    boolean wall;
    final List<AABB> collisions = new ArrayList<>(32);

    /**
     * @param region область для классификации блоков (обычно старый ∪ новый бокс)
     * @param box    бокс игрока в конце тика (для опоры, потолка, стен)
     */
    static WorldScan scan(World w, AABB region, AABB box) {
        WorldScan s = new WorldScan();
        int minX = (int) Math.floor(region.minX() - 0.31), maxX = (int) Math.floor(region.maxX() + 0.31);
        int minY = (int) Math.floor(region.minY() - 0.6), maxY = (int) Math.floor(region.maxY() + 0.6);
        int minZ = (int) Math.floor(region.minZ() - 0.31), maxZ = (int) Math.floor(region.maxZ() + 0.31);
        if (maxX - minX > 12 || maxY - minY > 12 || maxZ - minZ > 12) {
            // Огромный скачок (телепорт/лаг) — не сканируем.
            s.unloaded = true;
            return s;
        }
        int minH = w.getMinHeight(), maxH = w.getMaxHeight();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!w.isChunkLoaded(cx, cz)) {
                    s.unloaded = true;
                    return s;
                }
            }
        }
        AABB touch = region.expand(0.001);
        for (int x = minX; x <= maxX; x++) {
            for (int y = Math.max(minY, minH); y <= Math.min(maxY, maxH - 1); y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = w.getBlockAt(x, y, z);
                    Material type = b.getType();
                    if (type.isAir()) continue;
                    boolean touching = touch.intersects(x, y, z, x + 1, y + 1, z + 1);
                    BlockData bd = null;
                    if (touching) {
                        bd = b.getBlockData();
                        classify(s, type, bd);
                    }
                    if (!type.isSolid() && !isSpecialCollider(type)) continue;
                    for (BoundingBox bb : b.getCollisionShape().getBoundingBoxes()) {
                        s.collisions.add(new AABB(x + bb.getMinX(), y + bb.getMinY(), z + bb.getMinZ(),
                                x + bb.getMaxX(), y + bb.getMaxY(), z + bb.getMaxZ()));
                    }
                }
            }
        }
        // Опора под ногами и стены/потолок.
        double feet = box.minY();
        for (AABB c : s.collisions) {
            boolean overlapXZ = c.maxX() > box.minX() + 1.0E-4 && c.minX() < box.maxX() - 1.0E-4
                    && c.maxZ() > box.minZ() + 1.0E-4 && c.minZ() < box.maxZ() - 1.0E-4;
            if (overlapXZ && c.maxY() <= feet + 1.0E-4 && c.maxY() >= feet - 0.03) {
                s.groundBelow = true;
            }
            if (overlapXZ && c.minY() >= box.maxY() - 1.0E-4 && c.minY() <= box.maxY() + 0.5) {
                s.ceiling = true;
            }
            boolean overlapY = c.maxY() > feet + 0.01 && c.minY() < box.maxY() - 0.01;
            if (overlapY && c.intersects(box.expand(0.02, 0, 0.02)) && !c.intersects(box.expand(-0.01, 0, -0.01))) {
                s.wall = true;
            }
        }
        // Блок под ногами: скользкость и «опора-жидкость».
        int bx = (int) Math.floor((box.minX() + box.maxX()) / 2);
        int bz = (int) Math.floor((box.minZ() + box.maxZ()) / 2);
        int by = (int) Math.floor(feet - 0.5000001);
        if (by >= minH && by < maxH) {
            Material under = w.getBlockAt(bx, by, bz).getType();
            float slip = under.isAir() ? 0.6f : under.getSlipperiness();
            s.slipperiness = slip <= 0 ? 0.6f : slip;
        }
        int fy = (int) Math.floor(feet - 0.05);
        if (fy >= minH && fy < maxH) {
            Block under = w.getBlockAt(bx, fy, bz);
            Material m = under.getType();
            if (m == Material.POWDER_SNOW || m == Material.SCAFFOLDING) s.contextualBelow = true;
            if (!s.groundBelow && (m == Material.WATER || m == Material.LAVA
                    || (under.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged()))) {
                s.liquidBelow = true;
            }
        }
        return s;
    }

    private static boolean isSpecialCollider(Material type) {
        return type == Material.SCAFFOLDING || type == Material.POWDER_SNOW || type == Material.BAMBOO
                || type == Material.LILY_PAD || type == Material.SNOW;
    }

    private static void classify(WorldScan s, Material type, BlockData bd) {
        switch (type) {
            case WATER, BUBBLE_COLUMN, KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS -> s.liquid = true;
            case LAVA -> {
                s.liquid = true;
                s.lava = true;
            }
            case COBWEB -> s.web = true;
            case POWDER_SNOW -> s.powderSnow = true;
            case SWEET_BERRY_BUSH -> s.berry = true;
            case HONEY_BLOCK -> s.honey = true;
            case SLIME_BLOCK -> s.slime = true;
            case SOUL_SAND, SOUL_SOIL -> s.soul = true;
            case PISTON, STICKY_PISTON, MOVING_PISTON, PISTON_HEAD -> s.piston = true;
            case SCAFFOLDING -> {
                s.scaffolding = true;
                s.climbable = true;
            }
            default -> {
            }
        }
        if (type == Material.BUBBLE_COLUMN) s.bubble = true;
        if (Tag.CLIMBABLE.isTagged(type)) s.climbable = true;
        if (Tag.ICE.isTagged(type)) s.ice = true;
        if (Tag.BEDS.isTagged(type)) s.bed = true;
        if (bd instanceof Waterlogged wl && wl.isWaterlogged()) s.liquid = true;
    }

    /** Пересекает ли бокс твёрдые формы (с небольшим допуском), не пересекавшиеся старым боксом. */
    boolean newlyInsideSolid(AABB now, AABB before) {
        AABB n = now.expand(-0.02);
        AABB b = before.expand(-0.02);
        for (AABB c : collisions) {
            if (c.intersects(n) && !c.intersects(b)) return true;
        }
        return false;
    }
}
