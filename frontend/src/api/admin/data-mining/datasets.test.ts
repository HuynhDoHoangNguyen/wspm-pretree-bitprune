/** @jest-environment node */
import { runInThisContext } from 'vm';
import { createWdmApiClient } from './client';
import { createDatasetApi } from './datasets';

Object.assign(globalThis, runInThisContext('({ fetch, Headers, Response, FormData, Blob, File })'));
const id = '11111111-2222-4333-8444-555555555555';
const metadata = {
  snapshotId: id, sourceType: 'CSV', sourceName: 'orders.csv', validationStatus: 'PENDING',
  conversionStatus: 'NOT_STARTED', statisticsStatus: 'NOT_COMPUTED', rawSha256: 'a'.repeat(64),
  rawSizeBytes: '9007199254740993', sequenceCount: null, fromTime: null, toTime: null,
  createdAt: '2026-11-05T01:02:03.123456Z',
};
const detail = { ...metadata, rawAvailability: 'AVAILABLE', provenance: {
  contractVersion: 'wdm-order-csv-v1', acquisition: 'CSV_UPLOAD', sourceTimeZone: null,
  timeField: 'ordered_at', statusPolicy: 'COMPLETED_CURRENT_AT_ACQUISITION',
} };
const period = { fromTime: '2026-10-31T17:00:00.000Z', toTime: '2026-11-01T17:00:00.000Z' };
let fetchMock: jest.Mock;
let api: ReturnType<typeof createDatasetApi>;

function json(data: unknown, status = 201) {
  return new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'request-1' }), {
    status, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'request-1' },
  });
}

beforeEach(() => {
  fetchMock = jest.fn();
  api = createDatasetApi(createWdmApiClient({ fetchImpl: fetchMock, getAuthAdapter: () => ({ getAccessToken: () => 'shared-token' }) }));
});

