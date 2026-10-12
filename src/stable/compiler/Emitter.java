package stable.compiler;

final class Emitter {
    private final Semantic semantic;
    private final String className;
    private final StringBuilder output = new StringBuilder();
    private int depth;
    private int temporary;

    Emitter(Semantic semantic, String sourceStem) {
        this.semantic = semantic;
        this.className = classNameFor(sourceStem);
    }

    String className() {
        return className;
    }

    static String classNameFor(String sourceStem) {
        StringBuilder name = new StringBuilder("Zherebtsov");
        boolean capitalize = true;
        for (int i = 0; i < sourceStem.length(); i++) {
            char ch = sourceStem.charAt(i);
            if (ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9') {
                name.append(capitalize ? Character.toUpperCase(ch) : ch);
                capitalize = false;
            } else {
                name.append("_u").append(String.format("%04X", (int) ch)).append('_');
                capitalize = true;
            }
        }
        return sourceStem.isEmpty() ? "ZherebtsovProgram" : name.toString();
    }

    String emit(Ast.Program program) {
        line("public final class " + className + " {");
        depth++;
        helpers();
        for (Ast.Function function : program.functions()) emitFunction(function);
        line("public static void main(String[] args) {");
        depth++;
        emitStatements(program.run());
        depth--;
        line("}");
        depth--;
        line("}");
        return output.toString();
    }

    private void emitFunction(Ast.Function function) {
        StringBuilder header = new StringBuilder("private static ");
        header.append(javaType(function.type())).append(' ').append(semantic.functions.get(function).javaName()).append('(');
        for (int i = 0; i < function.params().size(); i++) {
            if (i > 0) header.append(", ");
            Ast.Param param = function.params().get(i);
            header.append(javaType(param.type())).append(' ').append(semantic.variables.get(param).javaName());
        }
        header.append(") {");
        line(header.toString());
        depth++;
        boolean javaFallsThrough = emitStatements(function.body());
        if (javaFallsThrough) line("throw new AssertionError(\"unreachable Stable function path\");");
        depth--;
        line("}");
    }

    private boolean emitStatements(Ast.Block block) {
        boolean fallsThrough = true;
        for (Ast.Stmt statement : block.statements()) {
            if (!fallsThrough) break;
            fallsThrough = emitStatement(statement);
        }
        return fallsThrough;
    }

    private boolean emitStatement(Ast.Stmt statement) {
        if (statement instanceof Ast.VarDecl declaration) {
            line(javaType(declaration.type()) + " " + semantic.variables.get(declaration).javaName() + " = " + expr(declaration.value()) + ";");
        } else if (statement instanceof Ast.Assign assignment) {
            line(expr(assignment.target()) + " = " + expr(assignment.value()) + ";");
        } else if (statement instanceof Ast.Print print) {
            String value = expr(print.value());
            if (semantic.types.get(print.value()).equals(StableType.TAMED))
                value = "((" + value + ") ? \"tame\" : \"wild\")";
            line("System.out.println(" + value + ");");
        } else if (statement instanceof Ast.CallStmt call) {
            line(expr(call.call()) + ";");
        } else if (statement instanceof Ast.Return returned) {
            line("return " + expr(returned.value()) + ";");
            return false;
        } else if (statement instanceof Ast.If conditional) {
            line("if (" + expr(conditional.condition()) + ") {");
            depth++;
            boolean thenFalls = emitStatements(conditional.thenBlock());
            depth--;
            line("}");
            boolean elseFalls = true;
            if (conditional.elseBlock() != null) {
                line("else {");
                depth++;
                elseFalls = emitStatements(conditional.elseBlock());
                depth--;
                line("}");
            }
            return thenFalls || elseFalls;
        } else if (statement instanceof Ast.While loop) {
            line("while (st_truth(" + expr(loop.condition()) + ")) {");
            depth++;
            emitStatements(loop.body());
            depth--;
            line("}");
        } else if (statement instanceof Ast.For loop) {
            String start = "st_start" + temporary++;
            String end = "st_end" + temporary++;
            String counter = "st_counter" + temporary++;
            line("{");
            depth++;
            line("int " + start + " = " + expr(loop.start()) + ";");
            line("int " + end + " = " + expr(loop.end()) + ";");
            line("for (long " + counter + " = " + start + "; " + counter + " <= " + end + "; " + counter + "++) {");
            depth++;
            line("int " + semantic.variables.get(loop).javaName() + " = (int) " + counter + ";");
            emitStatements(loop.body());
            depth--;
            line("}");
            depth--;
            line("}");
        } else if (statement instanceof Ast.Block block) {
            line("{");
            depth++;
            boolean fallsThrough = emitStatements(block);
            depth--;
            line("}");
            return fallsThrough;
        } else throw new IllegalStateException("unknown statement");
        return true;
    }

