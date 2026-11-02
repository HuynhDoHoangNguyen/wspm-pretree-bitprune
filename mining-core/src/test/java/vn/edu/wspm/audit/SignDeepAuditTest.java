package vn.edu.wspm.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.mining.WspmMiningResult;
import vn.edu.wspm.mining.WspmPreTreeMiner;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;

/**
 * B21.4 diagnosis-only checks for the locally available SIGN research input.
 *
 * <p>The raw scanner, structural matcher, and projected enumerator below do not call production
 * statistics, weight, Prime-block join, candidate, or support code. The test intentionally does
 * not assert that the paper's expected count is 315; it compares two independently obtained
 * complete {@code pattern -> support/SID} results for the audited local bytes.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class SignDeepAuditTest {

    private static final Path SIGN_PATH =
            Path.of("src/main/resources/datasets/SIGN.txt");
    private static final MathContext AUDIT_CONTEXT = new MathContext(50);

    private RawDatabase raw;
    private SequenceDatabase productionDatabase;
    private WspmMiningResult productionResult;
    private WeightAudit weights;

    @BeforeAll
    void prepareAuditInputs() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(SIGN_PATH),
                "B21.4 requires the local ignored SIGN research input: " + SIGN_PATH);
        raw = RawDatabase.read(SIGN_PATH);
        productionDatabase = new SpmfSequenceParser().parse(SIGN_PATH);
        productionResult = new WspmPreTreeMiner().mine(
                productionDatabase, MinimumSupport.percentage(0.4));
        weights = WeightAudit.from(raw);
    }

    @Test
    void independentRawParserPreservesEverySignSequenceAndItemset() {
        assertEquals(raw.sequences().size(), productionDatabase.size());
        for (int index = 0; index < raw.sequences().size(); index++) {
            var expected = raw.sequences().get(index);
            var actual = productionDatabase.records().get(index);
            assertEquals(index + 1, actual.sid());
            assertEquals(expected.size(), actual.sequence().size(), "SID " + (index + 1));
            for (int itemsetIndex = 0; itemsetIndex < expected.size(); itemsetIndex++) {
                Set<Integer> actualIds = new TreeSet<>();
                for (var item : actual.sequence().itemsets().get(itemsetIndex).items()) {
                    actualIds.add(item.id());
                }
                assertEquals(expected.get(itemsetIndex), actualIds,
                        "SID " + (index + 1) + ", itemset " + (itemsetIndex + 1));
            }
        }

        assertEquals(730, raw.sequences().size());
        assertEquals(267, weights.occurrences().size());
        assertEquals(37_958L, weights.totalOccurrences());
        assertEquals(0, raw.multiItemItemsetCount());
        assertEquals(0, raw.duplicateWithinItemsetCount());
        assertEquals(0, raw.repeatedAcrossItemsetsSequenceCount());
        assertEquals(0, raw.emptyRecordCount());
    }

    @Test
    void exactRationalAndJavaDoubleBoundariesAgree() {
        // From raw counts: MeanW = ((680/37958) + (1/37958)) / 2 = 681/75916.
        Fraction exactMean = new Fraction(BigInteger.valueOf(681), BigInteger.valueOf(75_916));
        Fraction exactThreshold = new Fraction(BigInteger.valueOf(73), BigInteger.valueOf(25));
        double meanDouble = weights.meanWeight();
        double thresholdDouble = 0.004 * raw.sequences().size();

        assertEquals(680L, weights.maximumOccurrence());
        assertEquals(1L, weights.minimumOccurrence());
        assertEquals(exactMean.toBigDecimal(AUDIT_CONTEXT).doubleValue(), meanDouble);
        assertEquals(2.92, thresholdDouble);

        int exactRequiredSupport = firstAcceptedSupport(
                support -> exactMean.multiply(support).compareTo(exactThreshold) >= 0,
                raw.sequences().size());
        int doubleRequiredSupport = firstAcceptedSupport(
                support -> support * meanDouble >= thresholdDouble,
                raw.sequences().size());
        assertEquals(326, exactRequiredSupport);
        assertEquals(exactRequiredSupport, doubleRequiredSupport);

        System.out.println("B21.4 SIGN NUMERICAL AUDIT");
        System.out.println("exactMeanW=681/75916=" + exactMean.toBigDecimal(AUDIT_CONTEXT));
        System.out.println("doubleMeanW=" + meanDouble
                + ", hex=" + Double.toHexString(meanDouble));
        System.out.println("BigDecimal.valueOf(double)=" + BigDecimal.valueOf(meanDouble));
        System.out.println("new BigDecimal(double)=" + new BigDecimal(meanDouble));
        System.out.println("exactThreshold=73/25=" + exactThreshold.toBigDecimal(AUDIT_CONTEXT));
        System.out.println("doubleThreshold=" + thresholdDouble
                + ", hex=" + Double.toHexString(thresholdDouble));
        for (int support = 324; support <= 328; support++) {
            double weighted = support * meanDouble;
            System.out.printf(java.util.Locale.ROOT,
                    "support=%d weighted=%s weightedHex=%s threshold=%s delta=%s accepted=%s%n",
                    support,
                    Double.toString(weighted),
                    Double.toHexString(weighted),
                    Double.toString(thresholdDouble),
                    Double.toString(weighted - thresholdDouble),
                    weighted >= thresholdDouble);
        }
    }

    @Test
    void all267IndependentItemCountsSupportsAndWeightsMatchProduction() {
        var productionStatistics = new ItemStatisticsCalculator().calculate(productionDatabase);
        var productionWeights = new ItemWeightCalculator().calculate(productionStatistics);
        assertEquals(weights.occurrences().keySet(), productionStatistics.itemStatistics().keySet());
        for (int itemId : weights.occurrences().keySet()) {
            var actual = productionStatistics.statisticsFor(itemId).orElseThrow();
            assertEquals(weights.occurrences().get(itemId), actual.occurrenceCount(),
                    "occurrences for item " + itemId);
            assertEquals(weights.sequenceSupports().get(itemId), actual.sequenceSupport(),
                    "sequence support for item " + itemId);
            double expectedWeight = (double) weights.occurrences().get(itemId)
                    / weights.totalOccurrences();
            assertEquals(expectedWeight, productionWeights.weightFor(itemId).orElseThrow(),
                    "weight for item " + itemId);
        }
        assertEquals(weights.meanWeight(), productionWeights.meanWeight().orElseThrow());
        assertEquals(49, productionResult.statistics().f1Count());
        System.out.println("B21.4 SIGN ITEM AUDIT: 267/267 counts, supports, and weights equal; F1=49");
    }

    @Test
    void allAcceptedSupportsAndSidSetsMatchIndependentStructuralMatcher() {
        var uniquePatterns = new LinkedHashSet<>(productionResult.acceptedPatterns());
        assertEquals(productionResult.acceptedPatterns().size(), uniquePatterns.size());

        int supportMismatches = 0;
        int sidMismatches = 0;
        for (var pattern : productionResult.acceptedPatterns()) {
            List<Integer> structuralSids = raw.supportingSids(pattern);
            List<Integer> encodedSids = encodedSids(pattern);
            if (structuralSids.size() != productionResult.support(pattern)) {
                supportMismatches++;
            }
            if (!structuralSids.equals(encodedSids)) {
                sidMismatches++;
            }
            assertTrue(structuralSids.size() * weights.meanWeight()
                    >= productionResult.minimumSupport().effectiveThreshold());
        }
        assertEquals(0, supportMismatches);
        assertEquals(0, sidMismatches);
        System.out.printf("B21.4 SIGN FULL SUPPORT: %d/%d supports equal; %d/%d SID sets equal%n",
                uniquePatterns.size(), uniquePatterns.size(),
                uniquePatterns.size(), uniquePatterns.size());
    }

    @Test
    void independentProjectionEnumeratesTheCompleteExactSignResult() {
        int requiredSupport = firstAcceptedSupport(
                support -> support * weights.meanWeight() >= 0.004 * raw.sequences().size(),
                raw.sequences().size());
        ProjectionEnumeration independent = raw.enumerateSingletonItemsetPatterns(
                support -> support >= requiredSupport, 2_000_000);

        Map<String, Integer> production = new TreeMap<>();
        for (var pattern : productionResult.acceptedPatterns()) {
            assertTrue(pattern.itemsets().stream().allMatch(itemset -> itemset.size() == 1),
                    "SIGN source has only singleton itemsets, so accepted I-pattern is impossible");
            production.put(pattern.serialize(), productionResult.support(pattern));
        }
        assertEquals(production, independent.supports());
        assertFalse(independent.truncated());
        System.out.printf(
                "B21.4 SIGN COMPLETE PROJECTION: production=%d independent=%d candidates=%d%n",
                production.size(), independent.supports().size(), independent.candidateCount());
    }

    @Test
    void reportsCandidateBoundaryDistributionAndCountInvariants() {
        var rows = new ArrayList<AuditedPattern>();
        for (int itemId : weights.occurrences().keySet()) {
            Pattern singleton = Pattern.singleton(new Item(itemId));
            rows.add(new AuditedPattern(
                    singleton, Kind.SINGLETON, raw.supportingSids(singleton).size()));
        }

        List<Integer> f1Items = productionResult.acceptedPatterns().stream()
                .filter(pattern -> pattern.length() == 1)
                .map(pattern -> pattern.itemsets().getFirst().items().getFirst().id())
                .sorted()
                .toList();
        for (var parent : productionResult.acceptedPatterns()) {
            int lastItem = parent.lastItem().orElseThrow().id();
            for (int itemId : f1Items) {
                if (itemId > lastItem) {
                    Pattern candidate = independentIExtend(parent, itemId);
                    rows.add(new AuditedPattern(
                            candidate, Kind.I_EXTENSION, raw.supportingSids(candidate).size()));
                }
            }
            for (int itemId : f1Items) {
                Pattern candidate = independentSExtend(parent, itemId);
                rows.add(new AuditedPattern(
                        candidate, Kind.S_EXTENSION, raw.supportingSids(candidate).size()));
            }
        }

        long extensionCount = rows.stream().filter(row -> row.kind() != Kind.SINGLETON).count();
        long acceptedCount = rows.stream().filter(this::exactlyAccepted).count();
        assertEquals(productionResult.statistics().candidateCount(), extensionCount);
        assertEquals(productionResult.acceptedPatterns().size(), acceptedCount);
        assertEquals(productionResult.prefixTree().nodeCount() - 1,
                productionResult.statistics().patternCount());
        assertEquals(productionResult.statistics().patternCount(),
                productionResult.acceptedPatterns().size());
        assertEquals(productionResult.acceptedPatterns().size(),
                new HashSet<>(productionResult.acceptedPatterns()).size());

        System.out.println("B21.4 SIGN BOUNDARY DISTRIBUTION");
        for (var band : SupportBand.values()) {
            var matching = rows.stream().filter(row -> band.contains(row.support())).toList();
            System.out.printf(
                    "%s total=%d singleton=%d I=%d S=%d accepted=%d rejected=%d%n",
                    band.label,
                    matching.size(),
                    matching.stream().filter(row -> row.kind() == Kind.SINGLETON).count(),
                    matching.stream().filter(row -> row.kind() == Kind.I_EXTENSION).count(),
                    matching.stream().filter(row -> row.kind() == Kind.S_EXTENSION).count(),
                    matching.stream().filter(this::exactlyAccepted).count(),
                    matching.stream().filter(row -> !exactlyAccepted(row)).count());
        }
        printPatternDecomposition();
    }

    @Test
    void controlledRoundingHypothesesAreEnumeratedWithoutChangingProduction() throws IOException {
        double mean = weights.meanWeight();
        double threshold = 0.004 * raw.sequences().size();
        var policies = new LinkedHashMap<String, IntPredicate>();
        policies.put("exact >=", support -> support * mean >= threshold);
        policies.put("strict >", support -> support * mean > threshold);

        double displayedMean = BigDecimal.valueOf(mean)
                .setScale(5, RoundingMode.HALF_UP).doubleValue();
        policies.put("MeanW rounded to paper 5 decimals",
                support -> support * displayedMean >= threshold);
        policies.put("effective threshold rounded to 2 decimals",
                support -> support * mean >= BigDecimal.valueOf(threshold)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue());
        policies.put("weighted support rounded to 5 decimals",
                support -> BigDecimal.valueOf(support * mean)
                        .setScale(5, RoundingMode.HALF_UP)
                        .compareTo(BigDecimal.valueOf(threshold)) >= 0);
        policies.put("weighted support rounded to 2 decimals",
                support -> BigDecimal.valueOf(support * mean)
                        .setScale(2, RoundingMode.HALF_UP)
                        .compareTo(BigDecimal.valueOf(threshold)) >= 0);
        policies.put("floor effective threshold to integer",
                support -> support * mean >= Math.floor(threshold));
        policies.put("ceil effective threshold to integer",
                support -> support * mean >= Math.ceil(threshold));
        policies.put("round effective threshold to integer",
                support -> support * mean >= Math.round(threshold));
        // Unsupported diagnostic prompted by the complete Table 4 SIGN count series. These are
        // not proposed production semantics and are kept separate from the rounding policies.
        policies.put("unsupported Table 4 hypothesis: absolute threshold 4",
                support -> support * mean >= 4.0);
        policies.put("unsupported Table 4 hypothesis: absolute threshold 3",
                support -> support * mean >= 3.0);
        policies.put("unsupported Table 4 hypothesis: absolute threshold 2",
                support -> support * mean >= 2.0);
        policies.put("unsupported Table 4 hypothesis: absolute threshold 1",
                support -> support * mean >= 1.0);

        Map<Integer, ProjectionEnumeration> byRequiredSupport = new HashMap<>();
        ProjectionEnumeration exact = null;
        System.out.println("B21.4 SIGN ROUNDING HYPOTHESES");
        for (var entry : policies.entrySet()) {
            int requiredSupport = firstAcceptedSupport(entry.getValue(), raw.sequences().size());
            ProjectionEnumeration enumeration = byRequiredSupport.computeIfAbsent(
                    requiredSupport,
                    support -> raw.enumerateSingletonItemsetPatterns(
                            observed -> observed >= support, 10_000_000));
            if (entry.getKey().equals("exact >=")) {
                exact = enumeration;
            }
            System.out.printf("%s requiredSupport=%d patterns=%d candidates=%d truncated=%s%n",
                    entry.getKey(), requiredSupport, enumeration.supports().size(),
                    enumeration.candidateCount(), enumeration.truncated());
        }
        assertTrue(exact != null);
        assertFalse(exact.truncated());

        for (var entry : byRequiredSupport.entrySet()) {
            var changed = symmetricDifference(exact.supports().keySet(), entry.getValue().supports().keySet());
            System.out.printf("cutoff=%d changedPatternsVsExact=%d sample=%s%n",
                    entry.getKey(), changed.size(), changed.stream().limit(20).toList());
        }
        writeRoundingDiagnostic(exact, byRequiredSupport, mean, threshold);
    }

    private static void writeRoundingDiagnostic(
            ProjectionEnumeration exact,
            Map<Integer, ProjectionEnumeration> byRequiredSupport,
            double mean,
            double threshold) throws IOException {
        var lines = new ArrayList<String>();
        lines.add("requiredSupport\tpattern\tsupport\tsupportingSIDCount\tweightedSupport"
                + "\tthreshold\tdelta\texactDecision\troundedDecision");
        for (var entry : new TreeMap<>(byRequiredSupport).entrySet()) {
            // Only rounding hypotheses belong in this file. The separate unsupported Table 4
            // absolute-threshold probe (cutoffs 112 and 446) is summarized in the research note.
            if (entry.getKey() == 326 || entry.getKey() == 112 || entry.getKey() == 446) {
                continue;
            }
            var variant = entry.getValue();
            for (String pattern : symmetricDifference(
                    exact.supports().keySet(), variant.supports().keySet())) {
                Integer support = exact.supports().get(pattern);
                if (support == null) {
                    support = variant.supports().get(pattern);
                }
                double weighted = support * mean;
                lines.add(entry.getKey() + "\t" + pattern + "\t" + support + "\t" + support
                        + "\t" + weighted + "\t" + threshold + "\t" + (weighted - threshold)
                        + "\t" + exact.supports().containsKey(pattern)
                        + "\t" + variant.supports().containsKey(pattern));
            }
        }
        Path output = Path.of("target/b21.4/sign-rounding-boundary.tsv");
        Files.createDirectories(output.getParent());
        Files.write(output, lines, StandardCharsets.UTF_8);
        System.out.println("roundingDiagnostic=" + output + " rows=" + (lines.size() - 1));
    }

    private boolean exactlyAccepted(AuditedPattern row) {
        return row.support() * weights.meanWeight()
                >= productionResult.minimumSupport().effectiveThreshold();
    }

    private void printPatternDecomposition() {
        var lengthCounts = new TreeMap<Integer, Long>();
        long singleton = 0;
        long onlyI = 0;
        long onlyS = 0;
        long mixed = 0;
        long repeated = 0;
        for (var pattern : productionResult.acceptedPatterns()) {
            lengthCounts.merge(pattern.length(), 1L, Long::sum);
            if (pattern.length() == 1) {
                singleton++;
            } else {
                boolean hasMultiItemset = pattern.itemsets().stream()
                        .anyMatch(itemset -> itemset.size() > 1);
                boolean hasSeveralItemsets = pattern.size() > 1;
                if (hasMultiItemset && hasSeveralItemsets) {
                    mixed++;
                } else if (hasMultiItemset) {
                    onlyI++;
                } else {
                    onlyS++;
                }
            }
            var seen = new HashSet<Integer>();
            boolean repeats = pattern.itemsets().stream()
                    .flatMap(itemset -> itemset.items().stream())
                    .map(Item::id)
                    .anyMatch(itemId -> !seen.add(itemId));
            if (repeats) {
                repeated++;
            }
        }
        System.out.printf(
                "decomposition lengths=%s singleton=%d onlyI=%d onlyS=%d mixed=%d repeated=%d%n",
                lengthCounts, singleton, onlyI, onlyS, mixed, repeated);
    }

    private List<Integer> encodedSids(Pattern pattern) {
        var encoding = productionResult.encoding(pattern);
        var result = new ArrayList<Integer>();
        for (int sid = 1; sid <= raw.sequences().size(); sid++) {
            if (encoding.sequenceBlock().isSupporting(sid)) {
                result.add(sid);
            }
        }
        return List.copyOf(result);
    }

    private static Pattern independentIExtend(Pattern parent, int itemId) {
        var itemsets = new ArrayList<Itemset>(parent.itemsets());
        var items = new ArrayList<Item>(itemsets.getLast().items());
        items.add(new Item(itemId));
        itemsets.set(itemsets.size() - 1, new Itemset(items));
        return new Pattern(itemsets);
    }

    private static Pattern independentSExtend(Pattern parent, int itemId) {
        var itemsets = new ArrayList<Itemset>(parent.itemsets());
        itemsets.add(Itemset.of(new Item(itemId)));
        return new Pattern(itemsets);
    }

    private static int firstAcceptedSupport(IntPredicate decision, int maximum) {
        for (int support = 0; support <= maximum; support++) {
            if (decision.test(support)) {
                return support;
            }
        }
        throw new IllegalArgumentException("policy accepts no support in [0, " + maximum + "]");
    }

    private static Set<String> symmetricDifference(Set<String> left, Set<String> right) {
        var result = new TreeSet<String>(left);
        for (var value : right) {
            if (!result.add(value)) {
                result.remove(value);
            }
        }
        return result;
    }

    private enum Kind {
        SINGLETON,
        I_EXTENSION,
        S_EXTENSION
    }

    private enum SupportBand {
        BELOW_324("<=323") {
            @Override boolean contains(int support) { return support <= 323; }
        },
        AT_324("324") {
            @Override boolean contains(int support) { return support == 324; }
        },
        AT_325("325") {
            @Override boolean contains(int support) { return support == 325; }
        },
        AT_326("326") {
            @Override boolean contains(int support) { return support == 326; }
        },
        AT_327("327") {
            @Override boolean contains(int support) { return support == 327; }
        },
        AT_328("328") {
            @Override boolean contains(int support) { return support == 328; }
        },
        BETWEEN_329_AND_444("329-444") {
            @Override boolean contains(int support) { return support >= 329 && support <= 444; }
        },
        AT_445("445") {
            @Override boolean contains(int support) { return support == 445; }
        },
        AT_446("446") {
            @Override boolean contains(int support) { return support == 446; }
        },
        ABOVE_446(">=447") {
            @Override boolean contains(int support) { return support >= 447; }
        };

        private final String label;

        SupportBand(String label) {
            this.label = label;
        }

        abstract boolean contains(int support);
    }

    private record AuditedPattern(Pattern pattern, Kind kind, int support) {}

    private record WeightAudit(
            long totalOccurrences,
            TreeMap<Integer, Long> occurrences,
            TreeMap<Integer, Integer> sequenceSupports,
            long maximumOccurrence,
            long minimumOccurrence,
            double meanWeight) {

        private static WeightAudit from(RawDatabase database) {
            var occurrences = new TreeMap<Integer, Long>();
            var supports = new TreeMap<Integer, Integer>();
            long total = 0;
            for (var sequence : database.sequences()) {
                var inSequence = new HashSet<Integer>();
                for (var itemset : sequence) {
                    for (int itemId : itemset) {
                        occurrences.merge(itemId, 1L, Long::sum);
                        inSequence.add(itemId);
                        total++;
                    }
                }
                for (int itemId : inSequence) {
                    supports.merge(itemId, 1, Integer::sum);
                }
            }
            long max = occurrences.values().stream().mapToLong(Long::longValue).max().orElseThrow();
            long min = occurrences.values().stream().mapToLong(Long::longValue).min().orElseThrow();
            double maxWeight = (double) max / total;
            double minWeight = (double) min / total;
            return new WeightAudit(
                    total, occurrences, supports, max, min, (maxWeight + minWeight) / 2.0);
        }
    }

    private record Fraction(BigInteger numerator, BigInteger denominator)
            implements Comparable<Fraction> {

        private Fraction {
            if (denominator.signum() <= 0) {
                throw new IllegalArgumentException("denominator must be positive");
            }
        }

        private Fraction multiply(long factor) {
            return new Fraction(numerator.multiply(BigInteger.valueOf(factor)), denominator);
        }

        private BigDecimal toBigDecimal(MathContext context) {
            return new BigDecimal(numerator).divide(new BigDecimal(denominator), context);
        }

        @Override
        public int compareTo(Fraction other) {
            return numerator.multiply(other.denominator)
                    .compareTo(other.numerator.multiply(denominator));
        }
    }

    private record ProjectionEnumeration(
            Map<String, Integer> supports, long candidateCount, boolean truncated) {}

    private record ProjectedPattern(int[] items, int[] endpoints, int support) {

        private String serialize() {
            var value = new StringBuilder("<");
            for (int item : items) {
                value.append('(').append(item).append(')');
            }
            return value.append('>').toString();
        }
    }

    private record RawDatabase(
            List<List<Set<Integer>>> sequences,
            int multiItemItemsetCount,
            int duplicateWithinItemsetCount,
            int repeatedAcrossItemsetsSequenceCount,
            int emptyRecordCount) {

        private static RawDatabase read(Path path) throws IOException {
            var sequences = new ArrayList<List<Set<Integer>>>();
            int multiItemsets = 0;
            int duplicates = 0;
            int repeatedSequences = 0;
            int emptyRecords = 0;

            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine.strip();
                if (line.isEmpty()) {
                    emptyRecords++;
                    continue;
                }
                var sequence = new ArrayList<Set<Integer>>();
                var current = new LinkedHashSet<Integer>();
                var sequenceItems = new HashSet<Integer>();
                boolean repeated = false;
                boolean ended = false;
                for (String token : line.split("\\s+")) {
                    int value = Integer.parseInt(token);
                    if (value == -1) {
                        if (current.isEmpty()) {
                            throw new IllegalArgumentException("raw -1 closes an empty itemset");
                        }
                        if (current.size() > 1) {
                            multiItemsets++;
                        }
                        for (int itemId : current) {
                            if (!sequenceItems.add(itemId)) {
                                repeated = true;
                            }
                        }
                        sequence.add(Set.copyOf(current));
                        current.clear();
                    } else if (value == -2) {
                        if (!current.isEmpty() || sequence.isEmpty() || ended) {
                            throw new IllegalArgumentException("malformed raw sequence delimiter");
                        }
                        ended = true;
                    } else {
                        if (value < 0 || ended) {
                            throw new IllegalArgumentException("invalid raw item token: " + value);
                        }
                        if (!current.add(value)) {
                            duplicates++;
                        }
                    }
                }
                if (!ended) {
                    throw new IllegalArgumentException("raw sequence has no -2 terminator");
                }
                if (repeated) {
                    repeatedSequences++;
                }
                sequences.add(List.copyOf(sequence));
            }
            return new RawDatabase(
                    List.copyOf(sequences), multiItemsets, duplicates,
                    repeatedSequences, emptyRecords);
        }

        private List<Integer> supportingSids(Pattern pattern) {
            var result = new ArrayList<Integer>();
            for (int sequenceIndex = 0; sequenceIndex < sequences.size(); sequenceIndex++) {
                if (contains(sequences.get(sequenceIndex), pattern)) {
                    result.add(sequenceIndex + 1);
                }
            }
            return List.copyOf(result);
        }

        private static boolean contains(List<Set<Integer>> sequence, Pattern pattern) {
            int patternIndex = 0;
            for (var sourceItemset : sequence) {
                if (patternIndex == pattern.itemsets().size()) {
                    break;
                }
                boolean containsAll = true;
                for (var item : pattern.itemsets().get(patternIndex).items()) {
                    if (!sourceItemset.contains(item.id())) {
                        containsAll = false;
                        break;
                    }
                }
                if (containsAll) {
                    patternIndex++;
                }
            }
            return patternIndex == pattern.itemsets().size();
        }

        private ProjectionEnumeration enumerateSingletonItemsetPatterns(
                IntPredicate accepted, long maximumCandidates) {
            var items = new TreeSet<Integer>();
            for (var sequence : sequences) {
                for (var itemset : sequence) {
                    items.addAll(itemset);
                }
            }
            int maximumItemId = items.getLast();
            int[][] positions = new int[maximumItemId + 1][sequences.size()];
            for (int[] itemPositions : positions) {
                Arrays.fill(itemPositions, -1);
            }
            for (int sidIndex = 0; sidIndex < sequences.size(); sidIndex++) {
                var sequence = sequences.get(sidIndex);
                for (int position = 0; position < sequence.size(); position++) {
                    for (int itemId : sequence.get(position)) {
                        positions[itemId][sidIndex] = position;
                    }
                }
            }

            var f1 = new ArrayList<Integer>();
            var stack = new ArrayDeque<ProjectedPattern>();
            for (int itemId : items) {
                int support = 0;
                int[] endpoints = Arrays.copyOf(positions[itemId], sequences.size());
                for (int endpoint : endpoints) {
                    if (endpoint >= 0) {
                        support++;
                    }
                }
                if (accepted.test(support)) {
                    f1.add(itemId);
                    stack.push(new ProjectedPattern(new int[] {itemId}, endpoints, support));
                }
            }

            var result = new TreeMap<String, Integer>();
            long candidates = 0;
            while (!stack.isEmpty()) {
                var parent = stack.pop();
                result.put(parent.serialize(), parent.support());
                for (int itemId : f1) {
                    candidates++;
                    if (candidates > maximumCandidates) {
                        return new ProjectionEnumeration(
                                Map.copyOf(result), candidates, true);
                    }
                    int[] endpoints = new int[sequences.size()];
                    Arrays.fill(endpoints, -1);
                    int support = 0;
                    for (int sidIndex = 0; sidIndex < sequences.size(); sidIndex++) {
                        int prior = parent.endpoints()[sidIndex];
                        int next = positions[itemId][sidIndex];
                        if (prior >= 0 && next > prior) {
                            endpoints[sidIndex] = next;
                            support++;
                        }
                    }
                    if (accepted.test(support)) {
                        int[] patternItems = Arrays.copyOf(
                                parent.items(), parent.items().length + 1);
                        patternItems[patternItems.length - 1] = itemId;
                        stack.push(new ProjectedPattern(patternItems, endpoints, support));
                    }
                }
            }
            return new ProjectionEnumeration(Map.copyOf(result), candidates, false);
        }
    }
}
