import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { getProducts } from '../api/catalog';
import { ProductResponse } from '../models/catalog';
import ProductListPage from './ProductListPage';

jest.mock('../api/catalog', () => ({ getProducts: jest.fn() }));
const getProductsMock = getProducts as jest.MockedFunction<typeof getProducts>;
const href = (id: string) => `/future-products/${encodeURIComponent(id)}`;
const products: readonly ProductResponse[] = [
  { productId: '9007199254740993', productName: 'Sản phẩm A', categoryId: '9007199254740993',
    productStatus: null, price: 12000, imageUrl: '/uploads/products/a.jpg', createdAt: null },
  { productId: 'sku/B', productName: 'Sản phẩm B', categoryId: '9007199254740992',
    productStatus: null, price: 0, imageUrl: null, createdAt: null },
  { productId: 'unassigned', productName: null, categoryId: null,
    productStatus: null, price: null, imageUrl: null, createdAt: null },
];

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

beforeEach(() => getProductsMock.mockReset());

test('shows loading until the catalog request completes', () => {
  getProductsMock.mockReturnValue(new Promise(() => undefined));
  render(<ProductListPage getProductHref={href}/>);
  expect(screen.getByRole('status')).toHaveTextContent('Đang tải sản phẩm');
  expect(screen.queryByRole('list')).not.toBeInTheDocument();
  expect(getProductsMock).toHaveBeenCalledWith(expect.any(AbortSignal));
});

test('all mode reuses cards with images, prices, null fallbacks and exact ID navigation', async () => {
  getProductsMock.mockResolvedValue(products);
  const onProductClick = jest.fn((_id, event) => event.preventDefault());
  render(<ProductListPage getProductHref={href} onProductClick={onProductClick}/>);
  expect(await screen.findAllByRole('listitem')).toHaveLength(3);
  expect(screen.getByAltText('Sản phẩm A')).toHaveAttribute('src', '/uploads/products/a.jpg');
  expect(screen.getByText('12.000 ₫')).toBeInTheDocument();
  expect(screen.getByText('0 ₫')).toBeInTheDocument();
  expect(screen.getByText('Chưa có giá')).toBeInTheDocument();
  expect(screen.getByText('Sản phẩm chưa có tên')).toBeInTheDocument();
  const link = screen.getByRole('link', { name: 'Xem sản phẩm Sản phẩm B' });
  expect(link).toHaveAttribute('href', '/future-products/sku%2FB');
  fireEvent.click(link);
  expect(onProductClick).toHaveBeenCalledWith('sku/B', expect.anything());
});

test('filters exact string category IDs locally without coercion or server filter arguments', async () => {
  getProductsMock.mockResolvedValue(products);
  render(<ProductListPage categoryId="9007199254740993" getProductHref={href}/>);
  expect(await screen.findAllByRole('listitem')).toHaveLength(1);
  expect(screen.getByText('Sản phẩm A')).toBeInTheDocument();
  expect(screen.queryByText('Sản phẩm B')).not.toBeInTheDocument();
  expect(getProductsMock.mock.calls[0]).toEqual([expect.any(AbortSignal)]);
});

test.each([undefined, null])('empty catalog shows all-mode empty state for category %s', async (categoryId) => {
  getProductsMock.mockResolvedValue([]);
  render(<ProductListPage categoryId={categoryId} getProductHref={href}/>);
  expect(await screen.findByText('Chưa có sản phẩm.')).toBeInTheDocument();
  expect(screen.queryByRole('list')).not.toBeInTheDocument();
});

test.each(['missing', '0', ''])('no matching category %s shows filtered empty state', async (categoryId) => {
  getProductsMock.mockResolvedValue(products);
  render(<ProductListPage categoryId={categoryId} getProductHref={href}/>);
  expect(await screen.findByText('Không có sản phẩm trong danh mục này.')).toBeInTheDocument();
  expect(screen.queryByRole('list')).not.toBeInTheDocument();
});

