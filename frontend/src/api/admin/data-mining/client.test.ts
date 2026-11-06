/** @jest-environment node */
import { runInThisContext } from 'vm';
import { createWdmApiClient, normalizeWdmBaseUrl, WdmApiError } from './client';
import { WdmAuthAdapter, WdmPage } from './types';

// Jest 27's sandbox omits Node 18+ Web APIs; use the host's native implementations.
Object.assign(globalThis, runInThisContext('({ fetch, Headers, Response, FormData, Blob })'));

function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  const payload = status >= 200 && status < 300 ? { code: 'SUCCESS', message: 'Request completed successfully.', data: body, requestId: 'req-test' } : body;
  return new Response(JSON.stringify(payload), { status, headers: { 'Content-Type': 'application/json', ...(status < 300 ? { 'X-Request-Id': 'req-test' } : {}), ...headers } });
}

describe('WDM API client', () => {
  let fetchMock: jest.Mock;
  const client = (auth?: WdmAuthAdapter) => createWdmApiClient({
    fetchImpl: fetchMock as typeof fetch,
    getAuthAdapter: () => auth || null,
  });

  beforeEach(() => {
    fetchMock = jest.fn();
  });

  it('uses the relative API root, with repeated/encoded query parameters', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ content: [] }));
    await client().get('/v1/admin/mining-runs', {
      query: { page: 0, sort: ['createdAt,desc', 'runId,asc'], source: 'A & B', ignored: null },
    });
    const url = fetchMock.mock.calls[0][0];
    expect(url.startsWith('/api/v1/admin/mining-runs?')).toBe(true);
    const query = new URL(url, 'http://localhost').searchParams;
    expect(query.getAll('sort')).toEqual(['createdAt,desc', 'runId,asc']);
    expect(query.get('source')).toBe('A & B');
    expect(query.get('page')).toBe('0');
    expect(query.has('ignored')).toBe(false);
  });

  it('supports an explicit backend root without retaining port 8085', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ status: 'UP' }), { headers: { 'Content-Type': 'application/json' } }));
    await createWdmApiClient({ baseUrl: 'http://127.0.0.1:8080/api/', fetchImpl: fetchMock as typeof fetch }).get('/health', { responseMode: 'raw-health' });
    expect(fetchMock.mock.calls[0][0]).toBe('http://127.0.0.1:8080/api/health');
  });

  it('rejects legacy business JSON and cannot use health mode to bypass envelope validation', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { headers: { 'Content-Type': 'application/json' } }));
    await expect(client().get('/v1/admin/mining-runs')).rejects.toMatchObject({ kind: 'response', code: 'INVALID_RESPONSE' });
    fetchMock.mockClear();
    await expect(client().get('/v1/admin/mining-runs', { responseMode: 'raw-health' })).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each(['//other.example/api', 'ftp://other.example/api', 'https://u:p@example.com/api', '/api?token=x', '/api#x', '/wrong'])('rejects invalid API roots: %s', value => {
    expect(() => normalizeWdmBaseUrl(value)).toThrow();
  });

  it.each(['https://other.example/api', '//other.example', '/../health', '/%2e%2e/health', '/health?token=x'])('rejects endpoint overrides before sending credentials: %s', async path => {
    await expect(client({ getAccessToken: () => 'admin-token' }).get(path)).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('preserves exact string IDs/counts and pending null values', async () => {
    const page: WdmPage<{ patternId: string; supportAbs: string; patternCount: null }> = {
      content: [{ patternId: '18446744073709551615', supportAbs: '9007199254740993', patternCount: null }],
      page: 0, size: 20, totalElements: '9007199254740993', hasNext: true,
      sort: [{ field: 'patternId', direction: 'ASC' }],
    };
    fetchMock.mockResolvedValue(jsonResponse(page));
    await expect(client().get('/v1/admin/mining-runs/run-1/patterns')).resolves.toEqual(page);
  });

  it('reads the latest supplied Admin token on each request', async () => {
    let token = 'first';
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse({})));
    const api = client({ getAccessToken: () => token });
    await api.get('/v1/admin/mining-runs');
    token = 'second';
    await api.get('/v1/admin/mining-runs');
    expect(fetchMock.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer first');
    expect(fetchMock.mock.calls[1][1].headers.get('Authorization')).toBe('Bearer second');
  });

  it.each([null, undefined, '', '   '])('omits missing/blank credentials: %s', async token => {
    fetchMock.mockResolvedValue(jsonResponse({}));
    await client({ getAccessToken: () => token }).get('/v1/admin/mining-runs');
    expect(fetchMock.mock.calls[0][1].headers.has('Authorization')).toBe(false);
  });

  it('does not attach an Admin credential to public health requests', async () => {
    const getAccessToken = jest.fn(() => 'admin-token');
    fetchMock.mockResolvedValue(jsonResponse({}));
    await client({ getAccessToken }).get('/health', { auth: false });
    expect(getAccessToken).not.toHaveBeenCalled();
    expect(fetchMock.mock.calls[0][1].headers.has('Authorization')).toBe(false);
  });

  it.each([401, 403])('keeps structured HTTP %s errors and calls only the matching Auth callback', async status => {
    const onUnauthorized = jest.fn();
    const onForbidden = jest.fn();
    const code = status === 401 ? 'AUTHENTICATION_REQUIRED' : 'ACCESS_DENIED';
    fetchMock.mockResolvedValue(jsonResponse({
      status, code, message: 'Access denied.', path: '/api/v1/admin/mining-runs',
      requestId: 'req-1', details: [],
    }, status));
    await expect(client({ getAccessToken: () => 'expired', onUnauthorized, onForbidden })
      .post('/v1/admin/mining-runs', {})).rejects.toMatchObject({ kind: 'http', status, code, requestId: 'req-1' });
    expect(onUnauthorized).toHaveBeenCalledTimes(status === 401 ? 1 : 0);
    expect(onForbidden).toHaveBeenCalledTimes(status === 403 ? 1 : 0);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('retains field validation details', async () => {
    const details = [{ field: 'size', code: 'OUT_OF_RANGE', message: 'Invalid size.' }];
    fetchMock.mockResolvedValue(jsonResponse({
      status: 400, code: 'VALIDATION_FAILED', message: 'Invalid request.',
      path: '/api/v1/admin/mining-runs', requestId: 'req-2', details,
    }, 400));
    await expect(client().get('/v1/admin/mining-runs')).rejects.toMatchObject({ code: 'VALIDATION_FAILED', details });
  });

  it('does not replace a denial when a UI callback throws', async () => {
    fetchMock.mockResolvedValue(new Response('proxy denied', { status: 401 }));
    await expect(client({ getAccessToken: () => null, onUnauthorized: () => { throw new Error('UI callback failed'); } })
      .get('/v1/admin/mining-runs')).rejects.toMatchObject({ status: 401, code: 'HTTP_ERROR' });
  });

  it.each(['<html>proxy failure</html>', '{broken'])('handles non-contract error bodies without exposing raw content', async body => {
    fetchMock.mockResolvedValue(new Response(body, { status: 503, headers: { 'X-Request-Id': 'proxy-1' } }));
    await expect(client().get('/health')).rejects.toMatchObject({
      kind: 'http', status: 503, code: 'HTTP_ERROR', requestId: 'proxy-1',
      message: 'API request failed (HTTP 503).',
    });
  });

  it.each([
    new Response('<html>SPA fallback</html>', { headers: { 'Content-Type': 'text/html' } }),
    new Response('{broken', { headers: { 'Content-Type': 'application/json' } }),
  ])('rejects malformed successful responses', async response => {
    fetchMock.mockResolvedValue(response);
    await expect(client().get('/health')).rejects.toBeInstanceOf(WdmApiError);
  });

  it('normalizes network failure without retrying a POST', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(client().post('/v1/admin/mining-runs', {})).rejects.toMatchObject({ kind: 'network', code: 'NETWORK_ERROR' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('forwards cancellation and distinguishes it from a connection failure', async () => {
    const controller = new AbortController();
    controller.abort();
    fetchMock.mockRejectedValue(Object.assign(new Error('aborted'), { name: 'AbortError' }));
    await expect(client().get('/health', { signal: controller.signal })).rejects.toMatchObject({ code: 'REQUEST_ABORTED' });
    expect(fetchMock).not.toHaveBeenCalled(); // An already-cancelled request must not leave the browser.
  });

  it.each(['caller', 'session'])('cancels an in-flight request from either %s signal and removes listeners', async source => {
    const caller = new AbortController(), session = new AbortController();
    const removeCaller = jest.spyOn(caller.signal, 'removeEventListener');
    const removeSession = jest.spyOn(session.signal, 'removeEventListener');
    fetchMock.mockImplementation((_url, init: RequestInit) => new Promise((_resolve, reject) => {
      init.signal?.addEventListener('abort', () => reject(Object.assign(new Error('cancelled'), { name: 'AbortError' })));
    }));
    const result = client({ getAccessToken: () => null, getSessionSignal: () => session.signal }).get('/v1/admin/mining-runs', { signal: caller.signal });
    (source === 'caller' ? caller : session).abort();
    await expect(result).rejects.toMatchObject({ kind: 'aborted', code: 'REQUEST_ABORTED' });
    expect(removeCaller).toHaveBeenCalledWith('abort', expect.any(Function));
    expect(removeSession).toHaveBeenCalledWith('abort', expect.any(Function));
  });

  it('captures the current session signal per request and rejects a late successful response after logout', async () => {
    let session = new AbortController();
    let finish: (value: Response) => void = () => undefined;
    fetchMock.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
    const api = client({ getAccessToken: () => null, getSessionSignal: () => session.signal });
    const first = api.get('/v1/admin/mining-runs');
    const oldSignal = session.signal;session.abort();finish(jsonResponse({ stale: true }));
    await expect(first).rejects.toMatchObject({ code: 'REQUEST_ABORTED' });
    session = new AbortController();fetchMock.mockResolvedValueOnce(jsonResponse({ fresh: true }));
    await expect(api.get('/v1/admin/mining-runs')).resolves.toEqual({ fresh: true });
    expect(fetchMock.mock.calls[1][1].signal).toBe(session.signal);
    expect(fetchMock.mock.calls[1][1].signal).not.toBe(oldSignal);
  });

  it('does not consult the protected session signal for public health', async () => {
    const getSessionSignal = jest.fn(() => { throw new Error('must not access auth'); });
    fetchMock.mockResolvedValue(jsonResponse({ status: 'UP' }));
    await client({ getAccessToken: () => null, getSessionSignal }).get('/health', { auth: false });
    expect(getSessionSignal).not.toHaveBeenCalled();
  });

  it('serializes JSON bodies once and handles empty 204 responses', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await expect(client().post('/v1/admin/mining-runs', { snapshotId: 'snapshot-1' })).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0][1].body).toBe('{"snapshotId":"snapshot-1"}');
    expect(fetchMock.mock.calls[0][1].headers.get('Content-Type')).toBe('application/json');
  });

  it('lets the browser generate multipart boundaries', async () => {
    const body = new FormData();
    body.append('file', new Blob(['csv']), 'orders.csv');
    fetchMock.mockResolvedValue(jsonResponse({ snapshotId: 'snapshot-1' }, 201));
    await client().post('/v1/admin/dataset-snapshots', body);
    expect(fetchMock.mock.calls[0][1].body).toBe(body);
    expect(fetchMock.mock.calls[0][1].headers.has('Content-Type')).toBe(false);
  });

  it('downloads authorized artifacts as blobs while retaining response metadata', async () => {
    fetchMock.mockResolvedValue(new Response('1 -1 -2', { headers: {
      'Content-Type': 'text/plain; charset=UTF-8', 'Content-Disposition': 'attachment; filename="sequences.spmf"',
    } }));
    const result = await client({ getAccessToken: () => 'admin-token' }).download('/v1/admin/dataset-snapshots/snapshot-1/artifacts/sequence');
    expect(await result.blob.text()).toBe('1 -1 -2');
    expect(result.contentDisposition).toContain('sequences.spmf');
    expect(fetchMock.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer admin-token');
  });
});
