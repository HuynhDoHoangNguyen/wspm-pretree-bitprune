import { isApiErrorResponse, unwrapApiResponse } from 'api/common/response';
import { ComboBatchProvenance, PublicCombo, PublicTopK, PersonalCombos } from '../../models/recommendation/combos';

export class ComboApiError extends Error {
  constructor(public readonly status: number, public readonly code: string) {
    super('Không thể tải dữ liệu Combo.');
  }
}

const endpoint = '/api/v1/recommendations/combos';
const record = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const text = (v: unknown): v is string => typeof v === 'string' && !!v.trim();
const nullableText = (v: unknown) => v === null || typeof v === 'string';
const count = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const positive = (v: unknown) => count(v) && v > 0;
const finite = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v);
const money = (v: unknown) => v === null || (typeof v === 'string' && /^\d+(\.\d+)?$/.test(v));
const timestamp = (v: unknown) => text(v) && Number.isFinite(Date.parse(v));
const nullableTime = (v: unknown) => v === null || timestamp(v);

function validBatch(v: unknown): v is ComboBatchProvenance {
  return record(v) && text(v.batchId) && text(v.runId) && text(v.snapshotId) && text(v.algorithm)
    && v.rankingPolicy === 'SUPPORT_THEN_PATTERN_WEIGHT_V1' && text(v.patternWeightPolicy);
}

