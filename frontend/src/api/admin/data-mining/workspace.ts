import { createWdmApiClient, WdmApiError } from './client';
import { isRunId } from './mining-run-response';

export interface SnapshotRow { snapshotId: string; sourceName: string | null; sourceType: string; validationStatus: string; conversionStatus: string | null;
  sequenceCount: string | null; distinctItemCount: string | null; createdAt: string }
export interface RunRow { runId: string; snapshotId: string; sourceName: string | null; algorithm: string; status: string; minsupMode: string;
  configuredValue: number; patternCount: string | null; createdAt: string; startedAt: string | null; finishedAt: string | null }
export interface WorkspacePage<T> { items: T[]; page: number; size: number; total: number }
export interface StorageInfo { snapshotId: string; sourceName: string; root: string; binding: 'CURRENT_SNAPSHOT_PUBLICATION'; verification: 'METADATA_ONLY_NOT_INTEGRITY_VERIFIED';
  files: { kind: string; filename: string; reference: string; absolutePath: string; sha256: string | null }[] }
export interface CapacityInfo { enabled: boolean; scope: 'CURRENT_BACKEND_INSTANCE'; observedAt: string;
  snapshot: { capacity: number; used: number; available: number; closing: boolean; observedAt: string; entries: { runId: string; state: string }[] } | null }
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
// Live registry uses Instant (up to nanos); persisted DATETIME values use up to micros.
const timestamp = (v: unknown) => typeof v === 'string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?Z$/.test(v) && Number.isFinite(Date.parse(v));
const count = (v: unknown) => v === null || (typeof v === 'string' && /^(0|[1-9][0-9]*)$/.test(v) && v.length <= 20);
const name = (v: unknown) => v === null || typeof v === 'string';
const states = ['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'LEGACY_UNVERIFIED'];
function invalid(): never { throw new WdmApiError('response', 200, 'INVALID_WORKSPACE_RESPONSE', 'Workspace response could not be verified.'); }
const legacyId = (v: unknown) => typeof v === 'string' && !!v.trim() && v.length <= 40 && v.split('').every(ch => ch.charCodeAt(0) >= 32 && ch.charCodeAt(0) !== 127);
export function readWorkspacePage<T>(v: unknown, runs: boolean): WorkspacePage<T> {
  if (!object(v) || !Array.isArray(v.items) || !Number.isSafeInteger(v.page) || Number(v.page) < 1 || !Number.isSafeInteger(v.size)
    || Number(v.size) < 1 || Number(v.size) > 100 || !Number.isSafeInteger(v.total) || Number(v.total) < 0 || v.items.length > Number(v.size)) return invalid();
  for (const r of v.items) {
    if (!object(r)) return invalid();
    const legacy = (runs ? r.status : r.validationStatus) === 'LEGACY_UNVERIFIED';
    if (!(isRunId(r.snapshotId) || (legacy && legacyId(r.snapshotId))) || !name(r.sourceName) || !timestamp(r.createdAt)) return invalid();
    if (runs) {
      if (!(isRunId(r.runId) || (legacy && legacyId(r.runId))) || !['WSPM_PRETREE', 'WSPM_BITPRUNE'].includes(String(r.algorithm)) || !states.includes(String(r.status))
        || !['PERCENT', 'ABSOLUTE', 'LEGACY_COMPATIBILITY', 'LEGACY_V1'].includes(String(r.minsupMode))
        || typeof r.configuredValue !== 'number' || !Number.isFinite(r.configuredValue) || !count(r.patternCount)
        || !(r.startedAt === null || timestamp(r.startedAt)) || !(r.finishedAt === null || timestamp(r.finishedAt))) return invalid();
    } else if (!(['CSV', 'DATABASE'].includes(String(r.sourceType)) || (legacy && r.sourceType === 'LEGACY_PATH')) || !['PENDING', 'VALID', 'INVALID', 'LEGACY_UNVERIFIED'].includes(String(r.validationStatus))
      || !(r.conversionStatus === null || ['NOT_STARTED', 'RUNNING', 'SUCCEEDED', 'FAILED'].includes(String(r.conversionStatus))) || !count(r.sequenceCount) || !count(r.distinctItemCount)) return invalid();
  }
  return v as unknown as WorkspacePage<T>;
}
export function readCapacity(v: unknown): CapacityInfo {
  if (!object(v) || typeof v.enabled !== 'boolean' || v.scope !== 'CURRENT_BACKEND_INSTANCE' || !timestamp(v.observedAt)) return invalid();
  const s = v.snapshot;
  if (!v.enabled) { if (s !== null) return invalid(); }
  else {
    if (!object(s) || ![s.capacity, s.used, s.available].every(n => Number.isSafeInteger(n) && Number(n) >= 0) || Number(s.capacity) < 1
      || Number(s.used) + Number(s.available) !== s.capacity || typeof s.closing !== 'boolean' || !timestamp(s.observedAt) || !Array.isArray(s.entries) || s.entries.length !== s.used) return invalid();
    const ids = new Set();
    for (const e of s.entries) {
      if (!object(e) || !isRunId(e.runId) || ids.has(e.runId) || !['RESERVED', 'ACTIVATING', 'SUBMITTED', 'RUNNING', 'COMPLETED', 'FAILED', 'CLOSED'].includes(String(e.state))) return invalid();
      ids.add(e.runId);
    }
  }
  return v as unknown as CapacityInfo;
}
export function readStorage(v: unknown, id: string): StorageInfo {
  if (!object(v) || v.snapshotId !== id || typeof v.sourceName !== 'string' || typeof v.root !== 'string' || v.binding !== 'CURRENT_SNAPSHOT_PUBLICATION'
    || v.verification !== 'METADATA_ONLY_NOT_INTEGRITY_VERIFIED' || !Array.isArray(v.files) || v.files.length > 3) return invalid();
  const kinds = new Set();
  for (const f of v.files) {
    if (!object(f) || !['raw', 'sequence', 'mapping'].includes(String(f.kind)) || kinds.has(f.kind) || typeof f.filename !== 'string'
      || typeof f.reference !== 'string' || !f.reference.startsWith(id + '/') || typeof f.absolutePath !== 'string'
      || !(f.sha256 === null || (typeof f.sha256 === 'string' && /^[0-9a-f]{64}$/.test(f.sha256)))) return invalid();
    kinds.add(f.kind);
  }
  return v as unknown as StorageInfo;
}
export function createWorkspaceApi(client: ReturnType<typeof createWdmApiClient>) {
  return {
    snapshots: (query: Record<string, string>, signal?: AbortSignal) => client.get<WorkspacePage<SnapshotRow>>('/v1/admin/dataset-snapshots', { query, signal, expectedStatus: 200, decodeResponse: v => readWorkspacePage<SnapshotRow>(v, false) }),
    runs: (query: Record<string, string>, signal?: AbortSignal) => client.get<WorkspacePage<RunRow>>('/v1/admin/mining-runs', { query, signal, expectedStatus: 200, decodeResponse: v => readWorkspacePage<RunRow>(v, true) }),
    capacity: (signal?: AbortSignal) => client.get<CapacityInfo>('/v1/admin/mining-execution/capacity', { signal, expectedStatus: 200, decodeResponse: readCapacity }),
    storage: (id: string, signal?: AbortSignal) => {
      if (!isRunId(id)) throw new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Canonical snapshot ID required.');
      return client.get<StorageInfo>('/v1/admin/dataset-snapshots/' + id + '/storage', { signal, expectedStatus: 200, decodeResponse: v => readStorage(v, id) });
    },
  };
}
