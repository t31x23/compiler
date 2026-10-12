package stable.compiler;

import java.util.ArrayList;
import java.util.List;
import stable.compiler.constants.Constants;

final class Parser {
    // cursor указывает на первый токен, который ещё не разобран.
    private final List<Token> tokens;
    private int cursor;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Ast.Program parse() {
        // Верхний уровень: заголовок, функции, обязательный run и конец файла.
        take(Constants.KW_PROGRAM);
        String name = take(Constants.TOKEN_IDENT).text();
        take(Constants.LBRACE);
        List<Ast.Function> functions = new ArrayList<>();
        while (at(Constants.KW_FUNCTION)) functions.add(function());
        take(Constants.KW_RUN);
        Ast.Block run = block();
        take(Constants.RBRACE);
        take(Constants.TOKEN_EOF);
        return new Ast.Program(name, functions, run);
    }

    private Ast.Function function() {
        // Читаем сигнатуру функции, затем её тело как обычный блок.
        Pos pos = take(Constants.KW_FUNCTION).pos();
        StableType type = type();
        String name = take(Constants.TOKEN_IDENT).text();
        take(Constants.LPAREN);
        List<Ast.Param> params = new ArrayList<>();
        // Пустой список допустим; иначе параметры разделены запятыми.
        if (!at(Constants.RPAREN)) {
            do {
                StableType paramType = type();
                Token paramName = take(Constants.TOKEN_IDENT);
                params.add(new Ast.Param(paramType, paramName.text(), paramName.pos()));
            } while (match(Constants.COMMA));
        }
        take(Constants.RPAREN);
        return new Ast.Function(type, name, params, block(), pos);
    }

    private StableType type() {
        // herd<тип> отличается от простого типа только оболочкой массива.
        boolean array = match(Constants.KW_HERD);
        if (array) take(Constants.LT);
        Token token = current();
        if (!StableType.SIMPLE.contains(token.kind())) throw error("expected a simple type");
        cursor++;
        if (array) take(Constants.GT);
        return new StableType(token.kind(), array);
    }

    private Ast.Block block() {
        // Блок читает операторы до закрывающей скобки; EOF здесь даёт ошибку.
        Pos pos = take(Constants.LBRACE).pos();
        List<Ast.Stmt> statements = new ArrayList<>();
        while (!at(Constants.RBRACE) && !at(Constants.TOKEN_EOF)) statements.add(statement());
        take(Constants.RBRACE);
        return new Ast.Block(statements, pos);
    }

    private Ast.Stmt statement() {
        Token start = current();
        // Начальное ключевое слово выбирает грамматическую форму оператора.
        if (StableType.SIMPLE.contains(start.kind()) || at(Constants.KW_HERD)) {
            StableType type = type();
            String name = take(Constants.TOKEN_IDENT).text();
            take(Constants.ASSIGN);
            Ast.Expr value = expression();
            take(Constants.SEMICOLON);
            return new Ast.VarDecl(type, name, value, start.pos());
        }
        if (match(Constants.KW_PRINT)) {
            take(Constants.LPAREN);
            Ast.Expr value = expression();
            take(Constants.RPAREN);
            take(Constants.SEMICOLON);
            return new Ast.Print(value, start.pos());
        }
        if (match(Constants.KW_IF)) {
            take(Constants.LPAREN);
            Ast.Expr condition = expression();
            take(Constants.RPAREN);
            Ast.Block thenBlock = block();
            // else отсутствует в AST, если ветка не указана в исходнике.
            Ast.Block elseBlock = match(Constants.KW_ELSE) ? block() : null;
            return new Ast.If(condition, thenBlock, elseBlock, start.pos());
        }
        if (match(Constants.KW_WHILE)) {
            take(Constants.LPAREN);
            Ast.Expr condition = expression();
            take(Constants.RPAREN);
            return new Ast.While(condition, block(), start.pos());
        }
        if (match(Constants.KW_FOR)) {
            // Границы цикла оставляем выражениями: их тип проверит Semantic.
            String name = take(Constants.TOKEN_IDENT).text();
            take(Constants.KW_FROM);
            Ast.Expr from = expression();
            take(Constants.KW_TO);
            Ast.Expr to = expression();
            return new Ast.For(name, from, to, block(), start.pos());
        }
        if (match(Constants.KW_RETURN)) {
            Ast.Expr value = expression();
            take(Constants.SEMICOLON);
            return new Ast.Return(value, start.pos());
        }
        if (at(Constants.TOKEN_IDENT)) {
            // Оператор с имени может быть присваиванием или вызовом функции.
            Ast.Expr target = postfix();
            if (match(Constants.ASSIGN)) {
                // Присваивать разрешено только переменной или элементу массива.
                if (!(target instanceof Ast.Variable) && !(target instanceof Ast.Index))
                    throw error("expected assignment target");
                Ast.Expr value = expression();
                take(Constants.SEMICOLON);
                return new Ast.Assign(target, value, start.pos());
            }
            if (target instanceof Ast.Call call) {
                take(Constants.SEMICOLON);
                return new Ast.CallStmt(call, start.pos());
            }
        }
        throw error("expected statement");
    }

