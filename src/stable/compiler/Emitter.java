package stable.compiler;

import stable.compiler.constants.Constants;

final class Emitter {
    // Генератор получает уже проверенное дерево и таблицы типов/Java-имён.
    private final Semantic semantic;
    private final String className;
    private final StringBuilder output = new StringBuilder();
    private int depth;
    // Счётчик уникальных служебных имён внутри сгенерированного класса.
    private int temporary;

    Emitter(Semantic semantic, String sourceStem) {
        this.semantic = semantic;
        this.className = classNameFor(sourceStem);
    }

    String className() {
        return className;
    }

    static String classNameFor(String sourceStem) {
        // Имя файла Stable преобразуется в допустимое имя публичного Java-класса.
        StringBuilder name = new StringBuilder("Zherebtsov");
        boolean capitalize = true;
        for (int i = 0; i < sourceStem.length(); i++) {
            char ch = sourceStem.charAt(i);
            if (ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9') {
                // Первый символ фрагмента делаем заглавным, остальные сохраняем.
                name.append(capitalize ? Character.toUpperCase(ch) : ch);
                capitalize = false;
            } else {
                // Небуквенный символ кодируется как _uXXXX_, чтобы имя было легальным.
                name.append("_u").append(String.format("%04X", (int) ch)).append('_');
                capitalize = true;
            }
        }
        return sourceStem.isEmpty() ? "ZherebtsovProgram" : name.toString();
    }

    String emit(Ast.Program program) {
        // Один исходный файл Stable превращается в один публичный Java-класс.
        line("public final class " + className + " {");
        depth++;
        // Добавляем функции ввода и другие служебные методы, затем функции Stable.
        helpers();
        for (Ast.Function function : program.functions()) emitFunction(function);
        // Блок run становится точкой входа Java-программы.
        line("public static void main(String[] args) {");
        depth++;
        emitStatements(program.run());
        depth--;
        line(Constants.RBRACE);
        depth--;
        line(Constants.RBRACE);
        return output.toString();
    }

    private void emitFunction(Ast.Function function) {
        // Сигнатура берётся из AST, безопасные имена — из Semantic.
        StringBuilder header = new StringBuilder("private static ");
        header.append(javaType(function.type())).append(' ').append(semantic.functions.get(function).javaName()).append('(');
        for (int i = 0; i < function.params().size(); i++) {
            if (i > 0) header.append(Constants.JAVA_COMMA);
            Ast.Param param = function.params().get(i);
            header.append(javaType(param.type())).append(' ').append(semantic.variables.get(param).javaName());
        }
        header.append(Constants.JAVA_BLOCK_HEADER_END);
        line(header.toString());
        depth++;
        boolean javaFallsThrough = emitStatements(function.body());
        // Анализ уже доказал наличие return; эта ветка удовлетворяет javac
        // в случаях, где он не может доказать то же по сгенерированному коду.
        if (javaFallsThrough) line("throw new AssertionError(\"unreachable Stable function path\");");
        depth--;
        line(Constants.RBRACE);
    }

    private boolean emitStatements(Ast.Block block) {
        // После безусловного return код не печатается: Java отвергнет
        // недостижимые операторы, хотя Stable-анализ их уже проверил.
        boolean fallsThrough = true;
        for (Ast.Stmt statement : block.statements()) {
            if (!fallsThrough) break;
            fallsThrough = emitStatement(statement);
        }
        return fallsThrough;
    }

