package vn.edu.wspm.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.PatternExtensions;

class PrefixTreeTest {

    private static final Item ONE = new Item(1);
    private static final Item TWO = new Item(2);
    private static final Item THREE = new Item(3);

    private final PatternExtensions extensions = new PatternExtensions();

    @Test
    void createsExactlyOneLevelZeroRootWithoutParentOrEdgeType() {
        var tree = new PrefixTree();
        var root = tree.root();

        assertEquals(Pattern.empty(), root.pattern());
        assertEquals(0, root.level());
        assertTrue(root.parent().isEmpty());
        assertTrue(root.extensionType().isEmpty());
        assertTrue(root.children().isEmpty());
        assertEquals(1, tree.nodeCount());
        assertSame(root, tree.find(Pattern.empty()).orElseThrow());
    }

    @Test
    void insertsAndDistinguishesImmediateIAndSChildren() {
        var tree = new PrefixTree();
        var one = Pattern.singleton(ONE);
        var oneNode = tree.insert(Pattern.empty(), one, ExtensionType.S_EXTENSION);
        var iChild = extensions.iExtend(one, TWO);
        var sChild = extensions.sExtend(one, TWO);

        var iNode = tree.insert(one, iChild, ExtensionType.I_EXTENSION);
        var sNode = tree.insert(one, sChild, ExtensionType.S_EXTENSION);

        assertEquals(List.of(iChild, sChild),
                oneNode.children().stream().map(PrefixTreeNode::pattern).toList());
        assertSame(oneNode, iNode.parent().orElseThrow());
        assertSame(oneNode, sNode.parent().orElseThrow());
        assertEquals(ExtensionType.I_EXTENSION, iNode.extensionType().orElseThrow());
        assertEquals(ExtensionType.S_EXTENSION, sNode.extensionType().orElseThrow());
        assertEquals(2, iNode.level());
        assertEquals(2, sNode.level());
        assertFalse(iNode.pattern().equals(sNode.pattern()));
    }

    @Test
    void usesPatternLengthRatherThanItemsetCountAsLevel() {
        var tree = new PrefixTree();
        var one = Pattern.singleton(ONE);
        tree.insert(Pattern.empty(), one, ExtensionType.S_EXTENSION);
        var iChild = extensions.iExtend(one, TWO);
        var sChild = extensions.sExtend(one, TWO);

        var iNode = tree.insert(one, iChild, ExtensionType.I_EXTENSION);
        var sNode = tree.insert(one, sChild, ExtensionType.S_EXTENSION);

        assertEquals(1, iChild.size());
        assertEquals(2, sChild.size());
        assertEquals(2, iNode.level());
        assertEquals(2, sNode.level());
    }

    @Test
    void duplicateInsertionIsIdempotentButMultipleParentAttemptFails() {
        var tree = new PrefixTree();
        var one = Pattern.singleton(ONE);
        var two = Pattern.singleton(TWO);
        tree.insert(Pattern.empty(), one, ExtensionType.S_EXTENSION);
        tree.insert(Pattern.empty(), two, ExtensionType.S_EXTENSION);
        var child = extensions.sExtend(one, TWO);

        var first = tree.insert(one, child, ExtensionType.S_EXTENSION);
        var duplicate = tree.insert(one, child, ExtensionType.S_EXTENSION);

        assertSame(first, duplicate);
        assertEquals(4, tree.nodeCount());
        assertThrows(IllegalArgumentException.class,
                () -> tree.insert(two, child, ExtensionType.S_EXTENSION));
    }

