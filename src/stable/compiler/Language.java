package stable.compiler;

import java.util.Set;
import stable.compiler.constants.Constants;

// Координаты токена и диагностики в исходном файле.
record Pos(int line, int column) {
}

// Лексер передаёт парсеру вид токена, исходный текст и начальную позицию.
record Token(String kind, String text, Pos pos) {
}

// Тип Stable: один из простых типов либо массив herd<...>.
record StableType(String base, boolean array) {
    static final StableType HOOF = new StableType(Constants.TYPE_HOOF, false);
    static final StableType STRIDE = new StableType(Constants.TYPE_STRIDE, false);
    static final StableType NEIGH = new StableType(Constants.TYPE_NEIGH, false);
    static final StableType TAMED = new StableType(Constants.TYPE_TAMED, false);
    static final Set<String> SIMPLE = Set.of(Constants.TYPE_HOOF, Constants.TYPE_STRIDE, Constants.TYPE_NEIGH, Constants.TYPE_TAMED);

    // Для массива получаем тип элемента; для простого типа — тот же тип.
    StableType element() {
        return new StableType(base, false);
    }

    // Создаём массив из простого типа.
    StableType asArray() {
        return new StableType(base, true);
    }

    // Арифметика разрешена только для простых числовых типов.
    boolean numeric() {
        return !array && (base.equals(Constants.TYPE_HOOF) || base.equals(Constants.TYPE_STRIDE));
    }

    // Формат нужен в сообщениях об ошибках типов.
    @Override
    public String toString() {
        return array ? Constants.KW_HERD + Constants.LT + base + Constants.GT : base;
    }
}
