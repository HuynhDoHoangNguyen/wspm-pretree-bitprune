import { createWdmApiClient, WdmApiError } from './client';
import { isRunId } from './mining-run-response';

export interface RunResultMetadata {
  runId: string; executionStatus: string; publicationStatus: 'NOT_PUBLISHED' | 'COMMITTED' | 'UNVERIFIED';
  manifestVersion: string | null; resultSha256: string | null; committedAt: string | null;
  provenance: { version: 'RUN_PROVENANCE_V1'; conversionFormatVersion: string; snapshotId: string; attemptId: string;
    validationSha256: string; sequenceSha256: string; mappingSha256: string; sequenceCount: string } | null;
  storedPatternCount: string; storedPatternItemCount: string; storedRunItemCount: string;
  completeness: 'UNVERIFIED' | 'COMPLETE_VERIFIED'; stagingReadable: boolean; publishedReadable: boolean; eligibleForCombo: boolean;
}
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const hash = (v: unknown): v is string => typeof v === 'string' && /^[0-9a-f]{64}$/.test(v);
const count = (v: unknown): v is string => typeof v === 'string' && /^(0|[1-9][0-9]{0,19})$/.test(v)
  && (v.length < 20 || v <= '18446744073709551615');
function invalid(): never { throw new WdmApiError('response', 200, 'INVALID_RUN_RESULT_RESPONSE', 'Result metadata could not be verified.'); }

