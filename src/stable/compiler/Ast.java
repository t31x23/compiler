package stable.compiler;

import java.util.List;

// AST — дерево программы после разбора синтаксиса. Узлы хранят структуру,
// а типы и Java-имена к ним позже привязывает Semantic.
final class Ast {
    private Ast() {
    }

    // Корень: имя программы, объявления функций и обязательный блок run.
    record Program(String name, List<Function> functions, Block run) {
    }

    // pos указывает на начало конструкции для сообщения об ошибке.
    record Function(StableType type, String name, List<Param> params, Block body, Pos pos) {
    }

    // Параметр — объявление локального имени с типом.
    record Param(StableType type, String name, Pos pos) {
    }

    // Все виды операторов ограничены перечисленными реализациями.
    sealed interface Stmt permits Block, VarDecl, Assign, Print, If, While, For, Return, CallStmt {
        Pos pos();
    }

    // Блок — последовательность операторов и отдельная область видимости.
    record Block(List<Stmt> statements, Pos pos) implements Stmt {
    }

    // Объявление всегда содержит начальное значение.
    record VarDecl(StableType type, String name, Expr value, Pos pos) implements Stmt {
    }

    // Цель присваивания — переменная или элемент массива.
    record Assign(Expr target, Expr value, Pos pos) implements Stmt {
    }

    // Печать значения выражения.
    record Print(Expr value, Pos pos) implements Stmt {
    }

    // Ветка else необязательна и потому может быть null.
    record If(Expr condition, Block thenBlock, Block elseBlock, Pos pos) implements Stmt {
    }

    // Цикл проверяет условие перед каждой итерацией.
    record While(Expr condition, Block body, Pos pos) implements Stmt {
    }

    // Счётчик for существует внутри тела и пробегает границы включительно.
    record For(String name, Expr start, Expr end, Block body, Pos pos) implements Stmt {
    }

    // Возврат результата функции.
    record Return(Expr value, Pos pos) implements Stmt {
    }

    // Вызов функции, результат которого игнорируется.
    record CallStmt(Call call, Pos pos) implements Stmt {
    }

    // Выражения тоже образуют закрытый набор видов узлов.
    sealed interface Expr permits Literal, Variable, Call, Index, ArrayLiteral, Unary, Binary, Input {
        Pos pos();
    }

    // kind различает INT, REAL, STRING, tame и wild; text хранит исходное значение.
    record Literal(String kind, String text, Pos pos) implements Expr {
    }

    // Ссылка на имя; конкретное объявление будет найдено при анализе.
    record Variable(String name, Pos pos) implements Expr {
    }

    // Вызов с упорядоченным списком аргументов.
    record Call(String name, List<Expr> args, Pos pos) implements Expr {
    }

    // Доступ к одному элементу массива.
    record Index(Expr array, Expr index, Pos pos) implements Expr {
    }

    // Литерал массива; его тип выводится из ожидаемого контекста.
    record ArrayLiteral(List<Expr> elements, Pos pos) implements Expr {
    }

    // Унарные - и !.
    record Unary(String op, Expr value, Pos pos) implements Expr {
    }

    // Бинарный оператор с левым и правым операндами.
    record Binary(String op, Expr left, Expr right, Pos pos) implements Expr {
    }

    // Чтение одной строки ввода и преобразование к указанному типу.
    record Input(StableType type, Pos pos) implements Expr {
    }
}
