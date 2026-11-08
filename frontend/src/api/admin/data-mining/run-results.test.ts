import { runInThisContext } from 'vm';
import { createWdmApiClient } from './client';
import { createRunResultApi, readRunResultMetadata, readPatternPage, RunResultMetadata } from './run-results';
Object.assign(globalThis, runInThisContext('({ Headers, Response })'));
const id = '10000000-0000-4000-8000-000000000001';
export const resultMetadata = (): RunResultMetadata => ({ runId: id, executionStatus: 'SUCCEEDED', publicationStatus: 'NOT_PUBLISHED',
  manifestVersion: null, resultSha256: null, committedAt: null,
  provenance: { version: 'RUN_PROVENANCE_V1', conversionFormatVersion: 'CONVERSION_V1', snapshotId: id, attemptId: id,
    validationSha256: 'a'.repeat(64), sequenceSha256: 'b'.repeat(64), mappingSha256: 'c'.repeat(64), sequenceCount: '4' },
  storedPatternCount: '18446744073709551615', storedPatternItemCount: '9007199254740993', storedRunItemCount: '3',
  completeness: 'UNVERIFIED', stagingReadable: true, publishedReadable: false, eligibleForCombo: false });
test('exact metadata counts survive while private/unknown properties are stripped', () => {
  const original = resultMetadata();
  expect(readRunResultMetadata({ ...original, path: 'secret', provenance: { ...original.provenance, rawReference: 'secret' } }, id)).toEqual(original);
});
test.each([0, null, '01', '-1', '18446744073709551616', 'Infinity'])('rejects unsafe count representation %s', count => {
  expect(() => readRunResultMetadata({ ...resultMetadata(), storedPatternCount: count }, id)).toThrow();
});
test.each([{ completeness: 'COMPLETE' }, { eligibleForCombo: true }, { publishedReadable: true }, { stagingReadable: false },
  { runId: id.replace(/1$/, '2') }, { provenance: null }, { manifestVersion: 'UNEXPECTED' }])('rejects inconsistent visibility/binding %j', patch => {
  expect(() => readRunResultMetadata({ ...resultMetadata(), ...patch }, id)).toThrow();
});
test('legacy and unsupported committed markers remain explicitly unverified', () => {
  const legacy = { ...resultMetadata(), publicationStatus: 'UNVERIFIED', provenance: null, stagingReadable: false };
  expect(readRunResultMetadata(legacy, id).provenance).toBeNull();
  const committed = { ...resultMetadata(), publicationStatus: 'COMMITTED', stagingReadable: false, manifestVersion: 'FUTURE',
    resultSha256: 'd'.repeat(64), committedAt: '2026-11-08T00:00:00.123456Z' };
  expect(readRunResultMetadata(committed, id).publishedReadable).toBe(false);
  expect(() => readRunResultMetadata({ ...committed, committedAt: 'invalid' }, id)).toThrow();
});
test('client GET uses shared Auth, correct endpoint, abort signal and no query or write', async () => {
  const fetchMock = jest.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data: resultMetadata(), requestId: 'results-request' }),
    { status: 200, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'results-request' } }));
  const api = createRunResultApi(createWdmApiClient({ fetchImpl: fetchMock, getAuthAdapter: () => ({ getAccessToken: () => 'token' }) }));
  const controller = new AbortController();
  expect(await api.metadata(id, controller.signal)).toEqual(resultMetadata());
  expect(fetchMock).toHaveBeenCalledTimes(1); const [path, options] = fetchMock.mock.calls[0];
  expect(path).toBe('/api/v1/admin/mining-runs/' + id + '/results'); expect(options.method).toBe('GET');
  expect(options.body).toBeUndefined(); expect(options.headers.get('Authorization')).toBe('Bearer token'); expect(options.signal).toBeDefined();
  expect(() => api.metadata('../bad')).toThrow(); expect(fetchMock).toHaveBeenCalledTimes(1);
});

const pattern = () => ({ patternId: '18446744073709551615', runId: id, canonicalPattern: '<(1)(2)>', canonicalPatternHash: 'a'.repeat(64),
  supportAbs: '3', supportRatio: 0.75, weightedSupport: 0.375, patternWeight: 0.5,
  patternWeightPolicy: 'EQ2_EQ3_ARITHMETIC_MEAN_V1', itemsetCount: 2, patternItemCount: 2 });
