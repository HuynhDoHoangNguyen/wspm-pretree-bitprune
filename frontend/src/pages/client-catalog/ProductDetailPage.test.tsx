import React from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { CatalogApiError, getProduct, getProducts } from '../../api/catalog/catalog';
import { listPublicCombos } from '../../api/recommendation/combos';
import { ProductDetailResponse } from '../../models/catalog/catalog';
import ProductDetailPage from './ProductDetailPage';

jest.mock('../../api/catalog/catalog', () => ({ ...jest.requireActual('../../api/catalog/catalog'), getProduct: jest.fn(), getProducts: jest.fn().mockResolvedValue([]) }));
jest.mock('../../api/recommendation/combos', () => ({ ...jest.requireActual('../../api/recommendation/combos'), listPublicCombos: jest.fn() }));
const comboMock = listPublicCombos as jest.MockedFunction<typeof listPublicCombos>;
const getProductMock = getProduct as jest.MockedFunction<typeof getProduct>;
const listHref = '/future-product-list';
// Synthetic API fixtures are test-only; the page has no default/mock product.
const product: ProductDetailResponse = {
  productId: '9007199254740993', productName: 'Sản phẩm A', categoryId: 'cat/A',
  productStatus: 'ACTIVE', price: 12345.6789, imageUrl: '/uploads/products/a.jpg',
  createdAt: '2026-10-01T09:30:00',
  images: [
    { imageId: 'image/a', imageUrl: '/uploads/products/a.jpg', altText: 'Góc trước', displayOrder: 0 },
    { imageId: 'image/b', imageUrl: '/uploads/products/b.png', altText: 'Góc sau', displayOrder: 1 },
  ],
};
const otherProduct: ProductDetailResponse = {
  ...product, productId: 'sku/B', productName: 'Sản phẩm B', imageUrl: '/uploads/products/c.jpg', images: [],
};
function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}
function mainImage() { return within(screen.getByRole('group', { name: 'Ảnh chính' })).getByRole('img'); }

beforeEach(() => {
  getProductMock.mockReset().mockResolvedValue(product);
  (getProducts as jest.Mock).mockResolvedValue([]);
  comboMock.mockReset().mockReturnValue(new Promise(() => undefined));
});

test('shows loading and passes the exact string productId and AbortSignal to the Slice 1 client', () => {
  getProductMock.mockReturnValue(new Promise(() => undefined));
  render(<ProductDetailPage productId="9007199254740993" productListHref={listHref}/>);
  expect(screen.getByRole('status')).toHaveTextContent('Đang tải chi tiết sản phẩm');
  expect(getProductMock).toHaveBeenCalledWith('9007199254740993', expect.any(AbortSignal));
  expect(screen.queryByRole('heading', { level: 1 })).not.toBeInTheDocument();
});

test('renders only confirmed DTO information and formats the received price without arithmetic/rounding to two decimals', async () => {
  const frozen = Object.freeze({ ...product, images: Object.freeze(product.images.map((image) => Object.freeze({ ...image }))) });
  getProductMock.mockResolvedValue(frozen);
  render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Sản phẩm A');
  expect(screen.getByText('12.345,6789 ₫')).toBeInTheDocument();
  expect(screen.getByText('9007199254740993')).toBeInTheDocument();
  expect(screen.getByText('cat/A')).toBeInTheDocument();
  expect(screen.getByText('ACTIVE')).toBeInTheDocument();
  expect(screen.getByText(product.createdAt!)).toHaveAttribute('datetime', product.createdAt);
  expect(screen.queryByText(/mô tả|thương hiệu|bảo hành/i)).not.toBeInTheDocument();
  expect(frozen.price).toBe(12345.6789);
});

test.each([{ price: null, label: 'Chưa có giá' }, { price: 0, label: '0 ₫' }])('handles price $price and nullable DTO fields', async ({ price, label }) => {
  getProductMock.mockResolvedValue({ ...product, price, productName: null, categoryId: null, productStatus: null, createdAt: null });
  render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Sản phẩm chưa có tên');
  expect(screen.getByText(label)).toBeInTheDocument();
  expect(screen.getByText('Chưa có danh mục')).toBeInTheDocument();
  expect(screen.getAllByText('Chưa có thông tin')).toHaveLength(2);
});

