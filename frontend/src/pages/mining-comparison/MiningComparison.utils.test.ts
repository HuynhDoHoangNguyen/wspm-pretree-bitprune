import { compareRuns, difference, displayNumber } from './MiningComparison.utils';
import { comparisonFixture } from './MiningComparison.fixtures';

const a = comparisonFixture.runs[0];
const b = comparisonFixture.runs[1];
const evidence = comparisonFixture.previewChecks[0];

test('calculates runtime, heap and signed differences under equivalent synthetic conditions', () => {
  expect(compareRuns(a, b, evidence)).toMatchObject({ eligible: true, speedup: 2, runtimeReduction: 50, memoryReduction: 50 });
  expect(difference(a.totalExecutionMs, b.totalExecutionMs)).toBe(-600);
  expect(difference(a.peakSampledJvmHeapBytes, b.peakSampledJvmHeapBytes)).toBe(-33554432);
});

test.each([
  ['dataset', { snapshotId: 'another' }],
  ['unknown dataset', { snapshotId: null }],
  ['minsup', { minsupConfigured: 3 }],
  ['effective threshold', { effectiveThreshold: 3 }],
  ['zero effective threshold', { effectiveThreshold: 0 }],
  ['minsup mode', { minsupMode: 'RELATIVE', minsupRelativeFraction: 0.2 }],
  ['weight parameters', { meanW: 0.9 }],
  ['missing parameters', { maxW: null }],
  ['unfinished', { status: 'RUNNING' as const }],
  ['wrong algorithm', { algorithm: 'WSPM_PRETREE' as const }],
  ['pattern mismatch', { patternCount: 25 }],
  ['missing patterns', { patternCount: null }],
])('blocks derived metrics for %s', (_, changes) => {
  const result = compareRuns(a, { ...b, ...changes }, evidence);
  expect(result.eligible).toBe(false);
  expect(result.reasons.length).toBeGreaterThan(0);
  expect(result.speedup).toBeNull();
  expect(result.runtimeReduction).toBeNull();
  expect(result.memoryReduction).toBeNull();
});

test('matching counts do not prove output equality or measurement equivalence', () => {
  expect(compareRuns(a, b).eligible).toBe(false);
  expect(compareRuns(a, b, { ...evidence, fullPatternSupportMatch: null }).eligible).toBe(false);
  expect(compareRuns(a, b, { ...evidence, fullPatternSupportMatch: false }).eligible).toBe(false);
  expect(compareRuns(a, b, { ...evidence, measurementConditionsVerified: false }).eligible).toBe(false);
  expect(compareRuns(a, b, { ...evidence, configurationVerified: false }).eligible).toBe(false);
  expect(compareRuns(a, b, { ...evidence, bitPruneRunId: 'different-run' }).eligible).toBe(false);
});

test('identical invalid fractions still block comparison; zero weights are compared as source values', () => {
  expect(compareRuns({ ...a, minsupRelativeFraction: Infinity }, { ...b, minsupRelativeFraction: Infinity }, evidence).eligible).toBe(false);
  expect(compareRuns({ ...a, minW: 0 }, { ...b, minW: 0 }, evidence).eligible).toBe(true);
});

test.each([null, 0, -1, NaN, Infinity])('does not divide by zero or invalid runtime %s', value => {
  const result = compareRuns({ ...a, totalExecutionMs: value }, { ...b, totalExecutionMs: value }, evidence);
  expect(result.speedup).toBeNull();
  expect(result.runtimeReduction).toBeNull();
});

test.each([null, 0, -1, NaN, Infinity])('does not divide by invalid baseline heap %s', value => {
  expect(compareRuns({ ...a, peakSampledJvmHeapBytes: value }, b, evidence).memoryReduction).toBeNull();
});

test('zero BitPrune heap is a real zero and negative reductions remain negative', () => {
  expect(compareRuns(a, { ...b, peakSampledJvmHeapBytes: 0 }, evidence).memoryReduction).toBe(100);
  const slower = compareRuns(a, { ...b, totalExecutionMs: 1800, peakSampledJvmHeapBytes: a.peakSampledJvmHeapBytes! * 1.5 }, evidence);
  expect(slower.speedup).toBeCloseTo(2 / 3);
  expect(slower.runtimeReduction).toBe(-50);
  expect(slower.memoryReduction).toBe(-50);
});

test('guards nonfinite derived results and never formats NaN or Infinity', () => {
  expect(compareRuns({ ...a, totalExecutionMs: Number.MAX_VALUE }, { ...b, totalExecutionMs: Number.MIN_VALUE }, evidence).speedup).toBeNull();
  expect(difference(null, 10)).toBeNull();
  expect(difference(Infinity, 10)).toBeNull();
  [null, undefined, NaN, Infinity, -Infinity].forEach(value => expect(displayNumber(value)).toBe('Chưa có dữ liệu'));
  expect(displayNumber(0, 'ms')).toBe('0 ms');
});
