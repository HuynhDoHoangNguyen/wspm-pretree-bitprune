package vn.edu.wspm.mining;

/** Minimal B8 representation of an accepted weighted sequential 1-pattern. */
public record WeightedOnePattern(int itemId, int support) {

    public WeightedOnePattern {
        if (itemId < 0) {
            throw new IllegalArgumentException("item id must not be negative: " + itemId);
        }
        if (support <= 0) {
            throw new IllegalArgumentException("1-pattern support must be positive");
        }
    }
}
