import create from 'zustand';
import { UserResponse } from 'models/User';
import { PaymentMethodType } from 'models/PaymentMethod';

// Legacy cart state is separate from shared identity; never persisted as Auth.
const initial = { currentCartId: null as number | null, currentTotalCartItems: 0,
  currentPaymentMethod: PaymentMethodType.CASH, currentSignupUserId: null as number | null };
const unavailable = (_value: unknown) => { throw new Error('Profile legacy chưa được nối với API hiện tại.'); };
const useAuthStore = create<{
  user: UserResponse | null; jwtToken: string | null;
  currentCartId: number | null; currentTotalCartItems: number; currentPaymentMethod: PaymentMethodType;
  currentSignupUserId: number | null;
  updateUser: (value: UserResponse) => void; updateJwtToken: (value: string) => void;
  resetAuthState: () => void; updateCurrentCartId: (value: number | null) => void;
  updateCurrentTotalCartItems: (value: number) => void; updateCurrentPaymentMethod: (value: PaymentMethodType) => void;
  updateCurrentSignupUserId: (value: number | null) => void;
    }>((set) => ({
      ...initial, user: null, jwtToken: null, updateUser: unavailable, updateJwtToken: unavailable,
      resetAuthState: () => set(initial), updateCurrentCartId: value => set({ currentCartId: value }),
      updateCurrentTotalCartItems: value => set({ currentTotalCartItems: value }),
      updateCurrentPaymentMethod: value => set({ currentPaymentMethod: value }),
      updateCurrentSignupUserId: value => set({ currentSignupUserId: value }),
    }));
export const resetLegacyCart = () => useAuthStore.getState().resetAuthState();
export default useAuthStore;
