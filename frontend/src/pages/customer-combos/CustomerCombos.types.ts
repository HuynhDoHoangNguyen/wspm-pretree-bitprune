// UI models only. TV2's HTTP contract has not been agreed yet.
export interface CustomerProduct {
  productId: string;
  name: string;
  code: string;
  price: number;
  category: string;
  imageUrl?: string | null;
}

export interface CustomerCombo {
  comboId: string;
  name: string;
  items: CustomerProduct[];
  price: number;
  referencePrice?: number;
  supportCount?: number;
  patternWeight?: number;
}
