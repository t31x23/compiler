package stable.compiler.constants;

/** Общие обозначения языка Stable, токенов и категорий диагностики. */
public final class Constants {
    private Constants() {
    }

    // Ключевые слова и имена типов Stable.
    public static final String KW_PROGRAM = "zherebtsov";
    public static final String KW_RUN = "run";
    public static final String KW_FUNCTION = "function";
    public static final String KW_RETURN = "return";
    public static final String KW_IF = "if";
    public static final String KW_ELSE = "else";
    public static final String KW_WHILE = "while";
    public static final String KW_FOR = "for";
    public static final String KW_FROM = "from";
    public static final String KW_TO = "to";
    public static final String TYPE_HOOF = "hoof";
    public static final String TYPE_STRIDE = "stride";
    public static final String TYPE_NEIGH = "neigh";
    public static final String TYPE_TAMED = "tamed";
    public static final String KW_HERD = "herd";
    public static final String KW_INPUT = "input";
    public static final String KW_PRINT = "print";
    public static final String LIT_TAME = "tame";
    public static final String LIT_WILD = "wild";

    // Виды токенов, которые не совпадают с их текстом в исходнике.
    public static final String TOKEN_IDENT = "IDENT";
    public static final String TOKEN_INT = "INT";
    public static final String TOKEN_REAL = "REAL";
    public static final String TOKEN_STRING = "STRING";
    public static final String TOKEN_EOF = "EOF";

    // Знаки и операторы: их текст одновременно служит видом токена.
    public static final String LPAREN = "(";
    public static final String RPAREN = ")";
    public static final String LBRACE = "{";
    public static final String RBRACE = "}";
    public static final String LBRACKET = "[";
    public static final String RBRACKET = "]";
    public static final String LT = "<";
    public static final String GT = ">";
    public static final String COMMA = ",";
    public static final String SEMICOLON = ";";
    public static final String ASSIGN = "=";
    public static final String OP_PLUS = "+";
    public static final String OP_MINUS = "-";
    public static final String OP_MULTIPLY = "*";
    public static final String OP_DIVIDE = "/";
    public static final String OP_REMAINDER = "%";
    public static final String OP_NOT = "!";
    public static final String OP_LE = "<=";
    public static final String OP_GE = ">=";
    public static final String OP_EQ = "==";
    public static final String OP_NE = "!=";
    public static final String OP_AND = "&&";
    public static final String OP_OR = "||";

    public static final String ERROR_LEXICAL = "Lexical";
    public static final String ERROR_SYNTAX = "Syntax";
    public static final String ERROR_SEMANTIC = "Semantic";
    public static final String ERROR_UNKNOWN_STATEMENT = "unknown statement";
    public static final String ERROR_UNKNOWN_EXPRESSION = "unknown expression";
    public static final String ERROR_UNKNOWN_LITERAL = "unknown literal";
    public static final String ERROR_STRIDE_RANGE = "stride literal out of range";

    // Префикс для уникальных Java-имён локальных переменных.
    public static final String GENERATED_VARIABLE_PREFIX = "v";

    // Повторяемые фрагменты при сборке текста Java и сообщений об ошибках.
    public static final String EMPTY = "";
    public static final String SPACE = " ";
    public static final String APOSTROPHE = "'";
    public static final String DOUBLE_QUOTE = "\"";
    public static final String JAVA_ASSIGN = " = ";
    public static final String JAVA_COMMA = ", ";
    public static final String JAVA_INT_PREFIX = "int ";
    public static final String JAVA_BLOCK_HEADER_END = ") {";
    public static final String JAVA_SEMICOLON_SPACE = "; ";
}
