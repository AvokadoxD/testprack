package ru.strazh.ac.check;

import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.player.PlayerData;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Список всех проверок плагина и их создание для игрока. */
public final class CheckRegistry {

    private final StrazhPlugin plugin;
    private final List<Constructor<? extends Check>> constructors = new ArrayList<>();

    public CheckRegistry(StrazhPlugin plugin) {
        this.plugin = plugin;
        for (Class<? extends Check> type : Checks.ALL) {
            try {
                Constructor<? extends Check> c = type.getDeclaredConstructor(PlayerData.class);
                c.setAccessible(true);
                constructors.add(c);
            } catch (NoSuchMethodException e) {
                plugin.getLogger().severe("Проверка " + type.getName() + " не имеет конструктора (PlayerData)");
            }
        }
    }

    /** Экземпляры всех проверок для игрока. */
    public CheckSet create(PlayerData data) {
        List<Check> list = new ArrayList<>(constructors.size());
        for (Constructor<? extends Check> c : constructors) {
            try {
                Check check = c.newInstance(data);
                check.applySettings(plugin.config().settings(check.info()));
                list.add(check);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось создать проверку " + c.getDeclaringClass().getSimpleName(), t);
            }
        }
        return new CheckSet(list);
    }

    /** Создаёт прототипы (data == null), чтобы заполнить конфиг значениями по умолчанию. */
    public List<Check> prototypes() {
        List<Check> list = new ArrayList<>(constructors.size());
        for (Constructor<? extends Check> c : constructors) {
            try {
                Check check = c.newInstance((PlayerData) null);
                check.applySettings(plugin.config().settings(check.info()));
                list.add(check);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Прототип " + c.getDeclaringClass().getSimpleName() + ": " + t);
            }
        }
        return list;
    }

    public int count() {
        return constructors.size();
    }
}
