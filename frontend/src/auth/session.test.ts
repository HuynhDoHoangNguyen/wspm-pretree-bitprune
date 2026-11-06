import { AuthApi, AuthError, AuthGrant, AuthIdentity } from './api';
import { browserSessionLock, createAuthSession, AuthSession, SessionEvent } from './session';

const user: AuthIdentity = { userId: 'opaque-user-id', username: 'customer.test', email: 'customer@example.test',
  displayName: 'Customer', status: 'ACTIVE', roles: ['ADMIN', 'CUSTOMER'], emailVerified: false };
const grant: AuthGrant = { accessToken: 'access-one', tokenType: 'Bearer', expiresIn: 900,
  expiresAt: '2026-11-04T07:08:09Z', user };
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(r => { resolve = r; }); return { promise, resolve }; }
const flush = async () => { for (let index = 0; index < 12; index++) await Promise.resolve(); };
let api: jest.Mocked<AuthApi>, session: AuthSession;
beforeEach(() => {
  jest.useFakeTimers();
  api = { login: jest.fn().mockResolvedValue(grant), refresh: jest.fn().mockResolvedValue(grant),
    me: jest.fn().mockResolvedValue(user), logout: jest.fn().mockResolvedValue(undefined), register: jest.fn().mockResolvedValue(user) };
  session = createAuthSession(api, work => work());
});
afterEach(() => { session.stop(); jest.useRealTimers(); jest.restoreAllMocks(); });
test('bootstrap keeps protected identity/token absent until refresh and me both succeed', async () => {
  const identity = deferred<AuthIdentity>(); api.me.mockReturnValue(identity.promise);
  const pending = session.bootstrap(); await flush();
  expect(session.useStore.getState()).toMatchObject({ status: 'bootstrapping', user: null, accessToken: null });
  identity.resolve(user); await pending;
  expect(session.useStore.getState()).toMatchObject({ status: 'authenticated', user, accessToken: 'access-one' });
});
test.each([401, 503])('bootstrap failure %i never restores a cached identity', async status => {
  api.refresh.mockRejectedValue(new AuthError(status, 'AUTH_ERROR', 'safe'));
  await expect(session.bootstrap()).rejects.toBeInstanceOf(AuthError);
  expect(session.useStore.getState()).toMatchObject({ status: status === 401 ? 'anonymous' : 'unavailable', user: null, accessToken: null });
});
test('bootstrap and concurrent refresh share one flight', async () => {
  const reply = deferred<AuthGrant>(); api.refresh.mockReturnValue(reply.promise);
  const first = session.bootstrap(), second = session.refresh(), third = session.bootstrap(); await flush();
  expect(api.refresh).toHaveBeenCalledTimes(1); reply.resolve(grant);
  await Promise.all([first, second, third]); expect(api.me).toHaveBeenCalledTimes(1);
});
test('logout queues after refresh and stale refresh response cannot resurrect identity', async () => {
  await session.login('customer.test', '12345678');
  const reply = deferred<AuthGrant>(); api.refresh.mockReturnValue(reply.promise);
  const pending = session.refresh(); await flush(); const logout = session.logout();
  expect(session.useStore.getState()).toMatchObject({ status: 'signing-out', accessToken: null });
  expect(api.logout).not.toHaveBeenCalled(); reply.resolve({ ...grant, accessToken: 'access-stale' });
  await Promise.all([pending, logout]);
  expect(session.useStore.getState()).toMatchObject({ status: 'anonymous', user: null, accessToken: null });
  expect(api.me).toHaveBeenCalledTimes(1); expect(api.logout).toHaveBeenCalledTimes(1);
});
test('external logout invalidates pending login without sending tokens across tabs', async () => {
  const reply = deferred<AuthGrant>(); api.login.mockReturnValue(reply.promise);
  const pending = session.login('customer.test', '12345678').catch(error => error); await flush();
  session.receive({ kind: 'logout', source: 'another-tab' }); reply.resolve(grant);
  expect(await pending).toMatchObject({ code: 'SUPERSEDED' });
  expect(session.useStore.getState()).toMatchObject({ status: 'anonymous', accessToken: null }); expect(api.me).not.toHaveBeenCalled();
});
test('failed server logout clears local credentials but reports unconfirmed server state', async () => {
  await session.login('customer.test', '12345678'); const signal = session.signal(); const clear = jest.fn(); session.onClear(clear);
  api.logout.mockRejectedValue(new AuthError(503, 'SERVICE_UNAVAILABLE', 'safe'));
  await expect(session.logout()).rejects.toBeInstanceOf(AuthError);
  expect(signal.aborted).toBe(true); expect(clear).toHaveBeenCalled();
  expect(session.useStore.getState()).toMatchObject({ status: 'unavailable', user: null, accessToken: null });
  expect(session.useStore.getState().error).toContain('chưa xác nhận');
});
test('403 retains identity, clears protected work and never initiates refresh/replay', async () => {
  await session.login('customer.test', '12345678'); const signal = session.signal();
  session.forbidden(); expect(signal.aborted).toBe(true); expect(api.refresh).not.toHaveBeenCalled();
  expect(session.useStore.getState()).toMatchObject({ status: 'authenticated', forbidden: true, user });
  await session.refresh(); expect(session.useStore.getState().forbidden).toBe(true);
  api.me.mockResolvedValue({ ...user, roles: ['CUSTOMER'] }); await session.checkIdentity();
  expect(session.useStore.getState().user?.roles).toEqual(['CUSTOMER']);
});
test('proactive refresh uses relative TTL once and stops after expired/revoked refresh', async () => {
  await session.login('customer.test', '12345678');
  api.refresh.mockRejectedValue(new AuthError(401, 'AUTHENTICATION_REQUIRED', 'safe'));
  jest.advanceTimersByTime(870000); await flush();
  expect(api.refresh).toHaveBeenCalledTimes(1); expect(session.useStore.getState().status).toBe('anonymous');
  jest.advanceTimersByTime(3600000); await flush(); expect(api.refresh).toHaveBeenCalledTimes(1);
});
test('memory state never writes token/identity and cross-tab events contain no credentials', async () => {
  const persisted = jest.spyOn(Storage.prototype, 'setItem'); const events: SessionEvent[] = [];
  session.setBroadcast(event => events.push(event)); await session.login('customer.test', '12345678'); await session.logout();
  expect(persisted).not.toHaveBeenCalled();
  expect(events.map(event => event.kind)).toEqual(['identity-changed', 'logout-requested', 'logout']);
  events.forEach(event => expect(Object.keys(event).sort()).toEqual(['kind', 'source']));
  expect(JSON.stringify(events)).not.toContain('access-one');
});
test('Web Locks wraps cookie-changing operations when the browser supports it', async () => {
  const request = jest.fn((_name, work) => work());
  Object.defineProperty(navigator, 'locks', { configurable: true, value: { request } });
  try { await browserSessionLock(async () => 'done'); expect(request).toHaveBeenCalledWith('wspm-shared-auth-session', expect.any(Function)); }
  finally { Reflect.deleteProperty(navigator, 'locks'); }
});
test('register does not log in or manufacture profile state', async () => {
  const input = { username: 'customer.test', password: '12345678', email: 'customer@example.test', displayName: 'Customer' };
  expect(await session.register(input)).toEqual(user); expect(api.login).not.toHaveBeenCalled();
  expect(session.useStore.getState().user).toBeNull();
});

test('failed browser lock acquisition fails closed instead of leaving bootstrap stuck', async () => {
  session.stop();
  session = createAuthSession(api, async () => { throw new Error('Lock unavailable'); });
  await expect(session.bootstrap()).rejects.toThrow('Lock unavailable');
  expect(session.useStore.getState()).toMatchObject({ status: 'unavailable', user: null, accessToken: null });
  expect(api.refresh).not.toHaveBeenCalled();
});
