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
export type RegistrationField = keyof RegistrationInput;
export type AuthFormErrors = Partial<Record<RegistrationField | 'passwordConfirmation', string>>;
const registrationFields: RegistrationField[] = ['username', 'email', 'displayName', 'password'];
export class AuthError extends Error {
  constructor(public readonly status: number | null, public readonly code: string,
    message: string, public readonly requestId: string | null = null,
    public readonly fields: readonly RegistrationField[] = []) { super(message); }
}

export function registrationErrors(input: RegistrationInput): AuthFormErrors {
  const errors: AuthFormErrors = {};
  if (!/^[a-z0-9][a-z0-9._-]{2,79}$/i.test(input.username.trim())) {
    errors.username = 'Tên tài khoản cần 3–80 ký tự: chữ, số, dấu chấm, gạch dưới hoặc gạch ngang; bắt đầu bằng chữ hoặc số.';
  }
  if (!/^[\x21-\x7e]+@[^\s@]+$/.test(input.email.trim()) || input.email.trim().length > 254) errors.email = 'Vui lòng nhập email hợp lệ.';
  if (!input.displayName.trim() || Array.from(input.displayName.trim()).length > 160) errors.displayName = 'Tên hiển thị cần từ 1 đến 160 ký tự.';
  const password = input.password;
  if (Array.from(password).length < 8) errors.password = 'Mật khẩu cần ít nhất 8 ký tự.';
  else if (new Blob([password]).size > 72) errors.password = 'Mật khẩu quá dài (tối đa 72 byte UTF-8).';
  else if (password.includes('\0') || Array.from(password).some(c => c.length === 1 && /[\ud800-\udfff]/.test(c))) errors.password = 'Mật khẩu chứa ký tự không hợp lệ.';
  return errors;
}
export function validRegistration(input: RegistrationInput): boolean {
  return Object.keys(registrationErrors(input)).length === 0;
}

/** Field names are whitelisted; raw server detail messages are never rendered. */
export function authFieldErrors(error: unknown): AuthFormErrors {
  if (!(error instanceof AuthError)) return {};
  if (error.status === 401) return { username: 'Kiểm tra lại tên tài khoản và mật khẩu.', password: 'Kiểm tra lại tên tài khoản và mật khẩu.' };
  if (error.status === 409) return { username: 'Tên tài khoản hoặc email đã được sử dụng.', email: 'Tên tài khoản hoặc email đã được sử dụng.' };
  if (error.status !== 400) return {};
  const messages: Record<RegistrationField, string> = { username: 'Tên tài khoản không hợp lệ.',
    email: 'Email không hợp lệ.', displayName: 'Tên hiển thị không hợp lệ.', password: 'Mật khẩu không hợp lệ.' };
  return Object.fromEntries(registrationFields.filter(field => error.fields.includes(field)).map(field => [field, messages[field]]));
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
      const failure = isApiErrorResponse(data, response.status) ? data : null;
      const code = failure?.code || 'HTTP_ERROR';
      const details = failure?.details || [];
      const fields = registrationFields.filter(field => details.some(detail => detail.field === field));
      throw new AuthError(response.status, code,
        'Yêu cầu xác thực không thành công.', requestId, fields);
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