test('deduplicates the primary URL and lets keyboard-accessible thumbnail buttons select the main image', async () => {
  render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  await screen.findByRole('heading', { level: 1 });
  expect(mainImage()).toHaveAttribute('src', product.imageUrl);
  expect(mainImage()).toHaveAttribute('alt', 'Góc trước');
  const thumbs = screen.getByRole('group', { name: 'Chọn ảnh sản phẩm' });
  expect(within(thumbs).getAllByRole('button')).toHaveLength(2);
  expect(screen.getByRole('button', { name: 'Xem ảnh 1' })).toHaveAttribute('aria-pressed', 'true');
  fireEvent.click(screen.getByRole('button', { name: 'Xem ảnh 2' }));
  expect(mainImage()).toHaveAttribute('src', '/uploads/products/b.png');
  expect(mainImage()).toHaveAttribute('alt', 'Góc sau');
  expect(screen.getByRole('button', { name: 'Xem ảnh 2' })).toHaveAttribute('aria-pressed', 'true');
});

test('uses images[] when imageUrl is absent and preserves the API order/alt fallback', async () => {
  getProductMock.mockResolvedValue({ ...product, imageUrl: null, images: [
    { ...product.images[1], altText: null }, product.images[0],
  ] });
  render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  await screen.findByRole('heading', { level: 1 });
  expect(mainImage()).toHaveAttribute('src', '/uploads/products/b.png');
  expect(mainImage()).toHaveAttribute('alt', 'Sản phẩm A');
});

test('uses the primary image alone without unnecessary thumbnails', async () => {
  getProductMock.mockResolvedValue({ ...product, images: [] });
  render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  await screen.findByRole('heading', { level: 1 });
  expect(mainImage()).toHaveAttribute('src', '/uploads/products/a.jpg');
  expect(screen.queryByRole('group', { name: 'Chọn ảnh sản phẩm' })).not.toBeInTheDocument();
});

test('shows a placeholder for absent or blank image URLs without inventing an image', async () => {
  getProductMock.mockResolvedValue({ ...product, imageUrl: null, images: [{ ...product.images[0], imageUrl: ' ' }] });
  render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  await screen.findByRole('heading', { level: 1 });
  expect(mainImage()).toHaveAccessibleName('Chưa có ảnh cho Sản phẩm A');
  expect(mainImage().tagName).not.toBe('IMG');
});

test('falls back to the next API image after load failure and to a placeholder if all images fail', async () => {
  render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  await screen.findByRole('heading', { level: 1 });
  fireEvent.error(mainImage());
  expect(screen.getByRole('button', { name: 'Xem ảnh 1' })).toBeDisabled();
  expect(mainImage()).toHaveAttribute('src', '/uploads/products/b.png');
  fireEvent.error(mainImage());
  expect(mainImage()).toHaveAccessibleName('Chưa có ảnh cho Sản phẩm A');
  expect(screen.getByRole('button', { name: 'Xem ảnh 2' })).toBeDisabled();
});

test('handles thumbnail load failure without showing a broken thumbnail or selecting its URL', async () => {
  render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  await screen.findByRole('heading', { level: 1 });
  const second = screen.getByRole('button', { name: 'Xem ảnh 2' });
  fireEvent.error(within(second).getByAltText(''));
  expect(second).toBeDisabled();
  expect(within(second).queryAllByText(() => true, { selector: 'img', ignore: false })).toHaveLength(0);
  expect(mainImage()).toHaveAttribute('src', '/uploads/products/a.jpg');
});

test('404 is a not-found state rather than a general error', async () => {
  getProductMock.mockRejectedValue(new CatalogApiError(404, 'RESOURCE_NOT_FOUND', 'private details', 'request-id'));
  render(<ProductDetailPage productId="missing" productListHref={listHref}/>);
  expect(await screen.findByText('Không tìm thấy sản phẩm.')).toHaveAttribute('role', 'status');
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(screen.queryByRole('heading', { level: 1 })).not.toBeInTheDocument();
});

test.each([new Error('private network details'), new CatalogApiError(500, 'INTERNAL_ERROR', 'private DB details', null)])('request failure displays a safe error', async (error) => {
  getProductMock.mockRejectedValue(error);
  render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được chi tiết sản phẩm');
  expect(screen.queryByText(error.message)).not.toBeInTheDocument();
  expect(screen.queryByRole('heading', { level: 1 })).not.toBeInTheDocument();
});

