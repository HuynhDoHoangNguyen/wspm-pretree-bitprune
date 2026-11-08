import { runInThisContext } from 'vm';
import { createWdmApiClient } from './client';
import { createRunResultApi, isPatternId, readPatternDetail, PatternDetail } from './run-results';
import { detailFixture, detailRunId as run, detailPatternId as id } from './pattern-detail-test-data';
Object.assign(globalThis, runInThisContext('({ Headers, Response })'));
test('keeps repeated items across sets, exact counters, boundaries and public snapshot fields', () => {
  const data = detailFixture();
  expect(readPatternDetail({ ...data, privatePath: '/secret', itemsets: data.itemsets.map(s => ({ ...s, privateField: 'secret',
    items: s.items.map(i => ({ ...i, privateField: 'secret' })) })) }, run, id)).toEqual(data);
  expect(readPatternDetail(data, run, id).itemsets[1].items[0].itemId).toBe(1);
});
test.each(['0', '01', '-1', '18446744073709551616', '1.1', '../1', '', 1, null])('rejects unsupported pattern ID %s', value => {
  expect(isPatternId(value)).toBe(false);
});
test.each([
  (d: PatternDetail) => { d.pattern.patternId = '1'; }, (d: PatternDetail) => { d.pattern.runId = run.replace(/1$/, '2'); },
  (d: PatternDetail) => { d.result.stagingReadable = false; }, (d: PatternDetail) => { d.result.provenance = null; },
  (d: PatternDetail) => { d.itemsets[0].itemsetOrder = 2; }, (d: PatternDetail) => { d.itemsets[1].itemsetOrder = 1; },
  (d: PatternDetail) => { d.itemsets[0].items[0].itemOrder = 0; }, (d: PatternDetail) => { d.itemsets[0].items[1].itemOrder = 1; },
  (d: PatternDetail) => { d.itemsets[0].items[1].itemId = 1; }, (d: PatternDetail) => { d.itemsets[0].items.reverse(); },
  (d: PatternDetail) => { d.itemsets[0].items = []; }, (d: PatternDetail) => { d.itemsets.pop(); },
  (d: PatternDetail) => { d.pattern.patternItemCount = 5; }, (d: PatternDetail) => { d.pattern.canonicalPattern = '<(1)(2)(1)(3)>'; },
  (d: PatternDetail) => { d.itemsets[1].items[0].skuSnapshot = 'CURRENT-SKU'; }, (d: PatternDetail) => { Object.assign(d.itemsets[0].items[0], { productNameSnapshot: null }); },
  (d: PatternDetail) => { d.itemsets[0].items[0].occurrenceCount = '18446744073709551616'; },
  (d: PatternDetail) => { d.itemsets[0].items[0].occurrenceCount = '2'; }, (d: PatternDetail) => { d.itemsets[0].items[0].sequenceSupport = '5'; },
  (d: PatternDetail) => { d.itemsets[0].items[0].itemWeight = Infinity; }, (d: PatternDetail) => { d.pattern.patternItemCount = 100001; },
])('rejects malformed/binding/structure/snapshot input #%#', mutate => {
  const data = detailFixture(); mutate(data); expect(() => readPatternDetail(data, run, id)).toThrow();
});
test('retains empty snapshot names/SKUs as stored rather than inventing current catalog values', () => {
  const data = detailFixture(); data.itemsets[2].items[0].productNameSnapshot = ''; data.itemsets[2].items[0].skuSnapshot = '';
  expect(readPatternDetail(data, run, id).itemsets[2].items[0].skuSnapshot).toBe('');
});
test('detail GET uses explicit staging/Auth, decodes and cancels pending requests', async () => {
  const fetchMock = jest.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data: detailFixture(), requestId: 'detail-request' }),
    { status: 200, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'detail-request' } }));
  const api = createRunResultApi(createWdmApiClient({ fetchImpl: fetchMock, getAuthAdapter: () => ({ getAccessToken: () => 'token' }) }));
  expect(await api.detail(run, id)).toEqual(detailFixture());
  const [url, options] = fetchMock.mock.calls[0];
  expect(url).toBe('/api/v1/admin/mining-runs/' + run + '/patterns/' + id + '?view=staging');
  expect(options.method).toBe('GET'); expect(options.body).toBeUndefined(); expect(options.headers.get('Authorization')).toBe('Bearer token');
  expect(() => api.detail('bad', id)).toThrow(); expect(() => api.detail(run, '0')).toThrow(); expect(fetchMock).toHaveBeenCalledTimes(1);
  let finish!: (response: Response) => void; fetchMock.mockImplementationOnce(() => new Promise(done => { finish = done; }));
  const controller = new AbortController(), request = api.detail(run, id, controller.signal);
  controller.abort(); expect(fetchMock.mock.calls[1][1].signal.aborted).toBe(true);
  finish(new Response('{}')); await expect(request).rejects.toMatchObject({ kind: 'aborted' });
});
