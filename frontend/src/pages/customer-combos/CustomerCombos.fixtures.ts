import { CustomerCombo, CustomerProduct } from './CustomerCombos.types';

export const PREVIEW_COMBO_LIMIT = 4;
export const PREVIEW_PRODUCTS: CustomerProduct[] = [
  { productId: 'laptop-air', code: 'LT-001', name: 'Laptop Air 14', category: 'Laptop', price: 18000000 },
  { productId: 'mouse', code: 'PK-002', name: 'Chuột không dây', category: 'Phụ kiện', price: 450000 },
  { productId: 'keyboard', code: 'PK-003', name: 'Bàn phím cơ', category: 'Phụ kiện', price: 1200000 },
  { productId: 'monitor', code: 'MH-004', name: 'Màn hình 27 inch', category: 'Màn hình', price: 4500000 },
  { productId: 'sleeve', code: 'PK-005', name: 'Túi chống sốc', category: 'Phụ kiện', price: 350000 },
  { productId: 'speaker', code: 'PK-006', name: 'Loa mini', category: 'Phụ kiện', price: 650000 },
];

// Explicit synthetic display values, not calculated support, weight or ranking.
export const fixtures: (CustomerCombo & { approved: boolean })[] = [
  { comboId: 'c1', name: 'Bộ làm việc linh hoạt', items: [PREVIEW_PRODUCTS[0], PREVIEW_PRODUCTS[1]], price: 18100000, referencePrice: 18450000, supportCount: 24, patternWeight: 0.7, approved: true },
  { comboId: 'c2', name: 'Góc làm việc gọn gàng', items: [PREVIEW_PRODUCTS[0], PREVIEW_PRODUCTS[2]], price: 19000000, referencePrice: 19200000, supportCount: 20, approved: true },
  { comboId: 'c3', name: 'Không gian sáng tạo', items: [PREVIEW_PRODUCTS[0], PREVIEW_PRODUCTS[3]], price: 22000000, referencePrice: 22500000, approved: true },
  { comboId: 'c4', name: 'Sẵn sàng di chuyển', items: [PREVIEW_PRODUCTS[0], PREVIEW_PRODUCTS[4]], price: 18350000, approved: true },
  { comboId: 'c5', name: 'Bộ laptop đầy đủ', items: [PREVIEW_PRODUCTS[0], PREVIEW_PRODUCTS[1], PREVIEW_PRODUCTS[4]], price: 18500000, approved: true },
  { comboId: 'c6', name: 'Bộ phụ kiện bàn làm việc', items: [PREVIEW_PRODUCTS[1], PREVIEW_PRODUCTS[2]], price: 1550000, referencePrice: 1650000, approved: true },
  { comboId: 'draft', name: 'Combo chưa duyệt', items: [PREVIEW_PRODUCTS[0], PREVIEW_PRODUCTS[5]], price: 18650000, approved: false },
];

