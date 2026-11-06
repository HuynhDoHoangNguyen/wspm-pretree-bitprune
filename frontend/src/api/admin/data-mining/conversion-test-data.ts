/** Synthetic conversion fixtures, imported only by tests. */
import { ConversionArtifact, ConversionReport, ConversionStatistics } from './conversion-response';
import { validationId } from './validation-test-data';
export function conversionData(): ConversionReport & { statistics: ConversionStatistics; sequence: ConversionArtifact; mapping: ConversionArtifact };
export function conversionData(status: ConversionReport['conversionStatus']): ConversionReport;
export function conversionData(status: ConversionReport['conversionStatus'] = 'SUCCEEDED'): ConversionReport {
  const base = '/api/v1/admin/dataset-snapshots/' + validationId + '/artifacts/';
  return { snapshotId: validationId, conversionStatus: status,
    statisticsStatus: status === 'SUCCEEDED' ? 'COMPUTED' : 'NOT_COMPUTED',
    startedAt: status === 'NOT_STARTED' ? null : '2026-11-05T01:02:03.123456Z',
    completedAt: status === 'SUCCEEDED' ? '2026-11-05T01:02:04Z' : null,
    failedAt: status === 'FAILED' ? '2026-11-05T01:02:04Z' : null,
    failureCode: status === 'FAILED' ? 'CONVERSION_FAILED' : null,
    artifactVersion: status === 'NOT_STARTED' ? null : 'CONVERSION_ARTIFACT_V1',
    statistics: status === 'SUCCEEDED' ? { sequenceCount: '1', singleItemsetSequenceCount: '0', multiItemsetSequenceCount: '1',
      itemsetCount: '3', distinctItemCount: '1', totalItemOccurrences: '3', minSequenceLength: '3', maxSequenceLength: '3', averageSequenceLength: 3 } : null,
    sequence: status === 'SUCCEEDED' ? { sha256: 'b'.repeat(64), sizeBytes: '18', downloadUrl: base + 'sequence' } : null,
    mapping: status === 'SUCCEEDED' ? { sha256: 'c'.repeat(64), sizeBytes: '2', downloadUrl: base + 'mapping' } : null };
}
