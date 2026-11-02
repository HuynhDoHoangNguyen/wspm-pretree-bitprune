package vn.edu.wspm.mining;

/** Immutable execution statistics for one completed WSPM_PreTree mining run. */
public record WspmMiningStatistics(
        int sequenceCount,
        int f1Count,
        long candidateCount,
        long acceptedCandidateCount,
        long rejectedCandidateCount,
        long iCandidateCount,
        long sCandidateCount,
        long expandedNodeCount,
        int patternCount,
        long elapsedNanos) {

    public WspmMiningStatistics {
        requireNonNegative(sequenceCount, "sequence count");
        requireNonNegative(f1Count, "F1 count");
        requireNonNegative(candidateCount, "candidate count");
        requireNonNegative(acceptedCandidateCount, "accepted candidate count");
        requireNonNegative(rejectedCandidateCount, "rejected candidate count");
        requireNonNegative(iCandidateCount, "I-candidate count");
        requireNonNegative(sCandidateCount, "S-candidate count");
        requireNonNegative(expandedNodeCount, "expanded node count");
        requireNonNegative(patternCount, "pattern count");
        requireNonNegative(elapsedNanos, "elapsed nanoseconds");

        if (candidateCount != Math.addExact(iCandidateCount, sCandidateCount)) {
            throw new IllegalArgumentException(
                    "candidate count must equal I-candidate count plus S-candidate count");
        }
        if (candidateCount
                != Math.addExact(acceptedCandidateCount, rejectedCandidateCount)) {
            throw new IllegalArgumentException(
                    "candidate count must equal accepted plus rejected candidate counts");
        }
        if (patternCount != Math.addExact((long) f1Count, acceptedCandidateCount)) {
            throw new IllegalArgumentException(
                    "pattern count must equal F1 count plus accepted candidate count");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
