package stable.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import stable.compiler.constants.Constants;

final class Lexer {
    // Эти слова нельзя использовать как имена: они имеют собственный смысл в Stable.
    private static final Set<String> KEYWORDS = Set.of(
            Constants.KW_PROGRAM, Constants.KW_RUN, Constants.KW_FUNCTION, Constants.KW_RETURN,
            Constants.KW_IF, Constants.KW_ELSE, Constants.KW_WHILE, Constants.KW_FOR,
            Constants.KW_FROM, Constants.KW_TO, Constants.TYPE_HOOF, Constants.TYPE_STRIDE,
            Constants.TYPE_NEIGH, Constants.TYPE_TAMED, Constants.KW_HERD, Constants.KW_INPUT,
            Constants.KW_PRINT, Constants.LIT_TAME, Constants.LIT_WILD
    );
    private static final String STRING_PUNCTUATION = "_.,!?:;+-*/=()[]";
    // offset — позиция в строке Java, line/column — координаты для сообщений об ошибках.
    private final String source;
    private int offset;
    private int line = 1;
    private int column = 1;

    Lexer(String source) {
        this.source = source;
    }

    List<Token> scan() {
        List<Token> tokens = new ArrayList<>();
        // На каждой итерации распознаём один токен или пропускаем незначимый текст.
        while (!end()) {
            char ch = peek();
            // Пробелы и переводы строк разделяют токены, но сами токенами не становятся.
            if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n') {
                advance();
                continue;
            }
            // Комментарий // пропускается до конца строки.
            if (ch == '/' && peekNext() == '/') {
                while (!end() && peek() != '\n' && peek() != '\r') advance();
                continue;
            }
            Pos pos = position();
            // Имя начинается с буквы; после неё допускаются цифры и подчёркивания.
            // Зарезервированные слова получают собственный kind, остальные — IDENT.
            if (letter(ch)) {
                StringBuilder word = new StringBuilder();
                while (!end() && (letter(peek()) || digit(peek()) || peek() == '_')) word.append(advance());
                String text = word.toString();
                tokens.add(new Token(KEYWORDS.contains(text) ? text : Constants.TOKEN_IDENT, text, pos));
                continue;
            }
            // Число становится REAL лишь при наличии цифры после точки.
            if (digit(ch)) {
                StringBuilder number = new StringBuilder();
                while (!end() && digit(peek())) number.append(advance());
                boolean real = peek() == '.' && digit(peekNext());
                if (real) {
                    number.append(advance());
                    while (!end() && digit(peek())) number.append(advance());
                }
                tokens.add(new Token(real ? Constants.TOKEN_REAL : Constants.TOKEN_INT, number.toString(), pos));
                continue;
            }
            // В STRING храним содержимое без кавычек и проверяем разрешённый алфавит.
            if (ch == '"') {
                advance();
                StringBuilder value = new StringBuilder();
                while (!end() && peek() != '"') {
                    char next = peek();
                    if (!letter(next) && !digit(next) && next != ' ' && STRING_PUNCTUATION.indexOf(next) < 0) {
                        throw new Diagnostic(Constants.ERROR_LEXICAL, position(), "invalid character in string literal");
                    }
                    value.append(advance());
                }
                if (end()) throw new Diagnostic(Constants.ERROR_LEXICAL, pos, "unterminated string literal");
                advance();
                tokens.add(new Token(Constants.TOKEN_STRING, value.toString(), pos));
                continue;
            }
            // Сначала проверяем двухсимвольные операторы, чтобы <= не разбилось на < и =.
            String pair = Constants.EMPTY + ch + peekNext();
            if (Set.of(Constants.OP_LE, Constants.OP_GE, Constants.OP_EQ, Constants.OP_NE,
                    Constants.OP_AND, Constants.OP_OR).contains(pair)) {
                advance();
                advance();
                tokens.add(new Token(pair, pair, pos));
                continue;
            }
            // Остальные допустимые знаки — одиночные токены.
            if ("!;,+-*/=%()[]{}<>".indexOf(ch) >= 0) {
                advance();
                tokens.add(new Token(String.valueOf(ch), String.valueOf(ch), pos));
                continue;
            }
            // Любой нераспознанный знак — лексическая ошибка на его позиции.
            throw new Diagnostic(Constants.ERROR_LEXICAL, pos, "invalid character '" + ch + Constants.APOSTROPHE);
        }
        // Явный EOF позволяет парсеру проверить, что после программы ничего нет.
        tokens.add(new Token(Constants.TOKEN_EOF, Constants.EMPTY, position()));
        return tokens;
    }

    private Pos position() {
        return new Pos(line, column);
    }

    private boolean end() {
        return offset >= source.length();
    }

    private char peek() {
        return end() ? '\0' : source.charAt(offset);
    }

    private char peekNext() {
        return offset + 1 >= source.length() ? '\0' : source.charAt(offset + 1);
    }

    private char advance() {
        char ch = source.charAt(offset++);
        // CRLF считается одним переводом строки; одиночный CR тоже поддерживается.
        if (ch == '\n' || (ch == '\r' && peek() != '\n')) {
            line++;
            column = 1;
        } else if (ch != '\r') column++;
        return ch;
    }

    // Алфавит имён и цифр в спецификации — латинские ASCII-символы.
    private static boolean letter(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }
}
