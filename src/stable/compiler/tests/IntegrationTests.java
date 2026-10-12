package stable.compiler.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import stable.compiler.Diagnostic;
import stable.compiler.Translator;

final class IntegrationTests {
    private IntegrationTests() {
    }

    static void run() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("out").toAbsolutePath().normalize(), "stable-integration-");
        try {
            valid(directory);
            invalid(directory);
            runtimeFailures(directory);
        } finally {
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) Files.deleteIfExists(file);
            }
            Files.deleteIfExists(directory);
        }
    }

    private static void valid(Path directory) throws Exception {
        expectOutput(directory, "precedence", "zherebtsov D { run { print(2 + 3 * 4); } }", "", "14\n");
        String features = """
                zherebtsov Feature {
                function herd<hoof> values() { herd<hoof> a = [1, 2]; return a; }
                function hoof change(herd<hoof> a) { a[0] = a[0] + 5; return a[0]; }
                run {
                  herd<hoof> a = values();
                  print(change(a));
                  print(a[0]);
                  hoof n = 010;
                  print(n);
                  hoof padded = 00000000000000000000010;
                  print(padded);
                  hoof minimum = -0002147483648;
                  print(minimum);
                  print(-5 / 2);
                  hoof x = 1;
                  if (tame) { hoof x = 2; print(x); }
                  print(x);
                  for i from 1 to 2 { print(i); }
                }
                }
                """;
        expectOutput(directory, "features", features, "", "6\n6\n10\n10\n-2147483648\n-2\n2\n1\n1\n2\n");

        String evaluation = """
                zherebtsov Eval {
                function hoof one() { print(1); return 2; }
                function hoof two() { print(3); return 4; }
                function hoof add(hoof a, hoof b) { return a + b; }
                run {
                  print(add(one(), two()));
                  neigh a = "same";
                  neigh b = "same";
                  print(a == b);
                  print(!1 < 2);
                  print(!"a" == "a");
                  print(wild && input<tamed>());
                  print(tame || input<tamed>());
                }
                }
                """;
        expectOutput(directory, "evaluation", evaluation, "", "1\n3\n6\ntame\nwild\nwild\nwild\ntame\n");

        String input = """
                zherebtsov In { run {
                  hoof i = input<hoof>(); stride d = input<stride>();
                  neigh s = input<neigh>(); tamed b = input<tamed>();
                  print(i); print(d); print(s); print(b);
                } }
                """;
        expectOutput(directory, "input", input, "-42\n-3.5\nHello World\nwild\n", "-42\n-3.5\nHello World\nwild\n");

        String control = """
                zherebtsov Control {
                function hoof lo() { print(10); return 2147483646; }
                function hoof hi() { print(20); return 2147483647; }
                function hoof value() { while (tame) { return 7; } }
                run { for i from lo() to hi() { print(i); } print(value()); }
                }
                """;
        expectOutput(directory, "control", control, "", "10\n20\n2147483646\n2147483647\n7\n");
        expectOutput(directory, "some-name", "zherebtsov Demo { run { print(8); } }", "", "8\n");
        expectOutput(directory, "keywords", "zherebtsov class { function hoof String() { return 5; } run { hoof class = 1; if (tame) { hoof class = 2; print(class); } print(class); print(String()); } }", "", "2\n1\n5\n");
    }

    private static void invalid(Path directory) throws Exception {
        invalidSource(directory, "lexical", "zherebtsov D { run { @ } }", "Lexical");
        invalidSource(directory, "syntax", "zherebtsov D { run { print(1) } }", "Syntax");
        invalidSource(directory, "semantic", "zherebtsov D { run { hoof x = 1.0; } }", "Semantic");
    }

    private static void runtimeFailures(Path directory) throws Exception {
        expectFailure(directory, "divide", "zherebtsov D { run { stride x = 1.0 / 0.0; print(x); } }", "");
        expectFailure(directory, "integer-zero", "zherebtsov D { run { hoof x = 1 / 0; print(x); } }", "");
        expectFailure(directory, "bounds", "zherebtsov D { run { herd<hoof> a = [1]; print(a[1]); } }", "");
        expectFailure(directory, "bad-input", "zherebtsov D { run { hoof x = input<hoof>(); print(x); } }", "42.5\n");
        expectFailure(directory, "bad-real", "zherebtsov D { run { stride x = input<stride>(); print(x); } }", "42\n");
        expectFailure(directory, "bad-boolean", "zherebtsov D { run { tamed x = input<tamed>(); print(x); } }", "true\n");
    }

    private static void invalidSource(Path directory, String stem, String code, String category) throws IOException {
        Path input = directory.resolve(stem + ".stable");
        Path target = directory.resolve("Zherebtsov" + Character.toUpperCase(stem.charAt(0)) + stem.substring(1) + ".java");
        Files.writeString(input, code, StandardCharsets.UTF_8);
        try {
            Translator.translate(input);
            throw new AssertionError("expected " + category + " failure");
        } catch (Diagnostic error) {
            TranslatorTests.check(error.category().equals(category) && error.line() > 0 && error.column() > 0, "diagnostic " + category);
        }
        TranslatorTests.check(!Files.exists(target), "invalid source created output");
    }

    private static void expectOutput(Path directory, String stem, String code, String input, String expected) throws Exception {
        RunResult result = compileAndRun(directory, stem, code, input);
        TranslatorTests.check(result.exitCode == 0, stem + " failed: " + result.output);
        TranslatorTests.check(result.output.replace("\r\n", "\n").equals(expected), stem + " output: " + result.output);
    }

    private static void expectFailure(Path directory, String stem, String code, String input) throws Exception {
        RunResult result = compileAndRun(directory, stem, code, input);
        TranslatorTests.check(result.exitCode != 0, stem + " should fail at runtime");
    }

    private static RunResult compileAndRun(Path directory, String stem, String code, String input) throws Exception {
        Path source = directory.resolve(stem + ".stable");
        Files.writeString(source, code, StandardCharsets.UTF_8);
        Path generated = Translator.translate(source);
        String className = generated.getFileName().toString().replaceFirst("\\.java$", "");
        TranslatorTests.check(generated.getFileName().toString().equals(className + ".java"), "Java filename: " + stem);
        TranslatorTests.check(Files.readAllLines(generated, StandardCharsets.UTF_8).get(0).equals("public final class " + className + " {"), "public class matches filename");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        TranslatorTests.check(compiler != null, "JDK compiler required");
        int compileExit = compiler.run(null, null, null, "-d", directory.toString(), generated.toString());
        TranslatorTests.check(compileExit == 0, "generated Java failed compilation: " + generated);
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Process process = new ProcessBuilder(List.of(java.toString(), "-cp", directory.toString(), className))
                .redirectErrorStream(true).start();
        process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new RunResult(process.waitFor(), output);
    }

    private record RunResult(int exitCode, String output) {
    }
}
