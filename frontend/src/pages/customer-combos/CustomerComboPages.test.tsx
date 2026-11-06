import React from 'react';
import { ColorSchemeProvider } from '@mantine/core';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { CustomerComboDetailPage, CustomerComboListPage } from './CustomerComboPages';
import Client from 'pages/Client';
import { displayMoney, listPublicCombos, PublicCombo, readPublicCombo } from './CustomerComboRead.api';

jest.mock('components', () => ({
  ClientHeader: () => { throw new Error('Legacy header mounted'); },
  ClientFooter: () => { throw new Error('Legacy footer mounted'); },
  LoadingMiddleware: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
jest.mock('auth/session', () => ({ useSharedAuth: () => ({ user: null }) }));
jest.mock('auth/useSignOut', () => ({ useSignOut: () => jest.fn() }));
jest.mock('stores/use-auth-store', () => () => ({ user: null }));
jest.mock('react-query', () => ({ useIsFetching: () => 0 }));

const combo: PublicCombo = {
  comboId: 'c-z', name: 'Combo API', kind: 'SAME_ORDER_BUNDLE', status: 'PUBLISHED', explanation: 'Cùng một lần mua',
  metrics: { supportAbs: 12, supportRatio: .12, sequenceCount: 100, patternWeight: .5 },
  pricing: { basis: 'PUBLISH_SNAPSHOT', currencyCode: 'VND', referenceOriginalPrice: '200.00', referenceSalePrice: '150.00',
    discountType: 'FIXED_PRICE', discountValue: '150.0000', maxDiscountAmount: null, isCheckoutQuote: false },
  provenance: { primaryPatternId: '7', canonicalPattern: '<(1 2)>', batch: { batchId: 'b', runId: 'r', snapshotId: 's', algorithm: 'WSPM_PRETREE', rankingPolicy: 'SUPPORT_THEN_PATTERN_WEIGHT_V1', patternWeightPolicy: 'stored-policy' } },
  items: [{ itemsetOrder: 1, itemOrder: 1, quantity: 2, unitPriceSnapshot: '100.00', anchorEligible: true, discountEligible: true,
    product: { productId: 'p', name: 'Laptop API', sku: 'SKU', categoryId: 'cat', imageUrl: '/uploads/products/p.jpg', currentPrice: '999.00' } }],
};
const products = ['p', 'q'].map(id => ({ productId: id, productName: id === 'p' ? 'Laptop API' : 'Mouse API', categoryId: 'cat', productStatus: 'ACTIVE', price: 999, imageUrl: `/uploads/products/${id}.jpg` }));
const topK = (id: string, combos: PublicCombo[]) => ({ productId: id, requestedK: 4, effectiveK: 4, items: combos.map((c, i) => ({ rank: i + 1, combo: c })) });
const response = (data: unknown, status = 200) => ({ ok: status === 200, status, headers: { get: () => 'combo-request' },
  json: async () => ({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'combo-request' }) });
const network = jest.fn();
const originalFetch = global.fetch;
beforeEach(() => { global.fetch = network; network.mockReset(); });
beforeAll(() => Object.defineProperty(window, 'ResizeObserver', { configurable: true,
  value: class { observe() { return undefined; } unobserve() { return undefined; } disconnect() { return undefined; } },
}));
afterAll(() => { global.fetch = originalFetch; });
function page(route = '/products/p/combos') {
  return render(<ColorSchemeProvider colorScheme="light" toggleColorScheme={() => undefined}><MemoryRouter initialEntries={[route]}><Routes>
    <Route path="/" element={<Client/>}>
      <Route path="/products/:productId/combos" element={<CustomerComboListPage/>}/>
      <Route path="/combos/:comboId" element={<CustomerComboDetailPage/>}/>
    </Route>
  </Routes></MemoryRouter></ColorSchemeProvider>);
}
function source() {
  network.mockImplementation((url: string) => {
    if (url === '/api/v1/products') return Promise.resolve(response(products));
    if (url === '/api/v1/recommendations/combos/c-z') return Promise.resolve(response(combo));
    const id = new URL(url, 'http://local.test').searchParams.get('productId') || '';
    return Promise.resolve(response(topK(id, id === 'p' ? [combo] : [])));
  });
}

test('real list navigates to API detail, preserves quantity/prices/images and has no cart', async () => {
  source(); page();
  expect(await screen.findByRole('heading', { name: 'Combo API' })).toBeInTheDocument();
  const header = screen.getByRole('banner');
  const footer = screen.getByRole('contentinfo');
  expect(header).toHaveTextContent('Không gian combo của bạn');
  expect(footer).toHaveTextContent('© 2022 ComboStore. Bảo lưu mọi quyền.');
  expect(within(screen.getByRole('article')).getByRole('img', { name: 'Laptop API' })).toHaveAttribute('src', '/uploads/products/p.jpg');
  expect(screen.getByText(/12\/100/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('link', { name: 'Xem chi tiết' }));
  expect(await screen.findByRole('heading', { level: 1, name: 'Combo API' })).toBeInTheDocument();
  expect(screen.getByRole('banner')).toBe(header);
  expect(screen.getByRole('contentinfo')).toBe(footer);
  expect(screen.getByText('Số lượng: 2')).toBeInTheDocument();
  expect(screen.getByRole('img', { name: 'Laptop API' })).toHaveAttribute('src', '/uploads/products/p.jpg');
  expect(screen.getByText(/Giá Combo:/)).toHaveTextContent('150 VND');
  expect(screen.getByText(/không phải báo giá thanh toán/)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /giỏ hàng/i })).not.toBeInTheDocument();
  expect(network.mock.calls.every(([url]) => url.startsWith('/api/v1/'))).toBe(true);
  expect(network).toHaveBeenCalledWith('/api/v1/recommendations/combos/c-z', expect.anything());
});

test('changing product clears old results and renders true empty data without fixture fallback', async () => {
  source();page();await screen.findByRole('heading', { name: 'Combo API' });
  fireEvent.change(screen.getByLabelText('Sản phẩm'), { target: { value: 'q' } });
  expect(await screen.findByText('Hiện chưa có combo được công bố phù hợp với sản phẩm này.')).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Combo API' })).not.toBeInTheDocument();
  expect(network.mock.calls.some(([url]) => url.includes('productId=q&k=4'))).toBe(true);
});

test('loading and retry use the real endpoint', async () => {
  let finish: (value: unknown) => void = () => undefined;
  network.mockImplementation((url: string) => url === '/api/v1/products' ? Promise.resolve(response(products))
    : new Promise(resolve => { finish = resolve; }));
  page();expect(screen.getByRole('status')).toHaveTextContent('Đang tải Combo');
  finish(response(null, 503));
  expect(await screen.findByRole('alert')).toBeInTheDocument();
  source();fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByRole('heading', { name: 'Combo API' })).toBeInTheDocument();
});

test('detail 404 does not expose private state or use fixture data', async () => {
  network.mockResolvedValue(response(null, 404));page('/combos/missing');
  expect(await screen.findByRole('alert')).toHaveTextContent('không tồn tại hoặc hiện chưa được công bố');
  expect(screen.queryByRole('heading', { name: 'Combo API' })).not.toBeInTheDocument();
  expect(network).toHaveBeenCalledTimes(1);
});

test('source order is preserved rather than re-ranked by frontend', async () => {
  network.mockResolvedValue(response(topK('p', [combo, { ...combo, comboId: 'c-a', name: 'Second', metrics: { ...combo.metrics, supportAbs: 90 } }])));
  const result = await listPublicCombos('p', new AbortController().signal);
  expect(result.items.map(item => item.combo.comboId)).toEqual(['c-z', 'c-a']);
});

test('invalid/private response and duplicate envelopes are not accepted', async () => {
  network.mockResolvedValue(response({ ...combo, status: 'APPROVED' }));
  await expect(readPublicCombo('c-z', new AbortController().signal)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  network.mockResolvedValue(response({ code: 'SUCCESS', data: topK('p', []) }));
  await expect(listPublicCombos('p', new AbortController().signal)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
});

test('money display does not round through JavaScript Number', () => {
  expect(displayMoney('9007199254740993.99', 'VND')).toBe('9.007.199.254.740.993,99 VND');
  expect(displayMoney('100.0000', '%')).toBe('100 %');
});

test('a response arriving after unmount is ignored', async () => {
  let finish: (value: unknown) => void = () => undefined;
  network.mockImplementation((url: string) => url === '/api/v1/products' ? Promise.resolve(response(products))
    : new Promise(resolve => { finish = resolve; }));
  const view = page();
  await waitFor(() => expect(network).toHaveBeenCalledTimes(2));
  view.unmount();finish(response(topK('p', [combo])));
  await Promise.resolve();
  expect(screen.queryByText('Combo API')).not.toBeInTheDocument();
});
