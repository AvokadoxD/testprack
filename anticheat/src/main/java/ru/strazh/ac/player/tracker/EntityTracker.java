package ru.strazh.ac.player.tracker;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Locale;
import java.util.Map;

/** Отслеживание сущностей вокруг игрока по исходящим пакетам (event loop). */
public final class EntityTracker {

    private final Int2ObjectOpenHashMap<TrackedEntity> entities = new Int2ObjectOpenHashMap<>();

    public TrackedEntity get(int id) {
        return entities.get(id);
    }

    public void put(TrackedEntity e) {
        entities.put(e.id, e);
    }

    public TrackedEntity remove(int id) {
        return entities.remove(id);
    }

    public void clear() {
        entities.clear();
    }

    public int size() {
        return entities.size();
    }

    public Iterable<TrackedEntity> all() {
        return entities.values();
    }

    public void onConfirmed(long seq, long clientTick) {
        for (TrackedEntity e : entities.values()) {
            e.onConfirmed(seq, clientTick);
        }
    }

    /** Размеры по умолчанию (ширина, высота) для типов сущностей 1.21. */
    private static final Map<String, double[]> SIZES = Map.ofEntries(
            Map.entry("player", new double[]{0.6, 1.8}),
            Map.entry("zombie", new double[]{0.6, 1.95}),
            Map.entry("husk", new double[]{0.6, 1.95}),
            Map.entry("drowned", new double[]{0.6, 1.95}),
            Map.entry("zombie_villager", new double[]{0.6, 1.95}),
            Map.entry("villager", new double[]{0.6, 1.95}),
            Map.entry("wandering_trader", new double[]{0.6, 1.95}),
            Map.entry("witch", new double[]{0.6, 1.95}),
            Map.entry("pillager", new double[]{0.6, 1.95}),
            Map.entry("vindicator", new double[]{0.6, 1.95}),
            Map.entry("evoker", new double[]{0.6, 1.95}),
            Map.entry("illusioner", new double[]{0.6, 1.95}),
            Map.entry("piglin", new double[]{0.6, 1.95}),
            Map.entry("piglin_brute", new double[]{0.6, 1.95}),
            Map.entry("zombified_piglin", new double[]{0.6, 1.95}),
            Map.entry("skeleton", new double[]{0.6, 1.99}),
            Map.entry("stray", new double[]{0.6, 1.99}),
            Map.entry("bogged", new double[]{0.6, 1.99}),
            Map.entry("wither_skeleton", new double[]{0.7, 2.4}),
            Map.entry("creeper", new double[]{0.6, 1.7}),
            Map.entry("spider", new double[]{1.4, 0.9}),
            Map.entry("cave_spider", new double[]{0.7, 0.5}),
            Map.entry("enderman", new double[]{0.6, 2.9}),
            Map.entry("iron_golem", new double[]{1.4, 2.7}),
            Map.entry("snow_golem", new double[]{0.7, 1.9}),
            Map.entry("blaze", new double[]{0.6, 1.8}),
            Map.entry("breeze", new double[]{0.6, 1.77}),
            Map.entry("creaking", new double[]{0.9, 2.7}),
            Map.entry("warden", new double[]{0.9, 2.9}),
            Map.entry("ravager", new double[]{1.95, 2.2}),
            Map.entry("hoglin", new double[]{1.3965, 1.4}),
            Map.entry("zoglin", new double[]{1.3965, 1.4}),
            Map.entry("cow", new double[]{0.9, 1.4}),
            Map.entry("mooshroom", new double[]{0.9, 1.4}),
            Map.entry("pig", new double[]{0.9, 0.9}),
            Map.entry("sheep", new double[]{0.9, 1.3}),
            Map.entry("chicken", new double[]{0.4, 0.7}),
            Map.entry("wolf", new double[]{0.6, 0.85}),
            Map.entry("cat", new double[]{0.6, 0.7}),
            Map.entry("ocelot", new double[]{0.6, 0.7}),
            Map.entry("fox", new double[]{0.6, 0.7}),
            Map.entry("rabbit", new double[]{0.4, 0.5}),
            Map.entry("horse", new double[]{1.3964844, 1.6}),
            Map.entry("donkey", new double[]{1.3964844, 1.5}),
            Map.entry("mule", new double[]{1.3964844, 1.6}),
            Map.entry("skeleton_horse", new double[]{1.3964844, 1.6}),
            Map.entry("zombie_horse", new double[]{1.3964844, 1.6}),
            Map.entry("camel", new double[]{1.7, 2.375}),
            Map.entry("llama", new double[]{0.9, 1.87}),
            Map.entry("trader_llama", new double[]{0.9, 1.87}),
            Map.entry("goat", new double[]{0.9, 1.3}),
            Map.entry("panda", new double[]{1.3, 1.25}),
            Map.entry("polar_bear", new double[]{1.4, 1.4}),
            Map.entry("bee", new double[]{0.7, 0.6}),
            Map.entry("phantom", new double[]{0.9, 0.5}),
            Map.entry("ghast", new double[]{4.0, 4.0}),
            Map.entry("happy_ghast", new double[]{4.0, 4.0}),
            Map.entry("guardian", new double[]{0.85, 0.85}),
            Map.entry("elder_guardian", new double[]{1.9975, 1.9975}),
            Map.entry("shulker", new double[]{1.0, 1.0}),
            Map.entry("endermite", new double[]{0.4, 0.3}),
            Map.entry("silverfish", new double[]{0.4, 0.3}),
            Map.entry("vex", new double[]{0.4, 0.8}),
            Map.entry("allay", new double[]{0.35, 0.6}),
            Map.entry("parrot", new double[]{0.5, 0.9}),
            Map.entry("bat", new double[]{0.5, 0.9}),
            Map.entry("armadillo", new double[]{0.7, 0.65}),
            Map.entry("sniffer", new double[]{1.9, 1.75}),
            Map.entry("frog", new double[]{0.5, 0.5}),
            Map.entry("axolotl", new double[]{0.75, 0.42}),
            Map.entry("turtle", new double[]{1.2, 0.4}),
            Map.entry("dolphin", new double[]{0.9, 0.6}),
            Map.entry("squid", new double[]{0.8, 0.8}),
            Map.entry("glow_squid", new double[]{0.8, 0.8}),
            Map.entry("strider", new double[]{0.9, 1.7}),
            Map.entry("armor_stand", new double[]{0.5, 1.975}),
            Map.entry("end_crystal", new double[]{2.0, 2.0}),
            Map.entry("ender_dragon", new double[]{16.0, 8.0}),
            Map.entry("wither", new double[]{0.9, 3.5}),
            Map.entry("minecart", new double[]{0.98, 0.7}),
            Map.entry("item_frame", new double[]{0.75, 0.75}),
            Map.entry("glow_item_frame", new double[]{0.75, 0.75}),
            Map.entry("painting", new double[]{1.0, 1.0}),
            Map.entry("interaction", new double[]{1.0, 1.0}),
            Map.entry("slime", new double[]{2.04, 2.04}),
            Map.entry("magma_cube", new double[]{2.04, 2.04})
    );

    /** @return {ширина, высота, 1 если размер известен точно по таблице иначе 0} */
    public static double[] defaultSize(String typeName) {
        String key = typeName.toLowerCase(Locale.ROOT);
        int colon = key.indexOf(':');
        if (colon >= 0) key = key.substring(colon + 1);
        double[] s = SIZES.get(key);
        if (s != null) {
            return new double[]{s[0], s[1], key.equals("slime") || key.equals("magma_cube") ? 0 : 1};
        }
        if (key.endsWith("boat") || key.endsWith("raft")) {
            return new double[]{1.375, 0.5625, 1};
        }
        if (key.contains("minecart")) {
            return new double[]{0.98, 0.7, 1};
        }
        return new double[]{0.6, 1.8, 0};
    }
}
