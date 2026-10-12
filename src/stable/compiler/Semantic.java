package stable.compiler;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import stable.compiler.constants.Constants;

final class Semantic {
    // Символ хранит тип Stable и безопасное имя, которое будет использовано в Java.
    record VariableSymbol(String sourceName, String javaName, StableType type, boolean readOnly) {
    }

    // Сигнатура функции нужна для проверки вызовов и генерации Java-метода.
    record FunctionSymbol(String sourceName, String javaName, StableType result, List<StableType> params) {
    }

    // Привязки индексируются самими узлами AST, чтобы генератор видел результат анализа.
    final IdentityHashMap<Ast.Expr, StableType> types = new IdentityHashMap<>();
    final IdentityHashMap<Object, VariableSymbol> variables = new IdentityHashMap<>();
    final IdentityHashMap<Ast.Call, FunctionSymbol> calls = new IdentityHashMap<>();
    final IdentityHashMap<Ast.Function, FunctionSymbol> functions = new IdentityHashMap<>();
    // Стек областей видимости позволяет искать локальные имена изнутри наружу.
    private final Deque<Map<String, VariableSymbol>> scopes = new ArrayDeque<>();
    // Функции становятся видимыми по мере обхода: вызов более поздней запрещён.
    private final Map<String, FunctionSymbol> visibleFunctions = new HashMap<>();
    private StableType returnType;
    private int nextName;

    void analyze(Ast.Program program) {
        // Проверяем функции по порядку объявления и сразу добавляем текущую
        // функцию в таблицу, чтобы рекурсивный вызов был разрешён.
        for (Ast.Function function : program.functions()) {
            if (visibleFunctions.containsKey(function.name()))
                fail(function.pos(), "duplicate function '" + function.name() + Constants.APOSTROPHE);
            List<StableType> signature = function.params().stream().map(Ast.Param::type).toList();
            FunctionSymbol symbol = new FunctionSymbol(function.name(), name("f"), function.type(), signature);
            visibleFunctions.put(function.name(), symbol);
            functions.put(function, symbol);
            returnType = function.type();
            // Параметры становятся начальными переменными области тела функции.
            List<VariableSymbol> parameters = new ArrayList<>();
            for (Ast.Param param : function.params()) {
                VariableSymbol variable = new VariableSymbol(param.name(), name(Constants.GENERATED_VARIABLE_PREFIX), param.type(), false);
                variables.put(param, variable);
                parameters.add(variable);
            }
            // Если хотя бы один путь доходит до конца, обязательного return нет.
            if (checkBlock(function.body(), parameters)) fail(function.pos(), "function may end without return");
        }
        // В run возврат запрещён, поэтому текущий возвращаемый тип сбрасывается.
        returnType = null;
        checkBlock(program.run(), List.of());
    }

    private boolean checkBlock(Ast.Block block, List<VariableSymbol> initial) {
        // Каждый блок создаёт собственную область имён; вложенный блок может
        // перекрыть внешнее имя, а объявление в том же блоке повторить нельзя.
        scopes.push(new HashMap<>());
        try {
            for (VariableSymbol symbol : initial) declare(symbol, block.pos());
            boolean fallsThrough = true;
            for (Ast.Stmt statement : block.statements()) {
                // Проверяем даже недостижимые операторы, но запоминаем,
                // возможен ли путь выполнения за концом блока.
                boolean statementFallsThrough = checkStmt(statement);
                if (fallsThrough) fallsThrough = statementFallsThrough;
            }
            return fallsThrough;
        } finally {
            // Область убирается также при ошибке внутри блока.
            scopes.pop();
        }
    }