test('API failures show an error without exposing raw error details', async () => {
  getProductsMock.mockRejectedValue(new Error('internal connection details'));
  render(<ProductListPage getProductHref={href}/>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được sản phẩm');
  expect(screen.queryByText('internal connection details')).not.toBeInTheDocument();
  expect(screen.queryByRole('list')).not.toBeInTheDocument();
});

test('category changes hide previous cards, abort previous request and can return to all mode', async () => {
  const next = deferred<readonly ProductResponse[]>();
  getProductsMock.mockResolvedValueOnce(products).mockReturnValueOnce(next.promise).mockResolvedValueOnce(products);
  const view = render(<ProductListPage getProductHref={href}/>);
  await screen.findByText('Sản phẩm A');
  const firstSignal = getProductsMock.mock.calls[0][0];
  view.rerender(<ProductListPage categoryId="9007199254740992" getProductHref={href}/>);
  expect(firstSignal?.aborted).toBe(true);
  expect(screen.getByRole('status')).toHaveTextContent('Đang tải sản phẩm');
  expect(screen.queryByText('Sản phẩm A')).not.toBeInTheDocument();
  await act(async () => { next.resolve(products); });
  expect(screen.getAllByRole('listitem')).toHaveLength(1);
  expect(screen.getByText('Sản phẩm B')).toBeInTheDocument();
  view.rerender(<ProductListPage getProductHref={href}/>);
  expect(await screen.findAllByRole('listitem')).toHaveLength(3);
});

test.each(['resolve', 'reject'])('ignores obsolete request %s after the latest category succeeds', async (completion) => {
  const obsolete = deferred<readonly ProductResponse[]>();
  getProductsMock.mockReturnValueOnce(obsolete.promise).mockResolvedValueOnce(products);
  const view = render(<ProductListPage categoryId="9007199254740993" getProductHref={href}/>);
  view.rerender(<ProductListPage categoryId="9007199254740992" getProductHref={href}/>);
  await screen.findByText('Sản phẩm B');
  await act(async () => {
    if (completion === 'resolve') obsolete.resolve(products);
    else obsolete.reject(new Error('obsolete'));
  });
  expect(screen.getAllByRole('listitem')).toHaveLength(1);
  expect(screen.getByText('Sản phẩm B')).toBeInTheDocument();
  expect(screen.queryByText('Sản phẩm A')).not.toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('unmount aborts the request and ignores its later completion', async () => {
  const pending = deferred<readonly ProductResponse[]>();
  getProductsMock.mockReturnValue(pending.promise);
  const view = render(<ProductListPage getProductHref={href}/>);
  const signal = getProductsMock.mock.calls[0][0];
  view.unmount();
  expect(signal?.aborted).toBe(true);
  await act(async () => { pending.resolve(products); });
});

test('client pagination visits all 25 products exactly once with 12 products per full page', async () => {
  const many = Array.from({ length: 25 }, (_, index) => ({ ...products[0], productId: String(index), productName: `Item ${index}` }));
  getProductsMock.mockResolvedValue(many);
  render(<ProductListPage getProductHref={href}/>);
  const seen: string[] = [];
  await screen.findByText('Item 0');
  for (let page = 1; page <= 3; page++) {
    if (page > 1) fireEvent.click(screen.getByRole('button', { name: String(page) }));
    const names = screen.getAllByRole('link').map(link => link.getAttribute('aria-label')!);
    expect(names).toHaveLength(page === 3 ? 1 : 12);
    seen.push(...names);
  }
  expect(seen).toEqual(many.map(p => `Xem sản phẩm ${p.productName}`));
  expect(new Set(seen).size).toBe(25);
  expect(getProductsMock).toHaveBeenCalledTimes(1);
});

test('filters the complete response before pagination and clamps an out-of-range requested page', async () => {
  const many = Array.from({ length: 30 }, (_, index) => ({ ...products[0], productId: String(index),
    productName: `Item ${index}`, categoryId: index % 2 === 0 ? 'a' : 'b' }));
  getProductsMock.mockResolvedValue(many);
  render(<ProductListPage categoryId="b" page={999} getProductHref={href}/>);
  await screen.findByText('Item 25');
  expect(screen.getAllByRole('listitem')).toHaveLength(3);
  expect(screen.getByText('Trang 2 / 2 · 15 sản phẩm')).toBeInTheDocument();
  expect(screen.queryByText('Item 24')).not.toBeInTheDocument();
});

test.each([0, -1, NaN, Infinity, 1.5])('invalid page %s safely selects the first page', async (page) => {
  getProductsMock.mockResolvedValue(products);
  render(<ProductListPage page={page} getProductHref={href}/>);
  await screen.findByText('Sản phẩm A');
  expect(screen.getByText('Trang 1 / 1 · 3 sản phẩm')).toBeInTheDocument();
});
