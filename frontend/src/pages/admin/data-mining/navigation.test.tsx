import React from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { MemoryRouter, Outlet, Route, Routes, useNavigate } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import useAppStore from 'stores/use-app-store';
import { DefaultNavbar } from 'components/DefaultNavbar/DefaultNavbar';
import DataMiningNavigation from './DataMiningNavigation';
import { createDataMiningRoutes, dataMiningPages, isDataMiningPath } from './routes';
import { hasDataMiningAdminAccess } from './access';
import { patternHttpClient } from 'pages/pattern/PatternHttpClient';

jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
jest.mock('stores/use-app-store', () => ({ __esModule: true, default: jest.fn() }));

const authStore = useSharedAuth as unknown as jest.Mock;
const appStore = useAppStore as jest.Mock;
const admin = { roles: ['ADMIN'] };
const fetchMock = jest.fn();

function setIdentity(user: Parameters<typeof hasDataMiningAdminAccess>[0]) {
  authStore.mockReturnValue({ user, status: user ? 'authenticated' : 'anonymous', forbidden: false });
}

function HistoryButtons() {
  const navigate = useNavigate();
  return (
    <>
      <button onClick={() => navigate(-1)}>Quay lại</button>
      <button onClick={() => navigate(1)}>Tiến tới</button>
    </>
  );
}

function Feature({ entries = ['/admin/datasets'], fullNavbar = false }: {
  entries?: string[];
  fullNavbar?: boolean;
}) {
  return (
    <MantineProvider>
      <MemoryRouter initialEntries={entries} initialIndex={entries.length - 1}>
        {fullNavbar ? <DefaultNavbar/> : <DataMiningNavigation/>}
        <HistoryButtons/>
        <Routes>
          <Route path="/admin" element={<Outlet/>}>
            <Route index element={<p>Trang chủ hiện có</p>}/>
            {createDataMiningRoutes()}
            <Route path="*" element={<p>Route khác</p>}/>
          </Route>
        </Routes>
      </MemoryRouter>
    </MantineProvider>
  );
}

beforeEach(() => {
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(query => ({
    matches: false, media: query, onchange: null, addListener: jest.fn(), removeListener: jest.fn(),
    addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn(),
  })) });
  Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: jest.fn(() => ({
    observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn(),
  })) });
  Object.defineProperty(globalThis, 'fetch', { configurable: true, writable: true, value: fetchMock });
});

beforeEach(() => {
  setIdentity(admin);
  jest.spyOn(patternHttpClient, 'runs').mockImplementation(() => new Promise(() => undefined));
  appStore.mockReturnValue({ opened: true });
  fetchMock.mockClear();
});

afterEach(() => {
  // Navigation/page mount never triggers business export/upload requests.
  expect(fetchMock).not.toHaveBeenCalled();
  jest.restoreAllMocks();
});

const heading = (path: string, label: string) => path === '/admin/patterns' ? 'Khám phá Pattern' : label;

