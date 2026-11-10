import { MiningRunDetail } from '../../../../api/admin/data-mining/history/MiningRun.types';
import { MiningComparisonData } from './MiningComparison.types';

// Synthetic RUNs only; neither timings nor equivalence assumptions are research evidence.
const base: MiningRunDetail = {
  runId: 'preview-pretree', algorithm: 'WSPM_PRETREE', status: 'SUCCEEDED',
  datasetSource: 'UPLOAD', snapshotId: 'preview-snapshot-a', minsupMode: 'ABSOLUTE',
  minsupConfigured: 2, effectiveThreshold: 2, minsupRelativeFraction: null,
  maxW: 1, minW: 0.2, meanW: 0.6, patternCount: 24, candidateCount: 70,
  totalExecutionMs: 1200, peakSampledJvmHeapBytes: 67108864,
  createdAt: '2026-11-05T02:00:00Z', startedAt: '2026-11-05T02:00:01Z', finishedAt: '2026-11-05T02:00:03Z',
  datasetIoMs: 100, preprocessingMs: 150, dfsMiningMs: 800, completeAlgorithmMs: 950, outputIoMs: 100,
  errorMessage: null, sequenceCount: 100, distinctItemCount: 12, validationStatus: 'VALID',
};
const bit = (runId: string, changes: Partial<MiningRunDetail> = {}): MiningRunDetail => ({
  ...base, runId, algorithm: 'WSPM_BITPRUNE', totalExecutionMs: 600, peakSampledJvmHeapBytes: 33554432,
  preprocessingMs: 200, dfsMiningMs: 250, completeAlgorithmMs: 450, ...changes,
});

export const comparisonFixture: MiningComparisonData = {
  runs: [base,
    bit('preview-bitprune'),
    bit('preview-other-dataset', { snapshotId: 'preview-snapshot-b' }),
    bit('preview-other-minsup', { minsupConfigured: 3, effectiveThreshold: 3 }),
    bit('preview-other-parameters', { meanW: 0.7 }),
    bit('preview-running', { status: 'RUNNING', finishedAt: null, totalExecutionMs: null, peakSampledJvmHeapBytes: null }),
    bit('preview-missing-metrics', { totalExecutionMs: null, peakSampledJvmHeapBytes: null }),
    bit('preview-zero', { totalExecutionMs: 0, peakSampledJvmHeapBytes: 0 }),
    bit('preview-pattern-mismatch', { patternCount: 25 }),
    bit('preview-slower', { totalExecutionMs: 1800, peakSampledJvmHeapBytes: 100663296 }),
    bit('preview-no-evidence'),
    { ...base, runId: 'preview-zero-baseline', totalExecutionMs: 0, peakSampledJvmHeapBytes: 0 },
  ],
  // Explicit assumptions for demonstrating UI gates, never inferred from DTO equality.
  previewChecks: [],
};
comparisonFixture.previewChecks = comparisonFixture.runs.filter(run => run.algorithm === 'WSPM_BITPRUNE' && run.runId !== 'preview-no-evidence').flatMap(run =>
  ['preview-pretree', 'preview-zero-baseline'].map(preTreeRunId => ({
    origin: 'PREVIEW_ONLY' as const, preTreeRunId, bitPruneRunId: run.runId,
    configurationVerified: true, measurementConditionsVerified: true,
    fullPatternSupportMatch: run.runId !== 'preview-pattern-mismatch',
  }))
);
