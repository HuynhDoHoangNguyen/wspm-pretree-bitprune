/** @jest-environment node */
import { runInThisContext } from 'vm';
import { createWdmApiClient } from 'api/admin/data-mining/client';
import { WdmAuthAdapter } from 'api/admin/data-mining/types';
import { createPatternHttpClient } from './PatternHttpClient';

Object.assign(globalThis, runInThisContext('({ fetch, Headers, Response })'));
const run = { runId: 'A /?', snapshotId: 'S', algorithm: 'WSPM_PRETREE', status: 'SUCCEEDED',
  createdAt: '2026-11-05T00:00:00.000001Z', sequenceCount: '9007199254740993', issues: [] };
const summary = { runId: run.runId, patternId: '18446744073709551615', canonicalPattern: '<(1)(2)>',
  supportAbs: '9007199254740993', supportRatio: .5, weightedSupport: null, patternWeight: .2,
  patternWeightPolicy: 'TEST_ONLY', patternItemCount: 2, itemsetCount: 2 };
const page = { runId: run.runId, runStatus: 'SUCCEEDED', resultState: 'READY', page: 0, size: 20,
  runPatternTotal: '9007199254740995', filteredTotal: '1', items: [summary],
  weightFilter: { enabled: false, reason: 'POLICY_NOT_CONFIRMED', policy: 'TEST_ONLY' } };
function ok(data: unknown) {
  return new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'test-id' }),
    { headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'test-id' } });
}
function denial(status: number, code: string, details: unknown[] = []) {
  return new Response(JSON.stringify({ status, code, message: 'Safe server message.', path: '/api/v1/admin/mining-runs/r/patterns',
    requestId: 'denial-id', details }), { status, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'denial-id' } });
}
function setup(auth?: WdmAuthAdapter) {
  const fetchMock = jest.fn();
  const client = createPatternHttpClient(createWdmApiClient({ fetchImpl: fetchMock, getAuthAdapter: () => auth ?? null }));
  return { client, fetchMock };
}

