import React from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { authSession } from 'auth/session';
import App from './App';
import { datasetApi } from 'api/admin/data-mining';
import * as catalogApi from 'pages/client-home/CustomerCatalog.api';
import { listMiningRuns, getMiningRun } from 'pages/mining-run/MiningRun.api';

jest.mock('pages/mining-run/MiningRun.api', () => ({
  ...jest.requireActual('pages/mining-run/MiningRun.api'),
  listMiningRuns: jest.fn(), getMiningRun: jest.fn(),
}));
const list = listMiningRuns as jest.MockedFunction<typeof listMiningRuns>;
const detail = getMiningRun as jest.MockedFunction<typeof getMiningRun>;




const fetchMock = jest.fn();

beforeEach(() => {
  Object.defineProperty(window, 'ResizeObserver', { configurable: true,
    value: class { observe() { return undefined; } unobserve() { return undefined; } disconnect() { return undefined; } },
  });
  list.mockReset(); detail.mockReset();
  list.mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  authSession.useStore.setState({ status: 'anonymous', user: null, accessToken: null, forbidden: false, error: null });
  jest.spyOn(authSession, 'bootstrap').mockResolvedValue();
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(query => ({
    matches: false, media: query, onchange: null, addListener: jest.fn(), removeListener: jest.fn(),
    addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn(),
  })) });
  // JSDOM has no scrolling implementation or browser Fetch; keep the smoke test offline.
  jest.spyOn(window, 'scroll').mockImplementation(() => undefined);
  Object.defineProperty(globalThis, 'fetch', { configurable: true, writable: true, value: fetchMock });
  fetchMock.mockClear();
});

afterEach(() => {
  expect(fetchMock).not.toHaveBeenCalled();
  jest.restoreAllMocks();
});

test.each(['/admin', '/admin/datasets', '/admin/mining-runs'])('renders the actual sign-in form at %s when signed out', path => {
  render(
    <MemoryRouter initialEntries={[path]}>
      <App/>
    </MemoryRouter>
  );

  expect(screen.getByRole('textbox', { name: /tên tài khoản/i })).toBeEnabled();
  expect(screen.getByLabelText(/mật khẩu/i, { selector: 'input' })).toHaveAttribute('type', 'password');
  expect(screen.getByRole('button', { name: 'Đăng nhập' })).toBeEnabled();
  expect(screen.queryByRole('heading', { name: 'Thống kê hệ thống' })).not.toBeInTheDocument();
  expect(screen.queryByRole('navigation', { name: 'Dữ liệu & Khai phá' })).not.toBeInTheDocument();
});

test('forged persisted ADMIN identity is removed and never grants the admin shell', () => {
  localStorage.setItem('electro-admin-auth-store', JSON.stringify({ state: { user: { roles: ['ADMIN'] }, jwtToken: 'forged' } }));
  render(<MemoryRouter initialEntries={['/admin/datasets']}><App/></MemoryRouter>);
  expect(screen.getByRole('button', { name: 'Đăng nhập' })).toBeEnabled();
  expect(localStorage.getItem('electro-admin-auth-store')).toBeNull();
  expect(authSession.useStore.getState().user).toBeNull();
});

test('real Customer sign-in retains Shared Auth inside the original TV3 header and footer', () => {
  render(<MemoryRouter initialEntries={['/signin']}><App/></MemoryRouter>);
  expect(screen.getByRole('banner')).toHaveTextContent('Không gian combo của bạn');
  expect(screen.getByRole('contentinfo')).toHaveTextContent('© 2022 ComboStore. Bảo lưu mọi quyền.');
  expect(screen.getByRole('form', { name: 'Đăng nhập' })).toBeInTheDocument();
  expect(screen.getByRole('textbox', { name: /Tên tài khoản/ })).toBeEnabled();
  expect(screen.getByLabelText(/Mật khẩu/, { selector: 'input' })).toHaveAttribute('type', 'password');
});

test('Customer Register keeps the shared shell and the existing registration form', () => {
  render(<MemoryRouter initialEntries={['/signup']}><App/></MemoryRouter>);
  expect(screen.getByRole('banner')).toHaveTextContent('Không gian combo của bạn');
  expect(screen.getByRole('contentinfo')).toHaveTextContent('© 2022 ComboStore. Bảo lưu mọi quyền.');
  expect(screen.getByRole('form', { name: 'Đăng ký' })).toBeInTheDocument();
  expect(screen.getByRole('textbox', { name: /Email/ })).toHaveAttribute('type', 'email');
  expect(screen.getByLabelText(/Xác nhận mật khẩu/, { selector: 'input' })).toHaveAttribute('type', 'password');
  expect(screen.getByRole('button', { name: 'Đăng ký' })).toBeEnabled();
});

