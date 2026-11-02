package vn.edu.wspm.bitprune.mining;

import java.util.Objects;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.tree.ExtensionType;

/**
 * Immutable structural candidate generated from a Prefix Tree parent pattern.
 *
 * <p>
 * Contains only the candidate pattern identity, extension type, and extension
 * item.
 * Support, bitmap, endpoints, parent reference, acceptance flags, and counters
 * are intentionally omitted at this generation layer.
 * </p>
 */
public record BitPruneCandidateResult(
        Pattern pattern,
        ExtensionType extensionType,
        Item extensionItem) {

    public BitPruneCandidateResult {
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(extensionType, "extension type must not be null");
        Objects.requireNonNull(extensionItem, "extension item must not be null");
    }
}