function validCombo(v: unknown): v is PublicCombo {
  if (!record(v) || !record(v.metrics) || !record(v.pricing) || !record(v.provenance)) return false;
  const m = v.metrics, p = v.pricing, provenance = v.provenance;
  const positions = Array.isArray(v.items) ? v.items.map(i => record(i) ? i.itemsetOrder : null) : [];
  const steps = new Set(positions);
  const shape = v.kind === 'SAME_ORDER_BUNDLE' ? steps.size === 1
    : v.kind === 'SEQUENTIAL_CROSS_SELL' ? steps.size > 1 && steps.size === positions.length
      : v.kind === 'MIXED_SEQUENCE' && steps.size > 1 && steps.size < positions.length;
  return shape && text(v.comboId) && text(v.name) && ['SAME_ORDER_BUNDLE', 'SEQUENTIAL_CROSS_SELL', 'MIXED_SEQUENCE'].includes(String(v.kind)) && v.status === 'PUBLISHED'
    && nullableText(v.explanation) && nullableTime(v.validFrom) && nullableTime(v.validTo)
    && nullableTime(v.publishedAt) && timestamp(v.evaluatedAt)
    && validBatch(provenance.batch) && text(provenance.primaryPatternId) && text(provenance.canonicalPattern)
    && count(m.supportAbs) && count(m.sequenceCount) && finite(m.supportRatio)
    && m.supportRatio >= 0 && m.supportRatio <= 1 && finite(m.patternWeight)
    && p.basis === 'PUBLISH_SNAPSHOT' && p.isCheckoutQuote === false && text(p.currencyCode)
    && money(p.referenceOriginalPrice) && money(p.referenceSalePrice)
    && ['NONE', 'PERCENTAGE', 'FIXED_AMOUNT', 'FIXED_PRICE'].includes(String(p.discountType))
    && money(p.discountValue) && money(p.maxDiscountAmount)
    && Array.isArray(v.items) && v.items.length > 0 && v.items.every(i => record(i)
      && positive(i.itemsetOrder) && positive(i.itemOrder) && positive(i.quantity)
      && typeof i.anchorEligible === 'boolean' && typeof i.discountEligible === 'boolean'
      && money(i.unitPriceSnapshot) && record(i.product) && text(i.product.productId) && text(i.product.name)
      && nullableText(i.product.sku) && nullableText(i.product.categoryId) && money(i.product.currentPrice)
      && (i.product.availableQuantity === null || (typeof i.product.availableQuantity === 'string' && /^(0|[1-9][0-9]*)$/.test(i.product.availableQuantity)))
      && (i.product.imageUrl === null || (typeof i.product.imageUrl === 'string'
        && /^\/uploads\/products\/[^/?#\\]+$/.test(i.product.imageUrl))));
}

export function isComboIdentifier(value: string): boolean {
  return !!value.trim() && value.length <= 40 && !Array.from(value).some(char => {
    const code = char.charCodeAt(0);
    return code <= 31 || (code >= 127 && code <= 159);
  });
}

async function read(path: string, signal: AbortSignal, token?: string): Promise<unknown> {
  const response = await fetch(path, {
    headers: { Accept: 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, cache: 'no-store', credentials: 'same-origin', redirect: 'error', signal,
  });
  const value = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ComboApiError(response.status, isApiErrorResponse(value, response.status) ? value.code : 'HTTP_ERROR');
  }
  try {
    return unwrapApiResponse<unknown>(value, response.headers.get('X-Request-Id'));
  } catch {
    throw new ComboApiError(response.status, 'INVALID_RESPONSE');
  }
}

export async function listPublicCombos(productId: string, signal: AbortSignal): Promise<PublicTopK> {
  if (!isComboIdentifier(productId)) throw new ComboApiError(400, 'INVALID_IDENTIFIER');
  const value = await read(endpoint + '?' + new URLSearchParams({ productId }), signal);
  if (!record(value) || value.productId !== productId || !count(value.requestedK) || value.requestedK < 1 || value.requestedK > 20
    || !count(value.effectiveK) || value.effectiveK > value.requestedK || !timestamp(value.evaluatedAt)
    || !(value.provenance === null || validBatch(value.provenance))
    || !Array.isArray(value.items) || value.items.length > value.effectiveK
    || (value.items.length > 0 && value.provenance === null)
    || !value.items.every((item, i) => record(item) && item.rank === i + 1 && validCombo(item.combo) && item.combo.kind === 'SAME_ORDER_BUNDLE')) {
    throw new ComboApiError(200, 'INVALID_RESPONSE');
  }
  return value as unknown as PublicTopK;
}

export async function readPublicCombo(comboId: string, signal: AbortSignal): Promise<PublicCombo> {
  if (!isComboIdentifier(comboId)) throw new ComboApiError(400, 'INVALID_IDENTIFIER');
  const value = await read(endpoint + '/' + encodeURIComponent(comboId), signal);
  if (!validCombo(value) || value.kind !== 'SAME_ORDER_BUNDLE' || value.comboId !== comboId) throw new ComboApiError(200, 'INVALID_RESPONSE');
  return value;
}

/** Exact display of a decimal string; never round money through JavaScript Number. */
export function displayMoney(value: string | null, currency: string): string {
  if (value === null) return 'Chưa có giá';
  const [whole, fraction] = value.split('.');
  const decimals = fraction?.replace(/0+$/, '');
  return whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.') + (decimals ? ',' + decimals : '') + ' ' + currency;
}

export async function latestPublicCombos(signal: AbortSignal): Promise<PublicCombo[]> {
  const value = await read(endpoint + '/latest', signal);
  if (!Array.isArray(value) || value.length > 5 || !value.every(v => validCombo(v) && v.kind === 'SAME_ORDER_BUNDLE')) throw new ComboApiError(200, 'INVALID_RESPONSE');
  return value;
}
export async function personalCombos(token: string, signal: AbortSignal, productId?: string): Promise<PersonalCombos> {
  if (productId !== undefined && !isComboIdentifier(productId)) throw new ComboApiError(400, 'INVALID_IDENTIFIER');
  return readPersonalCombos(token, signal, productId ? new URLSearchParams({ productId }) : new URLSearchParams());
}

export async function personalCombo(token: string, comboId: string, signal: AbortSignal): Promise<PersonalCombos> {
  if (!isComboIdentifier(comboId)) throw new ComboApiError(400, 'INVALID_IDENTIFIER');
  const result = await readPersonalCombos(token, signal, new URLSearchParams({ comboId }));
  if (result.items.length !== 1 || result.items[0].combo.comboId !== comboId) throw new ComboApiError(200, 'INVALID_RESPONSE');
  return result;
}

async function readPersonalCombos(token: string, signal: AbortSignal, params: URLSearchParams): Promise<PersonalCombos> {
  const productId = params.get('productId') ?? undefined;
  if (productId !== undefined && !isComboIdentifier(productId)) throw new ComboApiError(400, 'INVALID_IDENTIFIER');
  const value = await read(endpoint + '/personalized' + (params.toString() ? '?' + params : ''), signal, token);
  if (!record(value) || value.policy !== 'HISTORY_PREFIX_V1' || !timestamp(value.evaluatedAt)
    || !Array.isArray(value.items) || value.items.length > 20 || !value.items.every((r, index) => record(r)
      && r.rank === index + 1 && positive(r.matchedItemsets) && positive(r.nextItemsetOrder)
      && validCombo(r.combo) && r.combo.kind !== 'SAME_ORDER_BUNDLE' && Array.isArray(r.suggestedProductIds) && r.suggestedProductIds.length > 0
      && r.suggestedProductIds.every(id => text(id) && (r.combo as PublicCombo).items.some(i =>
        i.product.productId === id && i.itemsetOrder === r.nextItemsetOrder)))) throw new ComboApiError(200, 'INVALID_RESPONSE');
  return value as unknown as PersonalCombos;
}
