import { MiningRequest, MiningValidation } from './mining-response';
import { MiningRunAccepted, MiningRunState, MiningRunStatus } from './mining-run-response';

/** Synthetic protocol fixtures only; not engine-output evidence. */
export const runId = '11111111-2222-4333-8444-555555555555';
export const runInput: MiningRequest = { snapshotId: runId, algorithm: 'WSPM_PRETREE', minsupMode: 'PERCENT', minsupValue: 14 };
export function acceptedRun(): MiningRunAccepted { return { runId, snapshotId: runId, algorithm: 'WSPM_PRETREE', status: 'QUEUED', createdAt: '2026-11-07T05:00:00.123456Z' }; }
export function validatedRun(): MiningValidation { return { snapshotId: runId, algorithm: 'WSPM_PRETREE', artifactVersion: 'CONVERSION_ARTIFACT_V1',
  sequenceSha256: 'a'.repeat(64), mappingSha256: 'b'.repeat(64), minimumSupport: { mode: 'PERCENT', configuredValue: 14, relativeFraction: .14, effectiveThreshold: .42 },
  sequenceCount: '3', configurationStatus: 'SUPPORTED' }; }
export function runStatus(status: MiningRunState = 'RUNNING'): MiningRunStatus { return { ...acceptedRun(), status,
  configuration: { minsupMode: 'PERCENT', configuredValue: 14, relativeFraction: .14, effectiveThreshold: .42, maxW: .6, minW: .4, meanW: .5 },
  startedAt: null, finishedAt: null, patternCount: status === 'SUCCEEDED' ? '0' : null, candidateCount: null,
  metrics: { datasetIoMs: null, preprocessingMs: null, dfsMiningMs: null, completeAlgorithmMs: null, outputIoMs: null, totalExecutionMs: null, peakSampledJvmHeapBytes: null },
  measurement: { contractVersion: null, timeUnit: 'MILLISECONDS', memoryUnit: 'BYTES', datasetIoScope: null, totalExecutionScope: null, heapScope: null },
  failure: status === 'FAILED' ? { code: 'RUN_FAILED', message: 'Run failed.' } : null }; }

export function measuredRun(algorithm: MiningRequest['algorithm'] = 'WSPM_PRETREE'): MiningRunStatus {
  return { ...runStatus('SUCCEEDED'), algorithm, patternCount: '0', candidateCount: '9007199254740993',
    metrics: { datasetIoMs: '0', preprocessingMs: '2', dfsMiningMs: '3', completeAlgorithmMs: '6', outputIoMs: null,
      totalExecutionMs: '10', peakSampledJvmHeapBytes: '18446744073709551615' },
    measurement: { contractVersion: 'WDM-05.6', timeUnit: 'MILLISECONDS', memoryUnit: 'BYTES',
      datasetIoScope: 'WORKER_VERIFIED_RAW_SEQUENCE_MAPPING_READS', totalExecutionScope: 'POST_CLAIM_REFRESH_THROUGH_ENGINE_RETURN',
      heapScope: 'ALGORITHM_JVM_WIDE_USED_HEAP_SAMPLING' } };
}
export function legacyMeasuredRun(): MiningRunStatus {
  const measured = measuredRun();
  return { ...measured, status: 'LEGACY_UNVERIFIED', configuration: { ...measured.configuration, minsupMode: 'LEGACY_V1' },
    measurement: runStatus().measurement };
}
