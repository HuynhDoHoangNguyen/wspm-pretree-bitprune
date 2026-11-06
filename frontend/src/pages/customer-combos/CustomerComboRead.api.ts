import { isApiErrorResponse, unwrapApiResponse } from 'api/common/response';

export interface PublicCombo {
  comboId: string; name: string; kind: string; status: 'PUBLISHED'; explanation: string;
  metrics: { supportAbs: number; sequenceCount: number; supportRatio: number; patternWeight: number };
  pricing: { basis: 'PUBLISH_SNAPSHOT'; currencyCode: string; referenceOriginalPrice: string;
    referenceSalePrice: string; discountType: string; discountValue: string | null;
    maxDiscountAmount: string | null; isCheckoutQuote: false };
  provenance: { primaryPatternId: string; canonicalPattern: string;
    batch: { batchId: string; runId: string; snapshotId: string; algorithm: string;
      rankingPolicy: string; patternWeightPolicy: string } };
  items: { itemsetOrder: number; itemOrder: number; quantity: number; unitPriceSnapshot: string;
    anchorEligible: boolean; discountEligible: boolean;
    product: { productId: string; name: string; sku: string | null; categoryId: string | null;
      imageUrl: string | null; currentPrice: string } }[];
}
export interface PublicTopK {
  productId: string; requestedK: number; effectiveK: number;
  items: { rank: number; combo: PublicCombo }[];
}
export class ComboApiError extends Error {
  constructor(public readonly status: number, public readonly code: string) { super('Không thể tải dữ liệu Combo.'); }
}
const endpoint = '/api/v1/recommendations/combos';
const text = (value: unknown): value is string => typeof value === 'string' && !!value.trim();
const count = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
const money = (value: unknown): value is string => typeof value === 'string' && /^\d+(\.\d+)?$/.test(value);
const finite = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);
function validCombo(value: unknown): value is PublicCombo {
  const c = value as PublicCombo | null;
  const p = c?.pricing, m = c?.metrics, b = c?.provenance?.batch;
  return !!c && text(c.comboId) && text(c.name) && c.status === 'PUBLISHED'
    && ['SAME_ORDER_BUNDLE', 'SEQUENTIAL_CROSS_SELL', 'MIXED_SEQUENCE'].includes(c.kind)
    && typeof c.explanation === 'string' && !!p && p.basis === 'PUBLISH_SNAPSHOT' && p.isCheckoutQuote === false
    && text(p.currencyCode) && money(p.referenceOriginalPrice) && money(p.referenceSalePrice)
    && ['NONE', 'PERCENTAGE', 'FIXED_AMOUNT', 'FIXED_PRICE'].includes(p.discountType)
    && (p.discountValue === null || money(p.discountValue)) && (p.maxDiscountAmount === null || money(p.maxDiscountAmount))
    && !!m && count(m.supportAbs) && count(m.sequenceCount) && finite(m.supportRatio) && m.supportRatio >= 0 && m.supportRatio <= 1
    && finite(m.patternWeight) && !!b && text(b.batchId) && text(b.runId) && text(b.snapshotId) && text(b.algorithm)
    && b.rankingPolicy === 'SUPPORT_THEN_PATTERN_WEIGHT_V1' && text(b.patternWeightPolicy)
    && text(c.provenance.primaryPatternId) && text(c.provenance.canonicalPattern)
    && Array.isArray(c.items) && c.items.length > 0 && c.items.every(i => i && count(i.itemsetOrder) && i.itemsetOrder > 0
      && count(i.itemOrder) && i.itemOrder > 0 && count(i.quantity) && i.quantity > 0 && money(i.unitPriceSnapshot)
      && typeof i.anchorEligible === 'boolean' && typeof i.discountEligible === 'boolean'
      && i.product && text(i.product.productId) && text(i.product.name) && money(i.product.currentPrice)
      && (i.product.sku === null || text(i.product.sku))
      && (i.product.categoryId === null || text(i.product.categoryId))
      && (i.product.imageUrl === null || (typeof i.product.imageUrl === 'string' && /^\/uploads\/products\/[^/?#\\]+$/.test(i.product.imageUrl))));
}
async function read(path: string, signal: AbortSignal): Promise<unknown> {
  const response = await fetch(path, { headers: { Accept: 'application/json' }, credentials: 'same-origin', redirect: 'error', signal });
  const value = await response.json().catch(() => null);
  if (!response.ok) throw new ComboApiError(response.status,
    isApiErrorResponse(value, response.status) ? value.code : 'HTTP_ERROR');
  return unwrapApiResponse<unknown>(value, response.headers.get('X-Request-Id'));
}
export async function listPublicCombos(productId: string, signal: AbortSignal): Promise<PublicTopK> {
  const value = await read(endpoint + '?' + new URLSearchParams({ productId, k: '4' }), signal) as PublicTopK;
  if (!value || value.productId !== productId || value.requestedK !== 4 || !count(value.effectiveK) || value.effectiveK > 4
    || !Array.isArray(value.items) || value.items.length > value.effectiveK
    || !value.items.every((item, i) => item && item.rank === i + 1 && validCombo(item.combo))) {
    throw new ComboApiError(200, 'INVALID_RESPONSE');
  }
  return value;
}
export async function readPublicCombo(comboId: string, signal: AbortSignal): Promise<PublicCombo> {
  const value = await read(endpoint + '/' + encodeURIComponent(comboId), signal);
  if (!validCombo(value) || value.comboId !== comboId) throw new ComboApiError(200, 'INVALID_RESPONSE');
  return value;
}

/** Format an exact decimal string without converting money to floating point or rounding. */
export function displayMoney(value: string, currency: string): string {
  const [whole, fraction] = value.split('.');
  const decimals = fraction?.replace(/0+$/, '');
  return whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.') + (decimals ? ',' + decimals : '') + ' ' + currency;
}
