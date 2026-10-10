package ru.strazh.ac.check;

/** Категории проверок. */
public enum Category {
    COMBAT("Бой"),
    MOVEMENT("Движение"),
    WORLD("Мир"),
    PLAYER("Игрок"),
    PACKET("Пакеты"),
    MISC("Прочее");

    private final String display;

    Category(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
