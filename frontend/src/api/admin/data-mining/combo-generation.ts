import { WdmApiError } from './client';
import { wdmApi as client } from './index';
export interface GenerationPreview { status: string; issues: string[]; examined: number; accepted: number; rejected: number;
  groups: number; eligible: number; topPatternIds: string[]; anchors: { productId: string; name: string }[];
  reasonCounts: Record<string, number>; referencePrices: Record<string, string>; contextToken: string | null;
  policyVersion?: string; proposals?: GenerationProposal[] }
export interface ProductSelection { requiredItems: { itemId: number; quantity: number }[]; minDistinctProducts: number; maxDistinctProducts: number }
export interface CategorySelection { requiredCategories: { categoryId: string; quantity: number }[]; minDistinctProducts: number; maxDistinctProducts: number }
export interface CategoryOptions { runId: string; policyVersion: string; maxDistinctProducts: number; maxQuantity: number; maxBatchItems: number;
  categories: { categoryId: string; name: string; productCount: number }[] }
export interface GenerationOptions { runId: string; policyVersion: string; maxDistinctProducts: number; maxQuantity: number; maxBatchItems: number;
  products: { itemId: number; productId: string; name: string; sku: string }[] }
export interface GenerationProposal { sourcePatternId: string; canonicalPattern: string; comboKind: string; total: string;
  items: { itemsetOrder: number; itemOrder: number; itemId: number; productId: string; name: string; sku: string; quantity: number; required: boolean; unitPrice: string; lineTotal: string }[] }
