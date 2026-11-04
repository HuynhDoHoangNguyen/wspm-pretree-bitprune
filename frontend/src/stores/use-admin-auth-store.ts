import { UserResponse } from 'models/User';

// Compatibility only for unsupported inherited profile screens; no fake profile.
const unavailable = (_value?: unknown) => { throw new Error('Profile legacy chưa được nối với API hiện tại.'); };
export default function useAdminAuthStore() {
  return { user: null as UserResponse | null, jwtToken: null as string | null,
    updateUser: unavailable, updateJwtToken: unavailable, resetAdminAuthState: unavailable,
    isOnlyEmployee: () => false };
}
