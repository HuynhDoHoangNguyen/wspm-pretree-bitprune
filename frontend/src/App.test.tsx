import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import useAdminAuthStore from 'stores/use-admin-auth-store';
import App from './App';

jest.mock('stores/use-admin-auth-store', () => ({ __esModule: true, default: jest.fn() }));

const authStore = useAdminAuthStore as jest.Mock;
const fetchMock = jest.fn();

beforeEach(() => {
  authStore.mockReturnValue({
    user: null,
    jwtToken: null,
    updateJwtToken: jest.fn(),
    updateUser: jest.fn(),
    resetAdminAuthState: jest.fn(),
  });
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
