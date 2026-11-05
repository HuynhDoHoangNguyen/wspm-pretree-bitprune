/** @jest-environment node */
import { runInThisContext } from 'vm';
import { getBackendHealth, setWdmAuthAdapter, wdmApi } from './index';

Object.assign(globalThis, runInThisContext('({ fetch, Headers, Response, FormData, Blob })'));

afterEach(() => {
  setWdmAuthAdapter(null);
  jest.restoreAllMocks();
});

it('checks the identity of the existing backend on the public health route', async () => {
  const getAccessToken = jest.fn(() => 'admin-token');
  setWdmAuthAdapter({ getAccessToken });
  const mock = jest.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
    JSON.stringify({ status: 'UP', service: 'wspm-backend' }),
    { headers: { 'Content-Type': 'application/json' } }
  ));
  await expect(getBackendHealth()).resolves.toEqual({ status: 'UP', service: 'wspm-backend' });
  expect(mock.mock.calls[0][0]).toBe('/api/health');
  expect(getAccessToken).not.toHaveBeenCalled();
});

it.each([null, { status: 'UP', service: 'other-backend' }, { status: 'DOWN', service: 'wspm-backend' }])('rejects unrelated or unhealthy backend responses', async response => {
  jest.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(response), {
    headers: { 'Content-Type': 'application/json' },
  }));
  await expect(getBackendHealth()).rejects.toMatchObject({ code: 'INVALID_HEALTH_RESPONSE' });
});

it('uses the installed shared Auth adapter and supports detaching it', async () => {
  const mock = jest.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({
    code: 'SUCCESS', message: 'Request completed successfully.', data: {}, requestId: 'test-id',
  }), {
    headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'test-id' },
  }));
  setWdmAuthAdapter({ getAccessToken: () => 'provided-by-shared-auth' });
  await wdmApi.get('/v1/admin/mining-runs');
  expect(new Headers(mock.mock.calls[0][1]?.headers).get('Authorization')).toBe('Bearer provided-by-shared-auth');
  setWdmAuthAdapter(null);
  await wdmApi.get('/v1/admin/mining-runs');
  expect(new Headers(mock.mock.calls[1][1]?.headers).has('Authorization')).toBe(false);
});
