import React from 'react';
import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { authSession } from 'auth/session';
import App from './App';
import { datasetApi } from 'api/admin/data-mining';
import { patternHttpClient } from 'pages/pattern/PatternHttpClient';
jest.mock('./pages/combo/ComboHttpClient', () => ({ comboHttpClient: require('./pages/combo/ComboClient').unavailableComboClient }));




const fetchMock = jest.fn();

beforeEach(() => {
  authSession.useStore.setState({ status: 'anonymous', user: null, accessToken: null, forbidden: false, error: null });
  jest.spyOn(authSession, 'bootstrap').mockResolvedValue();
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(query => ({
    matches: false, media: query, onchange: null, addListener: jest.fn(), removeListener: jest.fn(),
    addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn(),
  })) });
  Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: jest.fn(() => ({
    observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn(),
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

test.each(['/admin', '/admin/datasets'])('renders the actual sign-in form at %s when signed out', path => {
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

test.each(['/admin/combos', '/admin/combos/'])('mixed-role Admin reaches Combo outlet at %s and loses it after logout', async path => {
  authSession.useStore.setState({ status: 'authenticated', forbidden: false,
    user: { userId: 'fixture-admin', username: 'fixture', email: null, displayName: 'Fixture', roles: ['CUSTOMER', 'ADMIN'], status: 'ACTIVE', emailVerified: false } });
  render(<MemoryRouter initialEntries={[path]}><App/></MemoryRouter>);
  expect(await screen.findByRole('heading', { name: 'Quản lý Combo' })).toBeInTheDocument();
  expect(await screen.findByText(/Chưa thể kết nối: API Combo/)).toBeInTheDocument();
  act(() => authSession.unauthorized());
  expect(screen.queryByRole('heading', { name: 'Quản lý Combo' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Đăng nhập' })).toBeInTheDocument();
});

test('Customer cannot enter the Combo Admin outlet', () => {
  authSession.useStore.setState({ status: 'authenticated', forbidden: false,
    user: { userId: 'fixture-customer', username: 'fixture', email: null, displayName: 'Fixture', roles: ['CUSTOMER'], status: 'ACTIVE', emailVerified: false } });
  render(<MemoryRouter initialEntries={['/admin/combos']}><App/></MemoryRouter>);
  expect(screen.getByRole('alert')).toHaveTextContent('không có quyền ADMIN');
  expect(screen.queryByRole('heading', { name: 'Quản lý Combo' })).not.toBeInTheDocument();
});

test.each(['/admin/patterns', '/admin/patterns/?page=2#detail'])('actual App opens the shared Pattern route at %s', async path => {
  const runs = jest.spyOn(patternHttpClient, 'runs').mockResolvedValue({ page: 0, size: 20, total: '0', items: [] });
  authSession.useStore.setState({ status: 'authenticated', forbidden: false,
    user: { userId: 'fixture-admin', username: 'fixture', email: null, displayName: 'Fixture', roles: ['ADMIN'], status: 'ACTIVE', emailVerified: false } });
  render(<MemoryRouter initialEntries={[path]}><App/></MemoryRouter>);
  expect(await screen.findByRole('heading', { name: 'Khám phá Pattern' })).toBeInTheDocument();
  expect(await screen.findByText('Chưa có mining run.')).toBeInTheDocument();
  expect(runs).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('Chức năng đang được chuẩn bị')).not.toBeInTheDocument();
});
