/** @jest-environment node */
import { runInThisContext } from 'vm';
import { createWdmApiClient } from './client';
import { createConversionApi, PREVIEW_MAX_BYTES, previewAllowed } from './conversion';
import { readConversionReport } from './conversion-response';
import { conversionData } from './conversion-test-data';
import { validationId as id } from './validation-test-data';
import { readSnapshotDetail, readSnapshotReceipt } from './dataset-response';
import { pendingDetail } from './validation-test-data';
Object.assign(globalThis, runInThisContext('({ Headers, Response, Blob, ReadableStream })'));
const json = (data: unknown) => new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'req-1' }),
  { headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'req-1' } });
function api(fetchMock: jest.Mock) { return createConversionApi(createWdmApiClient({ fetchImpl: fetchMock,
  getAuthAdapter: () => ({ getAccessToken: () => 'token' }) })); }
function artifactResponse(body: BodyInit, kind = 'sequence', extra = {}) {
  return new Response(body, { headers: { 'Content-Type': kind === 'sequence' ? 'text/plain;charset=UTF-8' : 'application/json',
    'Content-Disposition': 'attachment; filename="snapshot-' + id + (kind === 'sequence' ? '.spmf' : '-mapping.json') + '"', ...extra } });
}
it.each(['NOT_STARTED', 'RUNNING', 'SUCCEEDED', 'FAILED'] as const)('decodes %s and projects only public fields', status => {
  const raw = { ...conversionData(status), privatePath: 'secret' };
  expect(readConversionReport(raw, id)).toEqual(conversionData(status));
});
it('preserves counts beyond Number precision without coercion', () => {
  const raw = conversionData(); raw.statistics.sequenceCount = '9007199254740993';
  expect(readConversionReport(raw, id).statistics?.sequenceCount).toBe('9007199254740993');
});
it.each(['NOT_STARTED', 'RUNNING', 'SUCCEEDED', 'FAILED'] as const)('accepts detail status %s without relaxing the upload receipt', status => {
  expect(readSnapshotDetail({ ...pendingDetail(), conversionStatus: status }, id).conversionStatus).toBe(status);
});
it.each(['RUNNING', 'SUCCEEDED', 'FAILED'])('rejects status %s in the initial upload receipt', status => {
  expect(() => readSnapshotReceipt({ ...pendingDetail(), conversionStatus: status })).toThrow();
});
it('retains the request ID when an endpoint decoder rejects a response', async () => {
  const fetchMock = jest.fn().mockResolvedValue(json({ ...conversionData(), statistics: null }));
  await expect(api(fetchMock).read(id)).rejects.toMatchObject({ code: 'INVALID_CONVERSION_RESPONSE', requestId: 'req-1' });
});
it.each(['NOT_STARTED', 'RUNNING', 'FAILED'] as const)('rejects HTTP 200 POST that reports %s instead of SUCCEEDED', async status => {
  const fetchMock = jest.fn().mockResolvedValue(json(conversionData(status)));
  await expect(api(fetchMock).start(id)).rejects.toMatchObject({ code: 'INVALID_CONVERSION_RESPONSE', requestId: 'req-1' });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});