    private String expr(Ast.Expr expression) {
        if (expression instanceof Ast.Literal literal) {
            return switch (literal.kind()) {
                case "INT" -> new java.math.BigInteger(literal.text()).toString();
                case "REAL" -> literal.text();
                case "STRING" -> "\"" + literal.text() + "\"";
                case "tame" -> "true";
                case "wild" -> "false";
                default -> throw new IllegalStateException("unknown literal");
            };
        }
        if (expression instanceof Ast.Variable variable) return semantic.variables.get(variable).javaName();
        if (expression instanceof Ast.Call call) {
            StringBuilder result = new StringBuilder(semantic.calls.get(call).javaName()).append('(');
            for (int i = 0; i < call.args().size(); i++) {
                if (i > 0) result.append(", ");
                result.append(expr(call.args().get(i)));
            }
            return result.append(')').toString();
        }
        if (expression instanceof Ast.Index index) return "(" + expr(index.array()) + ")[" + expr(index.index()) + "]";
        if (expression instanceof Ast.ArrayLiteral array) {
            StringBuilder result = new StringBuilder("new ").append(javaType(semantic.types.get(array).element())).append("[] {");
            for (int i = 0; i < array.elements().size(); i++) {
                if (i > 0) result.append(", ");
                result.append(expr(array.elements().get(i)));
            }
            return result.append('}').toString();
        }
        if (expression instanceof Ast.Input input) {
            return switch (input.type().base()) {
                case "hoof" -> "st_readInt()";
                case "stride" -> "st_readDouble()";
                case "neigh" -> "st_readLine()";
                case "tamed" -> "st_readBool()";
                default -> throw new IllegalStateException("unknown input type");
            };
        }
        if (expression instanceof Ast.Unary unary) {
            if (unary.op().equals("-") && unary.value() instanceof Ast.Literal literal && literal.kind().equals("INT") && new java.math.BigInteger(literal.text()).equals(java.math.BigInteger.valueOf(2147483648L)))
                return "Integer.MIN_VALUE";
            return "(" + unary.op() + expr(unary.value()) + ")";
        }
        if (expression instanceof Ast.Binary binary) {
            String left = expr(binary.left());
            String right = expr(binary.right());
            StableType operand = semantic.types.get(binary.left());
            if (operand.equals(StableType.NEIGH) && (binary.op().equals("==") || binary.op().equals("!="))) {
                String comparison = "java.util.Objects.equals(" + left + ", " + right + ")";
                return binary.op().equals("!=") ? "(!" + comparison + ")" : comparison;
            }
            if (operand.equals(StableType.STRIDE) && binary.op().equals("/"))
                return "st_div(" + left + ", " + right + ")";
            return "(" + left + " " + binary.op() + " " + right + ")";
        }
        throw new IllegalStateException("unknown expression");
    }

    private static String javaType(StableType type) {
        String base = switch (type.base()) {
            case "hoof" -> "int";
            case "stride" -> "double";
            case "neigh" -> "String";
            case "tamed" -> "boolean";
            default -> throw new IllegalStateException("unknown type");
        };
        return type.array() ? base + "[]" : base;
    }

    private void helpers() {
        line("private static final java.io.BufferedReader st_reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));");
        line("private static boolean st_truth(boolean value) { return value; }");
        line("private static double st_div(double left, double right) { if (right == 0.0) throw new ArithmeticException(\"stride division by zero\"); return left / right; }");
        line("private static String st_readLine() { try { String line = st_reader.readLine(); if (line == null) throw new IllegalArgumentException(\"end of input\"); return line; } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); } }");
        line("private static int st_readInt() { String value = st_readLine(); if (!value.matches(\"[+-]?[0-9]+\")) throw new IllegalArgumentException(\"invalid hoof input\"); return Integer.parseInt(value); }");
        line("private static double st_readDouble() { String value = st_readLine(); if (!value.matches(\"[+-]?[0-9]+\\\\.[0-9]+\")) throw new IllegalArgumentException(\"invalid stride input\"); double result = Double.parseDouble(value); if (!Double.isFinite(result)) throw new IllegalArgumentException(\"invalid stride input\"); return result; }");
        line("private static boolean st_readBool() { String value = st_readLine(); if (value.equals(\"tame\")) return true; if (value.equals(\"wild\")) return false; throw new IllegalArgumentException(\"invalid tamed input\"); }");
    }

    private void line(String line) {
        output.append("    ".repeat(depth)).append(line).append('\n');
    }
}