test('Customer shared footer toggles real light/dark theme and both ComboStore links navigate home', async () => {
  jest.spyOn(catalogApi, 'loadCatalog').mockResolvedValue([]);
  render(<MemoryRouter initialEntries={['/signin']}><App/></MemoryRouter>);
  const header = screen.getByRole('banner');
  const footer = screen.getByRole('contentinfo');
  const shell = screen.getByTestId('customer-store-layout');
  const form = screen.getByRole('form', { name: 'Đăng nhập' });
  expect(shell).toHaveAttribute('data-color-scheme', 'light');
  fireEvent.click(within(footer).getByRole('radio', { name: 'Tối' }));
  expect(shell).toHaveAttribute('data-color-scheme', 'dark');
  expect(screen.getByRole('form', { name: 'Đăng nhập' })).toBe(form);
  fireEvent.click(within(footer).getByRole('radio', { name: 'Sáng' }));
  expect(shell).toHaveAttribute('data-color-scheme', 'light');
  expect(footer).toHaveTextContent('(024) 3535 7272, (028) 35 111 222');
  expect(footer).toHaveTextContent('Tòa nhà Bitexco, Quận 1, Thành phố Hồ Chí Minh');
  expect(within(footer).getAllByRole('link')).toHaveLength(1);
  expect(within(footer).getByRole('link', { name: 'ComboStore' })).toHaveAttribute('href', '/');
  expect(within(header).getByRole('link', { name: 'ComboStore' })).toHaveAttribute('href', '/');
  expect(footer).not.toHaveTextContent(/Đặt hàng|Vận chuyển|Thanh toán|Đổi trả|Tuyển dụng/);
  fireEvent.click(within(header).getByRole('link', { name: 'ComboStore' }));
  expect(await screen.findByRole('heading', { name: 'Sản phẩm' })).toBeInTheDocument();
  expect(screen.getByRole('banner')).toBe(header);
  fireEvent.click(within(footer).getByRole('link', { name: 'ComboStore' }));
  expect(screen.getByRole('heading', { name: 'Sản phẩm' })).toBeInTheDocument();
});

test('snapshot deep URL reaches detail through the actual authenticated Admin shell', async () => {
  const id = '11111111-2222-4333-8444-555555555555';
  authSession.useStore.setState({ status: 'authenticated', accessToken: 'test-token', user: {
    userId: 'admin-fixture', username: 'fixture', email: null, displayName: 'Fixture admin',
    status: 'ACTIVE', roles: ['ADMIN'], emailVerified: false,
  } });
  Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: jest.fn(() => ({
    observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn(),
  })) });
  const detail = jest.spyOn(datasetApi, 'detail').mockImplementation(() => new Promise(() => undefined));
  render(<MemoryRouter initialEntries={['/admin/datasets/' + id]}><App/></MemoryRouter>);
  expect(await screen.findByRole('heading', { name: 'Chi tiết snapshot', exact: true })).toBeInTheDocument();
  expect(detail).toHaveBeenCalledWith(id, expect.any(AbortSignal));
  expect(screen.queryByText(/Chức năng legacy này/)).not.toBeInTheDocument();
});

function adminIdentity(roles = ['ADMIN']) {
  authSession.useStore.setState({ status: 'authenticated', forbidden: false, accessToken: 'test-access', user: {
    userId: 'admin-test', username: 'fixture', email: null, displayName: 'Fixture', status: 'ACTIVE', roles, emailVerified: false,
  } });
  Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: jest.fn(() => ({
    observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn(),
  })) });
}

