import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { readFileSync } from 'fs';
import path from 'path';
import { runInNewContext } from 'vm';
import ts from 'typescript';
import AdminLoginPreview from './AdminLoginPreview';
import useAdminAuthStore from 'stores/use-admin-auth-store';

jest.mock('stores/use-admin-auth-store', () => ({ __esModule: true, default: jest.fn(() => { throw new Error('Production auth accessed'); }) }));
jest.mock('stores/use-auth-store', () => ({ __esModule: true, default: jest.fn(() => { throw new Error('Customer auth accessed'); }) }));
jest.mock('utils/FetchUtils', () => { throw new Error('Production HTTP client imported'); });
jest.mock('../mining-run/MiningRun.api', () => ({
  listMiningRuns: () => { throw new Error('Real list API called'); }, getMiningRun: () => { throw new Error('Real detail API called'); },
  MiningRunApiError: class extends Error {},
}));
jest.mock('recharts', () => ({ ...jest.requireActual('recharts'), ResponsiveContainer: ({ children }: { children: React.ReactElement }) =>
  jest.requireActual('react').cloneElement(children, { width: 500, height: 280 }) }));
jest.setTimeout(20000);
const resize = Object.getOwnPropertyDescriptor(window, 'ResizeObserver');
beforeAll(() => Object.defineProperty(window, 'ResizeObserver', { configurable: true, value: class { observe = jest.fn(); unobserve = jest.fn(); disconnect = jest.fn(); } }));
afterAll(() => {
  if (resize) Object.defineProperty(window, 'ResizeObserver', resize);
  else Reflect.deleteProperty(window, 'ResizeObserver');
});
const originalFetch = global.fetch;
const originalEvents = global.EventSource;
const originalSocket = global.WebSocket;
let network: jest.Mock;
let storage: jest.SpyInstance;
let cookie: jest.SpyInstance;
beforeEach(() => {
  jest.clearAllMocks();
  network = jest.fn(() => { throw new Error('Unexpected network'); });
  global.fetch = network; global.EventSource = network as unknown as typeof EventSource; global.WebSocket = network as unknown as typeof WebSocket;
  storage = jest.spyOn(Storage.prototype, 'setItem'); cookie = jest.spyOn(document, 'cookie', 'set');
});
afterEach(() => {
  expect(network).not.toHaveBeenCalled(); expect(storage).not.toHaveBeenCalled(); expect(cookie).not.toHaveBeenCalled();
  expect(useAdminAuthStore).not.toHaveBeenCalled();
  storage.mockRestore(); cookie.mockRestore();
  global.fetch = originalFetch; global.EventSource = originalEvents; global.WebSocket = originalSocket;
});

function show(url = '/__preview/admin-login') {
  window.history.replaceState({}, '', url);
  return render(<BrowserRouter><AdminLoginPreview/></BrowserRouter>);
}
function login() {
  fireEvent.change(screen.getByLabelText('Tình huống preview'), { target: { value: 'admin' } });
}
function menu(label: string) { return within(screen.getByRole('navigation', { name: 'Điều hướng Dashboard' })).getByRole('button', { name: label }); }

test('login opens eight business functions without a project tracker; collapse and logout remain local', async () => {
  show();
  fireEvent.change(screen.getByLabelText('Tên đăng nhập', { exact: false }), { target: { value: 'sample' } });
  fireEvent.change(screen.getByLabelText('Mật khẩu', { exact: false }), { target: { value: 'sample-password' } });
  fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập', exact: true }));
  await screen.findByRole('heading', { name: 'Chuẩn bị dữ liệu' });
  const sidebar = screen.getByRole('complementary', { name: 'Sidebar Admin Preview' });
  const nav = within(sidebar).getByRole('navigation');
  expect(within(nav).getAllByRole('button').map(button => button.getAttribute('aria-label'))).toEqual([
    'Chuẩn bị dữ liệu', 'Cấu hình tham số', 'Chạy khai phá và kết quả', 'Quản lý Pattern', 'Quản lý Combo', 'Giám sát Mining', 'Lịch sử RUN', 'So sánh thuật toán',
  ]);
  expect(screen.queryByText(/Thành viên [123]|đầu việc|Tổng quan Dashboard/)).not.toBeInTheDocument();
  const collapse = screen.getByRole('button', { name: 'Thu gọn thanh điều hướng' });
  expect(within(collapse).getByTestId('sidebar-collapse-icon')).toBeInTheDocument();
  expect(collapse).not.toHaveTextContent('Thu gọn');
  const currentUrl = window.location.href;
  fireEvent.click(collapse);
  expect(sidebar).toHaveClass('is-collapsed');
  const expand = screen.getByRole('button', { name: 'Mở rộng thanh điều hướng' });
  expect(within(expand).getByTestId('sidebar-expand-icon')).toBeInTheDocument();
  expect(expand).toHaveAttribute('aria-expanded', 'false');
  expect(menu('Chuẩn bị dữ liệu')).toHaveAttribute('aria-current', 'page');
  expect(screen.getByRole('heading', { name: 'Chuẩn bị dữ liệu' })).toBeInTheDocument();
  expect(window.location.href).toBe(currentUrl);
  fireEvent.click(expand);
  expect(sidebar).not.toHaveClass('is-collapsed');
  expect(screen.getByRole('button', { name: 'Thu gọn thanh điều hướng' })).toHaveAttribute('aria-expanded', 'true');
  expect(window.location.href).toBe(currentUrl);
  fireEvent.click(screen.getByRole('button', { name: 'Thu gọn thanh điều hướng' }));
  fireEvent.click(menu('Cấu hình tham số'));
  expect(screen.getByRole('heading', { name: 'Cấu hình tham số' })).toBeInTheDocument();
  expect(menu('Cấu hình tham số')).toHaveAttribute('aria-current', 'page');
  expect(menu('Chuẩn bị dữ liệu')).not.toHaveAttribute('aria-current');
  expect(menu('Cấu hình tham số')).toHaveAttribute('title', 'Cấu hình tham số');
  expect(screen.getByRole('complementary')).toBe(sidebar);
  expect(sidebar).toHaveClass('is-collapsed');
  const buttons = within(sidebar).getAllByRole('button');
  expect(buttons[buttons.length - 1]).toHaveAccessibleName('Đăng xuất Preview');
  fireEvent.click(buttons[buttons.length - 1]);
  expect(screen.getByRole('form')).toBeInTheDocument();
  expect(screen.queryByRole('complementary')).not.toBeInTheDocument();
});

