import { isApiErrorResponse, unwrapApiResponse } from 'api/common/response';
import { CategoryResponse, ProductDetailResponse, ProductImageResponse, ProductResponse } from '../models/catalog';

const API_ROOT = '/api/v1';

export class CatalogApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly requestId: string | null,
  ) {
    super(message);
    this.name = 'CatalogApiError';
    Object.setPrototypeOf(this, CatalogApiError.prototype);
  }
}

type JsonObject = Record<string, unknown>;
const isObject = (value: unknown): value is JsonObject =>
  value !== null && typeof value === 'object' && !Array.isArray(value);
const nullableString = (value: unknown): value is string | null =>
  value === null || typeof value === 'string';

function isCategory(value: unknown): value is CategoryResponse {
  return isObject(value) && typeof value.categoryId === 'string' && nullableString(value.categoryName);
}

function isProduct(value: unknown): value is ProductResponse {
  return isObject(value) && typeof value.productId === 'string'
    && nullableString(value.productName) && nullableString(value.categoryId)
    && nullableString(value.productStatus) && nullableString(value.imageUrl)
    && nullableString(value.createdAt)
    && (value.price === null || (typeof value.price === 'number' && Number.isFinite(value.price)));
}

function isImage(value: unknown): value is ProductImageResponse {
  return isObject(value) && typeof value.imageId === 'string' && typeof value.imageUrl === 'string'
    && nullableString(value.altText) && typeof value.displayOrder === 'number'
    && Number.isInteger(value.displayOrder);
}

function isDetail(value: unknown): value is ProductDetailResponse {
  return isProduct(value) && isObject(value) && Array.isArray(value.images) && value.images.every(isImage);
}

function arrayOf<T>(isItem: (value: unknown) => value is T) {
  return (value: unknown): value is T[] => Array.isArray(value) && value.every(isItem);
}

async function read<T>(path: string, validate: (value: unknown) => value is T, signal?: AbortSignal): Promise<T> {
  // Public catalog reads do not acquire tokens or depend on the shared Auth session.
  const response = await fetch(path, { method: 'GET', credentials: 'omit', headers: { Accept: 'application/json' }, signal });
  const requestId = response.headers.get('X-Request-Id');
  let body: unknown;
  try {
    body = await response.json();
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new CatalogApiError(response.status, 'INVALID_API_RESPONSE', 'Phản hồi danh mục không hợp lệ.', requestId);
  }

  if (!response.ok) {
    if (isApiErrorResponse(body, response.status) && requestId && body.requestId === requestId) {
      throw new CatalogApiError(response.status, body.code, body.message, requestId);
    }
    throw new CatalogApiError(response.status, 'INVALID_API_RESPONSE', 'Phản hồi danh mục không hợp lệ.', requestId);
  }

  let data: unknown;
  try {
    data = unwrapApiResponse<unknown>(body, requestId);
  } catch {
    throw new CatalogApiError(response.status, 'INVALID_API_RESPONSE', 'Phản hồi danh mục không hợp lệ.', requestId);
  }
  if (!validate(data)) throw new CatalogApiError(response.status, 'INVALID_API_RESPONSE', 'Dữ liệu danh mục không hợp lệ.', requestId);
  return data;
}

export function getCategories(signal?: AbortSignal): Promise<readonly CategoryResponse[]> {
  return read(`${API_ROOT}/categories`, arrayOf(isCategory), signal);
}

export function getProducts(signal?: AbortSignal): Promise<readonly ProductResponse[]> {
  return read(`${API_ROOT}/products`, arrayOf(isProduct), signal);
}

/** Returns the complete server-sorted list; selecting 4–5 entries belongs to a future page. */
export function getLatestProducts(signal?: AbortSignal): Promise<readonly ProductResponse[]> {
  return read(`${API_ROOT}/products?sort=latest`, arrayOf(isProduct), signal);
}

export function getProduct(productId: string, signal?: AbortSignal): Promise<ProductDetailResponse> {
  if (!productId || productId === '.' || productId === '..') throw new Error('INVALID_PRODUCT_ID');
  return read(`${API_ROOT}/products/${encodeURIComponent(productId)}`, isDetail, signal);
}

/** Binary images are loaded by the browser, not passed through the JSON envelope parser. */
export function getProductImageUrl(filename: string): string {
  if (!/^[A-Za-z0-9_-][A-Za-z0-9_.-]*\.(jpg|jpeg|png|gif)$/i.test(filename)
    || filename.includes('..') || filename !== filename.trim()) {
    throw new Error('INVALID_PRODUCT_IMAGE_FILENAME');
  }
  return `/uploads/products/${filename}`;
}
