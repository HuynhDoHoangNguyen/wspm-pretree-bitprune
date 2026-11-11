import React from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from '../App';
import { authSession } from './session';
import { AuthError } from './api';

// Catalog APIs are a separate integration slice; exercise actual App/auth/headers here.
jest.mock('pages/client-home', () => ({ __esModule: true, default: () => <h1>Cửa hàng công khai</h1> }));
const identity = { userId: 'admin-fixture', username: 'fixture', email: null, displayName: 'Fixture',
  status: 'ACTIVE' as const, roles: ['ADMIN'], emailVerified: false };
// The actual inherited Admin shell is large; keep the deadline bounded for JSDOM rendering.
jest.setTimeout(15000);
beforeEach(() => {
  jest.spyOn(authSession, 'bootstrap').mockResolvedValue();
  authSession.useStore.setState({ status: 'authenticated', user: identity, accessToken: 'test', forbidden: false, error: null });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(query => ({ matches: false,
    media: query, addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() })) });
  Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: jest.fn(() => ({
    observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn(),
  })) });
  jest.spyOn(window, 'scroll').mockImplementation(() => undefined);
});
afterEach(() => { jest.restoreAllMocks(); authSession.stop(); });
function page(path = '/') { return render(<MemoryRouter initialEntries={[path]}><App/></MemoryRouter>); }

test('ADMIN switches both sites through visible links using the same shared session', async () => {
  page();
  const adminLink = within(screen.getByRole('banner')).getByRole('link', { name: 'Trang quản trị', exact: true });
  expect(adminLink).toHaveAttribute('href', '/admin'); expect(adminLink).toBeVisible(); fireEvent.click(adminLink);
  await screen.findByRole('heading', { name: 'Từ dữ liệu đơn hàng đến combo sản phẩm' });
  const clientSwitch = screen.getByText('Về cửa hàng');
  expect(clientSwitch).toBeVisible(); fireEvent.click(clientSwitch);
  await screen.findByText('Cửa hàng công khai', { selector: 'h1' });
  expect(authSession.useStore.getState().accessToken).toBe('test');
});

test.each([null, { ...identity, roles: ['CUSTOMER'] }])('public home remains available without ADMIN identity %j', user => {
  authSession.useStore.setState({ status: user ? 'authenticated' : 'anonymous', user });
  page(); expect(screen.getByRole('heading', { name: 'Cửa hàng công khai' })).toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'Trang quản trị', exact: true })).not.toBeInTheDocument();
});

test('Admin sign-in from a deep URL lands on the guide even when identity replaces the form', async () => {
  authSession.useStore.setState({ status: 'anonymous', user: null, accessToken: null });
  jest.spyOn(authSession, 'login').mockImplementation(async () => {
    authSession.useStore.setState({ status: 'authenticated', user: identity, accessToken: 'test' });
  });
  page('/admin/datasets');
  fireEvent.change(screen.getByLabelText('Tên tài khoản', { exact: false }), { target: { value: 'fixture' } });
  fireEvent.change(screen.getByLabelText('Mật khẩu', { exact: false }), { target: { value: 'password' } });
  fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập', exact: true }));
  expect(await screen.findByRole('heading', { name: 'Từ dữ liệu đơn hàng đến combo sản phẩm' })).toBeVisible();
  expect(authSession.login).toHaveBeenCalledTimes(1);
});

test.each(['/admin/account', '/user'])('logout at %s finishes on public home', async path => {
  const logout = jest.spyOn(authSession, 'logout').mockImplementation(async () => {
    authSession.useStore.setState({ status: 'anonymous', user: null, accessToken: null });
  });
  page(path);
  if (path === '/user') {
    fireEvent.click(screen.getByRole('button', { name: 'Tài khoản', exact: true }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Đăng xuất', exact: true }));
  } else fireEvent.click(screen.getByTitle('Đăng xuất'));
  await screen.findByRole('heading', { name: 'Cửa hàng công khai' });
  expect(logout).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('Vui lòng đăng nhập để truy cập.')).not.toBeInTheDocument();
});

test('unconfirmed server logout still reaches public home and reports the failure', async () => {
  jest.spyOn(authSession, 'logout').mockImplementation(async () => {
    authSession.useStore.setState({ status: 'unavailable', user: null, accessToken: null });
    throw new AuthError(null, 'NETWORK_ERROR', 'Không thể kết nối máy chủ.');
  });
  page('/admin/account'); fireEvent.click(screen.getByTitle('Đăng xuất'));
  await screen.findByRole('heading', { name: 'Cửa hàng công khai' });
  await screen.findByText(/chưa xác nhận đăng xuất máy chủ/);
});

test('session loss on a private client page redirects home without an access wall', async () => {
  page('/user');
  await act(async () => { authSession.useStore.setState({ status: 'anonymous', user: null, accessToken: null }); });
  await screen.findByRole('heading', { name: 'Cửa hàng công khai' });
});

test('an unavailable private session offers sign-in instead of manual restore', async () => {
  authSession.useStore.setState({ status: 'unavailable', user: null, accessToken: null });
  page('/admin/account');
  expect(screen.queryByText('Khôi phục phiên')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('link', { name: 'Đăng nhập lại', exact: true }));
  expect(await screen.findByRole('form', { name: 'Đăng nhập', exact: true })).toBeInTheDocument();
});