    private boolean emitStatement(Ast.Stmt statement) {
        // Каждому виду оператора AST соответствует его Java-форма.
        if (statement instanceof Ast.VarDecl declaration) {
            line(javaType(declaration.type()) + Constants.SPACE + semantic.variables.get(declaration).javaName() + Constants.JAVA_ASSIGN + expr(declaration.value()) + Constants.SEMICOLON);
        } else if (statement instanceof Ast.Assign assignment) {
            line(expr(assignment.target()) + Constants.JAVA_ASSIGN + expr(assignment.value()) + Constants.SEMICOLON);
        } else if (statement instanceof Ast.Print print) {
            // Внутри tamed — boolean, а наружу печатаем слова tame/wild.
            String value = expr(print.value());
            if (semantic.types.get(print.value()).equals(StableType.TAMED))
                value = "((" + value + ") ? \"tame\" : \"wild\")";
            line("System.out.println(" + value + ");");
        } else if (statement instanceof Ast.CallStmt call) {
            line(expr(call.call()) + Constants.SEMICOLON);
        } else if (statement instanceof Ast.Return returned) {
            // После return текущая ветвь уже не продолжается.
            line("return " + expr(returned.value()) + Constants.SEMICOLON);
            return false;
        } else if (statement instanceof Ast.If conditional) {
            // Флаг продолжения истинен, если хотя бы одна ветвь доходит до конца.
            line("if (" + expr(conditional.condition()) + ") {");
            depth++;
            boolean thenFalls = emitStatements(conditional.thenBlock());
            depth--;
            line(Constants.RBRACE);
            boolean elseFalls = true;
            if (conditional.elseBlock() != null) {
                line("else {");
                depth++;
                elseFalls = emitStatements(conditional.elseBlock());
                depth--;
                line(Constants.RBRACE);
            }
            return thenFalls || elseFalls;
        } else if (statement instanceof Ast.While loop) {
            // Обёртка st_truth сохраняет корректную компиляцию некоторых
            // бесконечных циклов с return при правилах достижимости Java.
            line("while (st_truth(" + expr(loop.condition()) + ")) {");
            depth++;
            emitStatements(loop.body());
            depth--;
            line(Constants.RBRACE);
        } else if (statement instanceof Ast.For loop) {
            // Границы вычисляем ровно по одному разу. long-счётчик позволяет
            // включить Integer.MAX_VALUE, не переполнившись после последней итерации.
            String start = "st_start" + temporary++;
            String end = "st_end" + temporary++;
            String counter = "st_counter" + temporary++;
            line(Constants.LBRACE);
            depth++;
            line(Constants.JAVA_INT_PREFIX + start + Constants.JAVA_ASSIGN + expr(loop.start()) + Constants.SEMICOLON);
            line(Constants.JAVA_INT_PREFIX + end + Constants.JAVA_ASSIGN + expr(loop.end()) + Constants.SEMICOLON);
            line("for (long " + counter + Constants.JAVA_ASSIGN + start + Constants.JAVA_SEMICOLON_SPACE + counter + " <= " + end + Constants.JAVA_SEMICOLON_SPACE + counter + "++) {");
            depth++;
            // Видимый в Stable счётчик остаётся int и имеет безопасное имя.
            line(Constants.JAVA_INT_PREFIX + semantic.variables.get(loop).javaName() + " = (int) " + counter + Constants.SEMICOLON);
            emitStatements(loop.body());
            depth--;
            line(Constants.RBRACE);
            depth--;
            line(Constants.RBRACE);
        } else if (statement instanceof Ast.Block block) {
            // Отдельные скобки сохраняют границу области видимости блока.
            line(Constants.LBRACE);
            depth++;
            boolean fallsThrough = emitStatements(block);
            depth--;
            line(Constants.RBRACE);
            return fallsThrough;
        } else throw new IllegalStateException(Constants.ERROR_UNKNOWN_STATEMENT);
        return true;
    }