const patternPage = () => ({ result: { ...resultMetadata(), storedPatternCount: '1' }, items: [pattern()], page: 1, size: 20, total: '1' });
test('pattern page keeps exact IDs, canonical boundaries and selects public fields', () => {
  const v = patternPage();
  expect(readPatternPage({ ...v, items: [{ ...pattern(), privatePath: 'secret' }] }, id, 1)).toEqual(v);
});
test.each([{ patternId: 1 }, { patternId: '0' }, { patternId: '18446744073709551616' }, { supportAbs: '5' },
  { runId: id.replace(/1$/, '2') }, { patternWeight: Infinity }, { supportRatio: -1 }, { weightedSupport: NaN },
  { patternWeightPolicy: 'FUTURE' }, { itemsetCount: 3 }, { patternItemCount: 0 }, { canonicalPatternHash: 'bad' }])('rejects malformed pattern %j', patch => {
  expect(() => readPatternPage({ ...patternPage(), items: [{ ...pattern(), ...patch }] }, id, 1)).toThrow();
});
test.each([{ page: 2 }, { size: 100 }, { total: '2' }, { items: [] }, { total: 1 },
  { result: { ...patternPage().result, stagingReadable: false } }])('rejects malformed page %j', patch => {
  expect(() => readPatternPage({ ...patternPage(), ...patch }, id, 1)).toThrow();
});
test('empty and out-of-range pages are accepted without coercing large totals', () => {
  expect(readPatternPage({ ...patternPage(), items: [], total: '0', result: { ...patternPage().result, storedPatternCount: '0' } }, id, 1).items).toEqual([]);
  expect(readPatternPage({ ...patternPage(), items: [], page: 3 }, id, 3).page).toBe(3);
  const total = '18446744073709551615';
  expect(readPatternPage({ ...patternPage(), total, result: { ...patternPage().result, storedPatternCount: total },
    items: Array.from({ length: 20 }, (_, i) => ({ ...pattern(), patternId: String(i + 1) })) }, id, 1).total).toBe(total);
});
test('pattern client uses staging query, Auth, GET and cancellation; invalid input never fetches', async () => {
  const fetchMock = jest.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data: patternPage(), requestId: 'patterns-request' }),
    { status: 200, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'patterns-request' } }));
  const api = createRunResultApi(createWdmApiClient({ fetchImpl: fetchMock, getAuthAdapter: () => ({ getAccessToken: () => 'token' }) }));
  const controller = new AbortController(); expect(await api.patterns(id, 1, controller.signal)).toEqual(patternPage());
  const [path, options] = fetchMock.mock.calls[0]; const url = new URL(path, 'https://test.invalid');
  expect(url.pathname).toBe('/api/v1/admin/mining-runs/' + id + '/patterns');
  expect(Object.fromEntries(url.searchParams)).toEqual({ view: 'staging', page: '1', size: '20' });
  expect(options.method).toBe('GET'); expect(options.body).toBeUndefined(); expect(options.headers.get('Authorization')).toBe('Bearer token');
  for (const page of [0, -1, NaN, 1.5, 100001]) expect(() => api.patterns(id, page)).toThrow();
  expect(() => api.patterns('invalid')).toThrow(); expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(options.signal).toBeDefined();
});

test('weighted support above one is displayed as an absolute weighted value', () => {
  expect(readPatternPage({ ...patternPage(), items: [{ ...pattern(), weightedSupport: 2.25 }] }, id, 1).items[0].weightedSupport).toBe(2.25);
});
test('cancels a pending pattern read through the shared client signal', async () => {
  let finish!: (response: Response) => void;
  const fetchMock = jest.fn().mockImplementation(() => new Promise(done => { finish = done; }));
  const api = createRunResultApi(createWdmApiClient({ fetchImpl: fetchMock, getAuthAdapter: () => ({ getAccessToken: () => 'token' }) }));
  const controller = new AbortController(); const request = api.patterns(id, 1, controller.signal);
  controller.abort(); expect(fetchMock.mock.calls[0][1].signal.aborted).toBe(true);
  finish(new Response('{}', { status: 200 })); await expect(request).rejects.toMatchObject({ kind: 'aborted' });
});

