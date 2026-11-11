package vn.edu.wspm.backend.services.dataset.validation;

import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.dataset.csv.OrderCleaningPipeline;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared binding reader, extracted unchanged from 02.5; callers classify decoding failures at their boundary. */
public final class StoredValidationReport {
    private StoredValidationReport() {}

    public static ValidationReport read(ObjectMapper mapper, DatasetSnapshotEntity snapshot) {
        var root = snapshot.getConversionReportJson();
        if (root == null || !root.containsKey("validation")) return null;
        if (!(root.get("validation") instanceof Map<?, ?>)) throw new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE);
        var report = mapper.convertValue(root.get("validation"), ValidationReport.class);
        if (!ValidationReport.VERSION.equals(report.reportVersion()) || !"wdm-order-csv-v1".equals(report.contractVersion())
                || !OrderCleaningPipeline.POLICY_VERSION.equals(report.policyVersion())
                || !Objects.equals(snapshot.getSnapshotId(), report.snapshotId())
                || !Objects.equals(snapshot.getRawSha256(), report.rawSha256())
                || !Objects.equals(rawSize(snapshot), report.rawSizeBytes())
                || !Set.of("VALID", "INVALID").contains(report.validationStatus())
                || !Objects.equals(snapshot.getValidationStatus(), report.validationStatus())) {
            throw new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Stored validation does not match this snapshot and policy.");
        }
        return report;
    }

    public static String rawSize(DatasetSnapshotEntity snapshot) {
        var root = snapshot.getConversionReportJson();
        Object value = root != null && root.get("provenance") instanceof Map<?, ?> provenance ? provenance.get("rawSizeBytes") : null;
        if (!(value instanceof String size) || !size.matches("[1-9][0-9]*")) throw new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE);
        try { Long.parseLong(size); } catch (NumberFormatException invalid) { throw new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE); }
        return size;
    }

    public static void canonicalId(String id) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw ApplicationException.invalid("snapshotId", "Supply a canonical snapshot UUID.");
        }
    }
}
