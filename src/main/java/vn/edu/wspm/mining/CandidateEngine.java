package vn.edu.wspm.mining;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.PatternExtensions;
import vn.edu.wspm.prism.PatternEncoding;
import vn.edu.wspm.prism.PrimeBlockJoins;
import vn.edu.wspm.tree.ExtensionType;

/** Generates and evaluates exactly one layer of WSPM_PreTree candidate extensions. */
public final class CandidateEngine {

    private final PatternExtensions extensions = new PatternExtensions();

    /**
     * Generates structurally valid candidates without mutating a Prefix Tree or recursing.
     *
     * <p>Deterministic B15 policy: eligible I-Extensions are returned first in ascending numeric
     * F1 item order, followed by all S-Extensions in that same order. This is an API ordering
     * policy, not a claim about the paper's DFS traversal order.</p>
     *
     * @param parent non-empty pattern to extend
     * @param parentEncoding Prime-block encoding of {@code parent}
     * @param f1 accepted one-pattern metadata
     * @param singletonEncodings exact item-ID to singleton Prime-block encoding mapping
     * @param globalMeanWeight global MeanW from B6
     * @param minimumSupport resolved B7 threshold for the same SID universe
     */
    public List<CandidateResult> generateOneStep(
            Pattern parent,
            PatternEncoding parentEncoding,
            Collection<WeightedOnePattern> f1,
            Map<Integer, PatternEncoding> singletonEncodings,
            double globalMeanWeight,
            ResolvedMinimumSupport minimumSupport) {
        Objects.requireNonNull(parent, "parent pattern must not be null");
        Objects.requireNonNull(parentEncoding, "parent encoding must not be null");
        Objects.requireNonNull(f1, "F1 must not be null");
        Objects.requireNonNull(singletonEncodings, "singleton encodings must not be null");
        Objects.requireNonNull(minimumSupport, "resolved minimum support must not be null");
        if (parent.isEmpty()) {
            throw new IllegalArgumentException(
                    "B15 CandidateEngine requires a non-empty parent pattern");
        }
        if (!Double.isFinite(globalMeanWeight) || globalMeanWeight < 0.0) {
            throw new IllegalArgumentException(
                    "global MeanW must be finite and non-negative");
        }
        if (parentEncoding.sequenceBlock().sidCount()
                != minimumSupport.numberOfSequences()) {
            throw new IllegalArgumentException(
                    "parent encoding and minimum support must use the same SID universe");
        }

        var inputs = snapshotAndValidateInputs(
                f1, singletonEncodings, minimumSupport.numberOfSequences());
        var orderedF1 = inputs.f1();
        var orderedEncodings = inputs.singletonEncodings();
        if (orderedF1.isEmpty()) {
            return List.of();
        }

        var results = new ArrayList<CandidateResult>(orderedF1.size() * 2);
        var generatedPatterns = new HashSet<Pattern>();
        int lastItemId = parent.lastItem().orElseThrow().id();

        for (var entry : orderedF1.tailMap(lastItemId, false).entrySet()) {
            Item extensionItem = new Item(entry.getKey());
            Pattern candidate = extensions.iExtend(parent, extensionItem);
            PatternEncoding encoding = PrimeBlockJoins.iJoin(
                    parentEncoding, orderedEncodings.get(entry.getKey()));
            addResult(
                    results,
                    generatedPatterns,
                    candidate,
                    ExtensionType.I_EXTENSION,
                    encoding,
                    globalMeanWeight,
                    minimumSupport);
        }

        for (var entry : orderedF1.entrySet()) {
            Item extensionItem = new Item(entry.getKey());
            Pattern candidate = extensions.sExtend(parent, extensionItem);
            PatternEncoding encoding = PrimeBlockJoins.sJoin(
                    parentEncoding, orderedEncodings.get(entry.getKey()));
            addResult(
                    results,
                    generatedPatterns,
                    candidate,
                    ExtensionType.S_EXTENSION,
                    encoding,
                    globalMeanWeight,
                    minimumSupport);
        }

        return List.copyOf(results);
    }

    private static ValidatedInputs snapshotAndValidateInputs(
            Collection<WeightedOnePattern> f1,
            Map<Integer, PatternEncoding> singletonEncodings,
            int expectedSidCount) {
        var orderedF1 = new TreeMap<Integer, WeightedOnePattern>();
        for (var onePattern : f1) {
            Objects.requireNonNull(onePattern, "F1 must not contain null");
            if (orderedF1.putIfAbsent(onePattern.itemId(), onePattern) != null) {
                throw new IllegalArgumentException(
                        "F1 contains duplicate item id: " + onePattern.itemId());
            }
        }

        var encodingSnapshot = new TreeMap<Integer, PatternEncoding>();
        for (var entry : singletonEncodings.entrySet()) {
            Integer itemId = Objects.requireNonNull(
                    entry.getKey(), "singleton encodings must not contain a null item id");
            if (itemId < 0) {
                throw new IllegalArgumentException(
                        "singleton encoding item id must not be negative: " + itemId);
            }
            encodingSnapshot.put(itemId, Objects.requireNonNull(
                    entry.getValue(), "singleton encodings must not contain null"));
        }
        if (!encodingSnapshot.keySet().equals(orderedF1.keySet())) {
            throw new IllegalArgumentException(
                    "singleton encoding item IDs must exactly match F1 item IDs");
        }

        for (var entry : orderedF1.entrySet()) {
            PatternEncoding encoding = encodingSnapshot.get(entry.getKey());
            if (encoding.sequenceBlock().sidCount() != expectedSidCount) {
                throw new IllegalArgumentException(
                        "singleton encoding for item " + entry.getKey()
                                + " uses a different SID universe");
            }
            if (encoding.sequenceBlock().support() != entry.getValue().support()) {
                throw new IllegalArgumentException(
                        "singleton encoding support disagrees with F1 for item "
                                + entry.getKey());
            }
        }
        return new ValidatedInputs(orderedF1, encodingSnapshot);
    }

    private static void addResult(
            List<CandidateResult> results,
            HashSet<Pattern> generatedPatterns,
            Pattern pattern,
            ExtensionType extensionType,
            PatternEncoding encoding,
            double globalMeanWeight,
            ResolvedMinimumSupport minimumSupport) {
        if (!generatedPatterns.add(pattern)) {
            throw new IllegalStateException(
                    "duplicate candidate pattern generated: " + pattern.serialize());
        }
        int support = encoding.sequenceBlock().support();
        boolean accepted = minimumSupport.meetsThreshold(support, globalMeanWeight);
        results.add(new CandidateResult(
                pattern,
                extensionType,
                encoding,
                globalMeanWeight,
                minimumSupport.effectiveThreshold(),
                accepted));
    }

    private record ValidatedInputs(
            TreeMap<Integer, WeightedOnePattern> f1,
            TreeMap<Integer, PatternEncoding> singletonEncodings) {}
}
