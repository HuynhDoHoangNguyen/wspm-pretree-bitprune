import { WdmApiError } from './client';
import { MiningRequest } from './mining-response';

export type MiningRunState = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'LEGACY_UNVERIFIED';
export interface MiningRunAccepted { runId: string; snapshotId: string; algorithm: MiningRequest['algorithm']; status: 'QUEUED'; createdAt: string }
export interface MiningRunStatus {
  runId: string; snapshotId: string; algorithm: MiningRequest['algorithm']; status: MiningRunState;
  configuration: { minsupMode: string; configuredValue: number | null; relativeFraction: number | null; effectiveThreshold: number | null;
    maxW: number | null; minW: number | null; meanW: number | null };
  createdAt: string; startedAt: string | null; finishedAt: string | null; patternCount: string | null; candidateCount: string | null;
  metrics: { datasetIoMs: string | null; preprocessingMs: string | null; dfsMiningMs: string | null; completeAlgorithmMs: string | null;
    outputIoMs: string | null; totalExecutionMs: string | null; peakSampledJvmHeapBytes: string | null };
  measurement: { contractVersion: 'WDM-05.6' | null; timeUnit: 'MILLISECONDS'; memoryUnit: 'BYTES'; datasetIoScope: string | null;
    totalExecutionScope: string | null; heapScope: string | null };
  failure: { code: string; message: string } | null;
}
export const runFailureMessages: Record<string, string> = {
  ADMISSION_FAILED: 'Chưa thể tiếp nhận lần chạy.', CLAIM_NOT_CONFIRMED: 'Chưa xác nhận được quyền thực thi.',
  INPUT_REVERIFICATION_FAILED: 'Không thể xác minh lại dữ liệu đầu vào.', ENGINE_EXECUTION_FAILED: 'Thực thi khai phá thất bại.',
  EXECUTION_LEASE_EXPIRED: 'Quyền thực thi đã hết hạn.', RUN_FAILED: 'Lần chạy thất bại.',
};
export const isRunId = (v: unknown): v is string => typeof v === 'string' && v.length === 36 && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const number = (v: unknown): v is number | null => v === null || (typeof v === 'number' && Number.isFinite(v));
const utc = (v: unknown): v is string => typeof v === 'string' && v.trim() === v && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,6})?Z$/.test(v)
  && Number.isFinite(Date.parse(v)) && new Date(v).toISOString().slice(0, 19) === v.slice(0, 19);
const timestamp = (v: unknown): v is string | null => v === null || utc(v);
const count = (v: unknown): v is string | null => v === null || (typeof v === 'string' && v.trim() === v && /^(0|[1-9][0-9]*)$/.test(v)
  && (v.length < 20 || (v.length === 20 && v <= '18446744073709551615')));
const algorithm = (v: unknown): v is MiningRequest['algorithm'] => v === 'WSPM_PRETREE' || v === 'WSPM_BITPRUNE';
function invalid(status: number): never { throw new WdmApiError('response', status, 'INVALID_RUN_RESPONSE', 'Run response could not be confirmed.'); }
export function readMiningRunAccepted(v: unknown, request: MiningRequest): MiningRunAccepted {
  if (!object(v) || !isRunId(v.runId) || v.snapshotId !== request.snapshotId || v.algorithm !== request.algorithm || v.status !== 'QUEUED' || !utc(v.createdAt)) return invalid(202);
  return { runId: v.runId, snapshotId: request.snapshotId, algorithm: request.algorithm, status: 'QUEUED', createdAt: v.createdAt };
}
export function readMiningRunStatus(v: unknown, id: string): MiningRunStatus {
  if (!object(v) || !isRunId(id) || v.runId !== id || !isRunId(v.snapshotId) || !algorithm(v.algorithm)
    || !['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'LEGACY_UNVERIFIED'].includes(v.status as string)
    || !utc(v.createdAt) || !timestamp(v.startedAt) || !timestamp(v.finishedAt) || !count(v.patternCount) || !count(v.candidateCount)
    || !object(v.configuration) || !object(v.metrics) || !object(v.measurement)) return invalid(200);
  const c = v.configuration, m = v.metrics, d = v.measurement;
  if (typeof c.minsupMode !== 'string' || !['PERCENT', 'ABSOLUTE', 'LEGACY_COMPATIBILITY', 'LEGACY_V1'].includes(c.minsupMode)
    || !['configuredValue', 'relativeFraction', 'effectiveThreshold', 'maxW', 'minW', 'meanW'].every(k => number(c[k]))
    || !['datasetIoMs', 'preprocessingMs', 'dfsMiningMs', 'completeAlgorithmMs', 'outputIoMs', 'totalExecutionMs', 'peakSampledJvmHeapBytes'].every(k => count(m[k]))
    || ![null, 'WDM-05.6'].includes(d.contractVersion as null | string) || d.timeUnit !== 'MILLISECONDS' || d.memoryUnit !== 'BYTES') return invalid(200);
  const scopes = ['WORKER_VERIFIED_RAW_SEQUENCE_MAPPING_READS', 'POST_CLAIM_REFRESH_THROUGH_ENGINE_RETURN', 'ALGORITHM_JVM_WIDE_USED_HEAP_SAMPLING'];
  if (!['datasetIoScope', 'totalExecutionScope', 'heapScope'].every((key, i) => d[key] === (d.contractVersion === null ? null : scopes[i]))) return invalid(200);
  if (d.contractVersion === 'WDM-05.6' && (v.patternCount === null || v.candidateCount === null
    || !['datasetIoMs', 'preprocessingMs', 'dfsMiningMs', 'completeAlgorithmMs', 'totalExecutionMs', 'peakSampledJvmHeapBytes'].every(k => m[k] !== null))) return invalid(200);
  let failure: MiningRunStatus['failure'] = null;
  if (v.status === 'FAILED') {
    if (!object(v.failure) || typeof v.failure.code !== 'string' || typeof v.failure.message !== 'string') return invalid(200);
    const code = Object.prototype.hasOwnProperty.call(runFailureMessages, v.failure.code) ? v.failure.code : 'RUN_FAILED';
    failure = { code, message: runFailureMessages[code] }; // Never render untrusted backend diagnostic text.
  } else if (v.failure !== null) return invalid(200);
  return { runId: id, snapshotId: v.snapshotId, algorithm: v.algorithm, status: v.status as MiningRunState,
    configuration: { minsupMode: c.minsupMode, configuredValue: c.configuredValue as number | null, relativeFraction: c.relativeFraction as number | null,
      effectiveThreshold: c.effectiveThreshold as number | null, maxW: c.maxW as number | null, minW: c.minW as number | null, meanW: c.meanW as number | null },
    createdAt: v.createdAt, startedAt: v.startedAt, finishedAt: v.finishedAt, patternCount: v.patternCount, candidateCount: v.candidateCount,
    metrics: { datasetIoMs: m.datasetIoMs as string | null, preprocessingMs: m.preprocessingMs as string | null, dfsMiningMs: m.dfsMiningMs as string | null,
      completeAlgorithmMs: m.completeAlgorithmMs as string | null, outputIoMs: m.outputIoMs as string | null, totalExecutionMs: m.totalExecutionMs as string | null,
      peakSampledJvmHeapBytes: m.peakSampledJvmHeapBytes as string | null },
    measurement: { contractVersion: d.contractVersion as 'WDM-05.6' | null, timeUnit: 'MILLISECONDS', memoryUnit: 'BYTES',
      datasetIoScope: d.datasetIoScope as string | null, totalExecutionScope: d.totalExecutionScope as string | null, heapScope: d.heapScope as string | null }, failure };
}
