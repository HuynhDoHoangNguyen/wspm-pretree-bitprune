package vn.edu.wspm.bitprune.mining;

import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;

/**
 * Mutable per-run statistics accumulator for WSPM_BitPrune.
 *
 * <p>
 * Collects extension events and enforces first-failure candidate routing
 * via an internal pending-candidate state machine. Checked arithmetic
 * ({@link Math#addExact(long, long)}) is used for every counter increment.
 *
 * <p>
 * This class is package-private to the mining engine and strictly unshared
 * across runs.
 * Once {@link #finish(long)} is invoked, the accumulator transitions to a
 * closed
 * state and produces an immutable {@link BitPruneStatistics} snapshot.
 */
final class BitPruneStatisticsAccumulator {

    private final long sequenceCount;
    private final long f1Count;

    private long generatedExtensions;
    private long generatedIExtensions;
    private long generatedSExtensions;
    private long cmapRejected;
    private long bitmapRejected;
    private long exactRejected;
    private long acceptedExtensions;
    private long expandedNodes;
    private long exactVerifications;

    private boolean candidatePending;
    private boolean finished;

    BitPruneStatisticsAccumulator(long sequenceCount, long f1Count) {
        if (sequenceCount < 0L) {
            throw new IllegalArgumentException("sequenceCount must not be negative: " + sequenceCount);
        }
        if (f1Count < 0L) {
            throw new IllegalArgumentException("f1Count must not be negative: " + f1Count);
        }
        this.sequenceCount = sequenceCount;
        this.f1Count = f1Count;
    }

    void recordGeneratedIExtension() {
        checkNotFinished();
        if (candidatePending) {
            throw new IllegalStateException("Cannot generate candidate: previous candidate is still pending");
        }
        long nextTotal = Math.addExact(generatedExtensions, 1L);
        long nextI = Math.addExact(generatedIExtensions, 1L);

        generatedExtensions = nextTotal;
        generatedIExtensions = nextI;
        candidatePending = true;
    }

    void recordGeneratedSExtension() {
        checkNotFinished();
        if (candidatePending) {
            throw new IllegalStateException("Cannot generate candidate: previous candidate is still pending");
        }
        long nextTotal = Math.addExact(generatedExtensions, 1L);
        long nextS = Math.addExact(generatedSExtensions, 1L);

        generatedExtensions = nextTotal;
        generatedSExtensions = nextS;
        candidatePending = true;
    }

    void recordCmapRejected() {
        checkNotFinished();
        checkCandidatePending();
        long nextCmap = Math.addExact(cmapRejected, 1L);

        cmapRejected = nextCmap;
        candidatePending = false;
    }

    void recordBitmapRejected() {
        checkNotFinished();
        checkCandidatePending();
        long nextBitmap = Math.addExact(bitmapRejected, 1L);

        bitmapRejected = nextBitmap;
        candidatePending = false;
    }

    void recordExactRejected() {
        checkNotFinished();
        checkCandidatePending();
        long nextExact = Math.addExact(exactRejected, 1L);
        long nextVerifications = Math.addExact(exactVerifications, 1L);

        exactRejected = nextExact;
        exactVerifications = nextVerifications;
        candidatePending = false;
    }

    void recordAcceptedExtension() {
        checkNotFinished();
        checkCandidatePending();
        long nextAccepted = Math.addExact(acceptedExtensions, 1L);
        long nextVerifications = Math.addExact(exactVerifications, 1L);

        acceptedExtensions = nextAccepted;
        exactVerifications = nextVerifications;
        candidatePending = false;
    }

    void recordExpandedNode() {
        checkNotFinished();
        long nextExpanded = Math.addExact(expandedNodes, 1L);
        expandedNodes = nextExpanded;
    }

    BitPruneStatistics finish(long patternCount) {
        checkNotFinished();
        if (candidatePending) {
            throw new IllegalStateException("Cannot finish accumulator while a candidate extension is pending");
        }
        finished = true;
        return new BitPruneStatistics(
                sequenceCount,
                f1Count,
                generatedExtensions,
                generatedIExtensions,
                generatedSExtensions,
                cmapRejected,
                bitmapRejected,
                exactRejected,
                acceptedExtensions,
                expandedNodes,
                patternCount,
                exactVerifications);
    }

    private void checkNotFinished() {
        if (finished) {
            throw new IllegalStateException("BitPruneStatisticsAccumulator is finished and closed for mutation");
        }
    }

    private void checkCandidatePending() {
        if (!candidatePending) {
            throw new IllegalStateException("No candidate extension is pending a terminal outcome");
        }
    }
}
