import type { MiningRunDetail } from '../mining-run/MiningRun.types';

// A fixed observation instant keeps fixture durations reproducible; not live measurements.
export const PREVIEW_OBSERVED_AT = Date.parse('2026-11-05T03:00:30Z');
const base: MiningRunDetail = {
  runId: 'monitor-running', algorithm: 'WSPM_BITPRUNE', status: 'RUNNING',
  datasetSource: 'UPLOAD', snapshotId: 'preview-snapshot-monitoring',
  minsupMode: 'ABSOLUTE', minsupConfigured: 2, effectiveThreshold: 2,
  minsupRelativeFraction: null, maxW: 1, minW: 0.2, meanW: 0.6,
  patternCount: null, candidateCount: null, totalExecutionMs: null, peakSampledJvmHeapBytes: null,
  createdAt: '2026-11-05T03:00:00Z', startedAt: '2026-11-05T03:00:10Z', finishedAt: null,
  datasetIoMs: null, preprocessingMs: null, dfsMiningMs: null, completeAlgorithmMs: null, outputIoMs: null,
  sequenceCount: 100, distinctItemCount: 12, validationStatus: 'VALID', errorMessage: null,
};

export const monitoringRuns: MiningRunDetail[] = [
  base,
  { ...base, runId: 'monitor-queued', algorithm: 'WSPM_PRETREE', status: 'QUEUED', startedAt: null },
  { ...base, runId: 'monitor-succeeded', status: 'SUCCEEDED', finishedAt: '2026-11-05T03:00:20Z',
    totalExecutionMs: 9700, patternCount: 24, candidateCount: 65, peakSampledJvmHeapBytes: 33554432,
    datasetIoMs: 100, preprocessingMs: 500, dfsMiningMs: 8800, completeAlgorithmMs: 9300, outputIoMs: 100 },
  { ...base, runId: 'monitor-failed', status: 'FAILED', finishedAt: '2026-11-05T03:00:15Z',
    totalExecutionMs: 4900, errorMessage: 'RUN có thông tin lỗi được ghi nhận; chi tiết chẩn đoán không được công bố qua API.' },
  { ...base, runId: 'monitor-missing-timestamp', startedAt: null },
  { ...base, runId: 'monitor-missing-metrics', status: 'SUCCEEDED', finishedAt: '2026-11-05T03:00:20Z' },
  { ...base, runId: 'monitor-future-start', startedAt: '2026-11-05T04:00:00Z' },
];