/** Select public fields rather than retaining unknown/private server properties. Current 06.7 scope only. */
export function readRunResultMetadata(value: unknown, runId: string): RunResultMetadata {
  if (!object(value) || value.runId !== runId || !isRunId(runId)
    || !['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'LEGACY_UNVERIFIED'].includes(String(value.executionStatus))
    || !['NOT_PUBLISHED', 'COMMITTED', 'UNVERIFIED'].includes(String(value.publicationStatus))
    || !count(value.storedPatternCount) || !count(value.storedPatternItemCount) || !count(value.storedRunItemCount)
    || !['UNVERIFIED', 'COMPLETE_VERIFIED'].includes(String(value.completeness))
    || value.publishedReadable !== (value.completeness === 'COMPLETE_VERIFIED')
    || value.eligibleForCombo !== (value.publishedReadable === true && value.storedPatternCount !== '0')
    || (value.publishedReadable === true && (value.publicationStatus !== 'COMMITTED' || value.executionStatus !== 'SUCCEEDED'
      || value.manifestVersion !== 'WDM_RESULT_MANIFEST_V1'))
    || value.stagingReadable !== (value.publicationStatus === 'NOT_PUBLISHED' && value.executionStatus === 'SUCCEEDED')) return invalid();
  let provenance: RunResultMetadata['provenance'] = null;
  const p = value.provenance;
  if (value.publicationStatus === 'UNVERIFIED') { if (p !== null) return invalid(); }
  else {
    if (!object(p) || p.version !== 'RUN_PROVENANCE_V1' || typeof p.conversionFormatVersion !== 'string' || !p.conversionFormatVersion
      || !isRunId(p.snapshotId) || !isRunId(p.attemptId) || !hash(p.validationSha256) || !hash(p.sequenceSha256)
      || !hash(p.mappingSha256) || !count(p.sequenceCount) || p.sequenceCount === '0') return invalid();
    provenance = { version: p.version, conversionFormatVersion: p.conversionFormatVersion, snapshotId: p.snapshotId, attemptId: p.attemptId,
      validationSha256: p.validationSha256, sequenceSha256: p.sequenceSha256, mappingSha256: p.mappingSha256, sequenceCount: p.sequenceCount };
  }
  if (value.publicationStatus === 'COMMITTED') {
    if (typeof value.manifestVersion !== 'string' || !value.manifestVersion.trim() || !hash(value.resultSha256)
      || typeof value.committedAt !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,6})?Z$/.test(value.committedAt)
      || !Number.isFinite(Date.parse(value.committedAt))) return invalid();
  } else if (value.manifestVersion !== null || value.resultSha256 !== null || value.committedAt !== null) return invalid();
  return { runId, executionStatus: value.executionStatus as string, publicationStatus: value.publicationStatus as RunResultMetadata['publicationStatus'],
    manifestVersion: value.manifestVersion as string | null, resultSha256: value.resultSha256 as string | null, committedAt: value.committedAt as string | null,
    provenance, storedPatternCount: value.storedPatternCount, storedPatternItemCount: value.storedPatternItemCount,
    storedRunItemCount: value.storedRunItemCount, completeness: value.completeness as RunResultMetadata['completeness'], stagingReadable: value.stagingReadable as boolean,
    publishedReadable: value.publishedReadable as boolean, eligibleForCombo: value.eligibleForCombo as boolean };
}
export function createRunResultApi(client: ReturnType<typeof createWdmApiClient>) {
  return { detail(runId: string, patternId: string, signal?: AbortSignal, view: ResultView = 'staging') {
    if (!isRunId(runId) || !isPatternId(patternId))
      throw new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Supply supported run and pattern IDs.');
    return client.get<PatternDetail>('/v1/admin/mining-runs/' + runId + '/patterns/' + patternId,
      { query: { view }, signal, expectedStatus: 200, decodeResponse: value => checkedView(readPatternDetail(value, runId, patternId), view) });
  }, patterns(runId: string, page = 1, signal?: AbortSignal, view: ResultView = 'staging') {
    if (!isRunId(runId) || !Number.isInteger(page) || page < 1 || page > 100000)
      throw new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Supply a canonical run UUID and supported page.');
    return client.get<PatternPage>('/v1/admin/mining-runs/' + runId + '/patterns',
      { query: { view, page, size: 20 }, signal, expectedStatus: 200,
        decodeResponse: value => checkedView(readPatternPage(value, runId, page), view) });
  }, async export(runId: string, view: ResultView, signal?: AbortSignal) {
    if (!isRunId(runId)) return invalid();
    const file = await client.download('/v1/admin/mining-runs/' + runId + '/patterns/export',
      { query: { view }, signal, expectedStatus: 200, maxDownloadBytes: 64 * 1024 * 1024 });
    if (!file.contentType || !/^application\/json(?:;|$)/i.test(file.contentType)) return invalid();
    try {
      const content = JSON.parse(await file.blob.text());
      const metadata = readRunResultMetadata(content.result, runId);
      if (content.formatVersion !== (view === 'published' ? 'WDM_RESULT_EXPORT_V2' : 'WDM_RESULT_EXPORT_V1')
      || content.purpose !== (view === 'published' ? 'COMBO_HANDOFF' : 'STAGING_INSPECTION')
      || !(view === 'published' ? metadata.publishedReadable : metadata.stagingReadable)
      || !Array.isArray(content.patterns) || String(content.patterns.length) !== metadata.storedPatternCount
      || (view === 'published' && (!Array.isArray(content.runItems) || String(content.runItems.length) !== metadata.storedRunItemCount))) return invalid();
      const ids = new Set<string>(); let temporal = 0;
      const snapshots = new Map<number, string>();
      for (const value of content.patterns) {
        if (!object(value) || !object(value.pattern) || !isPatternId(value.pattern.patternId) || ids.has(value.pattern.patternId)) return invalid();
        ids.add(value.pattern.patternId);
        const detail = readPatternDetail({ ...value, result: metadata }, runId, value.pattern.patternId);
        temporal += detail.pattern.patternItemCount;
        if (temporal > 100000) return invalid();
        detail.itemsets.forEach(set => set.items.forEach(item => {
          const snapshot = itemSnapshot(item);
          if (snapshots.has(item.itemId) && snapshots.get(item.itemId) !== snapshot) return invalid();
          snapshots.set(item.itemId, snapshot);
        }));
      }
      if (String(temporal) !== metadata.storedPatternItemCount || ids.size > 10000) return invalid();
      if (view === 'published') {
        if (content.runItems.length > 10000 || !metadata.provenance) return invalid();
        const itemIds = new Set<number>(), products = new Set<string>();
        for (const item of content.runItems) {
          if (!object(item) || !positiveInt(item.itemId) || itemIds.has(item.itemId) || typeof item.productId !== 'string' || !item.productId.trim()
          || products.has(item.productId) || typeof item.productNameSnapshot !== 'string' || typeof item.skuSnapshot !== 'string'
          || !unitNumber(item.itemWeight) || item.itemWeight === 0 || !isPatternId(item.occurrenceCount) || !isPatternId(item.sequenceSupport)
          || decimalCompare(item.sequenceSupport, metadata.provenance.sequenceCount) > 0 || decimalCompare(item.occurrenceCount, item.sequenceSupport) < 0) return invalid();
          itemIds.add(item.itemId); products.add(item.productId);
          const typed = item as unknown as PatternItem;
          if (snapshots.has(item.itemId) && snapshots.get(item.itemId) !== itemSnapshot(typed)) return invalid();
        }
        if (Array.from(snapshots.keys()).some(id => !itemIds.has(id))) return invalid();
      }
      return { blob: file.blob, filename: 'mining-result-' + runId + '.json' };
    } catch {
      throw new WdmApiError('response', 200, 'INVALID_RUN_RESULT_EXPORT', 'Result export could not be verified.', file.requestId);
    }
  }, metadata(runId: string, signal?: AbortSignal) {
    if (!isRunId(runId)) throw new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Supply a canonical run UUID.');
    return client.get<RunResultMetadata>('/v1/admin/mining-runs/' + runId + '/results',
      { signal, expectedStatus: 200, decodeResponse: value => readRunResultMetadata(value, runId) });
  } };
}

