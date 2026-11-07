package vn.edu.wspm.backend.services.dataset.conversion;

/** Internal immutable publication metadata, never an HTTP DTO. Paths are private storage references. */
public record ConversionArtifactResult(String snapshotId, String attemptId, String validationSha256,
                                       ArtifactFile sequence, ArtifactFile mapping, Statistics statistics) {
    public record ArtifactFile(String reference, String sha256, long sizeBytes) {
        @Override public String toString() { return "ArtifactFile[sizeBytes=" + sizeBytes + "]"; }
    }
    public record Statistics(long sequenceCount, long singleItemsetSequences, long multiItemsetSequences,
                             long itemsetCount, long distinctItems, long totalItemOccurrences,
                             int minSequenceLength, int maxSequenceLength, double averageSequenceLength) {}
    @Override public String toString() { return "ConversionArtifactResult[snapshotId=" + snapshotId + "]"; }
}
