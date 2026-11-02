package vn.edu.wspm.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.prism.PatternEncoding;
import vn.edu.wspm.prism.PositionBlock;
import vn.edu.wspm.prism.SequenceBlock;
import vn.edu.wspm.tree.ExtensionType;

class CandidateEngineTest {

    private static final Item ONE = new Item(1);
    private static final Item TWO = new Item(2);
    private static final Item THREE = new Item(3);

    private final CandidateEngine engine = new CandidateEngine();
    private final MinimumSupportResolver resolver = new MinimumSupportResolver();

    @Test
    void generatesValidIExtensionAndRepeatedItemSExtension() {
        Pattern parent = Pattern.singleton(ONE);
        PatternEncoding parentEncoding = encoding(PositionBlock.of(1, 3, 1));
        var f1 = List.of(
                new WeightedOnePattern(1, 1),
                new WeightedOnePattern(2, 1));
        var singletonEncodings = Map.of(
                1, encoding(PositionBlock.of(1, 3, 1, 2)),
                2, encoding(PositionBlock.of(1, 3, 1, 3)));

        var results = engine.generateOneStep(
                parent,
                parentEncoding,
                f1,
                singletonEncodings,
                0.5,
                resolver.resolve(MinimumSupport.absolute(0.1), 1));

        assertEquals(List.of("<(1 2)>", "<(1)(1)>", "<(1)(2)>"),
                results.stream().map(result -> result.pattern().serialize()).toList());
        assertEquals(ExtensionType.I_EXTENSION, results.get(0).extensionType());
        assertEquals(ExtensionType.S_EXTENSION, results.get(1).extensionType());
        assertEquals("<(1)(1)>", results.get(1).pattern().serialize());
    }

    @Test
    void omitsCanonicalInvalidIExtensionButStillGeneratesS() {
        Pattern parent = Pattern.singleton(TWO);
        PatternEncoding parentEncoding = encoding(PositionBlock.of(1, 2, 1));
        var results = engine.generateOneStep(
                parent,
                parentEncoding,
                List.of(new WeightedOnePattern(1, 1)),
                Map.of(1, encoding(PositionBlock.of(1, 2, 2))),
                0.5,
                resolver.resolve(MinimumSupport.absolute(0.1), 1));

        assertEquals(1, results.size());
        assertEquals("<(2)(1)>", results.getFirst().pattern().serialize());
        assertEquals(ExtensionType.S_EXTENSION, results.getFirst().extensionType());
    }

    @Test
    void returnsDeterministicOrderWithoutDuplicatePatternIdentity() {
        Pattern parent = Pattern.singleton(ONE);
        PatternEncoding parentEncoding = encoding(PositionBlock.of(1, 3, 1));
        var f1 = List.of(
                new WeightedOnePattern(3, 1),
                new WeightedOnePattern(1, 1),
                new WeightedOnePattern(2, 1));
        var singletons = Map.of(
                3, encoding(PositionBlock.of(1, 3, 3)),
                1, encoding(PositionBlock.of(1, 3, 1)),
                2, encoding(PositionBlock.of(1, 3, 2)));
        var threshold = resolver.resolve(MinimumSupport.absolute(0.1), 1);

        var first = engine.generateOneStep(
                parent, parentEncoding, f1, singletons, 0.5, threshold);
        var second = engine.generateOneStep(
                parent, parentEncoding, f1.reversed(), singletons, 0.5, threshold);

        assertEquals(List.of(
                "<(1 2)>", "<(1 3)>", "<(1)(1)>", "<(1)(2)>", "<(1)(3)>"),
                first.stream().map(result -> result.pattern().serialize()).toList());
        assertEquals(first, second);
        assertEquals(first.size(), first.stream().map(CandidateResult::pattern).distinct().count());
        assertThrows(UnsupportedOperationException.class, () -> first.clear());
    }

    @Test
    void weightedAcceptancePassFailAndExactEqualityUseResolvedPolicy() {
        Pattern parent = Pattern.singleton(ONE);
        PatternEncoding parentEncoding = encoding(
                PositionBlock.of(1, 2, 1),
                PositionBlock.of(2, 2, 1),
                PositionBlock.of(3, 2, 2));
        PatternEncoding itemTwoEncoding = encoding(
                PositionBlock.of(1, 2, 2),
                PositionBlock.of(2, 2, 2),
                PositionBlock.of(3, 2, 1));
        var f1 = List.of(new WeightedOnePattern(2, 3));
        var singletons = Map.of(2, itemTwoEncoding);

        CandidateResult pass = sResult(engine.generateOneStep(
                parent, parentEncoding, f1, singletons, 0.5,
                resolver.resolve(MinimumSupport.absolute(0.75), 3)));
        CandidateResult equality = sResult(engine.generateOneStep(
                parent, parentEncoding, f1, singletons, 0.5,
                resolver.resolve(MinimumSupport.absolute(1.0), 3)));
        CandidateResult fail = sResult(engine.generateOneStep(
                parent, parentEncoding, f1, singletons, 0.5,
                resolver.resolve(MinimumSupport.absolute(1.1), 3)));

        assertEquals(2, equality.support());
        assertEquals(1.0, equality.weightedSupport());
        assertTrue(pass.accepted());
        assertTrue(equality.accepted(), "inclusive >= must accept exact equality");
        assertFalse(fail.accepted());
    }

