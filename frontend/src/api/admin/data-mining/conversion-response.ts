import { WdmApiError } from './client';
import { isSnapshotId } from './dataset-response';

export const statisticKeys = ['sequenceCount', 'singleItemsetSequenceCount', 'multiItemsetSequenceCount', 'itemsetCount',
  'distinctItemCount', 'totalItemOccurrences', 'minSequenceLength', 'maxSequenceLength'] as const;
export type ConversionStatistics = Record<typeof statisticKeys[number], string> & { averageSequenceLength: number };
export interface ConversionArtifact { sha256: string; sizeBytes: string; downloadUrl: string }
export interface ConversionReport {
  snapshotId: string; conversionStatus: 'NOT_STARTED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  statisticsStatus: 'NOT_COMPUTED' | 'COMPUTED'; startedAt: string | null; completedAt: string | null;
  failedAt: string | null; failureCode: 'CONVERSION_FAILED' | null; artifactVersion: 'CONVERSION_ARTIFACT_V1' | null;
  statistics: ConversionStatistics | null; sequence: ConversionArtifact | null; mapping: ConversionArtifact | null;
}
function object(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}
function count(value: unknown): value is string { return typeof value === 'string' && /^(0|[1-9][0-9]*)$/.test(value); }
function instant(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value)) return false;
  const date = new Date(value);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 19) === value.slice(0, 19);
}
function invalid(): never { throw new WdmApiError('response', 200, 'INVALID_CONVERSION_RESPONSE', 'Conversion response could not be verified.'); }
function artifact(value: unknown, id: string, kind: 'sequence' | 'mapping'): ConversionArtifact {
  if (!object(value) || typeof value.sha256 !== 'string' || !/^[0-9a-f]{64}$/.test(value.sha256)
    || !count(value.sizeBytes) || value.sizeBytes === '0'
    || value.downloadUrl !== '/api/v1/admin/dataset-snapshots/' + id + '/artifacts/' + kind) return invalid();
  return { sha256: value.sha256, sizeBytes: value.sizeBytes, downloadUrl: value.downloadUrl };
}
/** Public projection only. Backend statistics are displayed without reimplementing their mathematics. */
export function readConversionReport(value: unknown, id: string): ConversionReport {
  if (!isSnapshotId(id) || !object(value) || value.snapshotId !== id) return invalid();
  const d = value;
  if (!['NOT_STARTED', 'RUNNING', 'SUCCEEDED', 'FAILED'].includes(d.conversionStatus as string)) return invalid();
  const state = d.conversionStatus as ConversionReport['conversionStatus'];
  if (state === 'NOT_STARTED' ? d.startedAt !== null || d.artifactVersion !== null
    : !instant(d.startedAt) || d.artifactVersion !== 'CONVERSION_ARTIFACT_V1') return invalid();
  if (state === 'FAILED' ? !instant(d.failedAt) || d.failureCode !== 'CONVERSION_FAILED'
    : d.failedAt !== null || d.failureCode !== null) return invalid();
  let statistics: ConversionStatistics | null = null, sequence: ConversionArtifact | null = null, mapping: ConversionArtifact | null = null;
  if (state === 'SUCCEEDED') {
    if (d.statisticsStatus !== 'COMPUTED' || !instant(d.completedAt) || !object(d.statistics)) return invalid();
    const source = d.statistics;
    if (!statisticKeys.every(key => count(source[key])) || typeof source.averageSequenceLength !== 'number'
      || !Number.isFinite(source.averageSequenceLength) || source.averageSequenceLength < 0) return invalid();
    const projected = { averageSequenceLength: source.averageSequenceLength } as ConversionStatistics;
    statisticKeys.forEach(key => { projected[key] = source[key] as string; });
    statistics = projected;
    sequence = artifact(d.sequence, id, 'sequence'); mapping = artifact(d.mapping, id, 'mapping');
  } else if (d.statisticsStatus !== 'NOT_COMPUTED' || d.completedAt !== null || d.statistics !== null || d.sequence !== null || d.mapping !== null) return invalid();
  return { snapshotId: id, conversionStatus: state, statisticsStatus: d.statisticsStatus as ConversionReport['statisticsStatus'],
    startedAt: d.startedAt as string | null, completedAt: d.completedAt as string | null, failedAt: d.failedAt as string | null,
    failureCode: d.failureCode as ConversionReport['failureCode'], artifactVersion: d.artifactVersion as ConversionReport['artifactVersion'],
    statistics, sequence, mapping };
}
