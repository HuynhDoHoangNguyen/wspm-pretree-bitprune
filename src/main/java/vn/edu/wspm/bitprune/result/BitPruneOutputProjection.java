package vn.edu.wspm.bitprune.result;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import vn.edu.wspm.model.Pattern;

/**
 * Immutable deterministic projection of a {@link BitPruneMiningResult} into stable output representations (C14.1).
 *
 * <p>
 * Projects accepted sequential patterns in their deterministic {@link vn.edu.wspm.tree.PrefixTree}
 * pre-order into:
 * <ul>
 *   <li>Immutable list of {@link BitPruneOutputEntry};</li>
 *   <li>Immutable list of single-line strings (LF-free);</li>
 *   <li>Immutable complete content string joined by LF ('\n') with exactly one trailing LF when non-empty,
 *       or empty string ("") when empty.</li>
 * </ul>
 * </p>
 *
 * <p>This projection does not retain any mining intermediate state, bitmaps, indexes, or samplers.</p>
 */
public final class BitPruneOutputProjection {

    private final List<BitPruneOutputEntry> entries;
    private final List<String> lines;
    private final String content;

    private BitPruneOutputProjection(List<BitPruneOutputEntry> entries, List<String> lines, String content) {
        this.entries = entries;
        this.lines = lines;
        this.content = content;
    }

    /**
     * Creates an immutable deterministic projection from a completed mining result.
     *
     * @param result non-null completed BitPrune mining result
     * @return immutable output projection
     * @throws NullPointerException if result is null
     * @throws IllegalArgumentException if duplicate patterns are detected in accepted patterns
     */
    public static BitPruneOutputProjection from(BitPruneMiningResult result) {
        Objects.requireNonNull(result, "result must not be null");

        List<Pattern> acceptedPatterns = result.acceptedPatterns();
        if (acceptedPatterns.isEmpty()) {
            return new BitPruneOutputProjection(List.of(), List.of(), "");
        }

        List<BitPruneOutputEntry> entryList = new ArrayList<>(acceptedPatterns.size());
        List<String> lineList = new ArrayList<>(acceptedPatterns.size());
        StringBuilder contentBuilder = new StringBuilder();
        Set<Pattern> seenPatterns = new HashSet<>(acceptedPatterns.size());

        for (Pattern pattern : acceptedPatterns) {
            Objects.requireNonNull(pattern, "pattern must not be null");
            if (!seenPatterns.add(pattern)) {
                throw new IllegalArgumentException(
                        "duplicate pattern encountered in accepted patterns: " + pattern.serialize());
            }

            int support = result.support(pattern);
            BitPruneOutputEntry entry = new BitPruneOutputEntry(pattern, support);
            String line = entry.formatLine();

            entryList.add(entry);
            lineList.add(line);
            contentBuilder.append(line).append('\n');
        }

        return new BitPruneOutputProjection(
                List.copyOf(entryList),
                List.copyOf(lineList),
                contentBuilder.toString());
    }

    /**
     * Returns an immutable list of output entries in deterministic PrefixTree pre-order.
     *
     * @return immutable list of entries
     */
    public List<BitPruneOutputEntry> entries() {
        return entries;
    }

    /**
     * Returns an immutable list of formatted lines in deterministic PrefixTree pre-order without line separators.
     *
     * @return immutable list of formatted lines
     */
    public List<String> lines() {
        return lines;
    }

    /**
     * Returns the full content string.
     *
     * <p>Empty projection produces {@code ""}. Non-empty projection produces all lines joined by
     * LF ('\n') with exactly one trailing LF.</p>
     *
     * @return LF-joined content string
     */
    public String content() {
        return content;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BitPruneOutputProjection other)) {
            return false;
        }
        return entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "BitPruneOutputProjection[entries=" + entries.size() + "]";
    }
}
