import { isApiErrorResponse, unwrapApiResponse } from 'api/common/response';
export interface AuthIdentity {
  userId: string;
  username: string | null;
  email: string | null;
  displayName: string;
  status: 'ACTIVE';
  roles: string[];
  emailVerified: boolean;
}
export interface AuthGrant {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  expiresAt: string;
  user: AuthIdentity;
}
export interface RegistrationInput { username: string; email: string; password: string; displayName: string }
export class AuthError extends Error {
  constructor(public readonly status: number | null, public readonly code: string,
    message: string, public readonly requestId: string | null = null) { super(message); }
}

export function validRegistration(input: RegistrationInput): boolean {
  const password = input.password;
  return /^[a-z0-9][a-z0-9._-]{2,79}$/i.test(input.username.trim())
    && !!input.displayName.trim() && Array.from(input.displayName.trim()).length <= 160
    && /^[\x21-\x7e]+@[^\s@]+$/.test(input.email.trim()) && input.email.trim().length <= 254
    && Array.from(password).length >= 8 && new Blob([password]).size <= 72
    && !password.includes('\0') && !Array.from(password).some(c => c.length === 1 && /[\ud800-\udfff]/.test(c));
}
function identity(value: unknown): AuthIdentity {
  const user = value as AuthIdentity | null;
  if (!user || typeof user.userId !== 'string' || !user.userId.trim() || user.userId.length > 40
    || (typeof user.username !== 'string' && user.username !== null) || (typeof user.email !== 'string' && user.email !== null) || typeof user.displayName !== 'string'
    || user.status !== 'ACTIVE' || typeof user.emailVerified !== 'boolean' || !Array.isArray(user.roles)
    || !user.roles.every(role => typeof role === 'string' && !!role.trim() && role.length <= 40)) {
    throw new AuthError(null, 'INVALID_RESPONSE', 'Phản hồi danh tính không hợp lệ.');
  }
  return { userId: user.userId, username: user.username, email: user.email, displayName: user.displayName,
    status: user.status, roles: [...user.roles], emailVerified: user.emailVerified };
}
function grant(value: unknown): AuthGrant {
  const result = value as AuthGrant | null;
  if (!result || typeof result.accessToken !== 'string' || !result.accessToken.trim()
    || result.tokenType !== 'Bearer' || result.expiresIn !== 900 || typeof result.expiresAt !== 'string'
    || !Number.isFinite(Date.parse(result.expiresAt))) {
    throw new AuthError(null, 'INVALID_RESPONSE', 'Phản hồi phiên không hợp lệ.');
  }
  return { accessToken: result.accessToken, tokenType: result.tokenType, expiresIn: result.expiresIn,
    expiresAt: result.expiresAt, user: identity(result.user) };
}

/** Auth is same-origin only. Refresh credentials are exclusively browser cookies. */
export function createAuthApi(fetchImpl: typeof fetch = (...args) => fetch(...args)) {
  async function request(path: string, method: 'GET' | 'POST', body?: unknown, token?: string, csrf?: string): Promise<unknown> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (token) headers.Authorization = 'Bearer ' + token;
    if (csrf) headers['X-XSRF-TOKEN'] = csrf;
    let response: Response;
    try {
      response = await fetchImpl('/api/v1/auth/' + path, {
        method, headers, credentials: 'same-origin', redirect: 'error',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch { throw new AuthError(null, 'NETWORK_ERROR', 'Không thể kết nối máy chủ.'); }
    const requestId = response.headers.get('X-Request-Id');
    if (response.status === 204 && response.ok) return undefined;
    let data: unknown;
    try {
      if (/\bapplication\/(?:[\w.-]+\+)?json\b/i.test(response.headers.get('Content-Type') || '')) data = await response.json();
    } catch { /* Raw transport/error payloads never become displayed messages. */ }
    if (!response.ok) {
      const code = isApiErrorResponse(data, response.status) ? data.code : 'HTTP_ERROR';
      throw new AuthError(response.status, code,
        'Yêu cầu xác thực không thành công.', requestId);
    }
    if (data === undefined) throw new AuthError(response.status, 'INVALID_RESPONSE', 'Máy chủ trả dữ liệu không hợp lệ.', requestId);
    try { return unwrapApiResponse(data, requestId); }
    catch { throw new AuthError(response.status, 'INVALID_RESPONSE', 'Máy chủ trả dữ liệu không hợp lệ.', requestId); }
  }
  async function post(path: string, body?: unknown) {
    const csrf = await request('csrf', 'GET') as { headerName?: string; token?: string };
    if (csrf.headerName !== 'X-XSRF-TOKEN' || typeof csrf.token !== 'string' || !csrf.token) {
      throw new AuthError(null, 'INVALID_RESPONSE', 'Không lấy được CSRF token.');
    }
    return request(path, 'POST', body, undefined, csrf.token);
  }
  return {
    register: async (input: RegistrationInput) => identity(await post('register', input)),
    login: async (username: string, password: string) => grant(await post('login', { username, password })),
    refresh: async () => grant(await post('refresh')),
    me: async (token: string) => identity(await request('me', 'GET', undefined, token)),
    logout: async () => { await post('logout'); },
  };
}
export type AuthApi = ReturnType<typeof createAuthApi>;
export function authMessage(error: unknown): string {
  if (!(error instanceof AuthError)) return 'Không thể hoàn tất yêu cầu.';
  if (error.status === 401) return 'Tên tài khoản, mật khẩu hoặc phiên đăng nhập không hợp lệ.';
  if (error.status === 409) return 'Tên tài khoản hoặc email đã được sử dụng.';
  if (error.status === 400) return 'Dữ liệu không hợp lệ. Vui lòng kiểm tra các trường.';
  if (error.status === 403) return 'Yêu cầu bị từ chối. Vui lòng tải lại trang và thử lại.';
  return error.message + (error.requestId ? ' Mã yêu cầu: ' + error.requestId : '');
}
