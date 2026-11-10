/** @jest-environment node */
import { runInThisContext } from 'vm';
import { createWdmApiClient } from './client';
import { createRunResultApi, readPatternDetail, readPatternPage } from './run-results';

Object.assign(globalThis, runInThisContext('({ fetch, Headers, Response, Blob })'));
const runId = '10000000-0000-4000-8000-000000000001';
const id = '18446744073709551615';
const metadata = () => ({ runId, executionStatus: 'SUCCEEDED', publicationStatus: 'COMMITTED',
  manifestVersion: 'WDM_RESULT_MANIFEST_V1', resultSha256: 'a'.repeat(64), committedAt: '2026-11-09T10:00:00.000000Z',
  provenance: { version: 'RUN_PROVENANCE_V1', conversionFormatVersion: 'CONVERSION_ARTIFACT_V1', snapshotId: runId,
    attemptId: runId, validationSha256: 'b'.repeat(64), sequenceSha256: 'c'.repeat(64), mappingSha256: 'd'.repeat(64), sequenceCount: '100' },
  storedPatternCount: '1', storedPatternItemCount: '2', storedRunItemCount: '2', completeness: 'COMPLETE_VERIFIED',
  stagingReadable: false, publishedReadable: true, eligibleForCombo: true });
const pattern = () => ({ patternId: id, runId, canonicalPattern: '<(1)(2)>', canonicalPatternHash: 'e'.repeat(64),
  supportAbs: '53', supportRatio: .53, weightedSupport: 2.5474406991260925, patternWeight: .375,
  patternWeightPolicy: 'EQ2_EQ3_ARITHMETIC_MEAN_V1', itemsetCount: 2, patternItemCount: 2 });
const page = () => ({ result: metadata(), items: [pattern()], page: 1, size: 20, total: '1' });
const detail = () => ({ result: metadata(), pattern: pattern(), itemsets: [1, 2].map(i => ({
  itemsetOrder: i, items: [{ itemOrder: 1, itemId: i, productId: 'product-' + i, productNameSnapshot: 'Historical name',
    skuSnapshot: 'Historical SKU', itemWeight: i * .25, occurrenceCount: '9007199254740993', sequenceSupport: '53' }],
})) });
function response(data: unknown) {
  return new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'p3-read' }), {
    status: 200, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'p3-read' },
  });
}

test('published page preserves full string IDs and persisted weighted values', () => {
  expect(readPatternPage(page(), runId, 1)).toEqual(page());
  const large = page();
  large.result.provenance.sequenceCount = '18446744073709551615';
  large.items[0].supportAbs = '9007199254740993';
  expect(readPatternPage(large, runId, 1).items[0].supportAbs).toBe('9007199254740993');
});
test('detail retains separate itemsets and historical product fields', () => {
  const read = readPatternDetail(detail(), runId, id);
  expect(read.pattern.canonicalPattern).toBe('<(1)(2)>');
  expect(read.itemsets).toHaveLength(2);
  expect(read.itemsets[1].items[0].occurrenceCount).toBe('9007199254740993');
  expect(read.itemsets[0].items[0].productNameSnapshot).toBe('Historical name');
});
test.each([{ patternId: 12 }, { supportAbs: 53 }, { patternWeight: NaN }, { patternWeightPolicy: 'UNKNOWN' },
  { runId: '20000000-0000-4000-8000-000000000002' }, { supportAbs: '0' }])('rejects invalid persisted pattern %j', patch => {
  expect(() => readPatternPage({ ...page(), items: [{ ...pattern(), ...patch }] }, runId, 1)).toThrow();
});
test('uses authenticated GET, one-based page and explicit published view', async () => {
  const fetchMock = jest.fn().mockResolvedValue(response(page()));
  const api = createRunResultApi(createWdmApiClient({ fetchImpl: fetchMock,
    getAuthAdapter: () => ({ getAccessToken: () => 'test-token' }) }));
  const controller = new AbortController();
  await api.patterns(runId, 1, controller.signal, 'published');
  const [path, options] = fetchMock.mock.calls[0];
  const url = new URL(path, 'https://test.invalid');
  expect(url.pathname).toBe('/api/v1/admin/mining-runs/' + runId + '/patterns');
  expect(Object.fromEntries(url.searchParams)).toEqual({ view: 'published', page: '1', size: '20' });
  expect(options.method).toBe('GET'); expect(options.body).toBeUndefined();
  expect(options.headers.get('Authorization')).toBe('Bearer test-token');
  expect(options.signal).toBeDefined(); expect(fetchMock).toHaveBeenCalledTimes(1);
});
test('does not fall back to staging when published was requested', async () => {
  const staging = { ...page(), result: { ...metadata(), publicationStatus: 'NOT_PUBLISHED', manifestVersion: null,
    resultSha256: null, committedAt: null, completeness: 'UNVERIFIED', stagingReadable: true, publishedReadable: false, eligibleForCombo: false } };
  const fetchMock = jest.fn().mockResolvedValue(response(staging));
  const api = createRunResultApi(createWdmApiClient({ fetchImpl: fetchMock }));
  await expect(api.patterns(runId, 1, undefined, 'published')).rejects.toMatchObject({ kind: 'response' });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});
test('rejects invalid page and overflow ID without network calls', () => {
  const fetchMock = jest.fn(); const api = createRunResultApi(createWdmApiClient({ fetchImpl: fetchMock }));
  expect(() => api.patterns(runId, 0, undefined, 'published')).toThrow();
  expect(() => api.detail(runId, '18446744073709551616', undefined, 'published')).toThrow();
  expect(fetchMock).not.toHaveBeenCalled();
});
test('rejects detail whose itemset boundaries disagree with canonical', () => {
  const value = detail(); value.itemsets[1].itemsetOrder = 1;
  expect(() => readPatternDetail(value, runId, id)).toThrow();
});
test('zero published patterns remain an explicit empty result', () => {
  const value = { ...page(), items: [], total: '0', result: { ...metadata(), storedPatternCount: '0', eligibleForCombo: false } };
  expect(readPatternPage(value, runId, 1).total).toBe('0');
});
test('cancels a pending GET without replaying it', async () => {
  let finish!: (value: Response) => void;
  const fetchMock = jest.fn().mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  const api = createRunResultApi(createWdmApiClient({ fetchImpl: fetchMock }));
  const controller = new AbortController();
  const pending = api.patterns(runId, 1, controller.signal, 'published'); controller.abort();
  finish(response(page()));
  await expect(pending).rejects.toMatchObject({ kind: 'aborted' });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});