it.each([
  { snapshotId: 'other' }, { conversionStatus: 'UNKNOWN' }, { statisticsStatus: 'NOT_COMPUTED' }, { startedAt: '2026-02-30T00:00:00Z' },
  { failureCode: 'secret' }, { completedAt: null }, { artifactVersion: 'v2' }, { statistics: null },
  { statistics: { ...conversionData().statistics, itemsetCount: 3 } },
  { statistics: { ...conversionData().statistics, minSequenceLength: '01' } },
  { statistics: { ...conversionData().statistics, averageSequenceLength: Infinity } },
  { sequence: { ...conversionData().sequence, downloadUrl: 'https://evil.example/api' } },
  { mapping: { ...conversionData().mapping, downloadUrl: '/api/v1/admin/dataset-snapshots/' + id + '/artifacts/sequence' } },
  { sequence: { ...conversionData().sequence, sizeBytes: 19 } }, { mapping: { ...conversionData().mapping, sha256: 'BAD' } },
])('rejects malformed or inconsistent metadata %j', patch => {
  expect(() => readConversionReport({ ...conversionData(), ...patch }, id)).toThrow('could not be verified');
});
it('rejects computed payload in pending states', () => {
  expect(() => readConversionReport({ ...conversionData('RUNNING'), statistics: conversionData().statistics }, id)).toThrow();
  expect(() => readConversionReport({ ...conversionData('FAILED'), failureCode: 'private' }, id)).toThrow();
});
it('sends one bodyless POST and GET at the verified endpoint with Auth', async () => {
  const fetchMock = jest.fn().mockImplementation(() => Promise.resolve(json(conversionData()))), service = api(fetchMock);
  await service.start(id); await service.read(id);
  expect(fetchMock).toHaveBeenCalledTimes(2);
  expect(fetchMock.mock.calls.map(call => call[0])).toEqual(Array(2).fill('/api/v1/admin/dataset-snapshots/' + id + '/conversion'));
  expect(fetchMock.mock.calls.map(call => call[1].method)).toEqual(['POST', 'GET']);
  expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
  expect(fetchMock.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer token');
  expect(fetchMock.mock.calls[0][1].headers.has('Content-Type')).toBe(false);
  await expect(service.start('bad')).rejects.toMatchObject({ status: 400 }); expect(fetchMock).toHaveBeenCalledTimes(2);
});
it.each(['sequence', 'mapping'] as const)('downloads %s with exact MIME/name/size', async kind => {
  const text = kind === 'sequence' ? '1 -1 1 -1 1 -1 -2\n' : '{}';
  const expected = { ...conversionData()[kind], sizeBytes: String(text.length) };
  const fetchMock = jest.fn().mockResolvedValue(artifactResponse(text, kind));
  expect((await api(fetchMock).download(id, kind, expected)).filename).toBe('snapshot-' + id + (kind === 'sequence' ? '.spmf' : '-mapping.json'));
  expect(fetchMock.mock.calls[0][0]).toBe(expected.downloadUrl);
});
it.each([
  { 'Content-Type': 'text/html' }, { 'Content-Disposition': 'attachment; filename="evil.spmf"' },
])('fails closed on download MIME/name/size %j', async headers => {
  const fetchMock = jest.fn().mockResolvedValue(artifactResponse('1 -1 1 -1 1 -1 -2\n', 'sequence', headers));
  await expect(api(fetchMock).download(id, 'sequence', conversionData().sequence)).rejects.toMatchObject({ code: 'INVALID_CONVERSION_ARTIFACT' });
});
it('rejects a size mismatch even with the correct download MIME and name', async () => {
  const fetchMock = jest.fn().mockResolvedValue(artifactResponse('wrong-size'));
  await expect(api(fetchMock).download(id, 'sequence', conversionData().sequence)).rejects.toMatchObject({ code: 'INVALID_CONVERSION_ARTIFACT' });
});
it('never fetches response-provided foreign URLs or oversized preview metadata', async () => {
  const fetchMock = jest.fn(), service = api(fetchMock);
  await expect(service.download(id, 'sequence', { ...conversionData().sequence, downloadUrl: 'https://evil.example' })).rejects.toThrow();
  await expect(service.preview(id, { ...conversionData().sequence, sizeBytes: '9007199254740993' })).rejects.toThrow();
  expect(fetchMock).not.toHaveBeenCalled();
  expect(previewAllowed('1048576')).toBe(true); expect(previewAllowed('1048577')).toBe(false);
});
it('bounds preview to 20 sequences and 4000 escaped characters', async () => {
  const text = Array(25).fill('1 -1 -2').join('\n') + '\n', fetchMock = jest.fn().mockResolvedValue(artifactResponse(text));
  const result = await api(fetchMock).preview(id, { ...conversionData().sequence, sizeBytes: String(text.length) });
  expect(result.text.trimEnd().split('\n')).toHaveLength(20); expect(result.truncated).toBe(true);
  const long = '1 '.repeat(3000) + '-1 -2\n'; fetchMock.mockResolvedValue(artifactResponse(long));
  expect((await api(fetchMock).preview(id, { ...conversionData().sequence, sizeBytes: String(long.length) })).text).toBe('');
});
it('rejects Content-Length before reading and cancels a stream that crosses the byte cap', async () => {
  const cancel = jest.fn(); let pulls = 0;
  const stream = new ReadableStream<Uint8Array>({ pull(controller) { pulls++; controller.enqueue(new Uint8Array(600000)); }, cancel });
  const fetchMock = jest.fn().mockResolvedValue(artifactResponse(stream));
  await expect(api(fetchMock).preview(id, { ...conversionData().sequence, sizeBytes: String(PREVIEW_MAX_BYTES) }))
    .rejects.toMatchObject({ code: 'DOWNLOAD_LIMIT_EXCEEDED' });
  expect(cancel).toHaveBeenCalledTimes(1); expect(pulls).toBeLessThanOrEqual(3);
  const blocked = new ReadableStream({ cancel });
  fetchMock.mockResolvedValue(artifactResponse(blocked, 'sequence', { 'Content-Length': '1048577' }));
  await expect(api(fetchMock).preview(id, conversionData().sequence)).rejects.toMatchObject({ code: 'DOWNLOAD_LIMIT_EXCEEDED' });
  expect(cancel).toHaveBeenCalledTimes(2);
});
it('session abort cancels an in-flight bounded stream without delivering a file', async () => {
  const session = new AbortController(), cancel = jest.fn(); let started!: () => void;
  const ready = new Promise<void>(resolve => { started = resolve; });
  const stream = new ReadableStream({ pull() { started(); }, cancel });
  const client = createWdmApiClient({ fetchImpl: jest.fn().mockResolvedValue(artifactResponse(stream)),
    getAuthAdapter: () => ({ getAccessToken: () => 'token', getSessionSignal: () => session.signal }) });
  const pending = createConversionApi(client).preview(id, conversionData().sequence);
  await ready; session.abort();
  await expect(pending).rejects.toMatchObject({ kind: 'aborted' }); expect(cancel).toHaveBeenCalled();
});
