import type { LoginRequest } from '../../models/Authentication';

// UI-only roles/identity. These do not define backend roles or a production session.
export interface PreviewIdentity {
  username: string;
  displayName: string;
  access: 'ADMIN' | 'USER';
}

export type AdminLoginState =
  | { status: 'guest' | 'validation' | 'loading' | 'invalid-credentials' | 'system-error' | 'logged-out' }
  | { status: 'authenticated'; identity: PreviewIdentity }
  | { status: 'denied'; identity?: PreviewIdentity };

/** Future application adapter seam, NOT a confirmed HTTP/session contract. */
export interface AdminLoginAdapter {
  signIn(request: LoginRequest, signal: AbortSignal): Promise<PreviewIdentity>;
}

export class AdminLoginError extends Error {
  constructor(public readonly kind: 'invalid-credentials' | 'forbidden' | 'system-error') {
    super('Không thể hoàn tất đăng nhập.');
    this.name = 'AdminLoginError';
  }
}