    private String expr(Ast.Expr expression) {
        // Возвращаем текст Java-выражения, сохраняя порядок вычисления.
        if (expression instanceof Ast.Literal literal) {
            // Целое нормализуем через BigInteger: ведущие нули не станут
            // для Java восьмеричной записью. Булевы слова переводим в boolean.
            return switch (literal.kind()) {
                case Constants.TOKEN_INT -> new java.math.BigInteger(literal.text()).toString();
                case Constants.TOKEN_REAL -> literal.text();
                case Constants.TOKEN_STRING -> Constants.DOUBLE_QUOTE + literal.text() + Constants.DOUBLE_QUOTE;
                case Constants.LIT_TAME -> "true";
                case Constants.LIT_WILD -> "false";
                default -> throw new IllegalStateException(Constants.ERROR_UNKNOWN_LITERAL);
            };
        }
        // Имена берём из результатов анализа, а не из исходного текста Stable.
        if (expression instanceof Ast.Variable variable) return semantic.variables.get(variable).javaName();
        if (expression instanceof Ast.Call call) {
            // Аргументы идут в исходном порядке, что важно при побочных эффектах.
            StringBuilder result = new StringBuilder(semantic.calls.get(call).javaName()).append('(');
            for (int i = 0; i < call.args().size(); i++) {
                if (i > 0) result.append(Constants.JAVA_COMMA);
                result.append(expr(call.args().get(i)));
            }
            return result.append(')').toString();
        }
        if (expression instanceof Ast.Index index) return Constants.LPAREN + expr(index.array()) + ")[" + expr(index.index()) + Constants.RBRACKET;
        if (expression instanceof Ast.ArrayLiteral array) {
            // Контекстный тип из Semantic определяет тип Java-массива.
            StringBuilder result = new StringBuilder("new ").append(javaType(semantic.types.get(array).element())).append("[] {");
            for (int i = 0; i < array.elements().size(); i++) {
                if (i > 0) result.append(Constants.JAVA_COMMA);
                result.append(expr(array.elements().get(i)));
            }
            return result.append('}').toString();
        }
        if (expression instanceof Ast.Input input) {
            // Разные типы ввода вызывают разные проверки строки в helpers().
            return switch (input.type().base()) {
                case Constants.TYPE_HOOF -> "st_readInt()";
                case Constants.TYPE_STRIDE -> "st_readDouble()";
                case Constants.TYPE_NEIGH -> "st_readLine()";
                case Constants.TYPE_TAMED -> "st_readBool()";
                default -> throw new IllegalStateException("unknown input type");
            };
        }
        if (expression instanceof Ast.Unary unary) {
            // Минимальное int — отдельная константа Java, иначе литерал 2147483648
            // сам по себе не пройдёт компиляцию javac.
            if (unary.op().equals(Constants.OP_MINUS) && unary.value() instanceof Ast.Literal literal && literal.kind().equals(Constants.TOKEN_INT) && new java.math.BigInteger(literal.text()).equals(java.math.BigInteger.valueOf(2147483648L)))
                return "Integer.MIN_VALUE";
            return Constants.LPAREN + unary.op() + expr(unary.value()) + Constants.RPAREN;
        }
        if (expression instanceof Ast.Binary binary) {
            // Скобки защищают приоритеты Stable от отличий в Java.
            String left = expr(binary.left());
            String right = expr(binary.right());
            StableType operand = semantic.types.get(binary.left());
            // Строки сравниваются по содержимому, а не по ссылке Java.
            if (operand.equals(StableType.NEIGH) && (binary.op().equals(Constants.OP_EQ) || binary.op().equals(Constants.OP_NE))) {
                String comparison = "java.util.Objects.equals(" + left + Constants.JAVA_COMMA + right + Constants.RPAREN;
                return binary.op().equals(Constants.OP_NE) ? "(!" + comparison + Constants.RPAREN : comparison;
            }
            // Для stride деление на ноль должно вызвать ошибку времени выполнения.
            if (operand.equals(StableType.STRIDE) && binary.op().equals(Constants.OP_DIVIDE))
                return "st_div(" + left + Constants.JAVA_COMMA + right + Constants.RPAREN;
            return Constants.LPAREN + left + Constants.SPACE + binary.op() + Constants.SPACE + right + Constants.RPAREN;
        }
        throw new IllegalStateException(Constants.ERROR_UNKNOWN_EXPRESSION);
    }

    private static String javaType(StableType type) {
        // Прямое соответствие простых типов Stable типам Java.
        String base = switch (type.base()) {
            case Constants.TYPE_HOOF -> "int";
            case Constants.TYPE_STRIDE -> "double";
            case Constants.TYPE_NEIGH -> "String";
            case Constants.TYPE_TAMED -> "boolean";
            default -> throw new IllegalStateException("unknown type");
        };
        // herd<T> представляется обычным массивом T[].
        return type.array() ? base + "[]" : base;
    }

    private void helpers() {
        // Все вызовы input<T>() читают по одной UTF-8 строке из общего reader.
        line("private static final java.io.BufferedReader st_reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));");
        // Служебная обёртка boolean для генерации while.
        line("private static boolean st_truth(boolean value) { return value; }");
        // Java-double при делении на ноль даёт Infinity; Stable требует ошибку.
        line("private static double st_div(double left, double right) { if (right == 0.0) throw new ArithmeticException(\"stride division by zero\"); return left / right; }");
        // Конец ввода и I/O-ошибка превращаются в исключения запуска программы.
        line("private static String st_readLine() { try { String line = st_reader.readLine(); if (line == null) throw new IllegalArgumentException(\"end of input\"); return line; } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); } }");
        // Для чисел сначала проверяем формат, затем преобразуем и проверяем диапазон.
        line("private static int st_readInt() { String value = st_readLine(); if (!value.matches(\"[+-]?[0-9]+\")) throw new IllegalArgumentException(\"invalid hoof input\"); return Integer.parseInt(value); }");
        line("private static double st_readDouble() { String value = st_readLine(); if (!value.matches(\"[+-]?[0-9]+\\\\.[0-9]+\")) throw new IllegalArgumentException(\"invalid stride input\"); double result = Double.parseDouble(value); if (!Double.isFinite(result)) throw new IllegalArgumentException(\"invalid stride input\"); return result; }");
        // tamed допускает только слова tame и wild.
        line("private static boolean st_readBool() { String value = st_readLine(); if (value.equals(\"tame\")) return true; if (value.equals(\"wild\")) return false; throw new IllegalArgumentException(\"invalid tamed input\"); }");
    }

    private void line(String line) {
        // Единая точка форматирования: отступ по уровню вложенности и перевод строки.
        output.append("    ".repeat(depth)).append(line).append('\n');
    }
}
