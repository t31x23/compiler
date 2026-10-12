package stable.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Translator {
    private Translator() {
    }

    public static Path translate(Path sourcePath) throws IOException {
        Path source = sourcePath.toAbsolutePath().normalize();
        String filename = source.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        String text = Files.readString(source, StandardCharsets.UTF_8);
        Ast.Program program = new Parser(new Lexer(text).scan()).parse();
        Semantic semantic = new Semantic();
        semantic.analyze(program);
        Emitter emitter = new Emitter(semantic, stem);
        String java = emitter.emit(program);
        Path target = source.resolveSibling(emitter.className() + ".java");
        Path temporary = Files.createTempFile(source.getParent(), ".stable-", ".tmp");
        try {
            Files.writeString(temporary, java, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }
}
