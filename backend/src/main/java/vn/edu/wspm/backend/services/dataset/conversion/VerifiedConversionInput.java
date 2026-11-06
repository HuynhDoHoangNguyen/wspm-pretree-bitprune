package vn.edu.wspm.backend.services.dataset.conversion;

import vn.edu.wspm.backend.services.dataset.csv.ParsedCsvRecord;
import vn.edu.wspm.backend.services.dataset.validation.ValidationReport;
import java.util.List;

/** Internal point-in-time proof, not an execution reservation or HTTP DTO. No storage paths. */
public final class VerifiedConversionInput {
    private final byte[] raw;
    private final ValidationReport report;
    private final List<ParsedCsvRecord> keptRecords;
    private final Binding binding;

    public record Binding(String sourceType, String rawReference) {
        @Override public String toString() { return "ConversionInputBinding[redacted]"; }
    }

    VerifiedConversionInput(byte[] raw, ValidationReport report, List<ParsedCsvRecord> keptRecords) {
        this(raw, report, keptRecords, null);
    }
    VerifiedConversionInput(byte[] raw, ValidationReport report, List<ParsedCsvRecord> keptRecords, Binding binding) {
        this.raw = raw.clone();
        this.report = report;
        this.keptRecords = List.copyOf(keptRecords);
        this.binding = binding;
    }
    public byte[] rawBytes() { return raw.clone(); }
    public ValidationReport report() { return report; }
    public List<ParsedCsvRecord> keptRecords() { return keptRecords; }
    public Binding binding() { return binding; }
    @Override public String toString() { return "VerifiedConversionInput[keptRecordCount=" + keptRecords.size() + "]"; }
}
