package vn.edu.wspm.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import vn.edu.wspm.model.Pattern;

/** A structural Prefix Tree node without support, weight, or encoding metadata. */
public final class PrefixTreeNode {

    private static final Comparator<PrefixTreeNode> CHILD_ORDER = Comparator.comparing(
            node -> node.pattern.serialize());

    private final Pattern pattern;
    private final PrefixTreeNode parent;
    private final ExtensionType extensionType;
    private final List<PrefixTreeNode> children = new ArrayList<>();

    PrefixTreeNode(
            Pattern pattern, PrefixTreeNode parent, ExtensionType extensionType) {
        this.pattern = pattern;
        this.parent = parent;
        this.extensionType = extensionType;
    }

    public Pattern pattern() {
        return pattern;
    }

    public Optional<PrefixTreeNode> parent() {
        return Optional.ofNullable(parent);
    }

    /** Empty for the root; present for every non-root edge. */
    public Optional<ExtensionType> extensionType() {
        return Optional.ofNullable(extensionType);
    }

    /** Paper level: total number of items in the node's k-pattern. */
    public int level() {
        return pattern.length();
    }

    /** Returns a protected snapshot in deterministic canonical-serialization order. */
    public List<PrefixTreeNode> children() {
        return List.copyOf(children);
    }

    void addChild(PrefixTreeNode child) {
        children.add(child);
        children.sort(CHILD_ORDER);
    }
}
