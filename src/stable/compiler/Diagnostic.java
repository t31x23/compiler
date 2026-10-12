package stable.compiler;

public final class Diagnostic extends RuntimeException {
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
        return category + " error at " + line() + ":" + column() + ": " + getMessage();
    }
}
