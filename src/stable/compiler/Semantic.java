package stable.compiler;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class Semantic {
    record VariableSymbol(String sourceName, String javaName, StableType type, boolean readOnly) {
    }

    record FunctionSymbol(String sourceName, String javaName, StableType result, List<StableType> params) {
    }

    final IdentityHashMap<Ast.Expr, StableType> types = new IdentityHashMap<>();
    final IdentityHashMap<Object, VariableSymbol> variables = new IdentityHashMap<>();
    final IdentityHashMap<Ast.Call, FunctionSymbol> calls = new IdentityHashMap<>();
    final IdentityHashMap<Ast.Function, FunctionSymbol> functions = new IdentityHashMap<>();
    private final Deque<Map<String, VariableSymbol>> scopes = new ArrayDeque<>();
    private final Map<String, FunctionSymbol> visibleFunctions = new HashMap<>();
    private StableType returnType;
    private int nextName;

    void analyze(Ast.Program program) {
        for (Ast.Function function : program.functions()) {
            if (visibleFunctions.containsKey(function.name()))
                fail(function.pos(), "duplicate function '" + function.name() + "'");
            List<StableType> signature = function.params().stream().map(Ast.Param::type).toList();
            FunctionSymbol symbol = new FunctionSymbol(function.name(), name("f"), function.type(), signature);
            visibleFunctions.put(function.name(), symbol);
            functions.put(function, symbol);
            returnType = function.type();
            List<VariableSymbol> parameters = new ArrayList<>();
            for (Ast.Param param : function.params()) {
                VariableSymbol variable = new VariableSymbol(param.name(), name("v"), param.type(), false);
                variables.put(param, variable);
                parameters.add(variable);
            }
            if (checkBlock(function.body(), parameters)) fail(function.pos(), "function may end without return");
        }
        returnType = null;
        checkBlock(program.run(), List.of());
    }

    private boolean checkBlock(Ast.Block block, List<VariableSymbol> initial) {
        scopes.push(new HashMap<>());
        try {
            for (VariableSymbol symbol : initial) declare(symbol, block.pos());
            boolean fallsThrough = true;
            for (Ast.Stmt statement : block.statements()) {
                boolean statementFallsThrough = checkStmt(statement);
                if (fallsThrough) fallsThrough = statementFallsThrough;
            }
            return fallsThrough;
        } finally {
            scopes.pop();
        }
    }

    private boolean checkStmt(Ast.Stmt statement) {
        if (statement instanceof Ast.VarDecl declaration) {
            require(checkExpr(declaration.value(), declaration.type()), declaration.type(), declaration.pos());
            VariableSymbol symbol = new VariableSymbol(declaration.name(), name("v"), declaration.type(), false);
            declare(symbol, declaration.pos());
            variables.put(declaration, symbol);
        } else if (statement instanceof Ast.Assign assignment) {
            StableType targetType = checkExpr(assignment.target(), null);
            if (assignment.target() instanceof Ast.Variable variable && variables.get(variable).readOnly()) {
                fail(assignment.pos(), "cannot assign to for variable '" + variable.name() + "'");
            }
            require(checkExpr(assignment.value(), targetType), targetType, assignment.pos());
        } else if (statement instanceof Ast.Print print) {
            StableType type = checkExpr(print.value(), null);
            if (type.array()) fail(print.pos(), "cannot print an array");
        } else if (statement instanceof Ast.If conditional) {
            require(checkExpr(conditional.condition(), null), StableType.TAMED, conditional.pos());
            boolean thenFalls = checkBlock(conditional.thenBlock(), List.of());
            boolean elseFalls = conditional.elseBlock() == null || checkBlock(conditional.elseBlock(), List.of());
            return thenFalls || elseFalls;
        } else if (statement instanceof Ast.While loop) {
            require(checkExpr(loop.condition(), null), StableType.TAMED, loop.pos());
            boolean bodyFalls = checkBlock(loop.body(), List.of());
            if (loop.condition() instanceof Ast.Literal literal && literal.kind().equals("tame") && !bodyFalls)
                return false;
        } else if (statement instanceof Ast.For loop) {
            require(checkExpr(loop.start(), null), StableType.HOOF, loop.pos());
            require(checkExpr(loop.end(), null), StableType.HOOF, loop.pos());
            VariableSymbol symbol = new VariableSymbol(loop.name(), name("v"), StableType.HOOF, true);
            variables.put(loop, symbol);
            checkBlock(loop.body(), List.of(symbol));
        } else if (statement instanceof Ast.Return result) {
            if (returnType == null) fail(result.pos(), "return is only allowed in a function");
            require(checkExpr(result.value(), returnType), returnType, result.pos());
            return false;
        } else if (statement instanceof Ast.CallStmt call) {
            checkExpr(call.call(), null);
        } else if (statement instanceof Ast.Block block) {
            return checkBlock(block, List.of());
        } else throw new IllegalStateException("unknown statement");
        return true;
    }

    private StableType checkExpr(Ast.Expr expression, StableType expected) {
        StableType type;
        if (expression instanceof Ast.Literal literal) {
            type = switch (literal.kind()) {
                case "INT" -> {
                    checkInteger(literal.text(), literal.pos());
                    yield StableType.HOOF;
                }
                case "REAL" -> {
                    checkReal(literal.text(), literal.pos());
                    yield StableType.STRIDE;
                }
                case "STRING" -> StableType.NEIGH;
                case "tame", "wild" -> StableType.TAMED;
                default -> throw new IllegalStateException("unknown literal");
            };
        } else if (expression instanceof Ast.Variable variable) {
            VariableSymbol symbol = lookup(variable.name(), variable.pos());
            variables.put(variable, symbol);
            type = symbol.type();
        } else if (expression instanceof Ast.Call call) {
            FunctionSymbol symbol = visibleFunctions.get(call.name());
            if (symbol == null) fail(call.pos(), "unknown or later-declared function '" + call.name() + "'");
            if (call.args().size() != symbol.params().size())
                fail(call.pos(), "wrong number of arguments for '" + call.name() + "'");
            for (int i = 0; i < call.args().size(); i++) {
                StableType paramType = symbol.params().get(i);
                require(checkExpr(call.args().get(i), paramType), paramType, call.args().get(i).pos());
            }
            calls.put(call, symbol);
            type = symbol.result();
        } else if (expression instanceof Ast.Index index) {
            StableType arrayType = checkExpr(index.array(), null);
            if (!arrayType.array()) fail(index.pos(), "indexing requires an array");
            require(checkExpr(index.index(), null), StableType.HOOF, index.index().pos());
            type = arrayType.element();
        } else if (expression instanceof Ast.ArrayLiteral array) {
            if (expected == null || !expected.array())
                fail(array.pos(), "array literal requires an expected array type");
            for (Ast.Expr element : array.elements()) {
                require(checkExpr(element, expected.element()), expected.element(), element.pos());
            }
            type = expected;
        } else if (expression instanceof Ast.Input input) {
            type = input.type();
        } else if (expression instanceof Ast.Unary unary) {
            if (unary.op().equals("-") && unary.value() instanceof Ast.Literal literal && literal.kind().equals("INT") && new BigInteger(literal.text()).equals(BigInteger.valueOf(2147483648L))) {
                types.put(literal, StableType.HOOF);
                type = StableType.HOOF;
            } else {
                StableType operand = checkExpr(unary.value(), null);
                if (unary.op().equals("-")) {
                    if (!operand.numeric()) fail(unary.pos(), "unary '-' requires a number");
                    type = operand;
                } else {
                    require(operand, StableType.TAMED, unary.pos());
                    type = StableType.TAMED;
                }
            }
        } else if (expression instanceof Ast.Binary binary) {
            StableType left = checkExpr(binary.left(), null);
            StableType right = checkExpr(binary.right(), null);
            if (!left.equals(right) || left.array())
                fail(binary.pos(), "operator '" + binary.op() + "' requires matching simple types");
            type = binaryType(binary.op(), left, binary.pos());
        } else throw new IllegalStateException("unknown expression");
        if (expected != null) require(type, expected, expression.pos());
        types.put(expression, type);
        return type;
    }

    private StableType binaryType(String op, StableType operand, Pos pos) {
        return switch (op) {
            case "+" -> {
                if (!operand.numeric() && !operand.equals(StableType.NEIGH))
                    fail(pos, "'+' requires numbers or strings");
                yield operand;
            }
            case "-", "*", "/" -> {
                if (!operand.numeric()) fail(pos, "arithmetic operator requires numbers");
                yield operand;
            }
            case "%" -> {
                require(operand, StableType.HOOF, pos);
                yield StableType.HOOF;
            }
            case "<", "<=", ">", ">=" -> {
                if (!operand.numeric()) fail(pos, "relation requires numbers");
                yield StableType.TAMED;
            }
            case "==", "!=" -> StableType.TAMED;
            case "&&", "||" -> {
                require(operand, StableType.TAMED, pos);
                yield StableType.TAMED;
            }
            default -> throw new IllegalStateException("unknown operator " + op);
        };
    }

    private void declare(VariableSymbol symbol, Pos pos) {
        Map<String, VariableSymbol> scope = scopes.peek();
        if (scope.containsKey(symbol.sourceName())) fail(pos, "duplicate variable '" + symbol.sourceName() + "'");
        scope.put(symbol.sourceName(), symbol);
    }

    private VariableSymbol lookup(String name, Pos pos) {
        for (Map<String, VariableSymbol> scope : scopes) {
            VariableSymbol symbol = scope.get(name);
            if (symbol != null) return symbol;
        }
        fail(pos, "unknown variable '" + name + "'");
        return null;
    }

    private String name(String prefix) {
        return "st_" + prefix + nextName++;
    }

    private static void require(StableType actual, StableType expected, Pos pos) {
        if (!actual.equals(expected)) fail(pos, "expected " + expected + ", found " + actual);
    }

    private static void checkInteger(String text, Pos pos) {
        if (new BigInteger(text).compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0)
            fail(pos, "hoof literal out of range");
    }

    private static void checkReal(String text, Pos pos) {
        try {
            if (!Double.isFinite(Double.parseDouble(text))) fail(pos, "stride literal out of range");
        } catch (NumberFormatException e) {
            fail(pos, "stride literal out of range");
        }
    }

    private static void fail(Pos pos, String message) {
        throw new Diagnostic("Semantic", pos, message);
    }
}
