package stable.compiler;

public final class Diagnostic extends RuntimeException {
    // Ошибка исходной программы с категорией и точной позицией.
    final String category;
    final Pos pos;

    Diagnostic(String category, Pos pos, String message) {
        super(message);
        this.category = category;
        this.pos = pos;
    }

    public String category() {
        return category;
    }

    public int line() {
        return pos.line();
    }

    public int column() {
        return pos.column();
    }

    @Override
    public String toString() {
        // Единый формат для CLI и тестов: категория, строка, столбец, причина.
        return category + " error at " + line() + ":" + column() + ": " + getMessage();
    }
}
