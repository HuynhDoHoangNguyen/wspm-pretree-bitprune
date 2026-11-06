import React from 'react';
import { ColorSchemeProvider } from '@mantine/core';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Client from 'pages/Client';
import ClientHome from './ClientHome';
import { loadCatalog } from './CustomerCatalog.api';

jest.mock('components', () => ({
  ClientHeader: () => { throw new Error('Legacy header mounted'); },
  ClientFooter: () => { throw new Error('Legacy footer mounted'); },
  LoadingMiddleware: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
jest.mock('auth/session', () => ({ useSharedAuth: () => ({ user: null }) }));
jest.mock('auth/useSignOut', () => ({ useSignOut: () => jest.fn() }));
jest.mock('stores/use-auth-store', () => () => ({ user: null }));
jest.mock('react-query', () => ({ useIsFetching: () => 0 }));
jest.mock('hooks/use-title', () => () => undefined);

const products = Array.from({ length: 20 }, (_, i) => ({ productId: `p${i}`, productName: `Product ${i}`,
  categoryId: null, productStatus: 'ACTIVE', price: 100000, imageUrl: `/uploads/products/p${i}.jpg` }));
const response = (data: unknown, ok = true) => ({ ok, headers: { get: () => 'catalog-request' },
  json: async () => ({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'catalog-request' }) });
const fetchMock = jest.fn();
const originalFetch = global.fetch;
beforeEach(() => { global.fetch = fetchMock; fetchMock.mockReset(); });
beforeAll(() => Object.defineProperty(window, 'ResizeObserver', { configurable: true,
  value: class { observe() { return undefined; } unobserve() { return undefined; } disconnect() { return undefined; } },
}));
afterAll(() => { global.fetch = originalFetch; });
function catalog() {
  return render(<ColorSchemeProvider colorScheme="light" toggleColorScheme={() => undefined}><MemoryRouter initialEntries={['/']}><Routes>
    <Route path="/" element={<Client/>}><Route index element={<ClientHome/>}/></Route>
  </Routes></MemoryRouter></ColorSchemeProvider>);
}

test('root catalog renders 20 real-contract products and images without mounting Electro shell', async () => {
  fetchMock.mockResolvedValue(response(products));
  catalog();
  expect(await screen.findAllByRole('article')).toHaveLength(20);
  expect(screen.getByRole('banner')).toHaveTextContent('Không gian combo của bạn');
  expect(screen.getByRole('contentinfo')).toHaveTextContent('© 2022 ComboStore. Bảo lưu mọi quyền.');
  expect(screen.getByRole('img', { name: 'Product 0' })).toHaveAttribute('src', '/uploads/products/p0.jpg');
  expect(screen.getAllByText('Đang kinh doanh')).toHaveLength(20);
  expect(screen.getAllByRole('link', { name: 'Xem Combo gợi ý' })[0]).toHaveAttribute('href', '/products/p0/combos');
  expect(screen.getAllByText(/100.000/)).toHaveLength(20);
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(fetchMock).toHaveBeenCalledWith('/api/v1/products', expect.objectContaining({ signal: expect.any(AbortSignal) }));
});

test('customer sign-in uses the same TV3 shell without legacy widgets or requests', () => {
  render(<ColorSchemeProvider colorScheme="light" toggleColorScheme={() => undefined}><MemoryRouter initialEntries={['/signin']}><Routes>
    <Route path="/" element={<Client/>}><Route path="signin" element={<h1>Đăng nhập Customer</h1>}/></Route>
  </Routes></MemoryRouter></ColorSchemeProvider>);
  expect(screen.getByRole('banner')).toHaveTextContent('Không gian combo của bạn');
  expect(screen.getByRole('contentinfo')).toHaveTextContent('© 2022 ComboStore. Bảo lưu mọi quyền.');
  expect(screen.getByRole('heading', { name: 'Đăng nhập Customer' })).toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();
});

test('shows loading then empty', async () => {
  let finish: (value: unknown) => void = () => undefined;
  fetchMock.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  catalog();
  expect(screen.getByRole('status')).toHaveTextContent('Đang tải sản phẩm');
  finish(response([]));
  expect(await screen.findByText('Chưa có sản phẩm.')).toBeInTheDocument();
});

test('shows error and retries the same catalog endpoint', async () => {
  fetchMock.mockResolvedValueOnce(response(null, false)).mockResolvedValueOnce(response(products));
  catalog();
  expect(await screen.findByRole('alert')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
  expect(await screen.findAllByRole('article')).toHaveLength(20);
  expect(fetchMock.mock.calls.every(call => call[0] === '/api/v1/products')).toBe(true);
});

test('rejects malformed catalog and external image URLs', async () => {
  fetchMock.mockResolvedValue(response([{ ...products[0], imageUrl: 'http://localhost:8085/image.jpg' }]));
  await expect(loadCatalog(new AbortController().signal)).rejects.toThrow('INVALID_CATALOG_RESPONSE');
});

test('uses a placeholder when a product has no image', async () => {
  fetchMock.mockResolvedValue(response([{ ...products[0], imageUrl: null }]));
  catalog();
  expect(await screen.findAllByRole('article')).toHaveLength(1);
  expect(screen.getByRole('img', { name: 'Product 0' })).not.toHaveAttribute('src');
});