    @Test
    void rejectsMissingParentRootChildAndInvalidImmediateExtensions() {
        var tree = new PrefixTree();
        var one = Pattern.singleton(ONE);
        tree.insert(Pattern.empty(), one, ExtensionType.S_EXTENSION);

        assertThrows(IllegalArgumentException.class,
                () -> tree.insert(Pattern.singleton(TWO),
                        Pattern.of(Itemset.of(TWO), Itemset.of(THREE)),
                        ExtensionType.S_EXTENSION));
        assertThrows(IllegalArgumentException.class,
                () -> tree.insert(one, Pattern.empty(), ExtensionType.S_EXTENSION));
        assertThrows(IllegalArgumentException.class,
                () -> tree.insert(one,
                        Pattern.of(Itemset.of(ONE), Itemset.of(TWO), Itemset.of(THREE)),
                        ExtensionType.S_EXTENSION));
        assertThrows(IllegalArgumentException.class,
                () -> tree.insert(one, Pattern.singleton(TWO), ExtensionType.S_EXTENSION));
        assertThrows(IllegalArgumentException.class,
                () -> tree.insert(Pattern.empty(), one, ExtensionType.I_EXTENSION));
        assertThrows(IllegalArgumentException.class,
                () -> tree.insert(one, extensions.iExtend(one, TWO),
                        ExtensionType.S_EXTENSION));
        assertThrows(IllegalArgumentException.class,
                () -> tree.insert(one, extensions.sExtend(one, TWO),
                        ExtensionType.I_EXTENSION));
        assertEquals(2, tree.nodeCount());
    }

    @Test
    void protectsChildrenAndProvidesExistingAndMissingLookup() {
        var tree = new PrefixTree();
        var one = Pattern.singleton(ONE);
        var node = tree.insert(Pattern.empty(), one, ExtensionType.S_EXTENSION);

        assertSame(node, tree.find(one).orElseThrow());
        assertTrue(tree.find(Pattern.singleton(TWO)).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> tree.root().children().clear());
    }

    @Test
    void freezeMakesCompletedTreeReadOnly() {
        var tree = new PrefixTree();
        tree.insert(Pattern.empty(), Pattern.singleton(ONE), ExtensionType.S_EXTENSION);

        tree.freeze();

        assertTrue(tree.isFrozen());
        tree.freeze();
        assertThrows(IllegalStateException.class, () -> tree.insert(
                Pattern.empty(), Pattern.singleton(TWO), ExtensionType.S_EXTENSION));
    }

    @Test
    void providesDeterministicCanonicalChildOrderAndStructuralPreOrder() {
        var tree = buildExampleTreeInNonCanonicalInsertionOrder();

        assertEquals(List.of("<(1)>", "<(2)>", "<(3)>") ,
                tree.root().children().stream()
                        .map(node -> node.pattern().serialize())
                        .toList());
        assertEquals(List.of(
                "<>",
                "<(1)>",
                "<(1 2)>",
                "<(1)(1)>",
                "<(1)(2)>",
                "<(1)(3)>",
                "<(2)>",
                "<(3)>"),
                tree.preOrderPatterns().stream().map(Pattern::serialize).toList());
        assertEquals(8, tree.nodeCount());
        assertThrows(UnsupportedOperationException.class,
                () -> tree.preOrderPatterns().add(Pattern.singleton(new Item(4))));
    }

    private PrefixTree buildExampleTreeInNonCanonicalInsertionOrder() {
        var tree = new PrefixTree();
        var one = Pattern.singleton(ONE);
        var two = Pattern.singleton(TWO);
        var three = Pattern.singleton(THREE);
        tree.insert(Pattern.empty(), three, ExtensionType.S_EXTENSION);
        tree.insert(Pattern.empty(), one, ExtensionType.S_EXTENSION);
        tree.insert(Pattern.empty(), two, ExtensionType.S_EXTENSION);

        tree.insert(one, extensions.sExtend(one, THREE), ExtensionType.S_EXTENSION);
        tree.insert(one, extensions.sExtend(one, ONE), ExtensionType.S_EXTENSION);
        tree.insert(one, extensions.iExtend(one, TWO), ExtensionType.I_EXTENSION);
        tree.insert(one, extensions.sExtend(one, TWO), ExtensionType.S_EXTENSION);
        return tree;
    }
}