    private Ast.Expr expression() {
        // 1 — минимальный приоритет; binary соберёт всё выражение целиком.
        return binary(1);
    }

    private Ast.Expr binary(int minimum) {
        // Алгоритм приоритетов: сначала левый операнд, затем подходящие операции.
        Ast.Expr left = unary();
        while (precedence(current().kind()) >= minimum) {
            Token op = current();
            cursor++;
            // Справа ищем только операции с более высоким приоритетом.
            // Поэтому * в «2 + 3 * 4» попадает внутрь правой части сложения.
            Ast.Expr right = binary(precedence(op.kind()) + 1);
            left = new Ast.Binary(op.kind(), left, right, op.pos());
        }
        return left;
    }

    private int precedence(String kind) {
        // Чем больше число, тем теснее оператор связывает свои операнды.
        return switch (kind) {
            case Constants.OP_OR -> 1;
            case Constants.OP_AND -> 2;
            case Constants.OP_EQ, Constants.OP_NE -> 3;
            case Constants.LT, Constants.OP_LE, Constants.GT, Constants.OP_GE -> 4;
            case Constants.OP_PLUS, Constants.OP_MINUS -> 5;
            case Constants.OP_MULTIPLY, Constants.OP_DIVIDE, Constants.OP_REMAINDER -> 6;
            default -> 0;
        };
    }

    private Ast.Expr unary() {
        // Унарный минус применяется к одному первичному выражению.
        if (at(Constants.OP_MINUS)) {
            Token op = current();
            cursor++;
            return new Ast.Unary(op.kind(), primary(), op.pos());
        }
        if (at(Constants.OP_NOT)) {
            // У ! своя граница разбора: сравнение справа входит в его операнд.
            Token op = current();
            cursor++;
            if (at(Constants.OP_NOT)) throw error("repeated unary operator requires parentheses");
            return new Ast.Unary(op.kind(), binary(3), op.pos());
        }
        return primary();
    }

    private Ast.Expr primary() {
        // Атом выражения: литерал, имя, ввод, массив или скобки.
        Token token = current();
        if (match(Constants.TOKEN_INT) || match(Constants.TOKEN_REAL) || match(Constants.TOKEN_STRING)
                || match(Constants.LIT_TAME) || match(Constants.LIT_WILD)) {
            return new Ast.Literal(token.kind(), token.text(), token.pos());
        }
        if (at(Constants.TOKEN_IDENT)) return postfix();
        if (match(Constants.KW_INPUT)) {
            // input<T>() принимает только простой тип и не имеет аргументов.
            take(Constants.LT);
            StableType type = type();
            if (type.array()) throw error("input requires a simple type");
            take(Constants.GT);
            take(Constants.LPAREN);
            take(Constants.RPAREN);
            return new Ast.Input(type, token.pos());
        }
        if (match(Constants.LBRACKET)) {
            // Массивный литерал содержит хотя бы один элемент.
            List<Ast.Expr> values = new ArrayList<>();
            values.add(expression());
            while (match(Constants.COMMA)) values.add(expression());
            take(Constants.RBRACKET);
            return new Ast.ArrayLiteral(values, token.pos());
        }
        if (match(Constants.LPAREN)) {
            // Скобки возвращают внутреннее выражение, меняя порядок разбора.
            Ast.Expr inner = expression();
            take(Constants.RPAREN);
            return inner;
        }
        throw error("expected expression");
    }

    private Ast.Expr postfix() {
        // После имени допускается один вызов (...) или одна индексация [...].
        Token name = take(Constants.TOKEN_IDENT);
        Ast.Expr result = new Ast.Variable(name.text(), name.pos());
        if (match(Constants.LPAREN)) {
            List<Ast.Expr> args = new ArrayList<>();
            if (!at(Constants.RPAREN)) {
                args.add(expression());
                while (match(Constants.COMMA)) args.add(expression());
            }
            take(Constants.RPAREN);
            result = new Ast.Call(name.text(), args, name.pos());
        } else if (match(Constants.LBRACKET)) {
            Ast.Expr index = expression();
            take(Constants.RBRACKET);
            result = new Ast.Index(result, index, name.pos());
        }
        return result;
    }

    private Token current() {
        return tokens.get(cursor);
    }

    private boolean at(String kind) {
        return current().kind().equals(kind);
    }

    private boolean match(String kind) {
        // Необязательный токен: продвигаемся только при совпадении.
        if (at(kind)) {
            cursor++;
            return true;
        }
        return false;
    }

    private Token take(String kind) {
        // Обязательный токен: несовпадение превращаем в Syntax diagnostic.
        if (!at(kind)) throw error("expected '" + kind + "', found '" + current().kind() + Constants.APOSTROPHE);
        return tokens.get(cursor++);
    }

    private Diagnostic error(String message) {
        return new Diagnostic(Constants.ERROR_SYNTAX, current().pos(), message);
    }
}
