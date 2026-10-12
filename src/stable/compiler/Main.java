package stable.compiler;

import java.io.IOException;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java stable.compiler.Main <source-file>");
            System.exit(1);
        }
        try {
            Path target = Translator.translate(Path.of(args[0]));
            System.out.println(target);
        } catch (Diagnostic | IOException | IllegalArgumentException error) {
            System.err.println(error);
            System.exit(1);
        }
    }
}
