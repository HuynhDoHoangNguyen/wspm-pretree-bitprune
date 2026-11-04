import { AuthError, createAuthApi, validRegistration } from './api';

export const customer = { userId: '9007199254740993', username: 'customer.test', email: 'customer@example.test',
  displayName: 'Customer', status: 'ACTIVE' as const, roles: ['CUSTOMER'], emailVerified: false };
const token = { accessToken: 'opaque-access-token', tokenType: 'Bearer', expiresIn: 900,
  expiresAt: '2026-11-04T07:08:09Z', user: customer };
function response(body: unknown, status = 200, json = true): Response {
  return { ok: status >= 200 && status < 300, status,
    headers: { get: (name: string) => name === 'Content-Type' ? json ? 'application/json' : 'text/html' : 'request-test' },
    json: async () => status >= 200 && status < 300 && status !== 204 ? { code: 'SUCCESS', message: 'Request completed successfully.', data: body, requestId: 'request-test' } : body } as unknown as Response;
}
test('login uses same-origin CSRF and exact DTO without legacy/profile/cart calls', async () => {
  const fetcher = jest.fn().mockResolvedValueOnce(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }))
    .mockResolvedValueOnce(response(token));
  const api = createAuthApi(fetcher);
  const result = await api.login('customer.test', ' 123456 ');
  expect(result.user.userId).toBe('9007199254740993');
  expect(fetcher.mock.calls.map(call => call[0])).toEqual(['/api/v1/auth/csrf', '/api/v1/auth/login']);
  expect(fetcher.mock.calls[1][1]).toMatchObject({ method: 'POST', credentials: 'same-origin', redirect: 'error',
    headers: { 'X-XSRF-TOKEN': 'csrf' }, body: JSON.stringify({ username: 'customer.test', password: ' 123456 ' }) });
  expect(fetcher.mock.calls[1][1].headers.Authorization).toBeUndefined();
});
test.each(['refresh', 'logout'] as const)('%s has an empty body and no refresh token in JS', async operation => {
  const fetcher = jest.fn().mockResolvedValueOnce(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }))
    .mockResolvedValueOnce(operation === 'logout' ? response(undefined, 204) : response(token));
  await createAuthApi(fetcher)[operation]();
  expect(fetcher.mock.calls[1][1].body).toBeUndefined();
  expect(fetcher.mock.calls[1][1].credentials).toBe('same-origin');
});
test('identity uses the current Bearer and validates server roles/IDs', async () => {
  const fetcher = jest.fn().mockResolvedValue(response(customer));
  expect(await createAuthApi(fetcher).me('access')).toEqual(customer);
  expect(fetcher.mock.calls[0][1].headers.Authorization).toBe('Bearer access');
  fetcher.mockResolvedValue(response({ ...customer, userId: 123 }));
  await expect(createAuthApi(fetcher).me('access')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
});
test.each([401, 403, 409, 503])('sanitizes HTTP %i and retains request correlation', async status => {
  const fetcher = jest.fn().mockResolvedValue(response({ code: 'SAFE_CODE', message: 'private raw detail' }, status));
  const error = await createAuthApi(fetcher).me('access').catch(value => value);
  expect(error).toBeInstanceOf(AuthError); expect(error.status).toBe(status); expect(error.requestId).toBe('request-test');
  expect(error.message).not.toContain('private raw detail');
});
test('rejects non-JSON success and malformed token responses without trusting decoded JWTs', async () => {
  const fetcher = jest.fn().mockResolvedValue(response('HTML', 200, false));
  await expect(createAuthApi(fetcher).me('token')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  fetcher.mockResolvedValueOnce(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' })).mockResolvedValueOnce(response({ ...token, expiresIn: 0 }));
  await expect(createAuthApi(fetcher).refresh()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
});
test('registration honors 8 code points / 72 UTF-8 bytes without trimming password', () => {
  const input = { username: 'customer.test', email: 'customer@example.test', displayName: 'Customer', password: '12345678' };
  expect(validRegistration(input)).toBe(true);
  expect(validRegistration({ ...input, password: '1234567' })).toBe(false);
  expect(validRegistration({ ...input, password: ' 123456 ' })).toBe(true);
  expect(validRegistration({ ...input, password: 'é'.repeat(36) })).toBe(true);
  expect(validRegistration({ ...input, password: 'é'.repeat(37) })).toBe(false);
  expect(validRegistration({ ...input, password: '😀'.repeat(8) })).toBe(true);
  expect(validRegistration({ ...input, password: 'abc\0defgh' })).toBe(false);
});

test('nullable V2 identity fields stay nullable and unexpected profile/secrets are not copied', async () => {
  const fetcher = jest.fn().mockResolvedValue(response({ ...customer, email: null, username: null, passwordHash: 'not-allowed' }));
  const result = await createAuthApi(fetcher).me('access');
  expect(result.email).toBeNull(); expect(result.username).toBeNull();
  expect(result).not.toHaveProperty('passwordHash');
});
