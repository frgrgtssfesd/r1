package timetravel;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;

/** Запись результата игры в файл results.txt. */
public class ResultWriter {
    public static void save(Player p, String ending) {
        String line = String.format("%s | финал: %s | здоровье=%d знания=%d ресурсы=%d | счёт=%d%n",
                LocalDateTime.now().withNano(0), ending, p.health, p.knowledge, p.resources, p.knowledge * 2 + p.resources + p.health);
        try {
            Files.writeString(Path.of("results.txt"), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            System.err.println("Не удалось сохранить результат: " + ex.getMessage());
        }
    }
}
