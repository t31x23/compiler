package stable.compiler;

import java.io.IOException;
import java.nio.file.Path;

public final class Main {
    // Точка входа: этот класс только обрабатывает командную строку.
    private Main() {
    }

    public static void main(String[] args) {
        // Транслятору нужен ровно один путь к исходной программе Stable.
        if (args.length != 1) {
            System.err.println("Usage: java stable.compiler.Main <source-file>");
            System.exit(1);
        }
        try {
            // Вся работа по переводу находится в Translator; здесь печатаем путь результата.
            Path target = Translator.translate(Path.of(args[0]));
            System.out.println(target);
        } catch (Diagnostic | IOException | IllegalArgumentException error) {
            // Ошибки исходного кода, ввода-вывода и неверного пути дают ненулевой код выхода.
            System.err.println(error);
            System.exit(1);
        }
    }
}