test('run pagination uses total/items and server fields for labels without rounding counts', async () => {
  const { client, fetchMock } = setup({ getAccessToken: () => 'test-only-token' });
  fetchMock.mockResolvedValue(ok({ page: 2, size: 20, total: '9007199254740993', items: [run] }));
  const result = await client.runs(2, 20);
  expect(result.total).toBe('9007199254740993'); expect(result.items[0].label).toContain(run.createdAt);
  expect(result.items[0].label).toContain(run.status); expect(result.items[0].runId).toBe(run.runId);
  const [url, options] = fetchMock.mock.calls[0];
  expect(url).toBe('/api/v1/admin/mining-runs?page=2&size=20');
  expect(options.headers.get('Authorization')).toBe('Bearer test-only-token'); expect(options.credentials).toBe('omit');
});
test('list encodes path/repeated itemId and raw exact bounds, omitting only unentered bounds', async () => {
  const { client, fetchMock } = setup();fetchMock.mockResolvedValue(ok(page));
  expect(await client.list(run.runId, 0, 20, { itemIds: ['1', '2'], supportMin: '9007199254740993', supportMax: '', weightMin: '', weightMax: '0.40' })).toEqual(page);
  const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost');
  expect(url.pathname).toContain('A%20%2F%3F'); expect(url.searchParams.getAll('itemId')).toEqual(['1', '2']);
  expect(url.searchParams.get('supportMin')).toBe('9007199254740993');expect(url.searchParams.get('weightMax')).toBe('0.40');
  expect(url.searchParams.has('supportMax')).toBe(false);expect(url.searchParams.has('weightMin')).toBe(false);
});
test('detail has no query and preserves unsigned ID, itemsets, missing mapping and null metrics', async () => {
  const { client, fetchMock } = setup();
  const data = { ...summary, sequenceCount: '9007199254740993', integrityStatus: 'INCOMPLETE',
    issues: [{ code: 'MISSING_RUN_DICTIONARY_ITEM', itemsetOrder: 2, itemOrder: 1, itemId: 2 }],
    itemsets: [1, 2].map(n => ({ itemsetOrder: n, items: [{ itemId: n, itemOrder: 1, mappingStatus: 'MISSING',
      productId: null, nameSnapshot: null, skuSnapshot: null, itemWeight: null }] })) };
  fetchMock.mockResolvedValue(ok(data));expect(await client.detail(run.runId, summary.patternId)).toEqual(data);
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/mining-runs/A%20%2F%3F/patterns/18446744073709551615');
});
test('dictionary has independent totals/query pagination and preserves literal query input', async () => {
  const { client, fetchMock } = setup();const data = { runId: run.runId, resultState: 'READY', query: 'đ_%', page: 1, size: 20,
    dictionaryTotal: '9007199254740993', matchedTotal: '21', items: [{ itemId: 2, productId: 'P', nameSnapshot: 'Snapshot', skuSnapshot: 'SKU' }] };
  fetchMock.mockResolvedValue(ok(data));expect(await client.dictionary(run.runId, 'Đ_%', 1, 20)).toEqual(data);
  expect(new URL(fetchMock.mock.calls[0][0], 'http://localhost').searchParams.get('q')).toBe('Đ_%');
});
test('membership details are interpreted only for the matching field/code and valid item integers', async () => {
  const { client, fetchMock } = setup();
  fetchMock.mockResolvedValue(denial(400, 'ITEM_NOT_IN_RUN_DICTIONARY', [
    { field: 'offendingItemIds', code: 'ITEM_NOT_IN_RUN_DICTIONARY', message: '2' },
    { field: 'other', code: 'ITEM_NOT_IN_RUN_DICTIONARY', message: '3' },
    { field: 'offendingItemIds', code: 'WRONG', message: '4' },
    { field: 'offendingItemIds', code: 'ITEM_NOT_IN_RUN_DICTIONARY', message: 'not-an-id' }]));
  await expect(client.list('r', 0, 20)).rejects.toMatchObject({ status: 400, code: 'ITEM_NOT_IN_RUN_DICTIONARY',
    offendingItemIds: ['2'], requestId: 'denial-id', message: 'Safe server message.', path: '/api/v1/admin/mining-runs/r/patterns' });
});
test.each([401, 403])('HTTP %i survives auth callback aborting the captured session', async status => {
  const session = new AbortController();const callback = jest.fn(() => session.abort());
  const { client, fetchMock } = setup({ getAccessToken: () => 'test-only', getSessionSignal: () => session.signal,
    onUnauthorized: callback, onForbidden: callback });
  fetchMock.mockResolvedValue(denial(status, status === 401 ? 'AUTHENTICATION_REQUIRED' : 'ACCESS_DENIED'));
  await expect(client.runs(0, 20)).rejects.toMatchObject({ kind: 'http', status, requestId: 'denial-id' });expect(callback).toHaveBeenCalledTimes(1);
});
test.each(['caller', 'session'])('%s cancellation discards even late success from a fetch that ignores abort', async source => {
  const caller = new AbortController(), session = new AbortController();
  let finish: (value: Response) => void = () => undefined;
  const { client, fetchMock } = setup({ getAccessToken: () => 'test-only', getSessionSignal: () => session.signal });
  fetchMock.mockImplementation(() => new Promise<Response>(resolve => { finish = resolve; }));
  const pending = client.runs(0, 20, { signal: caller.signal });
  (source === 'caller' ? caller : session).abort(); finish(ok({ page: 0, size: 20, total: '0', items: [] }));
  await expect(pending).rejects.toMatchObject({ kind: 'aborted', code: 'REQUEST_ABORTED' });
});
test('session switch sends new token and rejects response belonging to the old identity', async () => {
  let session = new AbortController(), token = 'old-test-token';let finish: (r: Response) => void = () => undefined;
  const { client, fetchMock } = setup({ getAccessToken: () => token, getSessionSignal: () => session.signal });
  fetchMock.mockImplementationOnce(() => new Promise<Response>(resolve => { finish = resolve; }))
    .mockResolvedValueOnce(ok({ page: 0, size: 20, total: '0', items: [] }));
  const old = client.runs(0, 20);session.abort();session = new AbortController();token = 'new-test-token';
  expect((await client.runs(0, 20)).total).toBe('0');finish(ok({ page: 0, size: 20, total: '1', items: [run] }));
  await expect(old).rejects.toMatchObject({ kind: 'aborted' });
  expect(fetchMock.mock.calls[1][1].headers.get('Authorization')).toBe('Bearer new-test-token');
});
test.each([401, 403])('late HTTP %i from the old session keeps its error but cannot invalidate the new session', async status => {
  let session = new AbortController();let finish: (r: Response) => void = () => undefined;
  const callback = jest.fn();
  const { client, fetchMock } = setup({ getAccessToken: () => 'test-only', getSessionSignal: () => session.signal,
    onUnauthorized: callback, onForbidden: callback });
  fetchMock.mockImplementationOnce(() => new Promise<Response>(resolve => { finish = resolve; }))
    .mockResolvedValueOnce(ok({ page: 0, size: 20, total: '0', items: [] }));
  const old = client.runs(0, 20);session.abort();session = new AbortController();
  expect((await client.runs(0, 20)).total).toBe('0');
  finish(denial(status, status === 401 ? 'AUTHENTICATION_REQUIRED' : 'ACCESS_DENIED'));
  await expect(old).rejects.toMatchObject({ kind: 'http', status, requestId: 'denial-id' });
  expect(callback).not.toHaveBeenCalled();expect(session.signal.aborted).toBe(false);
});
test('pre-aborted requests do not fetch and network errors remain distinct', async () => {
  const { client, fetchMock } = setup();const controller = new AbortController();controller.abort();
  await expect(client.runs(0, 20, { signal: controller.signal })).rejects.toMatchObject({ kind: 'aborted' });expect(fetchMock).not.toHaveBeenCalled();
  fetchMock.mockRejectedValue(new TypeError('test network failure'));await expect(client.runs(0, 20)).rejects.toMatchObject({ kind: 'network', code: 'NETWORK_ERROR' });
});
test('rejects unwrapped/incorrect envelopes and numeric BIGINT data instead of coercing them', async () => {
  const { client, fetchMock } = setup();fetchMock.mockResolvedValue(new Response('{}', { headers: { 'Content-Type': 'application/json' } }));
  await expect(client.runs(0, 20)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  fetchMock.mockResolvedValue(ok({ page: 0, size: 20, total: 9007199254740992, items: [] }));
  await expect(client.runs(0, 20)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  fetchMock.mockResolvedValue(ok({ ...page, items: [{ ...summary, patternId: 42 }] }));
  await expect(client.list(run.runId, 0, 20)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
});
