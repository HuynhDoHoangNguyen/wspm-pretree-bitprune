import { runInThisContext } from 'vm';
import { createWdmApiClient } from './client';
import { createMiningRunApi } from './mining-runs';
import { acceptedRun, runInput, runId, runStatus } from './mining-run-test-data';
Object.assign(globalThis, runInThisContext('({ Headers, Response })'));
function response(data: unknown, status: number) { return new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'test-id' }),
  { status, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'test-id' } }); }
it('sends exactly four fields once with Auth and signal; reads a known ID without body', async () => {
  const fetchMock = jest.fn().mockResolvedValueOnce(response(acceptedRun(), 202)).mockResolvedValueOnce(response(runStatus(), 200));
  const api = createMiningRunApi(createWdmApiClient({ fetchImpl: fetchMock, getAuthAdapter: () => ({ getAccessToken: () => 'token' }) }));
  await api.create({ ...runInput, extra: 'private' } as typeof runInput, new AbortController().signal);
  await api.read(runId);
  expect(fetchMock).toHaveBeenCalledTimes(2);
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/mining-runs');
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(runInput);
  expect(fetchMock.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer token');
  expect(fetchMock.mock.calls[0][1].signal).toBeDefined();
  expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/admin/mining-runs/' + runId);
  expect(fetchMock.mock.calls[1][1].method).toBe('GET'); expect(fetchMock.mock.calls[1][1].body).toBeUndefined();
});
it('never retries a network failure and refuses unconfirmed HTTP status', async () => {
  const fetchMock = jest.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(response(acceptedRun(), 200));
  const api = createMiningRunApi(createWdmApiClient({ fetchImpl: fetchMock }));
  await expect(api.create(runInput)).rejects.toMatchObject({ kind: 'network' });
  await expect(api.create(runInput)).rejects.toMatchObject({ kind: 'response' }); expect(fetchMock).toHaveBeenCalledTimes(2);
});
it('rejects invalid ID/config before accessing the network', () => {
  const fetchMock = jest.fn(), api = createMiningRunApi(createWdmApiClient({ fetchImpl: fetchMock }));
  expect(() => api.read(runId + '\n')).toThrow(); expect(() => api.create({ ...runInput, minsupValue: Infinity })).toThrow(); expect(fetchMock).not.toHaveBeenCalled();
});
