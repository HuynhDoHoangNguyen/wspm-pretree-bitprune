package vn.edu.wspm.backend.services.dataset.validation;

import vn.edu.wspm.backend.services.dataset.csv.OrderConsistencyIssue.Position;
import java.util.List;

/** Persisted safe metadata + complete record manifest; never includes raw values or private paths. */
public record ValidationReport(String reportVersion, String contractVersion, String policyVersion,
        String snapshotId, String rawSha256, String rawSizeBytes, String completedAt,
        String validationStatus, String cleaningOutcome, boolean countsComplete, Counts counts,
        List<Diagnostic> diagnostics, List<Decision> manifest) {
    public static final String VERSION = "WDM_VALIDATION_REPORT_V1";
    public record Counts(String parsedRecords, String inputRecords, String inputOrders, String knownOrders,
            String businessInvalidRecords, String keptRecords, String rejectedRecords, String deduplicatedRecords,
            String blockedRecords, String keptOrders, String rejectedOrders, String issueCount) {}
    public record Diagnostic(String stage, String code, String field, String positionAccuracy, List<Position> positions) {
        public Diagnostic { positions = List.copyOf(positions); }
    }
    public record Decision(Position position, String action, Position representative, List<String> reasons) {
        public Decision { reasons = List.copyOf(reasons); }
    }
    public ValidationReport {
        diagnostics = List.copyOf(diagnostics);
        manifest = List.copyOf(manifest);
    }
}
