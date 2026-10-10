package ru.strazh.ac.log;

import ru.strazh.ac.StrazhPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Запись нарушений в файлы logs/ГГГГ-ММ-ДД.log (в отдельном потоке). */
public final class ViolationLogger {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final StrazhPlugin plugin;
    private final File folder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Strazh-Logger");
        t.setDaemon(true);
        return t;
    });
    private LocalDate currentDate;
    private BufferedWriter writer;

    public ViolationLogger(StrazhPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "logs");
    }

    public void log(String line) {
        if (!plugin.config().logEnabled) return;
        String stamped = "[" + LocalTime.now().format(TIME) + "] " + line;
        executor.execute(() -> write(stamped));
    }

    private void write(String line) {
        try {
            LocalDate today = LocalDate.now();
            if (writer == null || !today.equals(currentDate)) {
                if (writer != null) writer.close();
                if (!folder.exists() && !folder.mkdirs()) return;
                currentDate = today;
                writer = Files.newBufferedWriter(new File(folder, today + ".log").toPath(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                cleanup();
            }
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось записать лог: " + ex.getMessage());
        }
    }

    private void cleanup() {
        int keep = plugin.config().logKeepDays;
        if (keep <= 0) return;
        File[] files = folder.listFiles((d, n) -> n.endsWith(".log"));
        if (files == null) return;
        LocalDate border = LocalDate.now().minusDays(keep);
        for (File f : files) {
            try {
                LocalDate d = LocalDate.parse(f.getName().substring(0, 10));
                if (d.isBefore(border)) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
            if (writer != null) writer.close();
        } catch (Exception ignored) {
        }
    }
}