test('handles a synchronous invalid-ID rejection from the existing client', async () => {
  getProductMock.mockImplementation(() => { throw new Error('INVALID_PRODUCT_ID'); });
  render(<ProductDetailPage productId="" productListHref={listHref}/>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được chi tiết sản phẩm');
});

test('productId changes immediately hide old details, abort requests and reset gallery state', async () => {
  const pending = deferred<ProductDetailResponse>();
  getProductMock.mockResolvedValueOnce(product).mockReturnValueOnce(pending.promise);
  const view = render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  await screen.findByRole('heading', { level: 1 });
  fireEvent.click(screen.getByRole('button', { name: 'Xem ảnh 2' }));
  const signal = getProductMock.mock.calls[0][1];
  view.rerender(<ProductDetailPage productId="sku/B" productListHref={listHref}/>);
  expect(signal?.aborted).toBe(true);
  expect(screen.getByRole('status')).toHaveTextContent('Đang tải');
  expect(screen.queryByText('Sản phẩm A')).not.toBeInTheDocument();
  expect(screen.queryByRole('group', { name: 'Ảnh chính' })).not.toBeInTheDocument();
  expect(getProductMock).toHaveBeenLastCalledWith('sku/B', expect.any(AbortSignal));
  await act(async () => { pending.resolve(otherProduct); });
  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Sản phẩm B');
  expect(mainImage()).toHaveAttribute('src', '/uploads/products/c.jpg');
});

test.each(['resolve', 'reject'])('ignores obsolete request %s after another product has loaded', async (completion) => {
  const obsolete = deferred<ProductDetailResponse>();
  getProductMock.mockReturnValueOnce(obsolete.promise).mockResolvedValueOnce(otherProduct);
  const view = render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  view.rerender(<ProductDetailPage productId="sku/B" productListHref={listHref}/>);
  await screen.findByRole('heading', { level: 1 });
  await act(async () => {
    if (completion === 'resolve') obsolete.resolve(product);
    else obsolete.reject(new CatalogApiError(404, 'RESOURCE_NOT_FOUND', 'obsolete', null));
  });
  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Sản phẩm B');
  expect(screen.queryByText('Sản phẩm A')).not.toBeInTheDocument();
  expect(screen.queryByText('Không tìm thấy sản phẩm.')).not.toBeInTheDocument();
});

test.each(['resolve', 'reject'])('unmount aborts the request and ignores late %s', async (completion) => {
  const pending = deferred<ProductDetailResponse>();
  getProductMock.mockReturnValue(pending.promise);
  const view = render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  const signal = getProductMock.mock.calls[0][1];
  view.unmount();
  expect(signal?.aborted).toBe(true);
  await act(async () => {
    if (completion === 'resolve') pending.resolve(product);
    else pending.reject(new Error('late error'));
  });
});

test('back navigation uses the supplied href/callback without requiring the shared router', async () => {
  const onBackToList = jest.fn((event) => event.preventDefault());
  render(<ProductDetailPage productId={product.productId} productListHref={listHref} onBackToList={onBackToList}/>);
  const link = screen.getByRole('link', { name: 'Về danh sách sản phẩm' });
  expect(link).toHaveAttribute('href', listHref);
  fireEvent.click(link);
  expect(onBackToList).toHaveBeenCalledTimes(1);
  await screen.findByRole('heading', { level: 1 });
});

test('renders no fake combo or unsupported mining/discount data and exposes related product section', async () => {
  render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  await screen.findByRole('heading', { level: 1 });
  expect(screen.queryByRole('article')).not.toBeInTheDocument();
  expect(comboMock).toHaveBeenCalledWith(product.productId, expect.any(AbortSignal));
  expect(screen.queryByText(/support|weight|giảm giá/i)).not.toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Sản phẩm liên quan' })).toBeInTheDocument();
  expect(getProductMock).toHaveBeenCalledTimes(1);
});

test('Combo API failure leaves product information and gallery visible', async () => {
  comboMock.mockRejectedValue(new Error('offline'));
  render(<ProductDetailPage productId={product.productId} productListHref={listHref}/>);
  await screen.findByRole('alert');
  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(product.productName!);
  expect(screen.getByRole('group', { name: 'Ảnh chính' })).toBeVisible();
  expect(screen.getByRole('button', { name: 'Thử lại' })).toBeVisible();
});
