import React from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { MemoryRouter, Outlet, Route, Routes, useNavigate } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import useAppStore from 'stores/use-app-store';
import { DefaultNavbar } from 'components/DefaultNavbar/DefaultNavbar';
import DataMiningNavigation, { activeMiningItem } from './DataMiningNavigation';
import { createDataMiningRoutes, isDataMiningPath } from './routes';
import { hasDataMiningAdminAccess } from './access';
import { patternHttpClient } from 'pages/pattern/PatternHttpClient';

jest.mock('auth/session', () => ({ useSharedAuth: jest.fn(), authSession: { onClear: () => () => undefined } }));
jest.mock('pages/admin/data-mining/mining-run/MiningRunHistory', () => ({ __esModule: true, default: () => <h2>Lịch sử Mining RUN</h2> }));
jest.mock('pages/admin/data-mining/mining-monitoring/MiningMonitoringPage', () => ({ __esModule: true, default: () => <h2>Giám sát Mining</h2> }));
jest.mock('pages/admin/data-mining/mining-comparison/MiningComparisonPage', () => ({ __esModule: true, default: () => <h2>So sánh thuật toán</h2> }));
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

describe('Admin navigation', () => {
  it.each([
    ['/admin/datasets', 'Danh sách dữ liệu'],
    ['/admin/datasets/?tab=create', 'Tạo dữ liệu mới'],
    ['/admin/datasets/example', 'Danh sách dữ liệu'],
    ['/admin/mining-runs', 'Tạo lần chạy'],
    ['/admin/mining-runs?tab=history&page=2', 'Lịch sử lần chạy'],
    ['/admin/mining-runs/example', 'Lịch sử lần chạy'],
    ['/admin/mining-run-history', 'Lịch sử lần chạy'],
    ['/admin/mining-monitoring', 'Giám sát thực thi'],
    ['/admin/patterns/?page=2#detail', 'Tra cứu Pattern'],
    ['/admin/pattern-explorer', 'Tra cứu Pattern'],
    ['/admin/combos?batchId=123', 'Danh sách batch & duyệt'],
    ['/admin/combos?runId=123', 'Tạo đề xuất Combo'],
    ['/admin/combos?tab=generate', 'Tạo đề xuất Combo'],
    ['/admin/mining-comparison', 'So sánh thuật toán'],
    ['/admin/account', 'Tài khoản'],
  ])('opens and highlights the right item on direct load: %s', (url, label) => {
    render(<MantineProvider><MemoryRouter initialEntries={[url]}><DataMiningNavigation/></MemoryRouter></MantineProvider>);
    const active = screen.getByRole('link', { name: label });
    expect(active).toHaveAttribute('aria-current', 'page');
    expect(screen.getAllByRole('link').filter(link => link.getAttribute('aria-current') === 'page')).toHaveLength(1);
  });

  it('changes groups without navigation and handles back/forward using the URL', () => {
    render(<Feature entries={['/admin/datasets', '/admin/mining-runs?tab=history']}/>);
    expect(screen.getByRole('link', { name: 'Lịch sử lần chạy' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('button', { name: 'Dữ liệu khai phá' }));
    expect(screen.getByRole('link', { name: 'Lịch sử lần chạy' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('link', { name: 'Tạo dữ liệu mới' }));
    expect(screen.getByRole('form', { name: 'Upload CSV' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Tạo dữ liệu mới' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('button', { name: 'Quay lại' }));
    expect(screen.getByRole('link', { name: 'Lịch sử lần chạy' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('button', { name: 'Tiến tới' }));
    expect(screen.getByRole('link', { name: 'Tạo dữ liệu mới' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('button', { name: 'Dữ liệu khai phá' }));
    expect(screen.queryByRole('link', { name: 'Tạo dữ liệu mới' })).not.toBeInTheDocument();
  });

  it('keeps home first, removes duplicate Pattern links and expands legacy without navigating', () => {
    render(<Feature entries={['/admin']} fullNavbar/>);
    expect(screen.getAllByRole('link')[0]).toHaveTextContent('Trang chủ Admin');
    expect(screen.queryByRole('link', { name: 'Pattern', exact: true })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Quản lý cửa hàng' }));
    fireEvent.click(screen.getByRole('button', { name: 'Sản phẩm', exact: true }));
    expect(screen.getByRole('link', { name: 'Trang chủ Admin' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('link', { name: 'Danh mục sản phẩm' }));
    expect(screen.getByRole('link', { name: 'Danh mục sản phẩm' })).toHaveAttribute('aria-current', 'page');
  });

  it('opens the legacy group on a fresh child URL', () => {
    render(<Feature entries={['/admin/category']} fullNavbar/>);
    expect(screen.getByRole('button', { name: 'Quản lý cửa hàng' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: 'Sản phẩm', exact: true })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('link', { name: 'Danh mục sản phẩm' })).toHaveAttribute('aria-current', 'page');
  });

  it('closes the sidebar after mobile selection', () => {
    const closeNavbar = jest.fn(); appStore.mockReturnValue({ opened: true, closeNavbar });
    (window.matchMedia as jest.Mock).mockImplementation(query => ({ matches: query === '(max-width: 991px)', addListener: jest.fn(), removeListener: jest.fn() }));
    render(<Feature/>);
    fireEvent.click(screen.getByRole('link', { name: 'Tạo dữ liệu mới' }));
    expect(closeNavbar).toHaveBeenCalledTimes(1);
  });

  it.each([null, { roles: [] }, { roles: ['CUSTOMER'] }, { roles: ['EMPLOYEE'] }, { roles: ['ROLE_ADMIN'] }])('denies menu and direct route for %j', user => {
    setIdentity(user); render(<Feature entries={['/admin/patterns']}/>);
    expect(screen.queryByRole('navigation', { name: 'Dữ liệu & Khai phá' })).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(user ? 'Không có quyền truy cập' : 'Cần đăng nhập');
    expect(patternHttpClient.runs).not.toHaveBeenCalled();
  });

  it.each([
    ['/admin/mining-run-history', 'Lịch sử Mining RUN'],
    ['/admin/mining-monitoring', 'Giám sát Mining'],
    ['/admin/mining-comparison', 'So sánh thuật toán'],
  ])('retains the existing route %s', (url, heading) => {
    render(<Feature entries={[url]}/>);
    expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument();
  });

  it('does not match unrelated path prefixes', () => {
    expect(activeMiningItem('/admin/datasets-extra', '')).toBe('');
    expect(activeMiningItem('/ADMIN/DATASETS/', '?tab=create')).toBe('dataset-create');
    expect(isDataMiningPath('/admin/datasets-extra')).toBe(false);
  });
});

describe('Admin UI access predicate', () => {
  it.each([undefined, null, {}, { roles: null }, { roles: [] }, { roles: [{}] }, { roles: ['admin'] }])('fails closed for %j', identity => {
    expect(hasDataMiningAdminAccess(identity)).toBe(false);
  });
  it('allows the exact ADMIN role', () => expect(hasDataMiningAdminAccess({ roles: ['EMPLOYEE', 'ADMIN'] })).toBe(true));
});
