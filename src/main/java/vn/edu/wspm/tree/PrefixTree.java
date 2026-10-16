package vn.edu.wspm.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.PatternExtensions;

/** Structural Prefix Tree with one unique node per Pattern identity. */
public final class PrefixTree {

    private final PatternExtensions extensions = new PatternExtensions();
    private final PrefixTreeNode root = new PrefixTreeNode(Pattern.empty(), null, null);
    private final Map<Pattern, PrefixTreeNode> nodesByPattern = new HashMap<>();
    private boolean frozen;

    public PrefixTree() {
        nodesByPattern.put(root.pattern(), root);
    }

    public PrefixTreeNode root() {
        return root;
    }

    public int nodeCount() {
        return nodesByPattern.size();
    }

    public Optional<PrefixTreeNode> find(Pattern pattern) {
        return Optional.ofNullable(nodesByPattern.get(
                Objects.requireNonNull(pattern, "pattern must not be null")));
    }

    /**
     * Inserts one immediate B10 extension. Repeating the same edge is idempotent; attempting to
     * attach an existing Pattern under another parent or edge type fails fast.
     */
    public PrefixTreeNode insert(
            Pattern parentPattern, Pattern childPattern, ExtensionType extensionType) {
        if (frozen) {
            throw new IllegalStateException("frozen Prefix Tree cannot be modified");
        }
        Objects.requireNonNull(parentPattern, "parent pattern must not be null");
        Objects.requireNonNull(childPattern, "child pattern must not be null");
        Objects.requireNonNull(extensionType, "extension type must not be null");

        var parent = nodesByPattern.get(parentPattern);
        if (parent == null) {
            throw new IllegalArgumentException(
                    "parent pattern does not exist in tree: " + parentPattern.serialize());
        }
        if (childPattern.isEmpty()) {
            throw new IllegalArgumentException("the root pattern cannot be inserted as a child");
        }

        var existing = nodesByPattern.get(childPattern);
        if (existing != null) {
            if (existing.parent().orElse(null) == parent
                    && existing.extensionType().orElse(null) == extensionType) {
                return existing;
            }
            throw new IllegalArgumentException(
                    "pattern already exists with a different parent or extension type: "
                            + childPattern.serialize());
        }

        validateImmediateExtension(parentPattern, childPattern, extensionType);
        var child = new PrefixTreeNode(childPattern, parent, extensionType);
        parent.addChild(child);
        nodesByPattern.put(childPattern, child);
        return child;
    }

    /** Makes this completed tree read-only; repeated calls are idempotent. */
    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    /** Returns a deterministic pre-order snapshot of existing patterns, including the root. */
    public List<Pattern> preOrderPatterns() {
        var orderedPatterns = new ArrayList<Pattern>(nodesByPattern.size());
        var stack = new ArrayDeque<PrefixTreeNode>();
        stack.push(root);
        while (!stack.isEmpty()) {
            var node = stack.pop();
            orderedPatterns.add(node.pattern());
            var children = node.children();
            for (int index = children.size() - 1; index >= 0; index--) {
                stack.push(children.get(index));
            }
        }
        return List.copyOf(orderedPatterns);
    }

    private void validateImmediateExtension(
            Pattern parent, Pattern child, ExtensionType extensionType) {
        if (child.length() != parent.length() + 1) {
            throw new IllegalArgumentException(
                    "child must add exactly one item to its parent");
        }

        Pattern reconstructed = switch (extensionType) {
            case I_EXTENSION -> reconstructIExtension(parent, child);
            case S_EXTENSION -> reconstructSExtension(parent, child);
        };
        if (!reconstructed.equals(child)) {
            throw new IllegalArgumentException(
                    "child is not the declared immediate extension of parent");
        }
    }

    private Pattern reconstructIExtension(Pattern parent, Pattern child) {
        if (parent.isEmpty() || child.size() != parent.size()) {
            throw new IllegalArgumentException(
                    "I-Extension requires equal non-zero itemset counts");
        }
        int lastIndex = parent.size() - 1;
        if (!parent.itemsets().subList(0, lastIndex)
                .equals(child.itemsets().subList(0, lastIndex))) {
            throw new IllegalArgumentException(
                    "I-Extension must preserve all itemsets before the final itemset");
        }

        var parentLast = parent.itemsets().getLast();
        var childLast = child.itemsets().getLast();
        if (childLast.size() != parentLast.size() + 1
                || !childLast.items().containsAll(parentLast.items())) {
            throw new IllegalArgumentException(
                    "I-Extension must add exactly one item to the final itemset");
        }
        Item addedItem = childLast.items().stream()
                .filter(item -> !parentLast.contains(item))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "I-Extension child has no added item"));
        return extensions.iExtend(parent, addedItem);
    }

    private Pattern reconstructSExtension(Pattern parent, Pattern child) {
        if (child.size() != parent.size() + 1
                || !child.itemsets().subList(0, parent.size()).equals(parent.itemsets())) {
            throw new IllegalArgumentException(
                    "S-Extension must append exactly one final itemset");
        }
        var appendedItemset = child.itemsets().getLast();
        if (appendedItemset.size() != 1) {
            throw new IllegalArgumentException(
                    "S-Extension must append a singleton itemset");
        }
        return extensions.sExtend(parent, appendedItemset.items().getFirst());
    }
}