it('uploads exactly one file part with shared bearer identity, no manual boundary and exact count strings', async () => {
  fetchMock.mockResolvedValue(json(metadata));
  const file = new File(['raw CSV'], 'orders.csv', { type: 'text/csv' });
  const result = await api.upload(file);
  expect(result.rawSizeBytes).toBe('9007199254740993');
  expect(result.sequenceCount).toBeNull();
  const [path, options] = fetchMock.mock.calls[0];
  expect(path).toBe('/api/v1/admin/dataset-snapshots');
  expect(options.method).toBe('POST');
  expect(options.headers.get('Authorization')).toBe('Bearer shared-token');
  expect(options.headers.has('Content-Type')).toBe(false);
  expect(Array.from(options.body.keys())).toEqual(['file']);
  expect(options.body.get('file').name).toBe('orders.csv');
  expect(await options.body.get('file').text()).toBe('raw CSV');
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it.each([200, 202, 204])('does not accept status %s as completed acquisition', async status => {
  fetchMock.mockResolvedValue(status === 204 ? new Response(null, { status }) : json(metadata, status));
  await expect(api.upload(new File(['csv'], 'a.csv'))).rejects.toMatchObject({ code: 'INVALID_RESPONSE', status });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it.each([
  null, {}, { ...metadata, sourceType: 'DATABASE' }, { ...metadata, sequenceCount: '0' },
  { ...metadata, validationStatus: 'VALID' }, { ...metadata, statisticsStatus: 'COMPUTED' },
  { ...metadata, rawSizeBytes: 12 }, { ...metadata, rawSha256: 'bad' },
])('rejects invalid or misleading acquisition metadata', async value => {
  fetchMock.mockResolvedValue(json(value));
  await expect(api.upload(new File(['csv'], 'a.csv'))).rejects.toMatchObject({ code: 'INVALID_SNAPSHOT_RESPONSE' });
});

it('downloads header-only CSV and preserves exact server bytes and safe generated filename', async () => {
  const bytes = 'order_id,order_item_id\r\n';
  fetchMock.mockResolvedValue(new Response(bytes, { headers: {
    'Content-Type': 'text/csv; charset=UTF-8', 'Content-Disposition': 'attachment; filename="order-export-' + id + '.csv"',
  } }));
  const result = await api.exportOrders(period);
  expect(await result.blob.text()).toBe(bytes);
  expect(result.filename).toBe('order-export-' + id + '.csv');
  const url = new URL(fetchMock.mock.calls[0][0], 'https://example.test');
  expect(url.pathname).toBe('/api/v1/admin/order-exports');
  expect(Array.from(url.searchParams.keys()).sort()).toEqual(['fromTime', 'toTime']);
  expect(url.searchParams.get('fromTime')).toBe(period.fromTime);
});

it('uses a fixed safe fallback filename for missing or malicious disposition', async () => {
  fetchMock.mockResolvedValue(new Response('header\r\n', { headers: {
    'Content-Type': 'text/csv', 'Content-Disposition': 'attachment; filename="../../private.csv"',
  } }));
  expect((await api.exportOrders(period)).filename).toBe('order-export.csv');
});

it.each(['application/json', 'text/html'])('never downloads a successful %s response as CSV', async type => {
  fetchMock.mockResolvedValue(new Response('{}', { headers: { 'Content-Type': type } }));
  await expect(api.exportOrders(period)).rejects.toMatchObject({ code: 'INVALID_CSV_RESPONSE' });
});

it('rejects partial successful exports instead of downloading a truncated artifact', async () => {
  fetchMock.mockResolvedValue(new Response('partial', { status: 206, headers: { 'Content-Type': 'text/csv' } }));
  await expect(api.exportOrders(period)).rejects.toMatchObject({ code: 'INVALID_RESPONSE', status: 206 });
});

it('preserves HTTP failure metadata and never replays an upload', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({
    status: 503, code: 'SERVICE_UNAVAILABLE', message: 'Unavailable', path: '/api/v1/admin/dataset-snapshots',
    requestId: 'request-1', details: [],
  }), { status: 503, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'request-1' } }));
  await expect(api.upload(new File(['csv'], 'a.csv'))).rejects.toMatchObject({ status: 503, requestId: 'request-1' });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it('discards late download success after session cancellation even if fetch ignores the signal', async () => {
  const session = new AbortController();
  let finish!: (value: Response) => void;
  fetchMock.mockReturnValue(new Promise(resolve => { finish = resolve; }));
  api = createDatasetApi(createWdmApiClient({ fetchImpl: fetchMock, getAuthAdapter: () => ({
    getAccessToken: () => 'token', getSessionSignal: () => session.signal,
  }) }));
  const pending = api.exportOrders(period);
  session.abort();
  finish(new Response('header', { headers: { 'Content-Type': 'text/csv' } }));
  await expect(pending).rejects.toMatchObject({ kind: 'aborted' });
});

it('reads detail by ID, preserves exact count strings and strips unknown private fields', async () => {
  fetchMock.mockResolvedValue(json({ ...detail, rawFilePath: '/private/raw.csv', conversionReportJson: { secret: 'private' } }, 200));
  const result = await api.detail(id);
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/dataset-snapshots/' + id);
  expect(result.rawSizeBytes).toBe('9007199254740993');
  expect(result.sequenceCount).toBeNull();
  expect(result).not.toHaveProperty('rawFilePath');
  expect(result).not.toHaveProperty('conversionReportJson');
});

it('retains unknown optional metadata as null without inventing completed statistics', async () => {
  fetchMock.mockResolvedValue(json({ ...detail, sourceType: null, sourceName: null, conversionStatus: null,
    statisticsStatus: null, rawSha256: null, rawSizeBytes: null, rawAvailability: 'UNAVAILABLE',
    provenance: { contractVersion: null, acquisition: null, sourceTimeZone: null, timeField: null, statusPolicy: null },
  }, 200));
  expect((await api.detail(id)).statisticsStatus).toBeNull();
});

it.each([{ ...detail, snapshotId: 'wrong-id' }, { ...detail, sequenceCount: '0' },
  { ...detail, rawSizeBytes: 12 }, { ...detail, rawAvailability: 'VALID' }])('rejects misleading detail response', async value => {
  fetchMock.mockResolvedValue(json(value, 200));
  await expect(api.detail(id)).rejects.toMatchObject({ code: 'INVALID_DETAIL_RESPONSE' });
});

it('rejects malformed IDs before constructing a request path', async () => {
  await expect(api.detail('../private')).rejects.toMatchObject({ status: 400 });
  await expect(api.downloadRaw('bad-id')).rejects.toMatchObject({ status: 400 });
  expect(fetchMock).not.toHaveBeenCalled();
});

it('downloads exact raw bytes with a safe snapshot filename', async () => {
  const bytes = new Uint8Array([239, 187, 191, 13, 10, 255]);
  fetchMock.mockResolvedValue(new Response(bytes, { headers: {
    'Content-Type': 'text/csv', 'Content-Disposition': 'attachment; filename="snapshot-' + id + '-raw.csv"',
  } }));
  const result = await api.downloadRaw(id);
  expect(new Uint8Array(await result.blob.arrayBuffer())).toEqual(bytes);
  expect(result.filename).toBe('snapshot-' + id + '-raw.csv');
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/dataset-snapshots/' + id + '/artifacts/raw');
});

it('never saves raw error JSON as a successful CSV artifact', async () => {
  fetchMock.mockResolvedValue(new Response('{}', { headers: { 'Content-Type': 'application/json' } }));
  await expect(api.downloadRaw(id)).rejects.toMatchObject({ code: 'INVALID_CSV_RESPONSE' });
});
