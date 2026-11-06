import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AdminLogin from './AdminLogin';
import { AdminLoginError } from './AdminLogin.types';
import type { PreviewIdentity } from './AdminLogin.types';

const admin: PreviewIdentity = { username: 'example', displayName: 'Admin minh họa', access: 'ADMIN' };
function fill(username = 'sample', password = 'sample-password') {
  fireEvent.change(screen.getByLabelText('Tên đăng nhập', { exact: false }), { target: { value: username } });
  fireEvent.change(screen.getByLabelText('Mật khẩu', { exact: false }), { target: { value: password } });
}
function submit() {
  fireEvent.submit(screen.getByRole('form', { name: 'Form đăng nhập quản trị' }));
}
function show(signIn = jest.fn().mockResolvedValue(admin)) {
  return { signIn, ...render(<MemoryRouter><AdminLogin adapter={{ signIn }}/></MemoryRouter>) };
}

test('renders an initially empty form with password hidden and accessible visibility toggle', () => {
  show();
  expect(screen.getByRole('heading', { name: 'Đăng nhập quản trị' })).toBeInTheDocument();
  const password = screen.getByLabelText('Mật khẩu', { exact: false });
  expect(password).toHaveValue('');
  expect(password).toHaveAttribute('type', 'password');
  fireEvent.click(screen.getByRole('button', { name: 'Hiện mật khẩu' }));
  expect(password).toHaveAttribute('type', 'text');
  fireEvent.click(screen.getByRole('button', { name: 'Ẩn mật khẩu' }));
  expect(password).toHaveAttribute('type', 'password');
});

test.each([['   ', 'password', 'Vui lòng nhập tên đăng nhập.'], ['name', '', 'Vui lòng nhập mật khẩu.']])(
  'validates empty input without invoking the adapter', (username, password, error) => {
    const { signIn } = show();
    fill(username, password); submit();
    expect(screen.getByText(error)).toBeInTheDocument();
    expect(signIn).not.toHaveBeenCalled();
  }
);

test('trims username, preserves password bytes, clears the password and disables duplicate submit while pending', async () => {
  let finish: (user: PreviewIdentity) => void = () => undefined;
  const signIn = jest.fn(() => new Promise<PreviewIdentity>(resolve => { finish = resolve; }));
  show(signIn);
  fill('  username  ', '  password with spaces  '); submit();
  expect(signIn).toHaveBeenCalledWith({ username: 'username', password: '  password with spaces  ' }, expect.any(AbortSignal));
  expect(screen.getByRole('status')).toHaveTextContent('Đang đăng nhập');
  expect(screen.getByLabelText('Mật khẩu', { exact: false })).toHaveValue('');
  expect(screen.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeDisabled();
  submit();
  expect(signIn).toHaveBeenCalledTimes(1);
  await act(async () => finish(admin));
  expect(screen.getByRole('heading', { name: 'Chuẩn bị dữ liệu', exact: true })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Đăng xuất Preview' }));
  expect(screen.getByText(/Đã đăng xuất Preview/)).toBeInTheDocument();
  expect(screen.getByLabelText('Mật khẩu', { exact: false })).toHaveValue('');
  expect(screen.getByLabelText('Tên đăng nhập', { exact: false })).toHaveValue('');
  expect(screen.queryByRole('heading', { name: 'Chuẩn bị dữ liệu', exact: true })).not.toBeInTheDocument();
});

test.each([
  [new AdminLoginError('invalid-credentials'), 'Tên đăng nhập hoặc mật khẩu không đúng (mô phỏng).'],
  [new Error('private stack password=secret'), 'Không thể đăng nhập lúc này. Vui lòng thử lại.'],
  [new AdminLoginError('system-error'), 'Không thể đăng nhập lúc này. Vui lòng thử lại.'],
])('login failures use safe messages and keep the form', async (error, message) => {
  show(jest.fn().mockRejectedValue(error));
  fill(); submit();
  expect(await screen.findByText(message)).toBeInTheDocument();
  expect(screen.getByRole('form')).toBeInTheDocument();
  expect(screen.queryByText(/private stack|password=secret/)).not.toBeInTheDocument();
});

test.each(['identity', 'forbidden'])('denies non-admin access through %s outcome', async mode => {
  const signIn = mode === 'identity' ? jest.fn().mockResolvedValue({ ...admin, access: 'USER' }) : jest.fn().mockRejectedValue(new AdminLoginError('forbidden'));
  show(signIn); fill(); submit();
  expect(await screen.findByRole('heading', { name: 'Access Denied' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Mở Dashboard Admin Preview' })).not.toBeInTheDocument();
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Đăng xuất Preview' }));
  expect(screen.getByRole('form')).toBeInTheDocument();
});

test('an initial authenticated identity without Admin permission still cannot open the local dashboard', () => {
  render(<MemoryRouter><AdminLogin adapter={{ signIn: jest.fn() }} initialState={{ status: 'authenticated', identity: { ...admin, access: 'USER' } }}/></MemoryRouter>);
  expect(screen.getByRole('heading', { name: 'Access Denied' })).toBeInTheDocument();
});

test('unmount cancels a pending adapter operation without late login side effects', async () => {
  let finish: (user: PreviewIdentity) => void = () => undefined;
  const signIn = jest.fn((_request, _signal) => new Promise<PreviewIdentity>(resolve => { finish = resolve; }));
  const view = show(signIn); fill(); submit();
  const signal = signIn.mock.calls[0][1] as AbortSignal;
  view.unmount();
  expect(signal.aborted).toBe(true);
  await act(async () => finish(admin));
  expect(screen.queryByText('Admin minh họa')).not.toBeInTheDocument();
});
