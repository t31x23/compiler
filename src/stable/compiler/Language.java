package stable.compiler;

import java.util.Set;

record Pos(int line, int column) {
}

record Token(String kind, String text, Pos pos) {
}

record StableType(String base, boolean array) {
    static final StableType HOOF = new StableType("hoof", false);
    static final StableType STRIDE = new StableType("stride", false);
    static final StableType NEIGH = new StableType("neigh", false);
    static final StableType TAMED = new StableType("tamed", false);
    static final Set<String> SIMPLE = Set.of("hoof", "stride", "neigh", "tamed");

    StableType element() {
        return new StableType(base, false);
    }

    StableType asArray() {
        return new StableType(base, true);
    }

    boolean numeric() {
        return !array && (base.equals("hoof") || base.equals("stride"));
    }

    @Override
    public String toString() {
        return array ? "herd<" + base + ">" : base;
    }
}
