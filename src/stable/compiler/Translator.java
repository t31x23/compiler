package stable.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Translator {
    // Связывает все этапы перевода и отвечает за запись готового Java-файла.
    private Translator() {
    }

    public static Path translate(Path sourcePath) throws IOException {
        // Нормализуем путь и получаем основу имени будущего Java-класса.
        Path source = sourcePath.toAbsolutePath().normalize();
        String filename = source.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        // Исходник читается как UTF-8; лексер выдаёт токены, парсер строит AST.
        String text = Files.readString(source, StandardCharsets.UTF_8);
        Ast.Program program = new Parser(new Lexer(text).scan()).parse();
        // Анализ проверяет типы и имена до генерации файла.
        Semantic semantic = new Semantic();
        semantic.analyze(program);
        // Генератор использует проверенное дерево и сопоставление имён с Java.
        Emitter emitter = new Emitter(semantic, stem);
        String java = emitter.emit(program);
        Path target = source.resolveSibling(emitter.className() + ".java");
        // Сначала пишем временный файл: неудачная трансляция не затронет прежний результат.
        Path temporary = Files.createTempFile(source.getParent(), ".stable-", ".tmp");
        try {
            Files.writeString(temporary, java, StandardCharsets.UTF_8);
            try {
                // По возможности заменяем целевой файл атомарно.
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                // На файловых системах без атомарного переноса используем обычное.
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // Временный файл удаляется и после успешной записи, и после ошибки.
            Files.deleteIfExists(temporary);
        }
        return target;
    }
}
