package vn.edu.wspm.backend.dto.dataset;

import java.util.List;

/** Explicit projection: internal record manifest is never sent to the browser. */
public record ValidationResponse(String snapshotId, String reportVersion, String contractVersion,
        String policyVersion, String rawSha256, String rawSizeBytes, String completedAt,
        String validationStatus, String cleaningOutcome, boolean countsComplete,
        Counts counts, DiagnosticPage issues) {
    public record Counts(String parsedRecords, String inputRecords, String inputOrders, String knownOrders,
            String businessInvalidRecords, String keptRecords, String rejectedRecords, String deduplicatedRecords,
            String blockedRecords, String keptOrders, String rejectedOrders, String issueCount) {}
    public record Position(long recordNumber, long startLine, long endLine) {}
    public record Diagnostic(String stage, String code, String field, String positionAccuracy, List<Position> positions) {
        public Diagnostic { positions = List.copyOf(positions); }
    }
    public record DiagnosticPage(List<Diagnostic> content, int page, int size,
                                 String totalElements, String totalPages) {
        public DiagnosticPage { content = List.copyOf(content); }
    }
}
