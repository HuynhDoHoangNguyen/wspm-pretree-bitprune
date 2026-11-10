import { wdmApi, WdmApiError } from '../../api/admin/data-mining';
import { BatchPage, ComboClient, ComboClientError, ComboDetail, ComboPage, ReviewResult } from './ComboClient';
import { z } from 'zod';

const text = z.string(), nullable = text.nullable();
const count = text.regex(/^(0|[1-9]\d*)$/), money = text.regex(/^\d+\.\d{2}$/);
const finite = z.number().refine(Number.isFinite).nullable();
const audit = z.object({ reviewedBy: nullable, reviewerDisplayName: nullable, reviewedAt: nullable, reviewNote: nullable });
const metrics = z.object({ supportAbs: count.nullable(), supportRatio: finite, patternWeight: finite });
const issues = z.array(z.object({ code: text }));
const row = z.object({ comboId: text, name: text, comboKind: text, comboStatus: text, batchId: text, createdAt: text,
  batchStatus: nullable, runId: nullable, runStatus: nullable, explanation: nullable, currencyCode: nullable, rankingPolicy: nullable, patternWeightPolicy: nullable,
  topK: count, review: audit, metrics, referenceOriginalPrice: money, referenceSalePrice: money });
const summary = z.object({ data: row,
  assessment: z.object({ primaryPatternId: count.nullable(), sourceCount: count, allowedActions: z.array(z.enum(['APPROVE', 'REJECT'])), blockingIssues: issues, warnings: issues }) });
const position = z.number().int().min(1).max(4294967295);
const detail = summary.extend({ reviewToken: nullable, notePolicy: z.object({ confirmed: z.boolean(), rejectRequired: z.literal(true), maxCodePoints: z.literal(1000) }),
  itemsets: z.array(z.object({ itemsetOrder: position, items: z.array(z.object({ itemsetOrder: position, itemOrder: position, productId: text, quantity: count,
    unitPriceSnapshot: money, anchorEligible: z.boolean(), discountEligible: z.boolean(), nameSnapshot: nullable, skuSnapshot: nullable })) })),
  sources: z.array(z.object({ patternId: count, primary: z.boolean(), sourceRunId: nullable, canonicalPattern: nullable, metrics, weightedSupport: finite, patternWeightPolicy: nullable })) });
const pageSchema = z.object({ page: z.number().int(), size: z.number().int(), scopeTotal: count, filteredTotal: count,
  appliedFilters: z.object({ batchId: nullable.optional(), status: nullable.optional(), comboKind: nullable.optional() }), items: z.array(summary) });
const reviewSchema = z.object({ outcome: z.enum(['APPLIED', 'ALREADY_DECIDED', 'UNKNOWN']), currentStatus: nullable, storedAudit: audit.nullable(), issues: z.array(text) });
const batchPageSchema = z.object({ page: z.number().int().min(0), size: z.number().int().min(1).max(100), total: count,
  items: z.array(z.object({ batchId: text, runId: text, status: text, createdAt: text, comboCount: count, draftCount: count, approvedCount: count, rejectedCount: count })) });
export function createComboHttpClient(transport = wdmApi): ComboClient {
  async function call<T>(operation: () => Promise<unknown>, valid: (v: unknown) => boolean): Promise<T> {
    try {
      const v = await operation(); if (!valid(v)) throw new ComboClientError(0, 'INVALID_RESPONSE'); return v as T;
    } catch (e) {
      if (e instanceof WdmApiError) throw new ComboClientError(e.status ?? 0, e.code, e.requestId, e.details, e.path);
      throw e;
    }
  }
  const path = (id: string) => '/v1/admin/combos/' + encodeURIComponent(id);
  return {
    publish: (id, options) => call(() => transport.post('/v1/admin/combos/batches/' + encodeURIComponent(id) + '/publication', undefined, options),
      v => { const parsed = z.object({ batchId: text, status: z.literal('ACTIVE'), publishedCount: z.number().int().nonnegative() }).safeParse(v);
        return parsed.success && parsed.data.batchId === id; }),
    reviewReady: true, // Transport capability only. Every action also needs detail policy/token/allowedActions.
    batches: (page, size, options) => call<BatchPage>(() => transport.get('/v1/admin/combos/batches', { ...options, query: { page, size } }),
      v => { const parsed = batchPageSchema.safeParse(v); return parsed.success && parsed.data.page === page && parsed.data.size === size && parsed.data.items.length <= size; }),
    list: (filters, page, size, options) => call<ComboPage>(() => transport.get(filters.batchId ? '/v1/admin/combos/batches/' + encodeURIComponent(filters.batchId) + '/combos' : '/v1/admin/combos',
      { ...options, query: filters.batchId ? { status: filters.status, comboKind: filters.comboKind, page, size } : { ...filters, page, size } }),
    v => { const parsed = pageSchema.safeParse(v); return parsed.success && parsed.data.page === page && parsed.data.size === size
        && (['batchId', 'status', 'comboKind'] as const).every(k => (parsed.data.appliedFilters[k] ?? undefined) === filters[k]); }),
    detail: (id, options) => call<ComboDetail>(() => transport.get(path(id), options), v => { const parsed = detail.safeParse(v); return parsed.success && parsed.data.data.comboId === id
      && parsed.data.itemsets.every(s => s.items.every(i => i.itemsetOrder === s.itemsetOrder)); }),
    review: (id, command, options) => call<ReviewResult>(() => transport.post(path(id) + '/review', command, options), v => reviewSchema.safeParse(v).success),
  };
}
export const comboHttpClient = createComboHttpClient();
