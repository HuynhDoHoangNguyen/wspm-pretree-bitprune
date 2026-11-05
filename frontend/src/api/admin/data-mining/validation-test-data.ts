/** Synthetic contract examples shared only by validation tests. Never imported by application code. */
import { SnapshotDetail } from './types';
import { ValidationReport } from './validation-response';
export const validationId = '11111111-2222-4333-8444-555555555555';
export function pendingDetail(): SnapshotDetail {
  return { snapshotId: validationId, sourceType: 'CSV', sourceName: 'original.csv', validationStatus: 'PENDING',
    conversionStatus: 'NOT_STARTED', statisticsStatus: 'NOT_COMPUTED', rawSha256: 'a'.repeat(64), rawSizeBytes: '900',
    sequenceCount: null, fromTime: null, toTime: null, createdAt: '2026-11-05T00:00:00Z', rawAvailability: 'AVAILABLE',
    provenance: { contractVersion: 'wdm-order-csv-v1', acquisition: 'CSV_UPLOAD', sourceTimeZone: null,
      timeField: 'ordered_at', statusPolicy: 'COMPLETED_CURRENT_AT_ACQUISITION' } };
}
export function reportData(): ValidationReport {
  return { snapshotId: validationId, reportVersion: 'WDM_VALIDATION_REPORT_V1', contractVersion: 'wdm-order-csv-v1',
    policyVersion: 'WDM_ORDER_CLEANING_V1', rawSha256: 'a'.repeat(64), rawSizeBytes: '900', completedAt: '2026-11-05T01:02:03.123456Z',
    validationStatus: 'VALID', cleaningOutcome: 'READY', countsComplete: true,
    counts: { parsedRecords: '5', inputRecords: '5', inputOrders: '3', knownOrders: '3', businessInvalidRecords: '1',
      keptRecords: '1', rejectedRecords: '3', deduplicatedRecords: '1', blockedRecords: '0', keptOrders: '1', rejectedOrders: '2', issueCount: '3' },
    issues: { page: 0, size: 20, totalElements: '3', totalPages: '1', content: [
      { stage: 'BUSINESS', code: 'PRODUCT_NOT_FOUND', field: 'product_id', positionAccuracy: 'RECORD_RANGE',
        positions: [{ recordNumber: 2, startLine: 2, endLine: 3 }] },
      { stage: 'CONSISTENCY', code: 'ORDER_FIELD_CONFLICT', field: 'customer_code', positionAccuracy: 'RECORD_RANGE',
        positions: [{ recordNumber: 3, startLine: 4, endLine: 4 }, { recordNumber: 4, startLine: 5, endLine: 5 }] },
      { stage: 'CONSISTENCY', code: 'DUPLICATE_LINE_ID', field: 'order_item_id', positionAccuracy: 'RECORD_RANGE',
        positions: [{ recordNumber: 5, startLine: 6, endLine: 6 }, { recordNumber: 6, startLine: 7, endLine: 7 }] },
    ] } };
}
