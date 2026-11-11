import { readMiningRunAccepted, readMiningRunStatus, isRunId } from './mining-run-response';
import { acceptedRun, runId, runInput, runStatus, measuredRun, legacyMeasuredRun } from './mining-run-test-data';

it('decodes bound 202 and projects away unknown payload', () => {
  expect(readMiningRunAccepted({ ...acceptedRun(), privatePath: 'secret' }, runInput)).toEqual(acceptedRun());
});
it.each(['WSPM_PRETREE', 'WSPM_BITPRUNE'] as const)('preserves complete measured %s payload for UI presentation', algorithm => {
  const report = measuredRun(algorithm); expect(readMiningRunStatus(report, runId)).toEqual(report);
});
it('keeps legacy metrics without falsely assigning WDM-05.6 and rejects incomplete measured claims', () => {
  const legacy = legacyMeasuredRun(); expect(readMiningRunStatus(legacy, runId)).toEqual(legacy);
  const partial = measuredRun(); partial.metrics.dfsMiningMs = null;
  expect(() => readMiningRunStatus(partial, runId)).toThrow();
});
it.each([['runId', 'bad'], ['snapshotId', 'wrong'], ['algorithm', 'WSPM_BITPRUNE'], ['status', 'RUNNING'], ['createdAt', '2026-02-31T00:00:00Z'], ['createdAt', '2026-11-07T00:00:00Z\n']])('rejects invalid accepted %s', (key, value) => {
  expect(() => readMiningRunAccepted({ ...acceptedRun(), [key]: value }, runInput)).toThrow();
});
it.each(['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'LEGACY_UNVERIFIED'] as const)('decodes %s and preserves timestamps/nulls', state => {
  expect(readMiningRunStatus(runStatus(state), runId).createdAt).toBe('2026-11-07T05:00:00.123456Z');
});
it('retains full unsigned precision and zero without Number coercion', () => {
  const value = runStatus('SUCCEEDED'); value.candidateCount = '18446744073709551615'; value.metrics.totalExecutionMs = '9007199254740993';
  expect(readMiningRunStatus(value, runId).candidateCount).toBe(value.candidateCount);
  expect(readMiningRunStatus(value, runId).patternCount).toBe('0');
});
it.each(['18446744073709551616', '01', '-1', '1\n', '1.5', 1])('rejects invalid count %s', value => {
  expect(() => readMiningRunStatus({ ...runStatus(), patternCount: value }, runId)).toThrow();
});
it('binds run identity and validates scopes/config/nullable shapes', () => {
  for (const value of [{ ...runStatus(), runId: 'wrong' }, { ...runStatus(), status: 'OTHER' }, { ...runStatus(), metrics: {} },
    { ...runStatus(), configuration: null }, { ...runStatus(), startedAt: 'yesterday' },
    { ...runStatus(), measurement: { ...runStatus().measurement, heapScope: 'PRIVATE_RAM' } }]) expect(() => readMiningRunStatus(value, runId)).toThrow();
  expect(isRunId(runId + '\n')).toBe(false);
  const legacy = runStatus('LEGACY_UNVERIFIED'); legacy.configuration.minsupMode = 'LEGACY_V1';
  expect(readMiningRunStatus(legacy, runId).configuration.minsupMode).toBe('LEGACY_V1');
});
it('does not render unknown diagnostic text or private fields', () => {
  const raw = { ...runStatus('FAILED'), privatePath: 'secret', failure: { code: 'UNKNOWN', message: 'SQL /private secret' } };
  const decoded = readMiningRunStatus(raw, runId);
  expect(decoded.failure?.code).toBe('RUN_FAILED'); expect(JSON.stringify(decoded)).not.toContain('secret');
});