    private boolean checkStmt(Ast.Stmt statement) {
        if (statement instanceof Ast.VarDecl declaration) {
            // Инициализатор проверяется до объявления имени: самоссылка невозможна.
            require(checkExpr(declaration.value(), declaration.type()), declaration.type(), declaration.pos());
            VariableSymbol symbol = new VariableSymbol(declaration.name(), name(Constants.GENERATED_VARIABLE_PREFIX), declaration.type(), false);
            declare(symbol, declaration.pos());
            variables.put(declaration, symbol);
        } else if (statement instanceof Ast.Assign assignment) {
            // Для присваивания проверяем цель, её изменяемость и тип значения.
            StableType targetType = checkExpr(assignment.target(), null);
            if (assignment.target() instanceof Ast.Variable variable && variables.get(variable).readOnly()) {
                fail(assignment.pos(), "cannot assign to for variable '" + variable.name() + Constants.APOSTROPHE);
            }
            require(checkExpr(assignment.value(), targetType), targetType, assignment.pos());
        } else if (statement instanceof Ast.Print print) {
            // По спецификации массив целиком печатать нельзя.
            StableType type = checkExpr(print.value(), null);
            if (type.array()) fail(print.pos(), "cannot print an array");
        } else if (statement instanceof Ast.If conditional) {
            // Условие должно быть tamed; путь завершается только если обе ветви
            // обязательно завершаются return.
            require(checkExpr(conditional.condition(), null), StableType.TAMED, conditional.pos());
            boolean thenFalls = checkBlock(conditional.thenBlock(), List.of());
            boolean elseFalls = conditional.elseBlock() == null || checkBlock(conditional.elseBlock(), List.of());
            return thenFalls || elseFalls;
        } else if (statement instanceof Ast.While loop) {
            // Цикл обычно может не выполниться. Исключение — while(tame)
            // с телом, которое точно завершает выполнение функции.
            require(checkExpr(loop.condition(), null), StableType.TAMED, loop.pos());
            boolean bodyFalls = checkBlock(loop.body(), List.of());
            if (loop.condition() instanceof Ast.Literal literal && literal.kind().equals(Constants.LIT_TAME) && !bodyFalls)
                return false;
        } else if (statement instanceof Ast.For loop) {
            // Границы — целые hoof; счётчик объявляется только внутри тела
            // и помечается как неизменяемый для прямого присваивания.
            require(checkExpr(loop.start(), null), StableType.HOOF, loop.pos());
            require(checkExpr(loop.end(), null), StableType.HOOF, loop.pos());
            VariableSymbol symbol = new VariableSymbol(loop.name(), name(Constants.GENERATED_VARIABLE_PREFIX), StableType.HOOF, true);
            variables.put(loop, symbol);
            checkBlock(loop.body(), List.of(symbol));
        } else if (statement instanceof Ast.Return result) {
            // return допускается лишь в функции и должен вернуть её тип.
            if (returnType == null) fail(result.pos(), "return is only allowed in a function");
            require(checkExpr(result.value(), returnType), returnType, result.pos());
            return false;
        } else if (statement instanceof Ast.CallStmt call) {
            // Вызов-оператор проверяется как обычное выражение, результат игнорируется.
            checkExpr(call.call(), null);
        } else if (statement instanceof Ast.Block block) {
            return checkBlock(block, List.of());
        } else throw new IllegalStateException(Constants.ERROR_UNKNOWN_STATEMENT);
        return true;
    }

    private StableType checkExpr(Ast.Expr expression, StableType expected) {
        // Определяем фактический тип выражения. expected нужен там, где тип
        // нельзя вывести из самого выражения, например у литерала массива.
        StableType type;
        if (expression instanceof Ast.Literal literal) {
            // Числовые литералы дополнительно проверяются на допустимый диапазон.
            type = switch (literal.kind()) {
                case Constants.TOKEN_INT -> {
                    checkInteger(literal.text(), literal.pos());
                    yield StableType.HOOF;
                }
                case Constants.TOKEN_REAL -> {
                    checkReal(literal.text(), literal.pos());
                    yield StableType.STRIDE;
                }
                case Constants.TOKEN_STRING -> StableType.NEIGH;
                case Constants.LIT_TAME, Constants.LIT_WILD -> StableType.TAMED;
                default -> throw new IllegalStateException(Constants.ERROR_UNKNOWN_LITERAL);
            };
        } else if (expression instanceof Ast.Variable variable) {
            // Поиск идёт от текущей области к внешним; сохраняем найденный символ.
            VariableSymbol symbol = lookup(variable.name(), variable.pos());
            variables.put(variable, symbol);
            type = symbol.type();
        } else if (expression instanceof Ast.Call call) {
            // Проверяем видимость функции, количество и типы аргументов.
            FunctionSymbol symbol = visibleFunctions.get(call.name());
            if (symbol == null) fail(call.pos(), "unknown or later-declared function '" + call.name() + Constants.APOSTROPHE);
            if (call.args().size() != symbol.params().size())
                fail(call.pos(), "wrong number of arguments for '" + call.name() + Constants.APOSTROPHE);
            for (int i = 0; i < call.args().size(); i++) {
                StableType paramType = symbol.params().get(i);
                require(checkExpr(call.args().get(i), paramType), paramType, call.args().get(i).pos());
            }
            calls.put(call, symbol);
            type = symbol.result();
        } else if (expression instanceof Ast.Index index) {
            // Индексировать можно только массив целым hoof; результат — элемент.
            StableType arrayType = checkExpr(index.array(), null);
            if (!arrayType.array()) fail(index.pos(), "indexing requires an array");
            require(checkExpr(index.index(), null), StableType.HOOF, index.index().pos());
            type = arrayType.element();
        } else if (expression instanceof Ast.ArrayLiteral array) {
            // Массив получает тип из контекста присваивания, параметра или return.
            if (expected == null || !expected.array())
                fail(array.pos(), "array literal requires an expected array type");
            for (Ast.Expr element : array.elements()) {
                require(checkExpr(element, expected.element()), expected.element(), element.pos());
            }
            type = expected;
        } else if (expression instanceof Ast.Input input) {
            // Тип ввода уже задан в синтаксисе input<T>().
            type = input.type();
        } else if (expression instanceof Ast.Unary unary) {
            // 2147483648 само по себе вне диапазона int, но -2147483648 допустимо.
            if (unary.op().equals(Constants.OP_MINUS) && unary.value() instanceof Ast.Literal literal && literal.kind().equals(Constants.TOKEN_INT) && new BigInteger(literal.text()).equals(BigInteger.valueOf(2147483648L))) {
                types.put(literal, StableType.HOOF);
                type = StableType.HOOF;
            } else {
                // Минус требует число, логическое отрицание — tamed.
                StableType operand = checkExpr(unary.value(), null);
                if (unary.op().equals(Constants.OP_MINUS)) {
                    if (!operand.numeric()) fail(unary.pos(), "unary '-' requires a number");
                    type = operand;
                } else {
                    require(operand, StableType.TAMED, unary.pos());
                    type = StableType.TAMED;
                }
            }
        } else if (expression instanceof Ast.Binary binary) {
            // Оба операнда должны иметь одинаковый простой тип;
            // допустимость конкретного оператора проверяется отдельно.
            StableType left = checkExpr(binary.left(), null);
            StableType right = checkExpr(binary.right(), null);
            if (!left.equals(right) || left.array())
                fail(binary.pos(), "operator '" + binary.op() + "' requires matching simple types");
            type = binaryType(binary.op(), left, binary.pos());
        } else throw new IllegalStateException(Constants.ERROR_UNKNOWN_EXPRESSION);
        // Проверяем требование контекста и сохраняем тип для генератора.
        if (expected != null) require(type, expected, expression.pos());
        types.put(expression, type);
        return type;
    }

