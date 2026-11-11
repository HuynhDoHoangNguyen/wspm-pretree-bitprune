package vn.edu.wspm.bitprune.result;

import java.util.Objects;
import vn.edu.wspm.model.Pattern;

/**
 * Immutable output entry associating a sequential pattern with its exact absolute integer support (C14.1).
 *
 * @param pattern non-null sequential pattern
 * @param support non-negative absolute integer support
 */
public record BitPruneOutputEntry(Pattern pattern, int support) {

    public BitPruneOutputEntry {
        Objects.requireNonNull(pattern, "pattern must not be null");
        if (support < 0) {
            throw new IllegalArgumentException("support must be non-negative, got: " + support);
        }
    }

    /**
     * Formats this entry as a single output line adhering to Phase B repository conventions.
     *
     * <p>Syntax: {@code <Pattern.serialize()> #SUP: <support>}</p>
     *
     * @return formatted line without line termination
     */
    public String formatLine() {
        return pattern.serialize() + " #SUP: " + support;
    }
}
