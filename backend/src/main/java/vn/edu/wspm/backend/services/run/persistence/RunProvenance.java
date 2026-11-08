package vn.edu.wspm.backend.services.run.persistence;

import java.util.Map;
import java.util.Objects;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;
import vn.edu.wspm.backend.services.run.PreparedRun;

/** Immutable input descriptors only. No raw bytes, catalog text, weights or sequence database. */
public record RunProvenance(String version, String conversionFormatVersion, Map<String, String> policies,
                            PreparedRun.Binding binding) {
    public static final String VERSION = "RUN_PROVENANCE_V1";

    public RunProvenance {
        if (!VERSION.equals(version) || !ConversionArtifactCodec.VERSION.equals(conversionFormatVersion)
                || !ConversionArtifactCodec.POLICIES.equals(policies)) throw invalid();
        policies = Map.copyOf(policies);
        Objects.requireNonNull(binding, "binding is required");
        var p = Objects.requireNonNull(binding.publication(), "publication is required");
        text(p.snapshotId(), 40); text(p.attemptId(), 40); hash(p.validationSha256());
        if (!java.util.Set.of("CSV", "DATABASE").contains(binding.sourceType())) throw invalid();
        text(binding.sourceName(), 255); text(binding.rawReference(), 1024); hash(binding.rawSha256());
        for (var file : java.util.List.of(p.sequence(), p.mapping())) {
            text(file.reference(), 1024); hash(file.sha256());
            if (file.sizeBytes() <= 0) throw invalid();
        }
        var s = Objects.requireNonNull(p.statistics(), "statistics are required");
        if (s.sequenceCount() <= 0 || s.sequenceCount() > Integer.MAX_VALUE
                || s.distinctItems() <= 0 || s.distinctItems() > Integer.MAX_VALUE
                || s.singleItemsetSequences() < 0 || s.multiItemsetSequences() < 0
                || s.singleItemsetSequences() > s.sequenceCount()
                || s.multiItemsetSequences() != s.sequenceCount() - s.singleItemsetSequences()
                || s.itemsetCount() < s.sequenceCount() || s.totalItemOccurrences() < s.itemsetCount()
                || s.minSequenceLength() <= 0 || s.maxSequenceLength() < s.minSequenceLength()
                || !Double.isFinite(s.averageSequenceLength()) || s.averageSequenceLength() <= 0) throw invalid();
    }

    public static RunProvenance from(PreparedRun input) {
        Objects.requireNonNull(input);
        return new RunProvenance(VERSION, ConversionArtifactCodec.VERSION, ConversionArtifactCodec.POLICIES, input.binding());
    }

    private static void text(String value, int limit) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > limit) throw invalid();
    }
    private static void hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid run provenance"); }
    @Override public String toString() { return "RunProvenance[redacted]"; }
}
