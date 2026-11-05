import create from 'zustand';
import { AuthApi, AuthError, AuthGrant, AuthIdentity, createAuthApi, RegistrationInput } from './api';

export type AuthStatus = 'bootstrapping' | 'authenticated' | 'anonymous' | 'unavailable' | 'signing-out';
export interface AuthState {
  status: AuthStatus;
  user: AuthIdentity | null;
  accessToken: string | null;
  forbidden: boolean;
  error: string | null;
}
const empty: AuthState = { status: 'bootstrapping', user: null, accessToken: null, forbidden: false, error: null };
export interface SessionEvent { kind: 'identity-changed' | 'logout-requested' | 'logout'; source: string }
type Lock = <T>(work: () => Promise<T>) => Promise<T>;
export function browserSessionLock<T>(work: () => Promise<T>): Promise<T> {
  const locks = (typeof navigator === 'undefined' ? undefined : navigator as Navigator & {
    locks?: { request<T>(name: string, work: () => Promise<T>): Promise<T> };
  })?.locks;
  return locks ? locks.request('wspm-shared-auth-session', work) : work();
}

/** One in-memory authority. No persistence/devtools, decoded-JWT roles or token broadcast. */
export function createAuthSession(api: AuthApi = createAuthApi(), lock: Lock = browserSessionLock) {
  const useStore = create<AuthState>(() => ({ ...empty }));
  const source = Date.now().toString(36) + Math.random().toString(36).slice(2);
  let generation = 0, started = false;
  let queue: Promise<unknown> = Promise.resolve(), refreshFlight: Promise<void> | null = null;
  let flightGeneration = -1;
  let timer: ReturnType<typeof setTimeout> | undefined, due = Infinity;
  let protectedRequests = new AbortController();
  const clearListeners = new Set<() => void>();
  let broadcast: ((event: SessionEvent) => void) | undefined;
  const notify = (kind: SessionEvent['kind']) => broadcast?.({ kind, source });
  const serial = <T,>(work: () => Promise<T>): Promise<T> => {
    const result = queue.then(() => lock(work));
    queue = result.catch(() => undefined);
    return result;
  };
  function invalidate(status: AuthStatus, error: string | null = null) {
    generation++;
    clearTimeout(timer); due = Infinity;
    protectedRequests.abort(); protectedRequests = new AbortController();
    useStore.setState({ ...empty, status, error });
    clearListeners.forEach(listener => listener());
    return generation;
  }
  function fail(error: unknown, ticket: number, quiet401 = false) {
    if (ticket !== generation) return;
    const anonymous = error instanceof AuthError && error.status === 401;
    invalidate(anonymous ? 'anonymous' : 'unavailable', anonymous && quiet401 ? null :
      anonymous ? 'Phiên đăng nhập không còn hợp lệ.' : 'Không thể xác minh phiên. Vui lòng thử lại.');
  }
  async function verified(grant: AuthGrant, ticket: number) {
    if (ticket !== generation) return;
    const user = await api.me(grant.accessToken);
    if (user.userId !== grant.user.userId) throw new AuthError(null, 'INVALID_RESPONSE', 'Danh tính phiên không nhất quán.');
    if (ticket !== generation) return;
    const previous = useStore.getState().user;
    if (previous && previous.userId !== user.userId) {
      protectedRequests.abort(); protectedRequests = new AbortController();
      clearListeners.forEach(listener => listener());
    }
    const forbidden = previous?.userId === user.userId && useStore.getState().forbidden;
    useStore.setState({ status: 'authenticated', user, accessToken: grant.accessToken, forbidden: !!forbidden, error: null });
    // Relative TTL avoids a refresh loop when browser/server wall clocks differ.
    due = Date.now() + (grant.expiresIn - 30) * 1000;
    clearTimeout(timer);
    timer = setTimeout(() => { void refresh().catch(() => undefined); }, (grant.expiresIn - 30) * 1000);
  }
  function refresh(): Promise<void> {
    if (refreshFlight && flightGeneration === generation) return refreshFlight;
    const ticket = generation;
    const work = serial(async () => {
      if (ticket !== generation) return;
      await verified(await api.refresh(), ticket);
    }).catch(error => { fail(error, ticket, true); throw error; });
    refreshFlight = work;
    flightGeneration = ticket;
    void work.then(() => { if (refreshFlight === work) refreshFlight = null; }, () => { if (refreshFlight === work) refreshFlight = null; });
    return work;
  }
  const session = {
    useStore,
    bootstrap() {
      if (started) return refreshFlight || Promise.resolve();
      started = true;
      return refresh();
    },
    restore() {
      if (useStore.getState().status === 'bootstrapping' && refreshFlight && flightGeneration === generation) return refreshFlight;
      invalidate('bootstrapping'); return refresh();
    },
    register: (input: RegistrationInput) => serial(() => api.register(input)),
    async login(username: string, password: string) {
      const ticket = invalidate('anonymous');
      return serial(async () => {
        if (ticket !== generation) throw new AuthError(null, 'SUPERSEDED', 'Thao tác đã bị thay thế.');
        await verified(await api.login(username, password), ticket);
        if (ticket !== generation) throw new AuthError(null, 'SUPERSEDED', 'Thao tác đã bị thay thế.');
        notify('identity-changed');
      }).catch(error => { fail(error, ticket); throw error; });
    },
    refresh,
    async logout() {
      const ticket = invalidate('signing-out'); notify('logout-requested');
      return serial(async () => {
        await api.logout();
        if (ticket === generation) invalidate('anonymous');
        notify('logout');
      }).catch(error => {
        if (ticket === generation) invalidate('unavailable', 'Đã xóa thông tin truy cập cục bộ; chưa xác nhận đăng xuất trên máy chủ.');
        throw error;
      });
    },
    unauthorized() { invalidate('anonymous', 'Phiên truy cập bị từ chối. Vui lòng đăng nhập hoặc khôi phục phiên.'); },
    forbidden() {
      protectedRequests.abort(); protectedRequests = new AbortController();
      useStore.setState({ forbidden: true }); clearListeners.forEach(listener => listener());
    },
    async checkIdentity() {
      const state = useStore.getState(), ticket = generation;
      if (!state.accessToken) return session.restore();
      try {
        const user = await api.me(state.accessToken);
        if (state.user && user.userId !== state.user.userId) throw new AuthError(null, 'INVALID_RESPONSE', 'Danh tính phiên không nhất quán.');
        if (ticket === generation) useStore.setState({ user, forbidden: false });
      } catch (error) { fail(error, ticket); throw error; }
    },
    ensureFresh() { if (useStore.getState().status === 'authenticated' && Date.now() >= due) void refresh().catch(() => undefined); },
    token: () => useStore.getState().status === 'authenticated' ? useStore.getState().accessToken : null,
    signal: () => protectedRequests.signal,
    onClear(listener: () => void) { clearListeners.add(listener); return () => { clearListeners.delete(listener); }; },
    setBroadcast(sender?: (event: SessionEvent) => void) { broadcast = sender; },
    receive(event: unknown) {
      const message = event as SessionEvent | null;
      if (message && message.source !== source && ['identity-changed', 'logout-requested', 'logout'].includes(message.kind)) {
        invalidate('anonymous', message.kind === 'identity-changed' ? 'Tài khoản đã thay đổi ở tab khác. Hãy khôi phục phiên để tiếp tục.' : null);
      }
    },
    stop() { started = false; invalidate('bootstrapping'); broadcast = undefined; },
  };
  return session;
}
export const authSession = createAuthSession();
export type AuthSession = ReturnType<typeof createAuthSession>;
export const useSharedAuth = authSession.useStore;
