import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { DefaultNavbar } from './DefaultNavbar';
import { authSession } from 'auth/session';


jest.mock('stores/use-app-store', () => ({
  __esModule: true,
  default: () => ({ opened: true }),
}));
jest.mock('components/DefaultNavbar/DefaultNavbar.styles', () => ({
  __esModule: true,
  default: () => ({
    classes: {
      link: 'nav-link', linkActive: 'nav-active', linkDisabled: 'nav-disabled',
      linkIcon: 'nav-icon', childLinkActive: 'nav-child-active', childLinkDot: 'nav-child-dot',
    },
    cx: (base: string, conditions: Record<string, boolean>) =>
      [base, ...Object.keys(conditions).filter(name => conditions[name])].join(' '),
  }),
}));

beforeAll(() => {
  Object.defineProperty(window, 'ResizeObserver', {
    configurable: true,
    value: class {
      observe = () => undefined;
      unobserve = () => undefined;
      disconnect = () => undefined;
    },
  });
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (query: string) => ({
      matches: false, media: query, onchange: null,
      addListener: () => undefined, removeListener: () => undefined,
      addEventListener: () => undefined, removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }),
  });
});
beforeEach(() => {
  authSession.useStore.setState({ status: 'authenticated', forbidden: false, accessToken: 'test', user: {
    userId: 'admin', username: 'fixture', email: null, displayName: 'Admin', status: 'ACTIVE', roles: ['ADMIN'], emailVerified: false,
  } });
});

function NavigationHarness() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  return (
    <>
      <DefaultNavbar/>
      <output aria-label="Current path">{pathname}</output>
      <button onClick={() => navigate(-1)}>Back</button>
      <button onClick={() => navigate(1)}>Forward</button>
      <button onClick={() => navigate('/admin/category/update/42')}>Open nested category</button>
    </>
  );
}

function show(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <MantineProvider><NavigationHarness/></MantineProvider>
    </MemoryRouter>
  );
}

function expectActive(label: string) {
  const activeLink = screen.getByRole('link', { name: label, exact: true });
  expect(activeLink).toHaveClass('nav-active');
  screen.getAllByRole('link').filter(link => link !== activeLink).forEach(link => {
    expect(link).not.toHaveClass('nav-active');
  });
}

test.each([
  ['/admin/mining-runs', 'Lịch sử Mining RUN'],
  ['/admin/mining-runs/?page=2#detail', 'Lịch sử Mining RUN'],
  ['/admin', 'Trang chủ'],
  ['/admin/product/create', 'Sản phẩm'],
  ['/admin/product/brand/update/42', 'Sản phẩm'],
  ['/admin/category/update/42', 'Sản phẩm'],
  ['/admin/address/province/update/42', 'Địa chỉ'],
  ['/admin/user/role/create', 'Người dùng'],
  ['/admin/inventory/purchase-order/update/42', 'Tồn kho'],
  ['/admin/order/resource/create', 'Đơn hàng'],
  ['/admin/payment-method', 'Sổ quỹ'],
  ['/admin/promotion/update/42', 'Sổ quỹ'],
])('direct URL %s activates only its owning menu', (path, label) => {
  show(path);
  expectActive(label);
});

test('remounting at the same URL keeps Mining RUN active (F5 equivalent)', () => {
  const { unmount } = show('/admin/mining-runs');
  expectActive('Lịch sử Mining RUN');
  unmount();
  show('/admin/mining-runs');
  expectActive('Lịch sử Mining RUN');
});

test('updates active menu for navigation, browser back and forward', () => {
  show('/admin/mining-runs');
  fireEvent.click(screen.getByRole('link', { name: 'Sản phẩm', exact: true }));
  expect(screen.getByLabelText('Current path')).toHaveTextContent('/admin/product');
  expectActive('Sản phẩm');
  fireEvent.click(screen.getByRole('button', { name: 'Back', exact: true }));
  expectActive('Lịch sử Mining RUN');
  fireEvent.click(screen.getByRole('button', { name: 'Forward', exact: true }));
  expectActive('Sản phẩm');
  fireEvent.click(screen.getByRole('link', { name: 'Lịch sử Mining RUN', exact: true }));
  expectActive('Lịch sử Mining RUN');
});

test('tracks a nested route change without a sidebar click and preserves child destinations', () => {
  show('/admin/mining-runs');
  fireEvent.click(screen.getByRole('button', { name: 'Open nested category' }));
  expectActive('Sản phẩm');
  expect(screen.getByRole('link', { name: 'Danh mục sản phẩm', exact: true })).toHaveAttribute('href', '/admin/category');
  expect(screen.getByRole('link', { name: 'Sản phẩm', exact: true })).toHaveAttribute('href', '/admin/product');
});

test.each(['/admin/mining-runs-extra', '/admin/productivity', '/admin/orderly'])(
  'does not match a partial path segment: %s', path => {
    show(path);
    expectActive('Trang chủ');
  }
);

test('retains employee permission classes and link destinations', () => {
  const user = authSession.useStore.getState().user;
  if (!user) throw new Error('Missing test identity');
  authSession.useStore.setState({ user: { ...user, roles: ['CUSTOMER'] } });
  show('/admin/mining-runs');
  expect(screen.queryByRole('navigation', { name: 'Dữ liệu & Khai phá' })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Sản phẩm', exact: true })).toHaveClass('nav-disabled');
  expect(screen.getByRole('link', { name: 'Tồn kho', exact: true })).not.toHaveClass('nav-disabled');
});

test('keeps admin navigation enabled', () => {
  show('/admin/mining-runs');
  expect(screen.getByRole('link', { name: 'Lịch sử Mining RUN', exact: true })).not.toHaveClass('nav-disabled');
  expect(screen.getByRole('link', { name: 'Sản phẩm', exact: true })).not.toHaveClass('nav-disabled');
});
