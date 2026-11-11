/** @jest-environment node */
import { runInThisContext } from 'vm';
import { getMiningRun, listMiningRuns, MiningRunApiError } from './MiningRun.api';
import { MiningRunParams } from './MiningRun.types';
import { authSession } from 'auth/session';
import { setWdmAuthAdapter, WdmApiError } from 'api/admin/data-mining';

Object.assign(globalThis, runInThisContext('({ fetch, Headers, Response })'));
const originalFetch = global.fetch;
const fetchMock = jest.fn();
const params: MiningRunParams = { page: 0, size: 20, algorithm: '', status: '' };
const page = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
const envelope = (data: unknown, requestId = 'run-request') => ({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId });
function response(data: unknown, status = 200, requestId = 'run-request') {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json', 'X-Request-Id': requestId } });
}
beforeEach(() => {
  global.fetch = fetchMock;
  fetchMock.mockReset();
  authSession.unauthorized();
  authSession.useStore.setState({ status: 'authenticated', forbidden: false, accessToken: 'shared-test-access', user: {
    userId: 'admin', username: 'fixture', email: null, displayName: 'Admin', status: 'ACTIVE', roles: ['ADMIN'], emailVerified: false,
  } });
  // Exactly the adapter installed by the existing AuthBoundary; no separate auth mechanism.
  setWdmAuthAdapter({ getAccessToken: authSession.token, getSessionSignal: authSession.signal,
    onUnauthorized: authSession.unauthorized, onForbidden: authSession.forbidden });
});
afterEach(() => { setWdmAuthAdapter(null); authSession.unauthorized(); });
afterAll(() => { global.fetch = originalFetch; });

test('unwraps the shared envelope once with zero-based pagination and omitted empty filters', async () => {
  fetchMock.mockResolvedValue(response(envelope(page)));
  await expect(listMiningRuns(params)).resolves.toEqual(page);
  const [url, options] = fetchMock.mock.calls[0];
  expect(url).toBe('/api/v1/admin/mining-run-history?page=0&size=20');
  expect(options.credentials).toBe('omit');
  expect(options.headers.get('Authorization')).toBe('Bearer shared-test-access');
});

test('keeps exact filters and reads the latest shared token without refreshing or replaying', async () => {
  fetchMock.mockImplementation(async () => response(envelope(page)));
  await listMiningRuns({ ...params, page: 2, size: 50, algorithm: 'WSPM_BITPRUNE', status: 'FAILED' });
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/mining-run-history?page=2&size=50&algorithm=WSPM_BITPRUNE&status=FAILED');
  authSession.useStore.setState({ accessToken: 'rotated-access' });
  await listMiningRuns(params);
  expect(fetchMock.mock.calls[1][1].headers.get('Authorization')).toBe('Bearer rotated-access');
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

test('encodes the RUN ID as one path segment', async () => {
  fetchMock.mockResolvedValue(response(envelope({ runId: 'run #1' })));
  await getMiningRun('run #1');
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/mining-run-history/run%20%231');
});

test.each([page, envelope(page, 'wrong-request')])('rejects raw DTO or mismatched request correlation', async body => {
  fetchMock.mockResolvedValue(response(body));
  await expect(listMiningRuns(params)).rejects.toMatchObject({ kind: 'response', code: 'INVALID_RESPONSE', requestId: 'run-request' });
});

test.each([400, 401, 403, 404, 500])('retains HTTP %s correlation/details and invokes shared denial handling', async status => {
  const details = status === 400 ? [{ field: 'algorithm', code: 'INVALID_FILTER', message: 'Invalid filter.' }] : [];
  fetchMock.mockResolvedValue(response({ status, code: 'SAFE_CODE', message: 'private diagnostic',
    path: '/api/v1/admin/mining-run-history', requestId: 'run-request', details }, status));
  await expect(listMiningRuns(params)).rejects.toMatchObject({ status, requestId: 'run-request', details, code: 'SAFE_CODE' });
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(authSession.useStore.getState().status).toBe(status === 401 ? 'anonymous' : 'authenticated');
  expect(authSession.useStore.getState().forbidden).toBe(status === 403);
});

test('keeps diagnostic bodies out of the UI error message', async () => {
  fetchMock.mockResolvedValue(response({ status: 500, code: 'INTERNAL_ERROR', message: 'private diagnostic',
    path: '/api/v1/admin/mining-run-history', requestId: 'run-request', details: [] }, 500));
  await expect(listMiningRuns(params)).rejects.toThrow('Unable to load Mining RUN data.');
});

test('normalizes network failure through shared transport without retry', async () => {
  fetchMock.mockRejectedValue(new TypeError('private network error'));
  await expect(listMiningRuns(params)).rejects.toMatchObject({ kind: 'network', code: 'NETWORK_ERROR' });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

test.each(['anonymous', 'customer', 'forbidden'])('never requests for a locally blocked %s session', async mode => {
  if (mode === 'anonymous') authSession.unauthorized();
  if (mode === 'customer') {
    const user = authSession.useStore.getState().user;
    if (!user) throw new Error('Missing test identity');
    authSession.useStore.setState({ user: { ...user, roles: ['CUSTOMER'] } });
  }
  if (mode === 'forbidden') authSession.forbidden();
  await expect(listMiningRuns(params)).rejects.toBeInstanceOf(MiningRunApiError);
  expect(fetchMock).not.toHaveBeenCalled();
});

test.each(['caller', 'session'])('cancels an in-flight request on %s invalidation', async cause => {
  const controller = new AbortController();
  let finish: (value: Response) => void = () => undefined;
  fetchMock.mockReturnValue(new Promise(resolve => { finish = resolve; }));
  const request = listMiningRuns(params, controller.signal);
  const signal = fetchMock.mock.calls[0][1].signal as AbortSignal;
  if (cause === 'caller') controller.abort(); else authSession.unauthorized();
  expect(signal.aborted).toBe(true);
  finish(response(envelope(page)));
  await expect(request).rejects.toMatchObject({ kind: 'aborted' });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

test('uses the existing shared error type for cancellation', () => {
  expect(new MiningRunApiError(403)).toBeInstanceOf(WdmApiError);
});