export interface GenerationOutcome { status: string; issues: string[]; batchId: string | null; storedGroups: number }
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
function invalid(): never { throw new WdmApiError('response', 200, 'INVALID_GENERATION_RESPONSE', 'Generation response could not be verified.'); }
const strings = (v: unknown): v is string[] => Array.isArray(v) && v.every(i => typeof i === 'string');
const money = (v: unknown): v is string => typeof v === 'string' && /^[0-9]{1,17}(\.[0-9]{1,2})?$/.test(v);
const positive = (v: unknown, max: number) => Number.isSafeInteger(v) && Number(v) > 0 && Number(v) <= max;
function options(v: unknown, runId: string): GenerationOptions {
  if (!object(v) || v.runId !== runId || v.policyVersion !== 'TV2_SELECTED_CATALOG_V2'
    || !positive(v.maxDistinctProducts, 4000) || !positive(v.maxQuantity, 4294967295) || !positive(v.maxBatchItems, 4000)
    || !Array.isArray(v.products) || v.products.length > 10000) return invalid();
  const ids = new Set<number>(), products = new Set<string>();
  for (const p of v.products) {
    if (!object(p) || !positive(p.itemId, 2147483647) || typeof p.productId !== 'string' || !p.productId
      || typeof p.name !== 'string' || !p.name || typeof p.sku !== 'string' || !p.sku
      || ids.has(Number(p.itemId)) || products.has(p.productId)) return invalid();
    ids.add(Number(p.itemId)); products.add(p.productId);
  }
  return v as unknown as GenerationOptions;
}
function categoryOptions(v: unknown, runId: string): CategoryOptions {
  if (!object(v) || v.runId !== runId || v.policyVersion !== 'TV2_SELECTED_CATEGORIES_V3'
    || !positive(v.maxDistinctProducts, 4000) || !positive(v.maxQuantity, 4294967295) || !positive(v.maxBatchItems, 4000)
    || !Array.isArray(v.categories) || v.categories.length > 10000) return invalid();
  const ids = new Set<string>();
  for (const c of v.categories) {
    if (!object(c) || typeof c.categoryId !== 'string' || !c.categoryId || ids.has(c.categoryId)
      || typeof c.name !== 'string' || !c.name || !positive(c.productCount, 10000)) return invalid();
    ids.add(c.categoryId);
  }
  return v as unknown as CategoryOptions;
}
function preview(v: unknown): GenerationPreview {
  if (!object(v) || !['READY', 'SELECT_ANCHOR', 'EMPTY', 'BLOCKED'].includes(String(v.status)) || !strings(v.issues)
    || !['examined', 'accepted', 'rejected', 'groups', 'eligible'].every(k => Number.isSafeInteger(v[k]) && Number(v[k]) >= 0 && Number(v[k]) <= 10000)
    || !strings(v.topPatternIds) || v.topPatternIds.length > 20 || v.topPatternIds.some(i => !/^[1-9][0-9]{0,19}$/.test(i))
    || !Array.isArray(v.anchors) || v.anchors.length > 10000 || v.anchors.some(a => !object(a) || typeof a.productId !== 'string' || typeof a.name !== 'string')
    || !object(v.reasonCounts) || Object.values(v.reasonCounts).some(n => !Number.isSafeInteger(n) || Number(n) < 0)
    || !object(v.referencePrices) || Object.values(v.referencePrices).some(n => typeof n !== 'string' || !/^[0-9]+(\.[0-9]{1,2})?$/.test(n))
    || (v.status === 'READY' ? typeof v.contextToken !== 'string' || v.contextToken.length > 524288 : v.contextToken !== null)
    || !['TV2_SELECTED_CATALOG_V2', 'TV2_SELECTED_CATEGORIES_V3'].includes(String(v.policyVersion)) || !Array.isArray(v.proposals) || v.proposals.length > 20) return invalid();
  let rows = 0;
  for (const p of v.proposals) {
    if (!object(p) || typeof p.sourcePatternId !== 'string' || !/^[1-9][0-9]{0,19}$/.test(p.sourcePatternId)
      || typeof p.canonicalPattern !== 'string' || !['SAME_ORDER_BUNDLE', 'SEQUENTIAL_CROSS_SELL', 'MIXED_SEQUENCE'].includes(String(p.comboKind))
      || !money(p.total) || !Array.isArray(p.items) || p.items.length < 2 || (rows += p.items.length) > 4000) return invalid();
    for (const i of p.items) if (!object(i) || !positive(i.itemsetOrder, 4000) || !positive(i.itemOrder, 4000)
      || !positive(i.itemId, 2147483647) || !positive(i.quantity, 4294967295) || typeof i.required !== 'boolean'
      || typeof i.productId !== 'string' || typeof i.name !== 'string' || typeof i.sku !== 'string' || !money(i.unitPrice) || !money(i.lineTotal)) return invalid();
  }
  return v as unknown as GenerationPreview;
}
function outcome(v: unknown): GenerationOutcome {
  if (!object(v) || !['INSERTED', 'NO_OP', 'CONFLICT', 'BLOCKED', 'UNKNOWN', 'ROLLED_BACK', 'INVALID', 'EMPTY', 'SELECT_ANCHOR'].includes(String(v.status))
    || !strings(v.issues) || !Number.isSafeInteger(v.storedGroups) || Number(v.storedGroups) < 0
    || (v.batchId !== null && (typeof v.batchId !== 'string' || v.batchId.length > 40))
    || (['INSERTED', 'NO_OP'].includes(String(v.status)) && typeof v.batchId !== 'string')) return invalid();
  return v as unknown as GenerationOutcome;
}
export const comboGenerationApi = {
  categoryOptions(runId: string, signal?: AbortSignal) {
    return client.get('/v1/admin/combo-generation/category-options/' + encodeURIComponent(runId),
      { signal, expectedStatus: 200, decodeResponse: value => categoryOptions(value, runId) }) as Promise<CategoryOptions>;
  },
  previewCategories(runId: string, anchorProductId: string | null, k: number, requestIdentity: string, signal: AbortSignal | undefined, categorySelection: CategorySelection) {
    return client.post('/v1/admin/combo-generation/preview', { runId, anchorProductId, k, requestIdentity, categorySelection },
      { signal, expectedStatus: 200, decodeResponse: value => {
        const result = preview(value); if (result.policyVersion !== 'TV2_SELECTED_CATEGORIES_V3') return invalid(); return result;
      } }) as Promise<GenerationPreview>;
  },
  options(runId: string, signal?: AbortSignal) {
    return client.get('/v1/admin/combo-generation/options/' + encodeURIComponent(runId),
      { signal, expectedStatus: 200, decodeResponse: value => options(value, runId) }) as Promise<GenerationOptions>;
  },
  preview(runId: string, anchorProductId: string | null, k: number, requestIdentity: string, signal?: AbortSignal, selection?: ProductSelection) {
    return client.post('/v1/admin/combo-generation/preview', { runId, anchorProductId, k, requestIdentity, selection },
      { signal, expectedStatus: 200, decodeResponse: preview }) as Promise<GenerationPreview>;
  },
  submit(contextToken: string, signal?: AbortSignal) { return command('submit', contextToken, signal); },
  reconcile(contextToken: string, signal?: AbortSignal) { return command('reconcile', contextToken, signal); },
};
function command(name: string, contextToken: string, signal?: AbortSignal) {
  return client.post('/v1/admin/combo-generation/' + name, { contextToken },
    { signal, expectedStatus: 200, decodeResponse: outcome }) as Promise<GenerationOutcome>;
}