    private StableType binaryType(String op, StableType operand, Pos pos) {
        // Результат зависит от оператора: арифметика сохраняет числовой тип,
        // сравнения и логические операции дают tamed.
        return switch (op) {
            case Constants.OP_PLUS -> {
                if (!operand.numeric() && !operand.equals(StableType.NEIGH))
                    fail(pos, "'+' requires numbers or strings");
                yield operand;
            }
            case Constants.OP_MINUS, Constants.OP_MULTIPLY, Constants.OP_DIVIDE -> {
                if (!operand.numeric()) fail(pos, "arithmetic operator requires numbers");
                yield operand;
            }
            case Constants.OP_REMAINDER -> {
                require(operand, StableType.HOOF, pos);
                yield StableType.HOOF;
            }
            case Constants.LT, Constants.OP_LE, Constants.GT, Constants.OP_GE -> {
                if (!operand.numeric()) fail(pos, "relation requires numbers");
                yield StableType.TAMED;
            }
            case Constants.OP_EQ, Constants.OP_NE -> StableType.TAMED;
            case Constants.OP_AND, Constants.OP_OR -> {
                require(operand, StableType.TAMED, pos);
                yield StableType.TAMED;
            }
            default -> throw new IllegalStateException("unknown operator " + op);
        };
    }

    private void declare(VariableSymbol symbol, Pos pos) {
        // Совпадение запрещено только в текущей области видимости.
        Map<String, VariableSymbol> scope = scopes.peek();
        if (scope.containsKey(symbol.sourceName())) fail(pos, "duplicate variable '" + symbol.sourceName() + Constants.APOSTROPHE);
        scope.put(symbol.sourceName(), symbol);
    }

    private VariableSymbol lookup(String name, Pos pos) {
        // Первый найденный символ перекрывает одноимённые внешние.
        for (Map<String, VariableSymbol> scope : scopes) {
            VariableSymbol symbol = scope.get(name);
            if (symbol != null) return symbol;
        }
        fail(pos, "unknown variable '" + name + Constants.APOSTROPHE);
        return null;
    }

    private String name(String prefix) {
        // Новые Java-имена исключают конфликты между переменными разных блоков
        // и с ключевыми словами Java.
        return "st_" + prefix + nextName++;
    }

    private static void require(StableType actual, StableType expected, Pos pos) {
        if (!actual.equals(expected)) fail(pos, "expected " + expected + ", found " + actual);
    }

    private static void checkInteger(String text, Pos pos) {
        // BigInteger позволяет проверить диапазон без переполнения при разборе.
        if (new BigInteger(text).compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0)
            fail(pos, "hoof literal out of range");
    }

    private static void checkReal(String text, Pos pos) {
        // Бесконечность от слишком большого литерала не входит в stride.
        try {
            if (!Double.isFinite(Double.parseDouble(text))) fail(pos, Constants.ERROR_STRIDE_RANGE);
        } catch (NumberFormatException e) {
            fail(pos, Constants.ERROR_STRIDE_RANGE);
        }
    }

    private static void fail(Pos pos, String message) {
        throw new Diagnostic(Constants.ERROR_SEMANTIC, pos, message);
    }
}
