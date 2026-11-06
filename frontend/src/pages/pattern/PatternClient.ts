// Exact IMP-02/03 boundary. No runtime fixture or unprotected HTTP fallback.
export interface RunOption { runId: string; label: string }
export interface RunPage { page: number; size: number; total: string; items: RunOption[] }
export interface PatternRequestOptions { signal?: AbortSignal }
export interface PatternSummary {
  patternId: string; runId: string; canonicalPattern: string; supportAbs: string;
  supportRatio: number | null; weightedSupport: number | null; patternWeight: number | null;
  patternWeightPolicy: string | null; patternItemCount: number; itemsetCount: number;
}
export interface PatternPage {
  runId: string; runStatus: string; resultState: 'READY' | 'NOT_READY' | 'FAILED' | 'UNVERIFIED';
  runPatternTotal: string | null; filteredTotal: string | null; page: number; size: number;
  items: PatternSummary[];
  weightFilter?: WeightCapability;
}
export interface WeightCapability { enabled: boolean; reason: string | null; policy: string | null }
export interface PatternFilters {
  itemIds?: string[]; supportMin?: string; supportMax?: string; weightMin?: string; weightMax?: string;
}
export interface DictionaryOption { itemId: number; productId: string; nameSnapshot: string; skuSnapshot: string }
export interface DictionaryPage {
  runId: string; resultState: PatternPage['resultState']; query: string; page: number; size: number;
  dictionaryTotal: string | null; matchedTotal: string | null; items: DictionaryOption[];
}
export interface PatternDetail extends PatternSummary {
  sequenceCount: string | null; integrityStatus: 'COMPLETE' | 'INCOMPLETE';
  issues: { code: string; itemsetOrder: number | null; itemOrder: number | null; itemId: number | null }[];
  itemsets: { itemsetOrder: number; items: {
    itemOrder: number; itemId: number; mappingStatus: 'MAPPED' | 'MISSING'; productId: string | null;
    nameSnapshot: string | null; skuSnapshot: string | null; itemWeight: number | null;
  }[] }[];
}
export interface PatternClient {
  runs(page: number, size: number, options?: PatternRequestOptions): Promise<RunPage>;
  list(runId: string, page: number, size: number, filters?: PatternFilters, options?: PatternRequestOptions): Promise<PatternPage>;
  dictionary(runId: string, query: string, page: number, size: number, options?: PatternRequestOptions): Promise<DictionaryPage>;
  detail(runId: string, patternId: string, options?: PatternRequestOptions): Promise<PatternDetail>;
}
export class PatternClientError extends Error {
  constructor(public readonly status: number, public readonly code: string,
    public readonly requestId: string | null = null,
    public readonly offendingItemIds: string[] = [],
    public readonly kind: 'http' | 'network' | 'response' | 'aborted' = 'http',
    public readonly details: { field: string; code: string; message: string }[] = [],
    message = code, public readonly path: string | null = null) {
    super(message);
    Object.setPrototypeOf(this, PatternClientError.prototype);
  }
}
export const unavailablePatternClient: PatternClient = {
  runs: async () => { throw new PatternClientError(0, 'API_NOT_AVAILABLE'); },
  list: async () => { throw new PatternClientError(0, 'API_NOT_AVAILABLE'); },
  detail: async () => { throw new PatternClientError(0, 'API_NOT_AVAILABLE'); },
  dictionary: async () => { throw new PatternClientError(0, 'API_NOT_AVAILABLE'); },
};