export interface PatternSummary {
  patternId: string; runId: string; canonicalPattern: string; canonicalPatternHash: string;
  supportAbs: string; supportRatio: number; weightedSupport: number; patternWeight: number;
  patternWeightPolicy: string; itemsetCount: number; patternItemCount: number;
}
export interface PatternPage { result: RunResultMetadata; items: PatternSummary[]; page: number; size: number; total: string }
export interface PatternItem {
  itemOrder: number; itemId: number; productId: string; productNameSnapshot: string; skuSnapshot: string;
  itemWeight: number; occurrenceCount: string; sequenceSupport: string;
}
export interface PatternItemset { itemsetOrder: number; items: PatternItem[] }
export interface PatternDetail { result: RunResultMetadata; pattern: PatternSummary; itemsets: PatternItemset[] }
export const isPatternId = (v: unknown): v is string => count(v) && v !== '0';
const positiveInt = (v: unknown): v is number => typeof v === 'number' && Number.isInteger(v) && v > 0 && v <= 2147483647;
const unitNumber = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v) && v >= 0 && v <= 1;
const decimalCompare = (a: string, b: string) => a.length === b.length ? a.localeCompare(b) : a.length - b.length;

function readSummary(p: unknown, runId: string, sequenceCount: string): PatternSummary {
  if (!object(p) || !isPatternId(p.patternId) || p.runId !== runId
    || typeof p.canonicalPattern !== 'string' || !p.canonicalPattern.length || !hash(p.canonicalPatternHash)
    || !isPatternId(p.supportAbs) || decimalCompare(p.supportAbs, sequenceCount) > 0
    || !unitNumber(p.supportRatio) || p.supportRatio === 0
    || typeof p.weightedSupport !== 'number' || !Number.isFinite(p.weightedSupport) || p.weightedSupport <= 0
    || !unitNumber(p.patternWeight) || p.patternWeight === 0
    || p.patternWeightPolicy !== 'EQ2_EQ3_ARITHMETIC_MEAN_V1'
    || !positiveInt(p.itemsetCount) || !positiveInt(p.patternItemCount) || p.itemsetCount > p.patternItemCount) return invalid();
  return { patternId: p.patternId, runId, canonicalPattern: p.canonicalPattern, canonicalPatternHash: p.canonicalPatternHash,
    supportAbs: p.supportAbs, supportRatio: p.supportRatio, weightedSupport: p.weightedSupport, patternWeight: p.patternWeight,
    patternWeightPolicy: p.patternWeightPolicy, itemsetCount: p.itemsetCount, patternItemCount: p.patternItemCount };
}

