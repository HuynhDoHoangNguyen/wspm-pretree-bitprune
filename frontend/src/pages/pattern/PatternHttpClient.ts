import { wdmApi, WdmApiError } from 'api/admin/data-mining';
import { createWdmApiClient } from 'api/admin/data-mining/client';
import { DictionaryPage, PatternClient, PatternClientError, PatternDetail, PatternPage, PatternSummary, RunPage } from './PatternClient';

type Transport = Pick<ReturnType<typeof createWdmApiClient>, 'get'>;
type Value = Record<string, unknown>;
const object = (v: unknown): v is Value => !!v && typeof v === 'object' && !Array.isArray(v);
const count = (v: unknown): v is string => typeof v === 'string' && /^[0-9]+$/.test(v);
const text = (v: unknown): v is string => typeof v === 'string';
const nullableText = (v: unknown) => v === null || text(v);
const integer = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v);
const metric = (v: unknown) => v === null || (typeof v === 'number' && Number.isFinite(v));
const nullableCount = (v: unknown) => v === null || count(v);
const states = ['READY', 'NOT_READY', 'FAILED', 'UNVERIFIED'];
function requireData(valid: unknown): asserts valid {
  if (!valid) throw new PatternClientError(200, 'INVALID_RESPONSE', null, [], 'response');
}
function paged(v: unknown, page: number, size: number): v is Value {
  return object(v) && v.page === page && v.size === size && Array.isArray(v.items);
}
function summary(v: unknown): v is PatternSummary {
  return object(v) && count(v.patternId) && text(v.runId) && text(v.canonicalPattern) && count(v.supportAbs)
    && metric(v.supportRatio) && metric(v.weightedSupport) && metric(v.patternWeight)
    && nullableText(v.patternWeightPolicy) && integer(v.patternItemCount) && integer(v.itemsetCount);
}
function capability(v: unknown) {
  return v === undefined || (object(v) && typeof v.enabled === 'boolean' && nullableText(v.reason) && nullableText(v.policy));
}
function translate(error: unknown): never {
  if (error instanceof PatternClientError) throw error;
  if (error instanceof WdmApiError) {
    const offending = error.code === 'ITEM_NOT_IN_RUN_DICTIONARY' ? error.details
      .filter(d => d.field === 'offendingItemIds' && d.code === error.code && /^[0-9]{1,10}$/.test(d.message)
        && BigInt(d.message) > BigInt(0) && BigInt(d.message) <= BigInt(2147483647))
      .map(d => d.message) : [];
    throw new PatternClientError(error.status ?? 0, error.code, error.requestId, Array.from(new Set(offending)), error.kind, error.details, error.message, error.path);
  }
  throw new PatternClientError(0, 'INVALID_RESPONSE', null, [], 'response');
}

/** Uses the existing shared-auth carrier, parser, denial handling and session/request cancellation. */
export function createPatternHttpClient(transport: Transport = wdmApi): PatternClient {
  const runPath = (runId: string) => '/v1/admin/mining-runs/' + encodeURIComponent(runId);
  const bound = (value?: string) => value === '' ? undefined : value;
  return {
    async runs(page, size, options) {
      try {
        const data = await transport.get<unknown>('/v1/admin/mining-runs', { query: { page, size }, signal: options?.signal, expectedStatus: 200 });
        requireData(paged(data, page, size) && count(data.total));
        const items = (data.items as unknown[]).map(item => {
          requireData(object(item) && text(item.runId) && !!item.runId && nullableText(item.snapshotId)
            && nullableText(item.algorithm) && nullableText(item.status) && nullableText(item.createdAt)
            && nullableCount(item.sequenceCount) && Array.isArray(item.issues) && item.issues.every(text));
          return { runId: item.runId as string, label: [item.algorithm ?? 'Không rõ thuật toán', item.status ?? 'Không rõ trạng thái',
            item.createdAt ?? 'Không rõ thời gian', item.runId].join(' — ') };
        });
        return { page, size, total: data.total as string, items } as RunPage;
      } catch (error) { return translate(error); }
    },
    async list(runId, page, size, filters = {}, options) {
      try {
        const data = await transport.get<unknown>(runPath(runId) + '/patterns', { signal: options?.signal, expectedStatus: 200,
          query: { page, size, itemId: filters.itemIds, supportMin: bound(filters.supportMin), supportMax: bound(filters.supportMax),
            weightMin: bound(filters.weightMin), weightMax: bound(filters.weightMax) } });
        requireData(paged(data, page, size) && data.runId === runId && text(data.runStatus) && states.includes(data.resultState as string)
          && nullableCount(data.runPatternTotal) && nullableCount(data.filteredTotal) && capability(data.weightFilter)
          && (data.items as unknown[]).every(item => summary(item) && item.runId === runId));
        requireData(data.resultState !== 'READY' || (count(data.runPatternTotal) && count(data.filteredTotal)));
        return data as unknown as PatternPage;
      } catch (error) { return translate(error); }
    },
    async dictionary(runId, query, page, size, options) {
      try {
        const data = await transport.get<unknown>(runPath(runId) + '/dictionary', { signal: options?.signal,
          expectedStatus: 200, query: { q: query, page, size } });
        requireData(paged(data, page, size) && data.runId === runId && text(data.query) && states.includes(data.resultState as string)
          && nullableCount(data.dictionaryTotal) && nullableCount(data.matchedTotal)
          && (data.items as unknown[]).every(i => object(i) && integer(i.itemId) && text(i.productId) && text(i.nameSnapshot) && text(i.skuSnapshot)));
        return data as unknown as DictionaryPage;
      } catch (error) { return translate(error); }
    },
    async detail(runId, patternId, options) {
      try {
        const data = await transport.get<unknown>(runPath(runId) + '/patterns/' + encodeURIComponent(patternId), { signal: options?.signal, expectedStatus: 200 });
        requireData(summary(data) && data.runId === runId && data.patternId === patternId);
        const detail = data as unknown as Value;
        requireData(nullableCount(detail.sequenceCount) && ['COMPLETE', 'INCOMPLETE'].includes(detail.integrityStatus as string)
          && Array.isArray(detail.issues) && detail.issues.every(i => object(i) && text(i.code)
            && [i.itemsetOrder, i.itemOrder, i.itemId].every(n => n === null || integer(n)))
          && Array.isArray(detail.itemsets) && detail.itemsets.every(g => object(g) && integer(g.itemsetOrder) && Array.isArray(g.items)
            && g.items.every(i => object(i) && integer(i.itemOrder) && integer(i.itemId) && ['MAPPED', 'MISSING'].includes(i.mappingStatus as string)
              && nullableText(i.productId) && nullableText(i.nameSnapshot) && nullableText(i.skuSnapshot) && metric(i.itemWeight))));
        return data as unknown as PatternDetail;
      } catch (error) { return translate(error); }
    },
  };
}

export const patternHttpClient = createPatternHttpClient();
