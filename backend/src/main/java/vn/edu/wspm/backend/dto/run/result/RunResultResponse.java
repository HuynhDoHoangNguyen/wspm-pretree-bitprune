package vn.edu.wspm.backend.dto.run.result;

import java.time.Instant;
import java.util.List;

/** Public read models. Internal artifact references and engine outcomes are never exposed. */
public final class RunResultResponse {
    private RunResultResponse() {}
    public record Provenance(String version, String conversionFormatVersion, String snapshotId,
            String attemptId, String validationSha256, String sequenceSha256, String mappingSha256,
            String sequenceCount) {}
    public record Metadata(String runId, String executionStatus, String publicationStatus,
            String manifestVersion, String resultSha256, Instant committedAt, Provenance provenance,
            String storedPatternCount, String storedPatternItemCount, String storedRunItemCount,
            String completeness, boolean stagingReadable, boolean publishedReadable, boolean eligibleForCombo) {}
    public record PatternSummary(String patternId, String runId, String canonicalPattern, String canonicalPatternHash,
            String supportAbs, double supportRatio, double weightedSupport, double patternWeight,
            String patternWeightPolicy, int itemsetCount, int patternItemCount) {}
    public record Item(int itemOrder, int itemId, String productId, String productNameSnapshot,
            String skuSnapshot, double itemWeight, String occurrenceCount, String sequenceSupport) {}
    public record Itemset(int itemsetOrder, List<Item> items) {}
    public record PatternDetail(Metadata result, PatternSummary pattern, List<Itemset> itemsets) {}
    public record ExportPattern(PatternSummary pattern, List<Itemset> itemsets) {}
    public record RunItem(int itemId, String productId, String productNameSnapshot, String skuSnapshot,
            double itemWeight, String occurrenceCount, String sequenceSupport) {}
    public record PublishedBundle(Metadata result, List<ExportPattern> patterns, List<RunItem> runItems) {
        public PublishedBundle { patterns = List.copyOf(patterns); runItems = List.copyOf(runItems); }
    }
    public record PublishedExport(String formatVersion, String purpose, Metadata result, List<ExportPattern> patterns, List<RunItem> runItems) {}
    public record Page(Metadata result, List<PatternSummary> items, int page, int size, String total) {}
}
