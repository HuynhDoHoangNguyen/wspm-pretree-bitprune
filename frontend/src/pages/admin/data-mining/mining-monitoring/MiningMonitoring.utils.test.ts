import type { MiningRunDetail } from '../../../../api/admin/data-mining/history/MiningRun.types';
import { elapsedMs, metric, safeExecutionError, statusPresentation, timestampText } from './MiningMonitoring.utils';


// A fixed observation instant keeps fixture durations reproducible; not live measurements.
const PREVIEW_OBSERVED_AT = Date.parse('2026-11-05T03:00:30Z');
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

const monitoringRuns: MiningRunDetail[] = [
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

const running = monitoringRuns[0];
test('elapsed time is derived from timestamps, not runtime metrics or a progress percentage', () => {
  expect(elapsedMs(running, PREVIEW_OBSERVED_AT)).toBe(20000);
  expect(elapsedMs(monitoringRuns[2], PREVIEW_OBSERVED_AT)).toBe(10000);
  expect(monitoringRuns[2].totalExecutionMs).toBe(9700);
  expect(elapsedMs({ ...running, startedAt: new Date(PREVIEW_OBSERVED_AT).toISOString() }, PREVIEW_OBSERVED_AT)).toBe(0);
});

test.each([null, '', 'not-a-timestamp', '2026-11-05T04:00:00Z'])(
  'missing, invalid or future start %s never yields a negative duration', startedAt => {
    expect(elapsedMs({ ...running, startedAt }, PREVIEW_OBSERVED_AT)).toBeNull();
  }
);

test('queued, legacy, missing finish, reversed timestamps and invalid clock have no elapsed value', () => {
  expect(elapsedMs({ ...running, status: 'QUEUED' }, PREVIEW_OBSERVED_AT)).toBeNull();
  expect(elapsedMs({ ...running, status: 'LEGACY_UNVERIFIED' }, PREVIEW_OBSERVED_AT)).toBeNull();
  expect(elapsedMs({ ...running, status: 'SUCCEEDED' }, PREVIEW_OBSERVED_AT)).toBeNull();
  expect(elapsedMs({ ...running, status: 'FAILED', finishedAt: '2026-11-05T03:00:00Z' }, PREVIEW_OBSERVED_AT)).toBeNull();
  expect(elapsedMs(running, NaN)).toBeNull();
});

test.each([null, undefined, -1, NaN, Infinity])('missing or invalid metric %s is not converted to zero', value => {
  expect(metric(value, 'ms')).toBe('Chưa có dữ liệu');
});

test('zero remains zero, timestamps are labelled honestly and private errors are never returned', () => {
  expect(metric(0, 'ms')).toBe('0 ms');
  expect(timestampText(null)).toBe('Chưa có dữ liệu');
  expect(timestampText('invalid')).toBe('Chưa có dữ liệu');
  expect(timestampText(running.startedAt)).toBe(running.startedAt);
  expect(safeExecutionError('java.lang.SecretException password=secret at /private/data')).not.toMatch(/SecretException|password|private/);
  expect(safeExecutionError(null)).toBe('RUN thất bại; chưa có thông báo lỗi công khai.');
  expect(Object.keys(statusPresentation)).toEqual(['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'LEGACY_UNVERIFIED']);
});
