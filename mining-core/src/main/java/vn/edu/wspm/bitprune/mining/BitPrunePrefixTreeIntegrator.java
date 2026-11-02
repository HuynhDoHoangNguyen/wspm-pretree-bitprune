package vn.edu.wspm.bitprune.mining;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import vn.edu.wspm.mining.WeightedOnePattern;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.PatternExtensions;
import vn.edu.wspm.tree.ExtensionType;
import vn.edu.wspm.tree.PrefixTree;
import vn.edu.wspm.tree.PrefixTreeNode;

/**
 * Purely structural integration component for WSPM_BitPrune PrefixTree
 * management.
 *
 * <p>
 * Handles:
 * <ol>
 * <li>F1 root initialization: inserts singleton 1-patterns as direct
 * S-extension children of the PrefixTree root.</li>
 * <li>Accepted-child insertion: inserts a structural candidate into the tree
 * only when explicitly requested by caller.</li>
 * </ol>
 * </p>
 *
 * <p>
 * This component is stateless and intentionally unaware of support values,
 * minimum support thresholds,
 * bitmaps, CMAP, exact verifications, pruning decisions, or DFS traversal
 * state. Acceptance decisions
 * belong strictly to the mining coordinator (C10).
 * </p>
 */
public final class BitPrunePrefixTreeIntegrator {

    private final PatternExtensions extensions = new PatternExtensions();

    /**
     * Initializes the PrefixTree by inserting all frequent 1-patterns F1 as
     * singleton S-extension
     * children directly beneath the structural empty root.
     *
     * @param tree non-null, mutable PrefixTree to initialize
     * @param f1   non-null list of weighted 1-patterns without nulls or duplicate
     *             item IDs
     * @return immutable list of inserted root PrefixTreeNodes sorted in ascending
     *         numeric item ID order
     * @throws NullPointerException     if {@code tree}, {@code f1}, or any element
     *                                  of {@code f1} is null
     * @throws IllegalStateException    if {@code tree} is frozen
     * @throws IllegalArgumentException if {@code f1} contains duplicate item IDs
     */
    public List<PrefixTreeNode> initializeF1Roots(
            PrefixTree tree,
            List<WeightedOnePattern> f1) {
        Objects.requireNonNull(tree, "tree must not be null");
        Objects.requireNonNull(f1, "f1 must not be null");
        if (tree.isFrozen()) {
            throw new IllegalStateException("frozen Prefix Tree cannot be modified");
        }

        if (f1.isEmpty()) {
            return List.of();
        }

        var seenItemIds = new HashSet<Integer>(f1.size());
        for (var onePattern : f1) {
            Objects.requireNonNull(onePattern, "f1 element must not be null");
            if (!seenItemIds.add(onePattern.itemId())) {
                throw new IllegalArgumentException(
                        "duplicate item id in F1: " + onePattern.itemId());
            }
        }

        var sortedF1 = new ArrayList<>(f1);
        sortedF1.sort(Comparator.comparingInt(WeightedOnePattern::itemId));

        var insertedNodes = new ArrayList<PrefixTreeNode>(sortedF1.size());
        for (var onePattern : sortedF1) {
            var singleton = Pattern.singleton(new Item(onePattern.itemId()));
            var node = tree.insert(Pattern.empty(), singleton, ExtensionType.S_EXTENSION);
            insertedNodes.add(node);
        }

        return List.copyOf(insertedNodes);
    }

    /**
     * Inserts an accepted structural candidate pattern as a child of its parent in
     * the PrefixTree.
     *
     * <p>
     * Validates structural consistency between the candidate's extension item,
     * extension type,
     * and pattern before delegating insertion to the tree. If structural
     * consistency is violated,
     * the tree is left completely unmutated.
     * </p>
     *
     * @param tree              non-null, mutable PrefixTree to receive the child
     * @param parent            non-null parent pattern that must exist in the tree
     * @param acceptedCandidate non-null structural candidate result deemed accepted
     *                          by caller
     * @return the inserted or existing idempotent PrefixTreeNode
     * @throws NullPointerException     if {@code tree}, {@code parent}, or
     *                                  {@code acceptedCandidate} is null
     * @throws IllegalStateException    if {@code tree} is frozen
     * @throws IllegalArgumentException if {@code parent} does not exist in
     *                                  {@code tree},
     *                                  or if {@code acceptedCandidate} has
     *                                  inconsistent extension item/type/pattern,
     *                                  or if a conflicting node already exists in
     *                                  {@code tree}
     */
    public PrefixTreeNode insertAcceptedChild(
            PrefixTree tree,
            Pattern parent,
            BitPruneCandidateResult acceptedCandidate) {
        Objects.requireNonNull(tree, "tree must not be null");
        Objects.requireNonNull(parent, "parent pattern must not be null");
        Objects.requireNonNull(acceptedCandidate, "accepted candidate must not be null");

        if (tree.isFrozen()) {
            throw new IllegalStateException("frozen Prefix Tree cannot be modified");
        }

        if (tree.find(parent).isEmpty()) {
            throw new IllegalArgumentException(
                    "parent pattern does not exist in tree: " + parent.serialize());
        }

        Pattern reconstructed;
        try {
            if (acceptedCandidate.extensionType() == ExtensionType.I_EXTENSION) {
                reconstructed = extensions.iExtend(parent, acceptedCandidate.extensionItem());
            } else if (acceptedCandidate.extensionType() == ExtensionType.S_EXTENSION) {
                reconstructed = extensions.sExtend(parent, acceptedCandidate.extensionItem());
            } else {
                throw new IllegalArgumentException(
                        "unsupported extension type: " + acceptedCandidate.extensionType());
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "candidate extension is structurally invalid under parent: " + e.getMessage(), e);
        }

        if (!reconstructed.equals(acceptedCandidate.pattern())) {
            throw new IllegalArgumentException(
                    "mismatched candidate pattern: expected " + reconstructed.serialize()
                            + " but candidate was " + acceptedCandidate.pattern().serialize());
        }

        return tree.insert(parent, acceptedCandidate.pattern(), acceptedCandidate.extensionType());
    }
}
