package vn.edu.wspm.backend.dto.dataset;

import vn.edu.wspm.backend.services.dataset.validation.ValidationReport;
import java.util.List;

/** Explicit projection: internal record manifest is never sent to the browser. */
public record ValidationResponse(String snapshotId, String reportVersion, String contractVersion,
        String policyVersion, String rawSha256, String rawSizeBytes, String completedAt,
        String validationStatus, String cleaningOutcome, boolean countsComplete,
        ValidationReport.Counts counts, DiagnosticPage issues) {
    public record DiagnosticPage(List<ValidationReport.Diagnostic> content, int page, int size,
                                 String totalElements, String totalPages) {
        public DiagnosticPage { content = List.copyOf(content); }
    }
    public static ValidationResponse page(ValidationReport report, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid diagnostic page");
        int total = report.diagnostics().size();
        int from = (int) Math.min((long) page * size, total);
        return new ValidationResponse(report.snapshotId(), report.reportVersion(), report.contractVersion(),
                report.policyVersion(), report.rawSha256(), report.rawSizeBytes(), report.completedAt(),
                report.validationStatus(), report.cleaningOutcome(), report.countsComplete(), report.counts(),
                new DiagnosticPage(report.diagnostics().subList(from, Math.min(from + size, total)), page, size,
                        Integer.toString(total), Long.toString((total + (long) size - 1) / size)));
    }
}
