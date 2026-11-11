import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthError, AuthIdentity } from './api';
import { authSession } from './session';
import SignInForm from './SignInForm';
import ClientSignup from 'pages/client-signup/ClientSignup';
import Admin from 'pages/Admin';
import FetchUtils from 'utils/FetchUtils';

const user: AuthIdentity = { userId: 'opaque-id', username: 'customer.test', email: 'customer@example.test',
  displayName: 'Customer', status: 'ACTIVE', roles: ['CUSTOMER'], emailVerified: false };
beforeEach(() => {
  authSession.useStore.setState({ status: 'anonymous', user: null, accessToken: null, forbidden: false, error: null });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false,
    addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() })) });
});
afterEach(() => { jest.restoreAllMocks(); authSession.stop(); localStorage.clear(); });
function view(element: React.ReactElement) {
  return render(<MantineProvider><MemoryRouter initialEntries={['/signin']}><Routes><Route path="/signin" element={element}/>
    <Route path="/" element={<h1>Cửa hàng công khai</h1>}/>
    <Route path="/user" element={<h1>Danh tính đã xác thực</h1>}/>
    <Route path="/admin" element={<h1>Admin datasets</h1>}/></Routes></MemoryRouter></MantineProvider>);
}
test('registration uses exactly four fields and routes to separate login without fake email verification', async () => {
  const register = jest.spyOn(authSession, 'register').mockResolvedValue(user);
  view(<ClientSignup/>);
  fireEvent.change(screen.getByLabelText(/Tên tài khoản/), { target: { value: 'customer.test' } });
  fireEvent.change(screen.getByLabelText(/Email/), { target: { value: 'customer@example.test' } });
  fireEvent.change(screen.getByLabelText(/Tên hiển thị/), { target: { value: 'Customer' } });
  fireEvent.change(screen.getByLabelText(/Mật khẩu/), { target: { value: '12345678' } });
  fireEvent.change(screen.getByLabelText(/Xác nhận mật khẩu/), { target: { value: '12345678' } });
  fireEvent.click(screen.getByRole('button', { name: 'Đăng ký' }));
  await screen.findByText(/Đã tạo tài khoản/);
  expect(register).toHaveBeenCalledWith({ username: 'customer.test', email: 'customer@example.test', displayName: 'Customer', password: '12345678' });
  expect(screen.getByText(/Email chưa xác minh/)).toBeInTheDocument();
  expect(authSession.useStore.getState().user).toBeNull();
});
test('registration rejects 7 characters and displays duplicate errors', async () => {
  const register = jest.spyOn(authSession, 'register').mockRejectedValue(new AuthError(409, 'IDENTITY_CONFLICT', 'safe'));
  view(<ClientSignup/>);
  for (const [label, value] of [['Tên tài khoản', 'customer.test'], ['Email', 'customer@example.test'], ['Tên hiển thị', 'Customer'], ['Mật khẩu', '1234567']]) {
    fireEvent.change(screen.getByLabelText(new RegExp(label)), { target: { value } });
  }
  fireEvent.click(screen.getByRole('button', { name: 'Đăng ký' })); expect(register).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText(/Mật khẩu/), { target: { value: '12345678' } });
  fireEvent.change(screen.getByLabelText(/Xác nhận mật khẩu/), { target: { value: '12345678' } });
  fireEvent.click(screen.getByRole('button', { name: 'Đăng ký' }));
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('đã được sử dụng'));
  expect(screen.getByLabelText(/Tên tài khoản/)).toHaveAttribute('aria-invalid', 'true');
  expect(screen.getByLabelText(/Email/)).toHaveAttribute('aria-invalid', 'true');
});
test('client login uses shared session and succeeds without cart/profile APIs', async () => {
  const login = jest.spyOn(authSession, 'login').mockImplementation(async () => {
    authSession.useStore.setState({ user, status: 'authenticated', accessToken: 'access' });
  });
  view(<SignInForm/>);
  fireEvent.change(screen.getByLabelText(/Tên tài khoản/), { target: { value: 'customer.test' } });
  fireEvent.change(screen.getByLabelText(/Mật khẩu/), { target: { value: ' 123456 ' } });
  fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
  await screen.findByRole('heading', { name: 'Cửa hàng công khai' });
  expect(login).toHaveBeenCalledWith('customer.test', ' 123456 ');
});
test('customer identity cannot enter the admin shell and bootstrap never mounts protected content', () => {
  authSession.useStore.setState({ status: 'bootstrapping' }); const utils = view(<Admin/>);
  expect(screen.getByText('Đang xác minh phiên…')).toBeInTheDocument(); utils.unmount();
  authSession.useStore.setState({ status: 'authenticated', user, accessToken: 'access' }); view(<Admin/>);
  expect(screen.getByRole('alert')).toHaveTextContent('không có quyền ADMIN');
  expect(screen.queryByRole('heading', { name: 'Admin datasets' })).not.toBeInTheDocument();
});
test('wrong credentials remain on the form with a useful error', async () => {
  jest.spyOn(authSession, 'login').mockRejectedValue(new AuthError(401, 'AUTHENTICATION_REQUIRED', 'safe'));
  view(<SignInForm/>);
  fireEvent.change(screen.getByLabelText(/Tên tài khoản/), { target: { value: 'customer.test' } });
  fireEvent.change(screen.getByLabelText(/Mật khẩu/), { target: { value: 'wrong' } });
  fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('không hợp lệ'));
  expect(screen.getByLabelText(/Tên tài khoản/)).toHaveFocus();
});
test('legacy foreign endpoints never receive shared credentials or read persisted tokens', async () => {
  const fetcher = jest.fn(); Object.defineProperty(globalThis, 'fetch', { configurable: true, value: fetcher });
  authSession.useStore.setState({ status: 'authenticated', user, accessToken: 'real-token' });
  localStorage.setItem('electro-admin-auth-store', JSON.stringify({ state: { jwtToken: 'forged', user: { roles: ['ADMIN'] } } }));
  await expect(FetchUtils.getWithToken('http://localhost:8085/api/auth/info', undefined, true)).rejects.toMatchObject({ code: 'UNSUPPORTED_LEGACY_ENDPOINT' });
  expect(fetcher).not.toHaveBeenCalled();
});

