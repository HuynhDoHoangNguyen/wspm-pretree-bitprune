package vn.edu.wspm.bitprune.mining;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import vn.edu.wspm.bitprune.bitmap.SidBitmap;
import vn.edu.wspm.bitprune.bitmap.SidMapping;
import vn.edu.wspm.bitprune.cmap.CmapBuilder;
import vn.edu.wspm.bitprune.cmap.CmapI;
import vn.edu.wspm.bitprune.cmap.CmapS;
import vn.edu.wspm.bitprune.index.PatternEndpoints;
import vn.edu.wspm.bitprune.index.VerticalOccurrenceIndex;
import vn.edu.wspm.bitprune.measurement.BitPruneAlgorithmMetrics;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.bitprune.verification.VerificationResult;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.measurement.ExecutionMemorySampler;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;
import vn.edu.wspm.mining.F1Builder;
import vn.edu.wspm.mining.WeightedOnePattern;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;
import vn.edu.wspm.statistics.ItemWeightResult;
import vn.edu.wspm.statistics.SequenceDatabaseStatistics;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Positive-threshold DFS orchestration and accepted PrefixTree construction
 * for WSPM_BitPrune (C10.3, C12.3, C13.2).
 *
 * <p>
 * Decouples the Preprocessing boundary (Scope 2) from DFS Mining (Scope 3)
 * via the immutable package-private carrier {@link BitPrunePreparedInput}:
 * <ol>
 * <li>{@link #prepare(SequenceDatabase, MinimumSupport)} computes all database
 * statistics, item weights, MeanW, F1 singletons, SidMapping, VerticalOccurrenceIndex,
 * singleton bitmaps, singleton verification results, and CMAP structures without
 * mutating any PrefixTree;</li>
 * <li>{@link #minePreparedIntoResult(PrefixTree, BitPrunePreparedInput)} performs
 * F1 root initialization, sets up the per-run accumulator, executes DFS traversal
 * via {@link #executeDfs(PrefixTree, BitPrunePreparedInput, Map, BitPruneStatisticsAccumulator)},
 * freezes the PrefixTree, and constructs the completed {@link BitPruneMiningResult};</li>
 * <li>Traverses the sequential pattern search space in deterministic ascending
 * DFS order;</li>
 * <li>Generates candidates strictly using {@link BitPruneCandidateEngine};</li>
 * <li>Evaluates candidates strictly using {@link BitPruneCandidateEvaluator};</li>
 * <li>Inserts only accepted candidates via {@link BitPrunePrefixTreeIntegrator};</li>
 * <li>Recurses only into accepted candidates using child bitmap and verification state;</li>
 * <li>Returns an immutable map of accepted patterns to exact integer support, or an immutable {@link BitPruneMiningResult}.</li>
 * </ol>
 * </p>
 *
 * <p>
 * <strong>R6 BOUNDARY:</strong> Effective threshold equal to 0.0 is rejected
 * during preprocessing before any tree mutation because Decision R6 remains OPEN.
 * </p>
 */
public final class BitPruneMiner {

    private final BitPruneCandidateEngine candidateEngine = new BitPruneCandidateEngine();
    private final BitPruneCandidateEvaluator candidateEvaluator = new BitPruneCandidateEvaluator();
    private final BitPrunePrefixTreeIntegrator integrator = new BitPrunePrefixTreeIntegrator();
    private final ItemStatisticsCalculator itemStatisticsCalculator = new ItemStatisticsCalculator();
    private final ItemWeightCalculator itemWeightCalculator = new ItemWeightCalculator();
    private final F1Builder f1Builder = new F1Builder();
    private final MinimumSupportResolver minimumSupportResolver = new MinimumSupportResolver();
    private final CmapBuilder cmapBuilder = new CmapBuilder();

    public BitPruneMiner() {
    }

    /**
     * Mines all weighted sequential patterns from the given sequence database into
     * a newly constructed PrefixTree and returns an immutable completed-run result
     * carrier.
     *
     * @param database       non-null sequence database
     * @param minimumSupport non-null minimum support configuration
     * @return completed immutable result carrier
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if threshold is invalid, or if percentage mode is applied to an empty database
     */
    public BitPruneMiningResult mine(
            SequenceDatabase database,
            MinimumSupport minimumSupport) {
        PrefixTree tree = new PrefixTree();
        return mineIntoResult(tree, database, minimumSupport);
    }

    /**
     * Mines all weighted sequential patterns from the given sequence database while
     * measuring preprocessing, DFS mining, and complete algorithm execution using a
     * caller-owned execution memory sampler.
     *
     * <p>
     * Timing boundaries:
     * <ul>
     * <li>Complete Algorithm: starts immediately after argument validation before preprocessing,
     * and ends after PrefixTree freeze, statistics finish, and mining result construction,
     * immediately before metrics construction;</li>
     * <li>Preprocessing: starts immediately before database preprocessing, encompasses database
     * statistics, item weights, MeanW, F1 filtering, SidMapping, occurrence indexing, singleton
     * bitmaps and verifications, CMAP construction, PrefixTree root creation, and per-run accumulator
     * initialization, and ends immediately before DFS checkpoint;</li>
     * <li>DFS Mining: starts immediately before DFS candidate exploration and ends immediately
     * after recursive DFS completes;</li>
     * <li>Finalization: tree freeze, accumulator finish, and result construction occur outside
     * DFS mining but inside complete algorithm scope.</li>
     * </ul>
     * </p>
     *
     * @param database       non-null sequence database
     * @param minimumSupport non-null minimum support configuration
     * @param sampler        non-null caller-owned execution memory sampler
     * @return immutable measured run carrier holding the mining result and algorithm metrics
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if threshold is invalid, or if percentage mode is applied to an empty database
     */
    public MeasuredBitPruneRun mineMeasured(
            SequenceDatabase database,
            MinimumSupport minimumSupport,
            ExecutionMemorySampler sampler) {

        Objects.requireNonNull(database, "database must not be null");
        Objects.requireNonNull(minimumSupport, "minimumSupport must not be null");
        Objects.requireNonNull(sampler, "sampler must not be null");

        var algorithmStart = sampler.checkpoint();
        var preprocessingStart = sampler.checkpoint();

        PreparedRun preparedRun = prepareRun(database, minimumSupport);
        BitPrunePreparedInput prepared = preparedRun.preparedInput();
        PrefixTree tree = new PrefixTree();
        RunState runState = initializeRunState(tree, prepared);

        var preprocessingEnd = sampler.checkpoint();
        var preprocessingMetrics = PhaseExecutionMetrics.between(sampler, preprocessingStart, preprocessingEnd);

        var dfsStart = sampler.checkpoint();
        executeDfs(tree, prepared, runState.supportMap(), runState.accumulator());
        var dfsEnd = sampler.checkpoint();
        var dfsMetrics = PhaseExecutionMetrics.between(sampler, dfsStart, dfsEnd);

        BitPruneMiningResult result = finalizeResult(tree, runState);

        var algorithmEnd = sampler.checkpoint();
        long completeAlgorithmNanos = Math.max(0L, algorithmEnd.nanoTime() - algorithmStart.nanoTime());
        long heapAfterMiningBytes = algorithmEnd.heapUsedBytes();
        long observedPeakHeapAlgorithmBytes = sampler.observedPeakBytes(algorithmStart, algorithmEnd);

        var algorithmMetrics = new BitPruneAlgorithmMetrics(
                preprocessingMetrics,
                dfsMetrics,
                completeAlgorithmNanos,
                heapAfterMiningBytes,
                observedPeakHeapAlgorithmBytes);

        return new MeasuredBitPruneRun(result, algorithmMetrics, preparedRun.metadata());
    }

    /**
     * Mines all weighted sequential patterns from the given sequence database into
     * the caller-supplied PrefixTree and returns an immutable completed-run result
     * carrier.
     *
     * @param tree           non-null caller-owned PrefixTree containing only root Pattern.empty()
     * @param database       non-null sequence database
     * @param minimumSupport non-null minimum support configuration
     * @return completed immutable result carrier
     * @throws NullPointerException     if any argument is null
     * @throws IllegalStateException    if tree is frozen
     * @throws IllegalArgumentException if tree is not root-only, or if threshold is invalid,
     *                                  or if percentage mode is applied to an empty database
     */
    public BitPruneMiningResult mineIntoResult(
            PrefixTree tree,
            SequenceDatabase database,
            MinimumSupport minimumSupport) {

        validateTree(tree);
        BitPrunePreparedInput prepared = prepare(database, minimumSupport);
        return minePreparedIntoResult(tree, prepared);
    }

    /**
     * Mines all weighted sequential patterns from the given sequence database into
     * the caller-supplied PrefixTree under the specified minimum support.
     *
     * <p>
     * Orchestrates end-to-end preprocessing and DFS mining:
     * <ol>
     * <li>Validates input contracts and tree state before performing any mutation;</li>
     * <li>Executes preprocessing via {@link #prepare(SequenceDatabase, MinimumSupport)};</li>
     * <li>Delegates DFS mining and result construction to {@link #minePreparedIntoResult(PrefixTree, BitPrunePreparedInput)};</li>
     * <li>Returns an immutable map of accepted patterns to exact support.</li>
     * </ol>
     * </p>
     *
     * <p>
     * <strong>R6 BOUNDARY:</strong> Effective threshold equal to 0.0 on a non-empty
     * database is rejected before tree mutation because Decision R6 remains OPEN.
     * </p>
     *
     * @param tree           non-null caller-owned PrefixTree containing only root Pattern.empty()
     * @param database       non-null sequence database
     * @param minimumSupport non-null minimum support configuration
     * @return an immutable map of accepted patterns to exact integer support
     * @throws NullPointerException     if any argument is null
     * @throws IllegalStateException    if tree is frozen
     * @throws IllegalArgumentException if tree is not root-only, or if threshold is invalid,
     *                                  or if percentage mode is applied to an empty database
     */
    public Map<Pattern, Integer> mineInto(
            PrefixTree tree,
            SequenceDatabase database,
            MinimumSupport minimumSupport) {

        return mineIntoResult(tree, database, minimumSupport).supports();
    }

    /**
     * Executes the Preprocessing phase (Scope 2) for WSPM_BitPrune without mutating
     * the caller's PrefixTree or running DFS candidate exploration.
     *
     * @param database       non-null sequence database
     * @param minimumSupport non-null minimum support configuration
     * @return immutable prepared input carrier holding preprocessed structures
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if threshold is invalid, or if percentage mode is applied to an empty database
     */
    BitPrunePreparedInput prepare(
            SequenceDatabase database,
            MinimumSupport minimumSupport) {
        return prepareRun(database, minimumSupport).preparedInput();
    }

    private PreparedRun prepareRun(
            SequenceDatabase database,
            MinimumSupport minimumSupport) {

        Objects.requireNonNull(database, "database must not be null");
        Objects.requireNonNull(minimumSupport, "minimumSupport must not be null");

        if (database.size() == 0) {
            // PERCENT mode: resolver throws IllegalArgumentException("percentage minimum support cannot be resolved for an empty database")
            // ABSOLUTE mode: succeeds; returns empty prepared input
            ResolvedMinimumSupport resolvedMinSup = minimumSupportResolver.resolve(minimumSupport, 0);
            SidMapping mapping = SidMapping.of(database);
            VerticalOccurrenceIndex occIndex = VerticalOccurrenceIndex.build(database, mapping);
            CmapI cmapI = cmapBuilder.buildI(database, Collections.emptyList());
            CmapS cmapS = cmapBuilder.buildS(database, Collections.emptyList());
            BitPrunePreparedInput prepared = new BitPrunePreparedInput(
                    resolvedMinSup,
                    0.0,
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    cmapI,
                    cmapS,
                    occIndex);
            BitPruneRunMetadata metadata = new BitPruneRunMetadata(
                    0,
                    OptionalDouble.empty(),
                    resolvedMinSup);
            return new PreparedRun(prepared, metadata);
        }

        ResolvedMinimumSupport resolvedMinSup = minimumSupportResolver.resolve(minimumSupport, database.size());
        if (resolvedMinSup.effectiveThreshold() == 0.0) {
            throw new IllegalArgumentException(
                    "effectiveThreshold == 0.0 is rejected while Decision R6 remains OPEN");
        }
        if (resolvedMinSup.effectiveThreshold() < 0.0) {
            throw new IllegalArgumentException(
                    "effectiveThreshold must not be negative; got: " + resolvedMinSup.effectiveThreshold());
        }

        SequenceDatabaseStatistics stats = itemStatisticsCalculator.calculate(database);
        ItemWeightResult weightResult = itemWeightCalculator.calculate(stats);
        double globalMeanWeight = weightResult.meanWeight().orElseThrow(
                () -> new IllegalStateException("non-empty database must have global MeanW"));

        List<WeightedOnePattern> f1 = f1Builder.build(stats, weightResult, resolvedMinSup);
        List<WeightedOnePattern> sortedF1 = new ArrayList<>(f1);
        sortedF1.sort(Comparator.comparingInt(WeightedOnePattern::itemId));

        SidMapping mapping = SidMapping.of(database);
        VerticalOccurrenceIndex occIndex = VerticalOccurrenceIndex.build(database, mapping);

        Map<Integer, SidBitmap> singletonBitmaps = new LinkedHashMap<>();
        Map<Integer, VerificationResult> singletonVerifications = new LinkedHashMap<>();
        List<Item> f1Items = new ArrayList<>(sortedF1.size());

        for (WeightedOnePattern p : sortedF1) {
            int itemId = p.itemId();
            Item item = new Item(itemId);
            f1Items.add(item);

            SidBitmap.Builder bitmapBuilder = SidBitmap.builder(mapping);
            List<PatternEndpoints> endpoints = new ArrayList<>();

            for (SequenceRecord record : database.records()) {
                int sid = record.sid();
                int[] positions = occIndex.positions(item, sid);
                if (positions.length > 0) {
                    bitmapBuilder.setSid(sid);
                    endpoints.add(PatternEndpoints.of(sid, positions));
                }
            }

            singletonBitmaps.put(itemId, bitmapBuilder.build());
            singletonVerifications.put(itemId, VerificationResult.of(endpoints));
        }

        CmapI cmapI = cmapBuilder.buildI(database, f1Items);
        CmapS cmapS = cmapBuilder.buildS(database, f1Items);

        BitPrunePreparedInput prepared = new BitPrunePreparedInput(
                resolvedMinSup,
                globalMeanWeight,
                sortedF1,
                singletonBitmaps,
                singletonVerifications,
                cmapI,
                cmapS,
                occIndex);
        BitPruneRunMetadata metadata = new BitPruneRunMetadata(
                stats.numberOfDistinctItems(),
                OptionalDouble.of(globalMeanWeight),
                resolvedMinSup);
        return new PreparedRun(prepared, metadata);
    }

    /**
     * Executes DFS mining and result finalization over preprocessed input structures.
     *
     * @param tree          non-null caller-owned PrefixTree containing only root Pattern.empty()
     * @param preparedInput non-null preprocessed input carrier
     * @return completed immutable result carrier
     * @throws NullPointerException     if any argument is null
     * @throws IllegalStateException    if tree is frozen
     * @throws IllegalArgumentException if tree is not root-only
     */
    BitPruneMiningResult minePreparedIntoResult(
            PrefixTree tree,
            BitPrunePreparedInput preparedInput) {

        validateTree(tree);
        Objects.requireNonNull(preparedInput, "preparedInput must not be null");

        RunState runState = initializeRunState(tree, preparedInput);
        executeDfs(tree, preparedInput, runState.supportMap(), runState.accumulator());
        return finalizeResult(tree, runState);
    }

    /**
     * Executes the DFS recursive traversal starting from each F1 root.
     */
    void executeDfs(
            PrefixTree tree,
            BitPrunePreparedInput prepared,
            Map<Pattern, Integer> supportMap,
            BitPruneStatisticsAccumulator accumulator) {

        for (WeightedOnePattern rootOne : prepared.sortedF1()) {
            int rootId = rootOne.itemId();
            Pattern rootPattern = Pattern.singleton(new Item(rootId));
            SidBitmap rootBitmap = prepared.singletonBitmaps().get(rootId);
            VerificationResult rootVerif = prepared.singletonVerifications().get(rootId);

            dfs(rootPattern, rootBitmap, rootVerif, prepared.sortedF1(), prepared.singletonBitmaps(),
                    tree, supportMap, prepared.cmapI(), prepared.cmapS(), prepared.occurrenceIndex(),
                    prepared.globalMeanWeight(), prepared.resolvedMinimumSupport(), accumulator);
        }
    }

    /**
     * Result-producing DFS mining over prepared input structures with defensive validation.
     * Preserved for backward compatibility with C10/C12 integration test suites.
     */
    BitPruneMiningResult minePreparedIntoResult(
            PrefixTree tree,
            List<WeightedOnePattern> f1,
            Map<Integer, SidBitmap> singletonBitmaps,
            Map<Integer, VerificationResult> singletonVerifications,
            CmapI cmapI,
            CmapS cmapS,
            VerticalOccurrenceIndex occurrenceIndex,
            double globalMeanWeight,
            ResolvedMinimumSupport minimumSupport) {

        // --- 1. Global Input Validation Before Tree Mutation ---
        validateTree(tree);
        Objects.requireNonNull(f1, "f1 must not be null");
        Objects.requireNonNull(singletonBitmaps, "singletonBitmaps must not be null");
        Objects.requireNonNull(singletonVerifications, "singletonVerifications must not be null");
        Objects.requireNonNull(cmapI, "cmapI must not be null");
        Objects.requireNonNull(cmapS, "cmapS must not be null");
        Objects.requireNonNull(occurrenceIndex, "occurrenceIndex must not be null");
        Objects.requireNonNull(minimumSupport, "minimumSupport must not be null");

        if (!Double.isFinite(globalMeanWeight) || globalMeanWeight < 0.0) {
            throw new IllegalArgumentException(
                    "globalMeanWeight must be finite and non-negative; got: " + globalMeanWeight);
        }
        if (minimumSupport.effectiveThreshold() == 0.0) {
            throw new IllegalArgumentException(
                    "effectiveThreshold == 0.0 is rejected while Decision R6 remains OPEN");
        }
        if (minimumSupport.effectiveThreshold() < 0.0) {
            throw new IllegalArgumentException(
                    "effectiveThreshold must not be negative; got: " + minimumSupport.effectiveThreshold());
        }

        Set<Integer> f1ItemIds = new HashSet<>(f1.size());
        for (WeightedOnePattern p : f1) {
            Objects.requireNonNull(p, "f1 element must not be null");
            if (!f1ItemIds.add(p.itemId())) {
                throw new IllegalArgumentException("duplicate item id in F1: " + p.itemId());
            }
        }

        if (!singletonBitmaps.keySet().equals(f1ItemIds)) {
            throw new IllegalArgumentException("singletonBitmaps keys must equal F1 item IDs exactly");
        }
        if (!singletonVerifications.keySet().equals(f1ItemIds)) {
            throw new IllegalArgumentException("singletonVerifications keys must equal F1 item IDs exactly");
        }
        for (var entry : singletonBitmaps.entrySet()) {
            Objects.requireNonNull(entry.getValue(),
                    "singleton bitmap for item " + entry.getKey() + " must not be null");
        }
        for (var entry : singletonVerifications.entrySet()) {
            Objects.requireNonNull(entry.getValue(),
                    "singleton verification result for item " + entry.getKey() + " must not be null");
        }

        SidMapping commonMapping = null;
        for (SidBitmap bm : singletonBitmaps.values()) {
            if (commonMapping == null) {
                commonMapping = bm.mapping();
            } else if (!bm.mapping().equals(commonMapping)) {
                throw new IllegalArgumentException("all singleton bitmaps must use the same SidMapping");
            }
        }
        if (commonMapping != null && commonMapping.size() != minimumSupport.numberOfSequences()) {
            throw new IllegalArgumentException("bitmap universe size (" + commonMapping.size()
                    + ") must equal minimumSupport.numberOfSequences() ("
                    + minimumSupport.numberOfSequences() + ")");
        }

        for (WeightedOnePattern p : f1) {
            int itemId = p.itemId();
            Item item = new Item(itemId);
            VerificationResult vr = singletonVerifications.get(itemId);
            SidBitmap bm = singletonBitmaps.get(itemId);

            if (vr.exactSupport() != p.support()) {
                throw new IllegalArgumentException("singleton VerificationResult exactSupport ("
                        + vr.exactSupport() + ") must equal WeightedOnePattern support ("
                        + p.support() + ") for item " + itemId);
            }

            Set<Integer> bmSids = new HashSet<>();
            for (int sid : bm.toSupportingSids()) {
                bmSids.add(sid);
            }
            Set<Integer> vrSids = new HashSet<>();
            for (int sid : vr.supportingSids()) {
                vrSids.add(sid);
            }
            if (!bmSids.equals(vrSids)) {
                throw new IllegalArgumentException(
                        "singleton verification SIDs must equal singleton bitmap SIDs for item " + itemId);
            }

            for (PatternEndpoints ep : vr.endpointStates()) {
                int sid = ep.actualSid();
                int[] expectedPos = occurrenceIndex.positions(item, sid);
                if (!Arrays.equals(ep.endpoints(), expectedPos)) {
                    throw new IllegalArgumentException("singleton endpoint array for item " + itemId
                    + " and SID " + sid + " must equal occurrenceIndex.positions");
                }
            }

            if (Double.compare(p.support() * globalMeanWeight, minimumSupport.effectiveThreshold()) < 0) {
                throw new IllegalArgumentException("F1 item " + itemId + " does not meet minimum support threshold");
            }
        }

        BitPruneStatisticsAccumulator accumulator = new BitPruneStatisticsAccumulator(
                minimumSupport.numberOfSequences(), f1.size());

        // --- 2. Empty F1 Handling ---
        if (f1.isEmpty()) {
            tree.freeze();
            BitPruneStatistics statistics = accumulator.finish(0L);
            return new BitPruneMiningResult(tree, Collections.emptyMap(), statistics);
        }

        // --- 3. Deterministic Ascending F1 Initialization ---
        List<WeightedOnePattern> sortedF1 = new ArrayList<>(f1);
        sortedF1.sort(Comparator.comparingInt(WeightedOnePattern::itemId));

        BitPrunePreparedInput prepared = new BitPrunePreparedInput(
                minimumSupport,
                globalMeanWeight,
                sortedF1,
                singletonBitmaps,
                singletonVerifications,
                cmapI,
                cmapS,
                occurrenceIndex);

        integrator.initializeF1Roots(tree, sortedF1);

        Map<Pattern, Integer> supportMap = new LinkedHashMap<>();
        for (WeightedOnePattern one : sortedF1) {
            Pattern singleton = Pattern.singleton(new Item(one.itemId()));
            supportMap.put(singleton, one.support());
        }

        // --- 4. Positive-Threshold DFS Traversal ---
        executeDfs(tree, prepared, supportMap, accumulator);

        tree.freeze();
        BitPruneStatistics statistics = accumulator.finish((long) supportMap.size());
        return new BitPruneMiningResult(tree, supportMap, statistics);
    }

    /**
     * Mines all weighted sequential patterns into the provided PrefixTree under
     * positive threshold.
     *
     * @param tree                   non-null caller-owned PrefixTree containing only root
     * @param f1                     non-null list of frequent weighted 1-patterns
     * @param singletonBitmaps       non-null map of item ID to singleton bitmap
     * @param singletonVerifications non-null map of item ID to singleton verification result
     * @param cmapI                  non-null CMAP-I index
     * @param cmapS                  non-null CMAP-S index
     * @param occurrenceIndex        non-null vertical occurrence index
     * @param globalMeanWeight       finite, non-negative MeanW
     * @param minimumSupport         resolved minimum support with strictly positive effective threshold
     * @return an immutable map of accepted patterns to exact support
     * @throws NullPointerException     if any argument or required element is null
     * @throws IllegalStateException    if tree is frozen or child evaluation state is corrupt
     * @throws IllegalArgumentException if inputs violate structural, threshold, or universe invariants
     */
    public Map<Pattern, Integer> mineInto(
            PrefixTree tree,
            List<WeightedOnePattern> f1,
            Map<Integer, SidBitmap> singletonBitmaps,
            Map<Integer, VerificationResult> singletonVerifications,
            CmapI cmapI,
            CmapS cmapS,
            VerticalOccurrenceIndex occurrenceIndex,
            double globalMeanWeight,
            ResolvedMinimumSupport minimumSupport) {

        return minePreparedIntoResult(
                tree,
                f1,
                singletonBitmaps,
                singletonVerifications,
                cmapI,
                cmapS,
                occurrenceIndex,
                globalMeanWeight,
                minimumSupport).supports();
    }

    private static void validateTree(PrefixTree tree) {
        Objects.requireNonNull(tree, "tree must not be null");
        if (tree.isFrozen()) {
            throw new IllegalStateException("tree must not be frozen");
        }
        if (tree.nodeCount() != 1 || tree.find(Pattern.empty()).isEmpty()) {
            throw new IllegalArgumentException("tree must initially contain only root Pattern.empty()");
        }
    }

    private void dfs(
            Pattern parentPattern,
            SidBitmap parentBitmap,
            VerificationResult parentVerif,
            List<WeightedOnePattern> sortedF1,
            Map<Integer, SidBitmap> singletonBitmaps,
            PrefixTree tree,
            Map<Pattern, Integer> supportMap,
            CmapI cmapI,
            CmapS cmapS,
            VerticalOccurrenceIndex occurrenceIndex,
            double globalMeanWeight,
            ResolvedMinimumSupport minimumSupport,
            BitPruneStatisticsAccumulator accumulator) {

        accumulator.recordExpandedNode();

        List<BitPruneCandidateResult> candidates = candidateEngine.generateCandidates(parentPattern, sortedF1);
        for (BitPruneCandidateResult candidate : candidates) {
            Item extItem = candidate.extensionItem();
            SidBitmap extBitmap = singletonBitmaps.get(extItem.id());

            switch (candidate.extensionType()) {
                case I_EXTENSION -> accumulator.recordGeneratedIExtension();
                case S_EXTENSION -> accumulator.recordGeneratedSExtension();
            }

            BitPruneCandidateEvaluation eval = candidateEvaluator.evaluate(
                    parentPattern, candidate, parentBitmap, extBitmap, parentVerif,
                    cmapI, cmapS, occurrenceIndex, globalMeanWeight, minimumSupport);

            switch (eval.outcome()) {
                case CMAP_REJECTED -> accumulator.recordCmapRejected();
                case BITMAP_REJECTED -> accumulator.recordBitmapRejected();
                case EXACT_REJECTED -> accumulator.recordExactRejected();
                case ACCEPTED -> {
                    accumulator.recordAcceptedExtension();
                    SidBitmap childBitmap = eval.candidateBitmap().orElseThrow(
                            () -> new IllegalStateException("Accepted evaluation missing candidate bitmap"));
                    VerificationResult childVerif = eval.verificationResult().orElseThrow(
                            () -> new IllegalStateException("Accepted evaluation missing verification result"));

                    integrator.insertAcceptedChild(tree, parentPattern, candidate);
                    supportMap.put(candidate.pattern(), childVerif.exactSupport());

                    dfs(candidate.pattern(), childBitmap, childVerif, sortedF1, singletonBitmaps,
                            tree, supportMap, cmapI, cmapS, occurrenceIndex,
                            globalMeanWeight, minimumSupport, accumulator);
                }
            }
        }
    }

    private RunState initializeRunState(PrefixTree tree, BitPrunePreparedInput prepared) {
        List<WeightedOnePattern> sortedF1 = prepared.sortedF1();
        BitPruneStatisticsAccumulator accumulator = new BitPruneStatisticsAccumulator(
                prepared.resolvedMinimumSupport().numberOfSequences(), sortedF1.size());

        Map<Pattern, Integer> supportMap = new LinkedHashMap<>();
        if (!sortedF1.isEmpty()) {
            integrator.initializeF1Roots(tree, sortedF1);
            for (WeightedOnePattern one : sortedF1) {
                Pattern singleton = Pattern.singleton(new Item(one.itemId()));
                supportMap.put(singleton, one.support());
            }
        }
        return new RunState(supportMap, accumulator);
    }

    private BitPruneMiningResult finalizeResult(PrefixTree tree, RunState runState) {
        tree.freeze();
        BitPruneStatistics statistics = runState.accumulator().finish((long) runState.supportMap().size());
        return new BitPruneMiningResult(tree, runState.supportMap(), statistics);
    }

    private record RunState(
            Map<Pattern, Integer> supportMap,
            BitPruneStatisticsAccumulator accumulator) {
    }

    private record PreparedRun(
            BitPrunePreparedInput preparedInput,
            BitPruneRunMetadata metadata) {
    }
}
