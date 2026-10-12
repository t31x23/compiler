package stable.compiler.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import stable.compiler.Diagnostic;
import stable.compiler.Translator;

public final class TranslatorTests {
    private TranslatorTests() {
    }

    public static void main(String[] args) throws Exception {
        lexer();
        parser();
        semantic();
        translationBoundary();
        stable.compiler.tests.IntegrationTests.run();
        System.out.println("Tests passed");
    }

    private static void parser() {
        String source = "zherebtsov Demo { function hoof id(hoof x) { return x; } run { hoof x = 2 + 3 * 4; print(id(x)); } }";
        translateSource(source);
        expectError("Syntax", () -> translateSource("zherebtsov D { run { print(1) } }"));
        expectError("Syntax", () -> translateSource("zherebtsov D { run {} } print(1);"));
        expectError("Syntax", () -> translateSource("zherebtsov D { run { print(--5); } }"));
    }

    private static void semantic() {
        analyze("zherebtsov D { run { herd<hoof> a = [1, 2]; herd<hoof> b = a; b[0] = 3; print(a[0]); } }");
        expectError("Semantic", () -> analyze("zherebtsov D { run { hoof x = 1 + 2.0; } }"));
        expectError("Semantic", () -> analyze("zherebtsov D { run { herd<hoof> a = [1, \"x\"]; } }"));
        analyze("zherebtsov D { run { hoof x = 1; if (tame) { hoof x = 2; print(x); } print(x); } }");
        analyze("zherebtsov D { function hoof fact(hoof n) { if (n <= 1) { return 1; } else { return n * fact(n - 1); } } run { print(fact(3)); } }");
        analyze("zherebtsov D { function hoof age() { return 5; } run { hoof age = 10; print(age); print(age()); } }");
        expectError("Semantic", () -> analyze("zherebtsov D { run { print(x); } }"));
        expectError("Semantic", () -> analyze("zherebtsov D { run { hoof x = 1; hoof x = 2; } }"));
        expectError("Semantic", () -> analyze("zherebtsov D { function hoof f(hoof x, hoof x) { return x; } run {} }"));
        expectError("Semantic", () -> analyze("zherebtsov D { run { for i from 1 to 3 { i = 2; } } }"));
        expectError("Semantic", () -> analyze("zherebtsov D { function hoof a() { return b(); } function hoof b() { return 1; } run {} }"));
        expectError("Semantic", () -> analyze("zherebtsov D { function hoof a(hoof x) { if (x > 0) { return x; } } run {} }"));
        expectError("Semantic", () -> analyze("zherebtsov D { function hoof a() { return \"wrong\"; } run {} }"));
        expectError("Semantic", () -> analyze("zherebtsov D { function hoof a() { return 1; } run { print(a(1)); } }"));
        analyze("zherebtsov D { function hoof a() { return 1; print(2); } run {} }");
        analyze("zherebtsov D { run { hoof min = -2147483648; print(min); } }");
        expectError("Semantic", () -> analyze("zherebtsov D { run { hoof bad = 2147483648; } }"));
    }

    private static void translationBoundary() throws Exception {
        check(className("someName").equals("ZherebtsovSomeName"), "readable Java class name");
        check(className("some-name").equals("ZherebtsovSome_u002D_Name"), "escaped filename characters");
        Path directory = Files.createTempDirectory(Path.of("out").toAbsolutePath(), "stable-tests-");
        Path input = directory.resolve("bad.stable");
        Path output = directory.resolve("ZherebtsovBad.java");
        try {
            Files.writeString(input, "zherebtsov D {\nrun { hoof bad = 2147483648; }\n}", StandardCharsets.UTF_8);
            Files.writeString(output, "sentinel", StandardCharsets.UTF_8);
            try {
                Translator.translate(input);
                throw new AssertionError("expected semantic error");
            } catch (Diagnostic error) {
                check(error.category().equals("Semantic") && error.line() == 2 && error.column() > 1, "diagnostic category and location");
            }
            check(Files.readString(output).equals("sentinel"), "failed translation preserved output");
            Files.writeString(input, "zherebtsov D { run { print(1); } }", StandardCharsets.UTF_8);
            check(Translator.translate(input).equals(output), "output naming");
            check(Files.readString(output).startsWith("public final class ZherebtsovBad {"), "public class matches file");
            check(Files.readString(output).contains("public static void main"), "successful replacement");
            Files.delete(input);
            try {
                Translator.translate(input);
                throw new AssertionError("expected missing input error");
            } catch (java.io.IOException expected) {
                check(expected.getMessage().contains("bad.stable"), "useful I/O path");
            }
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
            Files.deleteIfExists(directory);
        }
    }

    private static void analyze(String source) {
        translateSource(source);
    }

    private static String className(String stem) throws IOException {
        Path directory = Files.createTempDirectory(Path.of("out").toAbsolutePath(), "stable-name-");
        Path source = directory.resolve(stem + ".stable");
        Path target = null;
        try {
            Files.writeString(source, "zherebtsov D { run {} }", StandardCharsets.UTF_8);
            target = Translator.translate(source);
            return target.getFileName().toString().replaceFirst("\\.java$", "");
        } finally {
            Files.deleteIfExists(source);
            if (target != null) Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }

    private static void translateSource(String code) {
        try {
            Path directory = Files.createTempDirectory(Path.of("out").toAbsolutePath(), "stable-source-");
            Path source = directory.resolve("sample.stable");
            Path target = directory.resolve("ZherebtsovSample.java");
            try {
                Files.writeString(source, code, StandardCharsets.UTF_8);
                Translator.translate(source);
            } finally {
                Files.deleteIfExists(source);
                Files.deleteIfExists(target);
                Files.deleteIfExists(directory);
            }
        } catch (IOException error) {
            throw new AssertionError(error);
        }
    }

    private static void lexer() {
        translateSource("zherebtsov D { run { neigh s = \"a//b\"; // ignored\nif (tame && 2 <= 3) { print(s); } } }");
        expectError("Lexical", () -> translateSource("@"));
        expectError("Lexical", () -> translateSource("\"hello\n"));
    }

    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void expectError(String category, Runnable action) {
        try {
            action.run();
        } catch (Diagnostic error) {
            check(error.category().equals(category), "expected " + category + ", got " + error);
            return;
        }
        throw new AssertionError("expected " + category + " error");
    }
}
