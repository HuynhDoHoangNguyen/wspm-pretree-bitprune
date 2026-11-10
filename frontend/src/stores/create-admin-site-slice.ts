import { SliceCreator } from 'stores/use-app-store';

export interface AdminSiteState {
  opened: boolean;
  toggleOpened: () => void;
  closeNavbar: () => void;
}

const initialAdminSiteState = {
  opened: false,
};

const createAdminSiteSlice: SliceCreator<AdminSiteState> = (set) => ({
  ...initialAdminSiteState,
  closeNavbar: () => set(() => ({ opened: false })),
  toggleOpened: () => set((state) => ({ opened: !state.opened })),
});

export default createAdminSiteSlice;
