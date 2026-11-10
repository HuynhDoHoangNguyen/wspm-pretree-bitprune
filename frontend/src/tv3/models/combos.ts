/** Public read contract from TV3 ComboReadResponse; IDs and decimal money stay strings. */
export interface ComboBatchProvenance {
  batchId: string;
  runId: string;
  snapshotId: string;
  algorithm: string;
  rankingPolicy: string;
  patternWeightPolicy: string;
}

export interface PublicCombo {
  comboId: string;
  name: string;
  kind: 'SAME_ORDER_BUNDLE';
  status: 'PUBLISHED';
  explanation: string | null;
  validFrom: string | null;
  validTo: string | null;
  publishedAt: string | null;
  evaluatedAt: string;
  provenance: {
    batch: ComboBatchProvenance;
    primaryPatternId: string;
    canonicalPattern: string;
  };
  metrics: { supportAbs: number; supportRatio: number; sequenceCount: number; patternWeight: number };
  pricing: {
    basis: 'PUBLISH_SNAPSHOT'; currencyCode: string;
    referenceOriginalPrice: string | null; referenceSalePrice: string | null;
    discountType: string; discountValue: string | null; maxDiscountAmount: string | null;
    isCheckoutQuote: false;
  };
  items: {
    itemsetOrder: number; itemOrder: number; quantity: number;
    anchorEligible: boolean; discountEligible: boolean; unitPriceSnapshot: string | null;
    product: {
      productId: string; name: string; sku: string | null; categoryId: string | null;
      imageUrl: string | null; currentPrice: string | null;
    };
  }[];
}

export interface PublicTopK {
  productId: string;
  requestedK: number;
  effectiveK: number;
  evaluatedAt: string;
  provenance: ComboBatchProvenance | null;
  items: { rank: number; combo: PublicCombo }[];
}
