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
  return render(<MantineProvider><MemoryRouter><Routes><Route path="/" element={element}/>
    <Route path="/user" element={<h1>Danh tính đã xác thực</h1>}/>
    <Route path="/admin/datasets" element={<h1>Admin datasets</h1>}/></Routes></MemoryRouter></MantineProvider>);
}
test('registration uses exactly four fields and routes to separate login without fake email verification', async () => {
  const register = jest.spyOn(authSession, 'register').mockResolvedValue(user);
  view(<ClientSignup/>);
  fireEvent.change(screen.getByLabelText(/Tên tài khoản/), { target: { value: 'customer.test' } });
  fireEvent.change(screen.getByLabelText(/Email/), { target: { value: 'customer@example.test' } });
  fireEvent.change(screen.getByLabelText(/Tên hiển thị/), { target: { value: 'Customer' } });
  fireEvent.change(screen.getByLabelText(/Mật khẩu/), { target: { value: '12345678' } });
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
  fireEvent.click(screen.getByRole('button', { name: 'Đăng ký' }));
  await screen.findByText(/đã được sử dụng/);
});
test('client login uses shared session and succeeds without cart/profile APIs', async () => {
  const login = jest.spyOn(authSession, 'login').mockImplementation(async () => {
    authSession.useStore.setState({ user, status: 'authenticated', accessToken: 'access' });
  });
  view(<SignInForm/>);
  fireEvent.change(screen.getByLabelText(/Tên tài khoản/), { target: { value: 'customer.test' } });
  fireEvent.change(screen.getByLabelText(/Mật khẩu/), { target: { value: ' 123456 ' } });
  fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
  await screen.findByRole('heading', { name: 'Danh tính đã xác thực' });
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
});
test('legacy foreign endpoints never receive shared credentials or read persisted tokens', async () => {
  const fetcher = jest.fn(); Object.defineProperty(globalThis, 'fetch', { configurable: true, value: fetcher });
  authSession.useStore.setState({ status: 'authenticated', user, accessToken: 'real-token' });
  localStorage.setItem('electro-admin-auth-store', JSON.stringify({ state: { jwtToken: 'forged', user: { roles: ['ADMIN'] } } }));
  await expect(FetchUtils.getWithToken('http://localhost:8085/api/auth/info', undefined, true)).rejects.toMatchObject({ code: 'UNSUPPORTED_LEGACY_ENDPOINT' });
  expect(fetcher).not.toHaveBeenCalled();
});
