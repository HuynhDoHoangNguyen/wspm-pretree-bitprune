import { WdmApiError } from './client';
import { DatasetSnapshotResponse, SnapshotDetail } from './types';

export const snapshotUuidPattern = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const snapshotId = new RegExp('^' + snapshotUuidPattern + '$');

export function isSnapshotId(value: unknown): value is string {
  return typeof value === 'string' && snapshotId.test(value);
}

function timestamp(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$/.test(value)
    && Number.isFinite(Date.parse(value));
}
function count(value: unknown): value is string {
  return typeof value === 'string' && /^(0|[1-9][0-9]*)$/.test(value);
}
function positiveCount(value: unknown): value is string {
  return count(value) && value !== '0';
}
function sourceName(value: unknown): value is string {
  return typeof value === 'string' && !!value.trim() && value.length <= 255;
}
function sha256(value: unknown): value is string {
  return typeof value === 'string' && /^[0-9a-f]{64}$/.test(value);
}
function nullable(value: unknown, allowed: readonly string[]): boolean {
  return value === null || (typeof value === 'string' && allowed.includes(value));
}

/** Validate each wire shape and project only its public fields. Counts remain exact strings. */
export function readSnapshotDetail(value: unknown, id: string): SnapshotDetail {
  const d = value as SnapshotDetail | null;
  if (!d || typeof d !== 'object' || d.snapshotId !== id || !timestamp(d.createdAt)
    || !nullable(d.sourceType, ['CSV', 'DATABASE']) || !(d.sourceName === null || sourceName(d.sourceName))
    || !nullable(d.validationStatus, ['PENDING', 'VALID', 'INVALID', 'LEGACY_UNVERIFIED'])
    || !nullable(d.conversionStatus, ['NOT_STARTED']) || !nullable(d.statisticsStatus, ['NOT_COMPUTED', 'COMPUTED'])
    || !(d.rawSha256 === null || sha256(d.rawSha256))
    || !(d.rawSizeBytes === null || positiveCount(d.rawSizeBytes))
    || !(d.sequenceCount === null || (d.statisticsStatus === 'COMPUTED' && count(d.sequenceCount)))
    || !(d.fromTime === null || timestamp(d.fromTime)) || !(d.toTime === null || timestamp(d.toTime))
    || !['AVAILABLE', 'MISSING', 'UNAVAILABLE'].includes(d.rawAvailability)
    || !d.provenance || typeof d.provenance !== 'object'
    || !nullable(d.provenance.contractVersion, ['wdm-order-csv-v1'])
    || !nullable(d.provenance.acquisition, ['CSV_UPLOAD', 'DATABASE_EXPORT']) || !nullable(d.provenance.sourceTimeZone, ['UTC'])
    || !nullable(d.provenance.timeField, ['ordered_at']) || !nullable(d.provenance.statusPolicy, ['COMPLETED_CURRENT_AT_ACQUISITION'])) {
    throw new WdmApiError('response', 200, 'INVALID_DETAIL_RESPONSE', 'Snapshot detail could not be verified.');
  }
  return { snapshotId: d.snapshotId, sourceType: d.sourceType, sourceName: d.sourceName,
    validationStatus: d.validationStatus, conversionStatus: d.conversionStatus, statisticsStatus: d.statisticsStatus,
    rawSha256: d.rawSha256, rawSizeBytes: d.rawSizeBytes, sequenceCount: d.sequenceCount,
    fromTime: d.fromTime, toTime: d.toTime, createdAt: d.createdAt, rawAvailability: d.rawAvailability,
    provenance: { contractVersion: d.provenance.contractVersion, acquisition: d.provenance.acquisition,
      sourceTimeZone: d.provenance.sourceTimeZone, timeField: d.provenance.timeField, statusPolicy: d.provenance.statusPolicy } };
}

export function readSnapshotReceipt(value: unknown): DatasetSnapshotResponse {
  const data = value as DatasetSnapshotResponse | null;
  if (!data || typeof data !== 'object'
    || !isSnapshotId(data.snapshotId)
    || data.sourceType !== 'CSV' || !sourceName(data.sourceName)
    || data.validationStatus !== 'PENDING' || data.conversionStatus !== 'NOT_STARTED' || data.statisticsStatus !== 'NOT_COMPUTED'
    || !sha256(data.rawSha256)
    || !positiveCount(data.rawSizeBytes)
    || data.sequenceCount !== null || data.fromTime !== null || data.toTime !== null
    || !timestamp(data.createdAt)) {
    throw new WdmApiError('response', 201, 'INVALID_SNAPSHOT_RESPONSE', 'Snapshot receipt could not be verified.');
  }
  return {
    snapshotId: data.snapshotId, sourceType: data.sourceType, sourceName: data.sourceName,
    validationStatus: data.validationStatus, conversionStatus: data.conversionStatus, statisticsStatus: data.statisticsStatus,
    rawSha256: data.rawSha256, rawSizeBytes: data.rawSizeBytes, sequenceCount: null,
    fromTime: null, toTime: null, createdAt: data.createdAt,
  };
}
