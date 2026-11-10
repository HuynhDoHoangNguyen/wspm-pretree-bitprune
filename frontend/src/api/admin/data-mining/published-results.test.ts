import { createRunResultApi, readRunResultMetadata, readPatternDetail, readPatternPage, RunResultMetadata } from './run-results';
import { createWdmApiClient } from './client';
import { detailFixture, detailRunId } from './pattern-detail-test-data';

function published(): RunResultMetadata {
  return { ...detailFixture().result, publicationStatus: 'COMMITTED', manifestVersion: 'WDM_RESULT_MANIFEST_V1',
    resultSha256: 'a'.repeat(64), committedAt: '2026-11-08T00:00:00Z', completeness: 'COMPLETE_VERIFIED',
    stagingReadable: false, publishedReadable: true, eligibleForCombo: true };
}
test('verified publication is readable while empty results cannot be combo eligible', () => {
  expect(readRunResultMetadata(published(), detailRunId).publishedReadable).toBe(true);
  const empty = { ...published(), storedPatternCount: '0', storedPatternItemCount: '0', eligibleForCombo: false };
  expect(readRunResultMetadata(empty, detailRunId).eligibleForCombo).toBe(false);
  expect(() => readRunResultMetadata({ ...empty, eligibleForCombo: true }, detailRunId)).toThrow();
});
test.each([{ manifestVersion: 'unknown' }, { publicationStatus: 'NOT_PUBLISHED' }, { executionStatus: 'FAILED' },
  { completeness: 'UNVERIFIED' }, { stagingReadable: true }, { publishedReadable: false }])('rejects incompatible published claim %j', patch => {
  expect(() => readRunResultMetadata({ ...published(), ...patch }, detailRunId)).toThrow();
});
test('published detail and list preserve repeated temporal items and exact IDs', () => {
  const detail = { ...detailFixture(), result: published() };
  expect(readPatternDetail(detail, detailRunId, detail.pattern.patternId)).toEqual(detail);
  expect(readPatternPage({ result: published(), items: [detail.pattern], page: 1, size: 20, total: '1' }, detailRunId, 1).items[0].patternId)
    .toBe('18446744073709551615');
});
test('published download uses bounded authenticated client and checks purpose and result binding', async () => {
  const doc = { formatVersion: 'WDM_RESULT_EXPORT_V2', purpose: 'COMBO_HANDOFF', result: published(),
    patterns: [{ pattern: detailFixture().pattern, itemsets: detailFixture().itemsets }],
    runItems: [detailFixture().itemsets[0].items[0], detailFixture().itemsets[0].items[1], detailFixture().itemsets[2].items[0]] };
  const client = createWdmApiClient();
  const download = jest.spyOn(client, 'download').mockResolvedValue({ blob: { text: async () => JSON.stringify(doc) } as Blob,
    contentType: 'application/json', contentDisposition: null, requestId: 'export-request' });
  const signal = new AbortController().signal;
  expect((await createRunResultApi(client).export(detailRunId, 'published', signal)).filename).toContain(detailRunId);
  expect(download).toHaveBeenCalledWith('/v1/admin/mining-runs/' + detailRunId + '/patterns/export',
    expect.objectContaining({ query: { view: 'published' }, maxDownloadBytes: 64 * 1024 * 1024, signal }));
  doc.purpose = 'STAGING_INSPECTION';
  await expect(createRunResultApi(client).export(detailRunId, 'published')).rejects.toThrow();
});