test('empty login marks both fields and focuses username without calling API', () => {
  const login = jest.spyOn(authSession, 'login');
  view(<SignInForm/>);
  fireEvent.submit(screen.getByRole('form', { name: 'Đăng nhập', exact: true }));
  expect(login).not.toHaveBeenCalled();
  expect(screen.getByLabelText(/Tên tài khoản/)).toHaveAttribute('aria-invalid', 'true');
  expect(screen.getByLabelText(/Mật khẩu/)).toHaveAttribute('aria-invalid', 'true');
  expect(screen.getByLabelText(/Tên tài khoản/)).toHaveFocus();
  fireEvent.change(screen.getByLabelText(/Tên tài khoản/), { target: { value: 'valid.user' } });
  expect(screen.getByLabelText(/Tên tài khoản/)).not.toHaveAttribute('aria-invalid', 'true');
  expect(screen.getByLabelText(/Mật khẩu/)).toHaveAttribute('aria-invalid', 'true');
});

test.each([false, true])('ADMIN uses admin destination from login form admin=%s', async admin => {
  jest.spyOn(authSession, 'login').mockImplementation(async () => {
    authSession.useStore.setState({ user: { ...user, roles: ['CUSTOMER', 'ADMIN'] }, status: 'authenticated' });
  });
  view(<SignInForm admin={admin}/>);
  fireEvent.change(screen.getByLabelText(/Tên tài khoản/), { target: { value: 'admin' } });
  fireEvent.change(screen.getByLabelText(/Mật khẩu/), { target: { value: 'password' } });
  fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập', exact: true }));
  await screen.findByRole('heading', { name: 'Admin datasets' });
});

test('confirmation mismatch blocks registration and is corrected without changing password bytes', async () => {
  const register = jest.spyOn(authSession, 'register').mockResolvedValue(user);
  view(<ClientSignup/>);
  for (const [label, value] of [['Tên tài khoản', 'customer.test'], ['Email', 'customer@example.test'],
    ['Tên hiển thị', 'Customer'], ['Mật khẩu', ' 12345678 '], ['Xác nhận mật khẩu', '12345678']]) {
    fireEvent.change(screen.getByLabelText(new RegExp('^' + label)), { target: { value } });
  }
  fireEvent.click(screen.getByRole('button', { name: 'Đăng ký', exact: true }));
  expect(register).not.toHaveBeenCalled();
  const repeated = screen.getByLabelText(/Xác nhận mật khẩu/);
  expect(repeated).toHaveAttribute('aria-invalid', 'true'); expect(repeated).toHaveFocus();
  expect(screen.getByText('Mật khẩu xác nhận không khớp.')).toBeInTheDocument();
  fireEvent.change(repeated, { target: { value: ' 12345678 ' } });
  expect(repeated).not.toHaveAttribute('aria-invalid', 'true');
  fireEvent.click(screen.getByRole('button', { name: 'Đăng ký', exact: true }));
  await screen.findByText(/Đã tạo tài khoản/);
  expect(register).toHaveBeenCalledWith({ username: 'customer.test', email: 'customer@example.test',
    displayName: 'Customer', password: ' 12345678 ' });
});

test('registration displays local errors under fields and focuses the first visual field', () => {
  const register = jest.spyOn(authSession, 'register'); view(<ClientSignup/>);
  fireEvent.submit(screen.getByRole('form', { name: 'Đăng ký', exact: true }));
  for (const label of ['Tên tài khoản', 'Email', 'Tên hiển thị', 'Mật khẩu', 'Xác nhận mật khẩu']) {
    expect(screen.getByLabelText(new RegExp('^' + label))).toHaveAttribute('aria-invalid', 'true');
  }
  expect(screen.getByLabelText(/Tên tài khoản/)).toHaveFocus(); expect(register).not.toHaveBeenCalled();
});

test('transport errors stay general instead of marking credentials invalid', async () => {
  jest.spyOn(authSession, 'login').mockRejectedValue(new AuthError(null, 'NETWORK_ERROR', 'Không thể kết nối máy chủ.'));
  view(<SignInForm/>);
  fireEvent.change(screen.getByLabelText(/Tên tài khoản/), { target: { value: 'customer.test' } });
  fireEvent.change(screen.getByLabelText(/Mật khẩu/), { target: { value: 'password' } });
  fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập', exact: true }));
  await screen.findByRole('alert');
  expect(screen.getByLabelText(/Tên tài khoản/)).not.toHaveAttribute('aria-invalid', 'true');
  expect(screen.getByLabelText(/Mật khẩu/)).not.toHaveAttribute('aria-invalid', 'true');
});

test('login has no recovery control or CUSTOMER label and Admin has no signup link', () => {
  view(<SignInForm admin/>);
  expect(screen.queryByText(/Khôi phục phiên|CUSTOMER/)).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'Đăng ký', exact: true })).not.toBeInTheDocument();
});
