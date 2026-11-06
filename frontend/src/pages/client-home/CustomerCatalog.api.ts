import { unwrapApiResponse } from 'api/common/response';

export interface CatalogProduct {
  productId: string;
  productName: string;
  categoryId: string | null;
  productStatus: string;
  price: number;
  imageUrl: string | null;
}

export async function loadCatalog(signal: AbortSignal): Promise<CatalogProduct[]> {
  const response = await fetch('/api/v1/products', {
    headers: { Accept: 'application/json' }, credentials: 'same-origin', redirect: 'error', signal,
  });
  if (!response.ok) throw new Error('CATALOG_UNAVAILABLE');
  const data = unwrapApiResponse<unknown>(await response.json(), response.headers.get('X-Request-Id'));
  if (!Array.isArray(data) || !data.every(product => product
    && typeof product.productId === 'string' && !!product.productId
    && typeof product.productName === 'string' && typeof product.productStatus === 'string'
    && (product.categoryId === null || typeof product.categoryId === 'string')
    && typeof product.price === 'number' && Number.isFinite(product.price) && product.price >= 0
    && (product.imageUrl === null || (typeof product.imageUrl === 'string'
      && /^\/uploads\/products\/[^/?#\\]+$/.test(product.imageUrl))))) {
    throw new Error('INVALID_CATALOG_RESPONSE');
  }
  return data;
}
