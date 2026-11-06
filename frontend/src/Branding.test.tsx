import React from 'react';
import { render, screen, within } from '@testing-library/react';
import { ColorSchemeProvider, MantineProvider } from '@mantine/core';
import { MemoryRouter } from 'react-router-dom';
import { readFileSync } from 'fs';
import path from 'path';
import ElectroLogo from './components/ElectroLogo/ElectroLogo';
import ClientFooter from './components/ClientFooter/ClientFooter';
import CustomerCombos from './pages/customer-combos/CustomerCombos';
import AdminDashboardSidebar from './pages/admin-auth-preview/AdminDashboardSidebar';
import useTitle from './hooks/use-title';
import Titles from './constants/Titles';
import { BRAND_DESCRIPTION, BRAND_NAME } from './constants/Brand';

// Isolate the logo consumer from the unrelated legacy components barrel.
jest.mock('components', () => ({ ElectroLogo: jest.requireActual('./components/ElectroLogo/ElectroLogo').default }));
const observer = Object.getOwnPropertyDescriptor(window, 'ResizeObserver');
beforeAll(() => Object.defineProperty(window, 'ResizeObserver', {
  configurable: true, value: class { observe = jest.fn(); unobserve = jest.fn(); disconnect = jest.fn(); },
}));
afterAll(() => {
  if (observer) Object.defineProperty(window, 'ResizeObserver', observer);
  else Reflect.deleteProperty(window, 'ResizeObserver');
});
const originalFetch = global.fetch;
let fetchSpy: jest.Mock;
beforeEach(() => {
  fetchSpy = jest.fn(() => { throw new Error('Unexpected branding test request'); });
  global.fetch = fetchSpy;
});
afterEach(() => { expect(fetchSpy).not.toHaveBeenCalled(); global.fetch = originalFetch; });

function show(element: React.ReactElement, url = '/') {
  return render(<ColorSchemeProvider colorScheme="light" toggleColorScheme={jest.fn()}><MantineProvider>
    <MemoryRouter initialEntries={[url]}>{element}</MemoryRouter>
  </MantineProvider></ColorSchemeProvider>);
}

test('shared wordmark displays ComboStore while preserving the existing width API', () => {
  show(<ElectroLogo width={150}/>);
  const logo = screen.getByRole('img', { name: BRAND_NAME });
  expect(logo).toHaveAttribute('width', '150');
  expect(within(logo).getByText(BRAND_NAME)).toBeInTheDocument();
  expect(within(logo).getByText(BRAND_DESCRIPTION)).toBeInTheDocument();
  ['components/ClientHeader/ClientHeader.tsx', 'components/DefaultHeader/DefaultHeader.tsx', 'pages/admin-signin/AdminSignin.tsx'].forEach(file => {
    expect(readFileSync(path.join(process.cwd(), 'src', file), 'utf8')).toContain('<ElectroLogo');
  });
});

test('customer footer and shared footer expose the ComboStore brand', () => {
  show(<ClientFooter/>);
  expect(screen.getByRole('img', { name: BRAND_NAME })).toBeInTheDocument();
  expect(screen.getByText(/© 2022 ComboStore/)).toBeInTheDocument();
  expect(screen.queryByText(/Electro Corporation/)).not.toBeInTheDocument();
});

test('customer preview header and footer use ComboStore without changing its data source', () => {
  const listCombos = jest.fn();
  show(<CustomerCombos products={[]} source={{ listCombos }} limit={4}/>);
  expect(within(screen.getByRole('banner')).getByText(BRAND_NAME)).toBeInTheDocument();
  expect(within(screen.getByRole('contentinfo')).getByText(/ComboStore/)).toBeInTheDocument();
  expect(listCombos).not.toHaveBeenCalled();
  expect(readFileSync(path.join(process.cwd(), 'src/pages/customer-combos/ComboDetail.tsx'), 'utf8')).toContain('<CustomerStoreHeader/>');
});

test('Admin sidebar uses the same brand without changing its business menu', () => {
  show(<AdminDashboardSidebar active="data-preparation" collapsed={false} mobileOpen={false}
    onSelect={jest.fn()} onToggle={jest.fn()} onLogout={jest.fn()}/>);
  const sidebar = screen.getByRole('complementary', { name: 'Sidebar Admin Preview' });
  expect(within(sidebar).getByText(BRAND_NAME)).toBeInTheDocument();
  expect(within(within(sidebar).getByRole('navigation')).getAllByRole('button')).toHaveLength(8);
});

function TitleProbe({ title }: { title?: string }) { useTitle(title); return null; }
test.each([['/', undefined, 'Trang chủ – ComboStore'], ['/cart', undefined, 'Giỏ hàng – ComboStore'], ['/', 'Chi tiết sản phẩm', 'Chi tiết sản phẩm – ComboStore']])(
  'document title for %s / %s uses ComboStore', (url, title, expected) => {
    show(<TitleProbe title={title}/>, url as string);
    expect(document.title).toBe(expected);
  }
);

test('fallback title and public app metadata use ComboStore', () => {
  expect(Titles['/unknown-branding-route']).toBe(BRAND_NAME);
  const html = readFileSync(path.join(process.cwd(), 'public/index.html'), 'utf8');
  expect(html).toContain(`<title>${BRAND_NAME}</title>`);
  expect(html).toContain(BRAND_DESCRIPTION);
  const manifest = JSON.parse(readFileSync(path.join(process.cwd(), 'public/manifest.json'), 'utf8'));
  expect(manifest.name).toBe(BRAND_NAME); expect(manifest.short_name).toBe(BRAND_NAME);
});