test('all unmerged functions show neutral placeholders, not fake business forms', () => {
  show(); login();
  ['Chuẩn bị dữ liệu', 'Cấu hình tham số', 'Chạy khai phá và kết quả', 'Quản lý Pattern', 'Quản lý Combo'].forEach(label => {
    fireEvent.click(menu(label));
    expect(screen.getByRole('heading', { level: 1, name: label })).toBeInTheDocument();
    expect(screen.getByText('Chức năng đang chờ tích hợp từ thành viên phụ trách.')).toBeInTheDocument();
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });
});

test('Monitoring, History and Comparison render in the same content area without losing the sidebar', async () => {
  show(); login();
  const sidebar = screen.getByRole('complementary');
  fireEvent.click(screen.getByRole('button', { name: 'Thu gọn thanh điều hướng' }));
  fireEvent.click(menu('Giám sát Mining'));
  expect(await screen.findByRole('table', { name: 'Danh sách RUN theo dõi' })).toBeInTheDocument();
  const main = screen.getByRole('main', { name: 'Nội dung Admin' });
  expect(within(main).getByRole('heading', { name: 'Giám sát quá trình chạy Mining' })).toBeInTheDocument();
  expect(window.location.pathname).toBe('/__preview/admin-login');
  fireEvent.click(screen.getByRole('link', { name: 'Xem lịch sử RUN' }));
  expect(await screen.findByRole('table', { name: 'Lịch sử RUN' })).toBeInTheDocument();
  expect(menu('Lịch sử RUN')).toHaveAttribute('aria-current', 'page');
  fireEvent.click(menu('So sánh thuật toán'));
  expect(await screen.findByRole('table', { name: 'Bảng so sánh tổng hợp' })).toBeInTheDocument();
  expect(within(main).getByRole('heading', { name: 'So sánh thuật toán Mining' })).toBeInTheDocument();
  expect(screen.getByRole('complementary')).toBe(sidebar);
  expect(sidebar).toHaveClass('is-collapsed');
  expect(screen.getAllByRole('main')).toHaveLength(1);
});

test('URL selection supports Back/Forward and refresh followed by preview reauthentication', async () => {
  const view = show('/__preview/admin-login?section=patterns'); login();
  expect(menu('Quản lý Pattern')).toHaveAttribute('aria-current', 'page');
  fireEvent.click(menu('Quản lý Combo'));
  expect(window.location.search).toBe('?section=combos');
  act(() => window.history.back());
  await waitFor(() => expect(menu('Quản lý Pattern')).toHaveAttribute('aria-current', 'page'));
  act(() => window.history.forward());
  await waitFor(() => expect(menu('Quản lý Combo')).toHaveAttribute('aria-current', 'page'));
  const url = window.location.pathname + window.location.search;
  view.unmount(); show(url);
  expect(screen.getByRole('form')).toBeInTheDocument();
  login();
  expect(menu('Quản lý Combo')).toHaveAttribute('aria-current', 'page');
});

test('mobile menu opens, closes on selection and preserves the current feature', () => {
  show(); login();
  const toggle = screen.getByRole('button', { name: 'Mở menu Admin', hidden: true });
  fireEvent.click(toggle);
  expect(toggle).toHaveAttribute('aria-expanded', 'true');
  expect(screen.getByRole('complementary')).toHaveClass('mobile-open');
  fireEvent.click(menu('Quản lý Pattern'));
  expect(toggle).toHaveAttribute('aria-expanded', 'false');
  expect(screen.getByRole('complementary')).not.toHaveClass('mobile-open');
  expect(menu('Quản lý Pattern')).toHaveAttribute('aria-current', 'page');
});

const appSource = readFileSync(path.join(process.cwd(), 'src/App.tsx'), 'utf8');
const declaration = appSource.match(/const AdminLoginPreview = [\s\S]*?;\r?\n/)?.[0];
test.each([['development', 'true', true], ['development', undefined, false], ['production', 'true', false]])('preview guard %s / %s', (mode, flag, enabled) => {
  expect(declaration).toBeDefined();
  const result = { enabled: false };
  const js = ts.transpileModule(`${declaration}\nresult.enabled = AdminLoginPreview !== null;`, { compilerOptions: { target: ts.ScriptTarget.ES2017, module: ts.ModuleKind.CommonJS } }).outputText;
  runInNewContext(js, { process: { env: { NODE_ENV: mode, REACT_APP_ENABLE_ADMIN_AUTH_PREVIEW: flag } }, React: { lazy: () => ({}) }, result });
  expect(result.enabled).toBe(enabled);
});

test('independent preview and production Admin routes stay registered in App unchanged', () => {
  ['customer-combos', 'mining-monitoring', 'mining-runs', 'mining-comparison', 'admin-login'].forEach(route => expect(appSource).toContain(`path="/__preview/${route}"`));
  expect(appSource).toContain('<Route path="/admin" element={<Admin/>}>');
});
