package stable.compiler;

import java.util.List;

final class Ast {
    private Ast() {
    }

    record Program(String name, List<Function> functions, Block run) {
    }

    record Function(StableType type, String name, List<Param> params, Block body, Pos pos) {
    }

    record Param(StableType type, String name, Pos pos) {
    }

    sealed interface Stmt permits Block, VarDecl, Assign, Print, If, While, For, Return, CallStmt {
        Pos pos();
    }

    record Block(List<Stmt> statements, Pos pos) implements Stmt {
    }

    record VarDecl(StableType type, String name, Expr value, Pos pos) implements Stmt {
    }

    record Assign(Expr target, Expr value, Pos pos) implements Stmt {
    }

    record Print(Expr value, Pos pos) implements Stmt {
    }

    record If(Expr condition, Block thenBlock, Block elseBlock, Pos pos) implements Stmt {
    }

    record While(Expr condition, Block body, Pos pos) implements Stmt {
    }

    record For(String name, Expr start, Expr end, Block body, Pos pos) implements Stmt {
    }

    record Return(Expr value, Pos pos) implements Stmt {
    }

    record CallStmt(Call call, Pos pos) implements Stmt {
    }

    sealed interface Expr permits Literal, Variable, Call, Index, ArrayLiteral, Unary, Binary, Input {
        Pos pos();
    }

    record Literal(String kind, String text, Pos pos) implements Expr {
    }

    record Variable(String name, Pos pos) implements Expr {
    }

    record Call(String name, List<Expr> args, Pos pos) implements Expr {
    }

    record Index(Expr array, Expr index, Pos pos) implements Expr {
    }

    record ArrayLiteral(List<Expr> elements, Pos pos) implements Expr {
    }

    record Unary(String op, Expr value, Pos pos) implements Expr {
    }

    record Binary(String op, Expr left, Expr right, Pos pos) implements Expr {
    }

    record Input(StableType type, Pos pos) implements Expr {
    }
}
