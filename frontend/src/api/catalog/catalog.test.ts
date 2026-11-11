import { CatalogApiError, getCategories, getLatestProducts, getProduct, getProductImageUrl, getProducts } from './catalog';
import { ProductResponse } from '../../models/catalog/catalog';

// Synthetic wire fixtures only; no application data or live server is used.
const product: ProductResponse = {
  productId: '9007199254740993', productName: 'Laptop', categoryId: 'category-1',
  productStatus: 'DISCONTINUED', price: 1234.5, imageUrl: '/uploads/products/sample.jpg',
  createdAt: '2026-11-03T12:34:56.123456',
};
const fetchMock = jest.fn();
const originalFetch = global.fetch;
const envelope = (data: unknown) => ({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'request-1' });
const response = (body: unknown, status = 200, requestId: string | null = 'request-1') => ({
  ok: status >= 200 && status < 300, status,
  headers: { get: () => requestId }, json: async () => body,
} as unknown as Response);

beforeEach(() => { fetchMock.mockReset(); global.fetch = fetchMock; });
afterEach(() => { global.fetch = originalFetch; });

test.each([
  ['/api/v1/categories', getCategories, [{ categoryId: 'category-1', categoryName: null }]],
  ['/api/v1/products', getProducts, [product]],
  ['/api/v1/products?sort=latest', getLatestProducts, [product]],
] as const)('reads %s using the shared correlated envelope and public GET', async (path, read, data) => {
  fetchMock.mockResolvedValue(response(envelope(data)));
  const signal = new AbortController().signal;
  expect(await read(signal)).toEqual(data);
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(fetchMock).toHaveBeenCalledWith(path, {
    method: 'GET', credentials: 'omit', headers: { Accept: 'application/json' }, signal,
  });
});

test('preserves latest order, nullable fields and LocalDateTime without inventing data', async () => {
  const nullable = { productId: 'p2', productName: null, categoryId: null, productStatus: null, price: null, imageUrl: null, createdAt: null };
  const rows = [nullable, product];
  fetchMock.mockResolvedValue(response(envelope(rows)));
  expect(await getLatestProducts()).toEqual(rows);
  fetchMock.mockResolvedValue(response(envelope([])));
  expect(await getProducts()).toEqual([]);
});

test('reads detail by exact string ID and retains gallery metadata', async () => {
  const detail = { ...product, images: [{ imageId: 'image-1', imageUrl: product.imageUrl, altText: null, displayOrder: 0 }] };
  fetchMock.mockResolvedValue(response(envelope(detail)));
  expect(await getProduct(product.productId)).toEqual(detail);
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/products/9007199254740993');
  await getProduct('product /?#');
  expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/products/product%20%2F%3F%23');
});

test('preserves the backend not-found error without retries or Auth calls', async () => {
  fetchMock.mockResolvedValue(response({
    status: 404, code: 'RESOURCE_NOT_FOUND', message: 'Resource was not found.',
    path: '/api/v1/products/missing', requestId: 'request-1', details: [],
  }, 404));
  await expect(getProduct('missing')).rejects.toMatchObject({
    name: 'CatalogApiError', status: 404, code: 'RESOURCE_NOT_FOUND', requestId: 'request-1',
  });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

test.each([
  response([product]),
  response(envelope([product]), 200, null),
  response({ ...envelope([product]), requestId: 'different' }),
  response(envelope([{ ...product, productId: 123 }])),
  response(envelope([{ ...product, price: '1234.50' }])),
  response(envelope([{ productId: 'p1', productName: 'Incomplete' }])),
])('rejects legacy, uncorrelated or incompatible product payloads', async wire => {
  fetchMock.mockResolvedValue(wire);
  await expect(getProducts()).rejects.toBeInstanceOf(CatalogApiError);
  await expect(getProducts()).rejects.toMatchObject({ code: 'INVALID_API_RESPONSE' });
});

test('rejects malformed gallery entries instead of silently dropping them', async () => {
  fetchMock.mockResolvedValue(response(envelope({ ...product, images: [{ imageId: 1, imageUrl: 'x' }] })));
  await expect(getProduct(product.productId)).rejects.toMatchObject({ code: 'INVALID_API_RESPONSE' });
});

test('rejects a non-JSON error without exposing its body', async () => {
  const wire = response(null, 503);
  wire.json = async () => { throw new SyntaxError('private-server-body'); };
  fetchMock.mockResolvedValue(wire);
  await expect(getProducts()).rejects.toMatchObject({ status: 503, code: 'INVALID_API_RESPONSE' });
});

test('preserves cancellation during both fetch and response-body reading', async () => {
  const controller = new AbortController();
  const abort = new DOMException('Aborted', 'AbortError');
  fetchMock.mockRejectedValue(abort);
  await expect(getProducts(controller.signal)).rejects.toBe(abort);
  const wire = response(null);
  wire.json = async () => { controller.abort(); throw abort; };
  fetchMock.mockResolvedValue(wire);
  await expect(getProducts(controller.signal)).rejects.toBe(abort);
});

test.each(['', '.', '..'])('rejects an ID that cannot identify a detail route: %s', id => {
  expect(() => getProduct(id)).toThrow('INVALID_PRODUCT_ID');
  expect(fetchMock).not.toHaveBeenCalled();
});

test('constructs public image URLs without JSON fetching', () => {
  expect(getProductImageUrl('sample.JPG')).toBe('/uploads/products/sample.JPG');
  expect(getProductImageUrl('sample.png')).toBe('/uploads/products/sample.png');
  expect(getProductImageUrl('sample.gif')).toBe('/uploads/products/sample.gif');
  expect(fetchMock).not.toHaveBeenCalled();
});

test.each(['../sample.jpg', '%2e%2e.jpg', 'folder/sample.jpg', 'folder\\sample.jpg', 'sample.svg', 'sample.jpg\n'])('rejects unsupported image filename %s', filename => {
  expect(() => getProductImageUrl(filename)).toThrow('INVALID_PRODUCT_IMAGE_FILENAME');
});
