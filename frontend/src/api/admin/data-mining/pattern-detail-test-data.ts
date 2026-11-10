import { PatternDetail, RunResultMetadata } from './run-results';
export const detailRunId = '10000000-0000-4000-8000-000000000001';
export const detailPatternId = '18446744073709551615';
export const detailMetadata = (): RunResultMetadata => ({ runId: detailRunId, executionStatus: 'SUCCEEDED', publicationStatus: 'NOT_PUBLISHED',
  manifestVersion: null, resultSha256: null, committedAt: null, provenance: { version: 'RUN_PROVENANCE_V1', conversionFormatVersion: 'CONVERSION_ARTIFACT_V1',
    snapshotId: detailRunId, attemptId: detailRunId, validationSha256: 'a'.repeat(64), sequenceSha256: 'b'.repeat(64), mappingSha256: 'c'.repeat(64), sequenceCount: '4' },
  storedPatternCount: '1', storedPatternItemCount: '4', storedRunItemCount: '3', completeness: 'UNVERIFIED', stagingReadable: true, publishedReadable: false, eligibleForCombo: false });
export function detailFixture(): PatternDetail {
  return { result: detailMetadata(), pattern: { runId: detailRunId, patternId: detailPatternId, canonicalPattern: '<(1 2)(1)(3)>', canonicalPatternHash: 'd'.repeat(64),
    supportAbs: '2', supportRatio: 0.5, weightedSupport: 1.5, patternWeight: 0.5, patternWeightPolicy: 'EQ2_EQ3_ARITHMETIC_MEAN_V1', itemsetCount: 3, patternItemCount: 4 },
  itemsets: [[1, 2], [1], [3]].map((ids, set) => ({ itemsetOrder: set + 1, items: ids.map((id, index) => ({ itemOrder: index + 1, itemId: id,
    productId: 'product-' + id, productNameSnapshot: 'Historical product ' + id, skuSnapshot: 'OLD-SKU-' + id,
    itemWeight: 0.5, occurrenceCount: '9007199254740993', sequenceSupport: '3' })) })) };
}
