import { CustomerCombo } from './CustomerCombos.types';

/** Application seam, not a proposed HTTP contract. Implement with TV2 later.
 * The source owns eligibility and ordering; the view never ranks results.
 */
export interface CustomerComboDataSource {
  listCombos(productId: string, limit: number, signal: AbortSignal): Promise<CustomerCombo[]>;
}

// No URL, fetch implementation or fallback to synthetic production data.