/** DTO consistency only, not mining/oracle verification or publication certification. */
export function readPatternDetail(value: unknown, runId: string, patternId: string): PatternDetail {
  if (!object(value) || !isPatternId(patternId) || !Array.isArray(value.itemsets)) return invalid();
  const result = readRunResultMetadata(value.result, runId);
  if ((!result.stagingReadable && !result.publishedReadable) || !result.provenance || result.storedPatternCount === '0') return invalid();
  const sequences = result.provenance.sequenceCount;
  const pattern = readSummary(value.pattern, runId, sequences);
  if (pattern.patternId !== patternId || value.itemsets.length !== pattern.itemsetCount || pattern.patternItemCount > 100000) return invalid();
  let total = 0;
  const snapshots = new Map<number, string>();
  const canonical: string[] = ['<'];
  const itemsets = value.itemsets.map((set: unknown, index: number): PatternItemset => {
    if (!object(set) || set.itemsetOrder !== index + 1 || !Array.isArray(set.items) || !set.items.length) return invalid();
    let previous = 0;
    const items = set.items.map((item: unknown, order: number): PatternItem => {
      if (++total > 100000 || !object(item) || item.itemOrder !== order + 1 || !positiveInt(item.itemId) || item.itemId <= previous
        || typeof item.productId !== 'string' || !item.productId.trim() || typeof item.productNameSnapshot !== 'string'
        || typeof item.skuSnapshot !== 'string' || !unitNumber(item.itemWeight) || item.itemWeight === 0
        || !isPatternId(item.sequenceSupport) || decimalCompare(item.sequenceSupport, sequences) > 0
        || !isPatternId(item.occurrenceCount) || decimalCompare(item.occurrenceCount, item.sequenceSupport) < 0) return invalid();
      previous = item.itemId;
      const selected: PatternItem = { itemOrder: item.itemOrder as number, itemId: item.itemId, productId: item.productId,
        productNameSnapshot: item.productNameSnapshot, skuSnapshot: item.skuSnapshot, itemWeight: item.itemWeight,
        occurrenceCount: item.occurrenceCount, sequenceSupport: item.sequenceSupport };
      const snapshot = JSON.stringify({ ...selected, itemOrder: 0 });
      if (snapshots.has(item.itemId) && snapshots.get(item.itemId) !== snapshot) return invalid();
      snapshots.set(item.itemId, snapshot);
      return selected;
    });
    canonical.push('(' + items.map(item => item.itemId).join(' ') + ')');
    return { itemsetOrder: index + 1, items };
  });
  canonical.push('>');
  if (total !== pattern.patternItemCount || canonical.join('') !== pattern.canonicalPattern) return invalid();
  return { result, pattern, itemsets };
}

/** Structural boundary only: never reconstruct mining formulas or certify temporal rows here. */
export function readPatternPage(value: unknown, runId: string, page: number): PatternPage {
  if (!object(value) || value.page !== page || value.size !== 20 || !count(value.total)
    || !Array.isArray(value.items) || value.items.length > 20) return invalid();
  const result = readRunResultMetadata(value.result, runId);
  if ((!result.stagingReadable && !result.publishedReadable) || value.total !== result.storedPatternCount || !result.provenance) return invalid();
  const sequenceCount = result.provenance.sequenceCount;
  const ids = new Set<string>();
  const items: PatternSummary[] = value.items.map((p: unknown) => {
    const summary = readSummary(p, runId, sequenceCount);
    if (ids.has(summary.patternId)) return invalid();
    ids.add(summary.patternId);
    return summary;
  });
  // Arithmetic is bounded by requested page, never by a potentially UINT64 total.
  const start = (page - 1) * 20;
  if (decimalCompare(value.total, String(start)) <= 0 ? items.length !== 0
    : items.length !== (decimalCompare(value.total, String(start + 20)) >= 0 ? 20 : Number(value.total) - start)) return invalid();
  return { result, items, page, size: 20, total: value.total };
}

export type ResultView = 'staging' | 'published';
function itemSnapshot(item: PatternItem): string {
  return JSON.stringify([item.productId, item.productNameSnapshot, item.skuSnapshot, item.itemWeight, item.occurrenceCount, item.sequenceSupport]);
}
function checkedView<T extends { result: RunResultMetadata }>(value: T, view: ResultView): T {
  if (!(view === 'published' ? value.result.publishedReadable : value.result.stagingReadable)) return invalid();
  return value;
}