test('TV3 shared History owns its production route and keeps the sidebar mounted across dataset navigation', async () => {
  adminIdentity();
  render(<MemoryRouter initialEntries={['/admin/mining-runs']}><App/></MemoryRouter>);
  await screen.findByText('Không có RUN phù hợp.');
  expect(list).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('Chức năng đang được chuẩn bị')).not.toBeInTheDocument();
  const nav = screen.getByRole('navigation', { name: 'Điều hướng Dashboard' });
  expect(within(nav).getAllByRole('button')).toHaveLength(8);
  expect(within(nav).getAllByRole('button').filter(button => button.hasAttribute('disabled'))).toHaveLength(4);
  expect(screen.queryByText(/Admin Layout Preview dùng fixture/)).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'Địa chỉ' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Lịch sử RUN' })).toHaveAttribute('aria-current', 'page');
  fireEvent.click(screen.getByRole('button', { name: 'Thu gọn thanh điều hướng' }));
  expect(screen.getByTestId('sidebar-expand-icon')).toBeInTheDocument();
  expect(screen.getByText('Không có RUN phù hợp.')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Chuẩn bị dữ liệu' }));
  await screen.findByRole('heading', { name: 'Dữ liệu khai phá' });
  expect(screen.getByRole('navigation', { name: 'Điều hướng Dashboard' })).toBe(nav);
  expect(screen.getByRole('button', { name: 'Chuẩn bị dữ liệu' })).toHaveAttribute('aria-current', 'page');
  expect(screen.getByTestId('sidebar-expand-icon')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Mở rộng thanh điều hướng' }));
  expect(screen.getByTestId('sidebar-collapse-icon')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Đăng xuất' })).toBeEnabled();
  fireEvent.click(screen.getByRole('button', { name: 'Lịch sử RUN' }));
  await screen.findByText('Không có RUN phù hợp.');
  expect(screen.getByRole('navigation', { name: 'Điều hướng Dashboard' })).toBe(nav);
  const logout = jest.spyOn(authSession, 'logout').mockImplementation(() => new Promise(() => undefined));
  fireEvent.click(screen.getByRole('button', { name: 'Đăng xuất' }));
  expect(logout).toHaveBeenCalledTimes(1);
}, 20000);

test('TV3 shared guard denies Customer at the History URL before querying', () => {
  adminIdentity(['CUSTOMER']);
  render(<MemoryRouter initialEntries={['/admin/mining-runs']}><App/></MemoryRouter>);
  expect(screen.getByRole('alert')).toHaveTextContent('ADMIN');
  expect(list).not.toHaveBeenCalled();
  expect(detail).not.toHaveBeenCalled();
  expect(screen.queryByRole('navigation', { name: 'Điều hướng Dashboard' })).not.toBeInTheDocument();
});

test('Monitoring production route uses the real sidebar and shared API, then navigates to unchanged History', async () => {
  adminIdentity();
  render(<MemoryRouter initialEntries={['/admin/mining-monitoring']}><App/></MemoryRouter>);
  await screen.findByText('Chưa có RUN để theo dõi.');
  const nav = screen.getByRole('navigation', { name: 'Điều hướng Dashboard' });
  expect(screen.getByRole('button', { name: 'Giám sát Mining' })).toHaveAttribute('aria-current', 'page');
  expect(list).toHaveBeenCalledTimes(1);
  expect(screen.queryByText(/Dữ liệu minh họa/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('link', { name: 'Xem lịch sử RUN' }));
  await screen.findByText('Không có RUN phù hợp.');
  expect(screen.getByRole('navigation', { name: 'Điều hướng Dashboard' })).toBe(nav);
  fireEvent.click(screen.getByRole('button', { name: 'Giám sát Mining' }));
  await screen.findByText('Chưa có RUN để theo dõi.');
  expect(screen.getByRole('button', { name: 'Giám sát Mining' })).toHaveAttribute('aria-current', 'page');
});

test.each(['guest', 'customer'])('Monitoring denies %s before requesting RUN data', role => {
  if (role === 'customer') adminIdentity(['CUSTOMER']);
  render(<MemoryRouter initialEntries={['/admin/mining-monitoring']}><App/></MemoryRouter>);
  const gate = role === 'guest' ? screen.getByRole('button', { name: 'Đăng nhập' }) : screen.getByRole('alert');
  expect(gate).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Giám sát quá trình chạy Mining' })).not.toBeInTheDocument();
  expect(list).not.toHaveBeenCalled();
  expect(detail).not.toHaveBeenCalled();
});

test('Comparison production route uses the shared API and the existing Admin sidebar', async () => {
  adminIdentity();
  render(<MemoryRouter initialEntries={['/admin/mining-comparison']}><App/></MemoryRouter>);
  await screen.findByText('Chưa có RUN để so sánh.');
  const nav = screen.getByRole('navigation', { name: 'Điều hướng Dashboard' });
  expect(screen.getByRole('button', { name: 'So sánh thuật toán' })).toHaveAttribute('aria-current', 'page');
  expect(list).toHaveBeenCalledWith({ page: 0, size: 100, algorithm: '', status: '' }, expect.any(AbortSignal));
  expect(detail).not.toHaveBeenCalled();
  expect(screen.queryByText(/Dữ liệu minh họa/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Giám sát Mining' }));
  await screen.findByText('Chưa có RUN để theo dõi.');
  fireEvent.click(screen.getByRole('button', { name: 'So sánh thuật toán' }));
  await screen.findByText('Chưa có RUN để so sánh.');
  expect(screen.getByRole('navigation', { name: 'Điều hướng Dashboard' })).toBe(nav);
});

test.each(['guest', 'customer'])('Comparison denies %s before requesting RUN data', role => {
  if (role === 'customer') adminIdentity(['CUSTOMER']);
  render(<MemoryRouter initialEntries={['/admin/mining-comparison']}><App/></MemoryRouter>);
  const gate = role === 'guest' ? screen.getByRole('button', { name: 'Đăng nhập' }) : screen.getByRole('alert');
  expect(gate).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'So sánh thuật toán Mining' })).not.toBeInTheDocument();
  expect(list).not.toHaveBeenCalled();
  expect(detail).not.toHaveBeenCalled();
});