describe('Admin Data & Mining navigation', () => {
  it.each(dataMiningPages)('opens $path directly with the correct page and active link', ({ path, label }) => {
    render(<Feature entries={[path]}/>);
    const nav = screen.getByRole('navigation', { name: 'Dữ liệu & Khai phá' });
    expect(screen.getByRole('heading', { level: 2, name: heading(path, label) })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: label })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getAllByRole('link')).toHaveLength(3);
    expect(screen.queryByText('Chức năng đang được chuẩn bị')).not.toBeInTheDocument();
    if (path === '/admin/patterns') expect(patternHttpClient.runs).toHaveBeenCalledWith(0, 20, { signal: expect.any(AbortSignal) });
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.queryByText(/không có dữ liệu/i)).not.toBeInTheDocument();
  });

  it('navigates through all three real route definitions from the menu', () => {
    render(<Feature/>);
    const nav = screen.getByRole('navigation', { name: 'Dữ liệu & Khai phá' });
    for (const { path, label } of dataMiningPages) {
      const link = within(nav).getByRole('link', { name: label });
      expect(link).toHaveAttribute('href', path);
      fireEvent.click(link);
      expect(screen.getByRole('heading', { level: 2, name: heading(path, label) })).toBeInTheDocument();
      expect(link).toHaveAttribute('aria-current', 'page');
      expect(within(nav).getAllByRole('link').filter(item => item.getAttribute('aria-current') === 'page')).toHaveLength(1);
    }
  });

  it('keeps active links correct on back/forward rather than relying on click state', () => {
    render(<Feature entries={['/admin/datasets', '/admin/mining-runs']}/>);
    expect(screen.getByRole('link', { name: 'Lần chạy khai phá' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('button', { name: 'Quay lại' }));
    expect(screen.getByRole('heading', { level: 2, name: 'Dữ liệu khai phá' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Dữ liệu khai phá' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('button', { name: 'Tiến tới' }));
    expect(screen.getByRole('heading', { level: 2, name: 'Lần chạy khai phá' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Lần chạy khai phá' })).toHaveAttribute('aria-current', 'page');
  });

  it('resolves a fresh page load with trailing slash, query and hash', () => {
    const view = render(<Feature entries={['/admin/patterns/?page=2#detail']}/>);
    expect(screen.getByRole('heading', { level: 2, name: 'Khám phá Pattern' })).toBeInTheDocument();
    view.unmount();
    render(<Feature entries={['/admin/patterns/?page=2#detail']}/>);
    expect(screen.getByRole('link', { name: 'Mẫu khai phá' })).toHaveAttribute('aria-current', 'page');
  });

  it.each([null, { roles: [] }, { roles: ['CUSTOMER'] }, { roles: ['EMPLOYEE'] }, { roles: ['ROLE_ADMIN'] }])('hides the menu and denies direct page access without the exact Admin UI role', user => {
    setIdentity(user);
    render(<Feature entries={['/admin/patterns']}/>);
    expect(screen.queryByRole('navigation', { name: 'Dữ liệu & Khai phá' })).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(user ? 'Không có quyền truy cập' : 'Cần đăng nhập');
    expect(screen.queryByRole('heading', { level: 2, name: 'Khám phá Pattern' })).not.toBeInTheDocument();
    expect(patternHttpClient.runs).not.toHaveBeenCalled();
  });

  it('removes menu and page access when the shared UI identity loses the Admin role', () => {
    const view = render(<Feature/>);
    setIdentity({ roles: ['EMPLOYEE'] });
    view.rerender(<Feature/>);
    expect(screen.queryByRole('navigation', { name: 'Dữ liệu & Khai phá' })).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Không có quyền truy cập');
  });

  it('integrates with the actual DefaultNavbar while retaining legacy links and child navigation', () => {
    render(<Feature fullNavbar/>);
    expect(screen.getByRole('navigation', { name: 'Dữ liệu & Khai phá' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Trang chủ Admin' })).toHaveAttribute('href', '/admin');
    expect(screen.getByRole('link', { name: 'Sản phẩm' })).toHaveAttribute('href', '/admin/product');
    fireEvent.click(screen.getByRole('link', { name: 'Sản phẩm' }));
    expect(screen.getByRole('link', { name: 'Danh mục sản phẩm' })).toHaveAttribute('href', '/admin/category');
  });

  it('does not add WDM navigation to the legacy navbar for a non-Admin', () => {
    setIdentity({ roles: ['EMPLOYEE'] });
    render(<Feature entries={['/admin']} fullNavbar/>);
    expect(screen.queryByRole('navigation', { name: 'Dữ liệu & Khai phá' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Đơn hàng' })).toHaveAttribute('href', '/admin/order');
  });

  it('matches only the intended route families, including direct URL casing', () => {
    expect(isDataMiningPath('/ADMIN/DATASETS')).toBe(true);
    expect(isDataMiningPath('/admin/datasets/future-detail')).toBe(true);
    expect(isDataMiningPath('/admin/datasets-extra')).toBe(false);
    expect(isDataMiningPath('/admin/product')).toBe(false);
  });
});

describe('Admin UI access predicate', () => {
  it.each([undefined, null, {}, { roles: null }, { roles: [] }, { roles: [{}] }, { roles: ['admin'] }])('fails closed for missing or unrelated role data', identity => {
    expect(hasDataMiningAdminAccess(identity)).toBe(false);
  });

  it('allows the exact role even when another role is also present', () => {
    expect(hasDataMiningAdminAccess({ roles: ['EMPLOYEE', 'ADMIN'] })).toBe(true);
  });
});
