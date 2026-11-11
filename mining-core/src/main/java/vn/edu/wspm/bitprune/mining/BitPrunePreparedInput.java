package vn.edu.wspm.bitprune.mining;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import vn.edu.wspm.bitprune.bitmap.SidBitmap;
import vn.edu.wspm.bitprune.cmap.CmapI;
import vn.edu.wspm.bitprune.cmap.CmapS;
import vn.edu.wspm.bitprune.index.VerticalOccurrenceIndex;
import vn.edu.wspm.bitprune.verification.VerificationResult;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.mining.WeightedOnePattern;

/**
 * Immutable package-private carrier of preprocessed structures for WSPM_BitPrune (C13.2).
 *
 * <p>Encapsulates all necessary structures computed during the Preprocessing phase (Scope 2)
 * before starting DFS Mining (Scope 3):
 * <ul>
 * <li>Resolved minimum support configuration;</li>
 * <li>Global MeanW constant;</li>
 * <li>Frequent weighted 1-patterns (F1) sorted in strictly ascending item ID order;</li>
 * <li>Singleton candidate bitmaps keyed by item ID;</li>
 * <li>Singleton verification results keyed by item ID;</li>
 * <li>CMAP-I and CMAP-S co-occurrence structures;</li>
 * <li>Vertical occurrence index.</li>
 * </ul>
 * </p>
 *
 * <p>This carrier is immutable, unmodifiable, and does not hold any reference to
 * a mutable PrefixTree or execution measurement sampler.</p>
 */
record BitPrunePreparedInput(
        ResolvedMinimumSupport resolvedMinimumSupport,
        double globalMeanWeight,
        List<WeightedOnePattern> sortedF1,
        Map<Integer, SidBitmap> singletonBitmaps,
        Map<Integer, VerificationResult> singletonVerifications,
        CmapI cmapI,
        CmapS cmapS,
        VerticalOccurrenceIndex occurrenceIndex) {

    BitPrunePreparedInput {
        Objects.requireNonNull(resolvedMinimumSupport, "resolvedMinimumSupport must not be null");
        Objects.requireNonNull(sortedF1, "sortedF1 must not be null");
        Objects.requireNonNull(singletonBitmaps, "singletonBitmaps must not be null");
        Objects.requireNonNull(singletonVerifications, "singletonVerifications must not be null");
        Objects.requireNonNull(cmapI, "cmapI must not be null");
        Objects.requireNonNull(cmapS, "cmapS must not be null");
        Objects.requireNonNull(occurrenceIndex, "occurrenceIndex must not be null");

        if (!Double.isFinite(globalMeanWeight) || globalMeanWeight < 0.0) {
            throw new IllegalArgumentException(
                    "globalMeanWeight must be finite and non-negative; got: " + globalMeanWeight);
        }

        sortedF1 = List.copyOf(sortedF1);
        singletonBitmaps = Collections.unmodifiableMap(new LinkedHashMap<>(singletonBitmaps));
        singletonVerifications = Collections.unmodifiableMap(new LinkedHashMap<>(singletonVerifications));

        Set<Integer> f1ItemIds = new HashSet<>(sortedF1.size());
        for (int i = 0; i < sortedF1.size(); i++) {
            WeightedOnePattern p = Objects.requireNonNull(sortedF1.get(i), "sortedF1 element must not be null");
            if (i > 0 && p.itemId() <= sortedF1.get(i - 1).itemId()) {
                throw new IllegalArgumentException("sortedF1 must be sorted in strictly ascending item ID order");
            }
            f1ItemIds.add(p.itemId());
        }

        if (!singletonBitmaps.keySet().equals(f1ItemIds)) {
            throw new IllegalArgumentException("singletonBitmaps keys must equal F1 item IDs");
        }
        if (!singletonVerifications.keySet().equals(f1ItemIds)) {
            throw new IllegalArgumentException("singletonVerifications keys must equal F1 item IDs");
        }

        for (var entry : singletonBitmaps.entrySet()) {
            Objects.requireNonNull(entry.getValue(),
                    "singleton bitmap for item " + entry.getKey() + " must not be null");
        }
        for (var entry : singletonVerifications.entrySet()) {
            Objects.requireNonNull(entry.getValue(),
                    "singleton verification result for item " + entry.getKey() + " must not be null");
        }
    }
}

