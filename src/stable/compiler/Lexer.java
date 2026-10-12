package stable.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class Lexer {
    private static final Set<String> KEYWORDS = Set.of(
            "zherebtsov", "run", "function", "return", "if", "else", "while", "for", "from", "to",
            "hoof", "stride", "neigh", "tamed", "herd", "input", "print", "tame", "wild"
    );
    private static final String STRING_PUNCTUATION = "_.,!?:;+-*/=()[]";
    private final String source;
    private int offset;
    private int line = 1;
    private int column = 1;

    Lexer(String source) {
        this.source = source;
    }

    List<Token> scan() {
        List<Token> tokens = new ArrayList<>();
        while (!end()) {
            char ch = peek();
            if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n') {
                advance();
                continue;
            }
            if (ch == '/' && peekNext() == '/') {
                while (!end() && peek() != '\n' && peek() != '\r') advance();
                continue;
            }
            Pos pos = position();
            if (letter(ch)) {
                StringBuilder word = new StringBuilder();
                while (!end() && (letter(peek()) || digit(peek()) || peek() == '_')) word.append(advance());
                String text = word.toString();
                tokens.add(new Token(KEYWORDS.contains(text) ? text : "IDENT", text, pos));
                continue;
            }
            if (digit(ch)) {
                StringBuilder number = new StringBuilder();
                while (!end() && digit(peek())) number.append(advance());
                boolean real = peek() == '.' && digit(peekNext());
                if (real) {
                    number.append(advance());
                    while (!end() && digit(peek())) number.append(advance());
                }
                tokens.add(new Token(real ? "REAL" : "INT", number.toString(), pos));
                continue;
            }
            if (ch == '"') {
                advance();
                StringBuilder value = new StringBuilder();
                while (!end() && peek() != '"') {
                    char next = peek();
                    if (!letter(next) && !digit(next) && next != ' ' && STRING_PUNCTUATION.indexOf(next) < 0) {
                        throw new Diagnostic("Lexical", position(), "invalid character in string literal");
                    }
                    value.append(advance());
                }
                if (end()) throw new Diagnostic("Lexical", pos, "unterminated string literal");
                advance();
                tokens.add(new Token("STRING", value.toString(), pos));
                continue;
            }
            String pair = "" + ch + peekNext();
            if (Set.of("<=", ">=", "==", "!=", "&&", "||").contains(pair)) {
                advance();
                advance();
                tokens.add(new Token(pair, pair, pos));
                continue;
            }
            if ("!;,+-*/=%()[]{}<>".indexOf(ch) >= 0) {
                advance();
                tokens.add(new Token(String.valueOf(ch), String.valueOf(ch), pos));
                continue;
            }
            throw new Diagnostic("Lexical", pos, "invalid character '" + ch + "'");
        }
        tokens.add(new Token("EOF", "", position()));
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
        if (ch == '\n' || (ch == '\r' && peek() != '\n')) {
            line++;
            column = 1;
        } else if (ch != '\r') column++;
        return ch;
    }

    private static boolean letter(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }
}
