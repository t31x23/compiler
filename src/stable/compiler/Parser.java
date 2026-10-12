package stable.compiler;

import java.util.ArrayList;
import java.util.List;

final class Parser {
    private final List<Token> tokens;
    private int cursor;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Ast.Program parse() {
        take("zherebtsov");
        String name = take("IDENT").text();
        take("{");
        List<Ast.Function> functions = new ArrayList<>();
        while (at("function")) functions.add(function());
        take("run");
        Ast.Block run = block();
        take("}");
        take("EOF");
        return new Ast.Program(name, functions, run);
    }

    private Ast.Function function() {
        Pos pos = take("function").pos();
        StableType type = type();
        String name = take("IDENT").text();
        take("(");
        List<Ast.Param> params = new ArrayList<>();
        if (!at(")")) {
            do {
                StableType paramType = type();
                Token paramName = take("IDENT");
                params.add(new Ast.Param(paramType, paramName.text(), paramName.pos()));
            } while (match(","));
        }
        take(")");
        return new Ast.Function(type, name, params, block(), pos);
    }

    private StableType type() {
        boolean array = match("herd");
        if (array) take("<");
        Token token = current();
        if (!StableType.SIMPLE.contains(token.kind())) throw error("expected a simple type");
        cursor++;
        if (array) take(">");
        return new StableType(token.kind(), array);
    }

    private Ast.Block block() {
        Pos pos = take("{").pos();
        List<Ast.Stmt> statements = new ArrayList<>();
        while (!at("}") && !at("EOF")) statements.add(statement());
        take("}");
        return new Ast.Block(statements, pos);
    }

    private Ast.Stmt statement() {
        Token start = current();
        if (StableType.SIMPLE.contains(start.kind()) || at("herd")) {
            StableType type = type();
            String name = take("IDENT").text();
            take("=");
            Ast.Expr value = expression();
            take(";");
            return new Ast.VarDecl(type, name, value, start.pos());
        }
        if (match("print")) {
            take("(");
            Ast.Expr value = expression();
            take(")");
            take(";");
            return new Ast.Print(value, start.pos());
        }
        if (match("if")) {
            take("(");
            Ast.Expr condition = expression();
            take(")");
            Ast.Block thenBlock = block();
            Ast.Block elseBlock = match("else") ? block() : null;
            return new Ast.If(condition, thenBlock, elseBlock, start.pos());
        }
        if (match("while")) {
            take("(");
            Ast.Expr condition = expression();
            take(")");
            return new Ast.While(condition, block(), start.pos());
        }
        if (match("for")) {
            String name = take("IDENT").text();
            take("from");
            Ast.Expr from = expression();
            take("to");
            Ast.Expr to = expression();
            return new Ast.For(name, from, to, block(), start.pos());
        }
        if (match("return")) {
            Ast.Expr value = expression();
            take(";");
            return new Ast.Return(value, start.pos());
        }
        if (at("IDENT")) {
            Ast.Expr target = postfix();
            if (match("=")) {
                if (!(target instanceof Ast.Variable) && !(target instanceof Ast.Index))
                    throw error("expected assignment target");
                Ast.Expr value = expression();
                take(";");
                return new Ast.Assign(target, value, start.pos());
            }
            if (target instanceof Ast.Call call) {
                take(";");
                return new Ast.CallStmt(call, start.pos());
            }
        }
        throw error("expected statement");
    }

    private Ast.Expr expression() {
        return binary(1);
    }

    private Ast.Expr binary(int minimum) {
        Ast.Expr left = unary();
        while (precedence(current().kind()) >= minimum) {
            Token op = current();
            cursor++;
            Ast.Expr right = binary(precedence(op.kind()) + 1);
            left = new Ast.Binary(op.kind(), left, right, op.pos());
        }
        return left;
    }

    private int precedence(String kind) {
        return switch (kind) {
            case "||" -> 1;
            case "&&" -> 2;
            case "==", "!=" -> 3;
            case "<", "<=", ">", ">=" -> 4;
            case "+", "-" -> 5;
            case "*", "/", "%" -> 6;
            default -> 0;
        };
    }

    private Ast.Expr unary() {
        if (at("-")) {
            Token op = current();
            cursor++;
            return new Ast.Unary(op.kind(), primary(), op.pos());
        }
        if (at("!")) {
            Token op = current();
            cursor++;
            if (at("!")) throw error("repeated unary operator requires parentheses");
            return new Ast.Unary(op.kind(), binary(3), op.pos());
        }
        return primary();
    }

    private Ast.Expr primary() {
        Token token = current();
        if (match("INT") || match("REAL") || match("STRING") || match("tame") || match("wild")) {
            return new Ast.Literal(token.kind(), token.text(), token.pos());
        }
        if (at("IDENT")) return postfix();
        if (match("input")) {
            take("<");
            StableType type = type();
            if (type.array()) throw error("input requires a simple type");
            take(">");
            take("(");
            take(")");
            return new Ast.Input(type, token.pos());
        }
        if (match("[")) {
            List<Ast.Expr> values = new ArrayList<>();
            values.add(expression());
            while (match(",")) values.add(expression());
            take("]");
            return new Ast.ArrayLiteral(values, token.pos());
        }
        if (match("(")) {
            Ast.Expr inner = expression();
            take(")");
            return inner;
        }
        throw error("expected expression");
    }

    private Ast.Expr postfix() {
        Token name = take("IDENT");
        Ast.Expr result = new Ast.Variable(name.text(), name.pos());
        if (match("(")) {
            List<Ast.Expr> args = new ArrayList<>();
            if (!at(")")) {
                args.add(expression());
                while (match(",")) args.add(expression());
            }
            take(")");
            result = new Ast.Call(name.text(), args, name.pos());
        } else if (match("[")) {
            Ast.Expr index = expression();
            take("]");
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
        if (at(kind)) {
            cursor++;
            return true;
        }
        return false;
    }

    private Token take(String kind) {
        if (!at(kind)) throw error("expected '" + kind + "', found '" + current().kind() + "'");
        return tokens.get(cursor++);
    }

    private Diagnostic error(String message) {
        return new Diagnostic("Syntax", current().pos(), message);
    }
}
