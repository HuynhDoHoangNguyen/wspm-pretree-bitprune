// Combo Admin HTTP domain boundary; shared transport owns authentication/envelopes.
export type Decision = 'APPROVE' | 'REJECT';
export interface ComboFilters { batchId?: string; status?: string; comboKind?: string }
export interface Audit { reviewedBy: string | null; reviewerDisplayName: string | null; reviewedAt: string | null; reviewNote: string | null }
export interface Metrics { supportAbs: string; supportRatio: number | null; patternWeight: number | null }
export interface ComboRow {
  comboId: string; name: string; comboKind: string; comboStatus: string; batchId: string;
  batchStatus: string; runId: string; runStatus: string; createdAt: string; review: Audit;
  metrics: Metrics; explanation: string; currencyCode: string; referenceOriginalPrice: string;
  referenceSalePrice: string; rankingPolicy: string; patternWeightPolicy: string; topK: string;
}
export interface Assessment { primaryPatternId: string | null; sourceCount: string; allowedActions?: Decision[];
  blockingIssues: { code: string }[]; warnings: { code: string }[] }
export interface ComboSummary { data: ComboRow; assessment: Assessment }
export interface ComboPage { page: number; size: number; scopeTotal: string; filteredTotal: string;
  appliedFilters: ComboFilters; items: ComboSummary[] }
export interface ComboDetail extends ComboSummary {
  itemsets: { itemsetOrder: number; items: { itemsetOrder: number; itemOrder: number; productId: string;
    quantity: string; unitPriceSnapshot: string; anchorEligible: boolean; discountEligible: boolean;
    nameSnapshot: string | null; skuSnapshot: string | null }[] }[];
  sources: { patternId: string; primary: boolean; sourceRunId: string; canonicalPattern: string;
    metrics: Metrics; weightedSupport: number | null; patternWeightPolicy: string }[];
  // Future verified adapter extension. Not supplied by the internal IMP-11 reader today.
  reviewToken?: string;
  notePolicy?: { confirmed: boolean; rejectRequired: boolean; maxCodePoints: number };
}
export interface ReviewCommand { decision: Decision; reviewNote: string | null; expectedReviewToken: string }
export interface ReviewResult { outcome: string; currentStatus: string | null; storedAudit: Audit | null; issues: string[] }
export interface ComboClient {
  publish?(batchId: string, options?: { signal?: AbortSignal }): Promise<{ batchId: string; status: 'ACTIVE'; publishedCount: number }>;
  batches?(page: number, size: number, options?: { signal?: AbortSignal }): Promise<BatchPage>;
  readonly reviewReady: boolean;
  readonly notePolicy?: { confirmed: boolean; rejectRequired: boolean; maxCodePoints: number };
  list(filters: ComboFilters, page: number, size: number, options?: { signal?: AbortSignal }): Promise<ComboPage>;
  detail(comboId: string, options?: { signal?: AbortSignal }): Promise<ComboDetail>;
  review(comboId: string, command: ReviewCommand, options?: { signal?: AbortSignal }): Promise<ReviewResult>;
}
export interface BatchSummary { topK?: string; batchId: string; runId: string; status: string; createdAt: string; comboCount: string; draftCount: string; approvedCount: string; rejectedCount: string }
export interface BatchPage { page: number; size: number; total: string; items: BatchSummary[] }
export class ComboClientError extends Error {
  constructor(public readonly status: number, public readonly code: string, public readonly requestId?: string | null,
    public readonly details?: unknown, public readonly path?: string | null) {
    super(code); Object.setPrototypeOf(this, ComboClientError.prototype);
  }
}
const unavailable = async (): Promise<never> => { throw new ComboClientError(0, 'API_NOT_AVAILABLE'); };
export const unavailableComboClient: ComboClient = { reviewReady: false, list: unavailable, detail: unavailable, review: unavailable };