    @Test
    void structurallyValidZeroSupportCandidateIsReturnedButRejectedAtPositiveThreshold() {
        Pattern parent = Pattern.singleton(ONE);
        PatternEncoding parentEncoding = encoding(PositionBlock.of(1, 2, 1));
        PatternEncoding itemTwoEncoding = encoding(PositionBlock.of(1, 2, 2));

        var results = engine.generateOneStep(
                parent,
                parentEncoding,
                List.of(new WeightedOnePattern(2, 1)),
                Map.of(2, itemTwoEncoding),
                0.5,
                resolver.resolve(MinimumSupport.absolute(0.1), 1));
        CandidateResult iResult = results.stream()
                .filter(result -> result.extensionType() == ExtensionType.I_EXTENSION)
                .findFirst()
                .orElseThrow();

        assertEquals("<(1 2)>", iResult.pattern().serialize());
        assertEquals(0, iResult.support());
        assertTrue(CandidateTestOracle.supportingSids(iResult.encoding()).isEmpty());
        assertFalse(iResult.accepted());
    }

    @Test
    void zeroSupportCandidateIsAcceptedByLiteralInclusiveComparisonAtZeroThreshold() {
        Pattern parent = Pattern.singleton(ONE);
        PatternEncoding parentEncoding = encoding(PositionBlock.of(1, 2, 1));
        PatternEncoding itemTwoEncoding = encoding(PositionBlock.of(1, 2, 2));

        CandidateResult iResult = engine.generateOneStep(
                        parent,
                        parentEncoding,
                        List.of(new WeightedOnePattern(2, 1)),
                        Map.of(2, itemTwoEncoding),
                        0.5,
                        resolver.resolve(MinimumSupport.absolute(0.0), 1))
                .stream()
                .filter(result -> result.extensionType() == ExtensionType.I_EXTENSION)
                .findFirst()
                .orElseThrow();

        assertEquals("<(1 2)>", iResult.pattern().serialize());
        assertEquals(0, iResult.support());
        assertEquals(0.0, iResult.weightedSupport());
        assertTrue(iResult.accepted(), "literal 0 >= 0 comparison accepts the candidate");
    }

    @Test
    void doesNotMutateParentOrInputEncodings() {
        Pattern parent = Pattern.singleton(ONE);
        PatternEncoding parentEncoding = encoding(PositionBlock.of(1, 3, 1));
        PatternEncoding singletonEncoding = encoding(PositionBlock.of(1, 3, 2));
        String parentBefore = parent.serialize();
        PatternEncoding parentEncodingBefore = parentEncoding;
        PatternEncoding singletonBefore = singletonEncoding;

        engine.generateOneStep(
                parent,
                parentEncoding,
                List.of(new WeightedOnePattern(2, 1)),
                Map.of(2, singletonEncoding),
                0.5,
                resolver.resolve(MinimumSupport.absolute(0.1), 1));

        assertEquals(parentBefore, parent.serialize());
        assertEquals(parentEncodingBefore, parentEncoding);
        assertEquals(singletonBefore, singletonEncoding);
    }

    @Test
    void validatesF1AndEncodingConsistency() {
        Pattern parent = Pattern.singleton(ONE);
        PatternEncoding parentEncoding = encoding(PositionBlock.of(1, 2, 1));
        PatternEncoding singleton = encoding(PositionBlock.of(1, 2, 2));
        var threshold = resolver.resolve(MinimumSupport.absolute(0.1), 1);

        assertThrows(IllegalArgumentException.class, () -> engine.generateOneStep(
                parent, parentEncoding,
                List.of(new WeightedOnePattern(2, 1), new WeightedOnePattern(2, 1)),
                Map.of(2, singleton), 0.5, threshold));
        assertThrows(IllegalArgumentException.class, () -> engine.generateOneStep(
                parent, parentEncoding,
                List.of(new WeightedOnePattern(2, 1)),
                Map.of(), 0.5, threshold));
        assertThrows(IllegalArgumentException.class, () -> engine.generateOneStep(
                parent, parentEncoding,
                List.of(new WeightedOnePattern(2, 1)),
                Map.of(2, singleton, 3, singleton), 0.5, threshold));
        assertThrows(IllegalArgumentException.class, () -> engine.generateOneStep(
                parent, parentEncoding,
                List.of(new WeightedOnePattern(2, 2)),
                Map.of(2, singleton), 0.5, threshold));
    }

    @Test
    void validatesParentThresholdAndMeanWeight() {
        PatternEncoding oneSid = encoding(PositionBlock.of(1, 2, 1));

        assertThrows(IllegalArgumentException.class, () -> engine.generateOneStep(
                Pattern.empty(), oneSid, List.of(), Map.of(), 0.5,
                resolver.resolve(MinimumSupport.absolute(0.1), 1)));
        assertThrows(IllegalArgumentException.class, () -> engine.generateOneStep(
                Pattern.singleton(ONE), oneSid, List.of(), Map.of(), Double.NaN,
                resolver.resolve(MinimumSupport.absolute(0.1), 1)));
        assertThrows(IllegalArgumentException.class, () -> engine.generateOneStep(
                Pattern.singleton(ONE), oneSid, List.of(), Map.of(), 0.5,
                resolver.resolve(MinimumSupport.absolute(0.1), 2)));
    }

    private static CandidateResult sResult(List<CandidateResult> results) {
        return results.stream()
                .filter(result -> result.extensionType() == ExtensionType.S_EXTENSION)
                .findFirst()
                .orElseThrow();
    }

    private static PatternEncoding encoding(PositionBlock... positions) {
        int[] supportingSids = java.util.Arrays.stream(positions)
                .filter(position -> !position.isEmpty())
                .mapToInt(PositionBlock::sid)
                .toArray();
        return new PatternEncoding(
                SequenceBlock.of(positions.length, supportingSids), List.of(positions));
    }
}
