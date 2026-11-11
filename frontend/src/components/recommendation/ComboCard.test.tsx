import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { render as renderUI, screen } from '@testing-library/react';
import ComboCard from './ComboCard';
import { PublicCombo } from '../../models/recommendation/combos';
const render = (ui: React.ReactElement) => renderUI(ui, { wrapper: MemoryRouter });
const combo: PublicCombo = {
  comboId: 'combo/A',
  name: 'Combo API thật',
  kind: 'SAME_ORDER_BUNDLE',
  status: 'PUBLISHED',
  explanation: 'Mua cùng một lần',
  validFrom: null,
  validTo: null,
  publishedAt: null,
  evaluatedAt: '2026-11-08T01:00:00Z',
  provenance: {
    batch: {
      batchId: 'batch',
      runId: 'run',
      snapshotId: 'snapshot',
      algorithm: 'WSPM_PRETREE',
      rankingPolicy: 'SUPPORT_THEN_PATTERN_WEIGHT_V1',
      patternWeightPolicy: 'stored-policy',
    },
    primaryPatternId: '9007199254740993',
    canonicalPattern: '<(1 2)>',
  },
  metrics: {
    supportAbs: 10,
    supportRatio: 0.5,
    sequenceCount: 20,
    patternWeight: 0.8,
  },
  pricing: {
    basis: 'PUBLISH_SNAPSHOT',
    currencyCode: 'VND',
    referenceOriginalPrice: '9007199254740993.99',
    referenceSalePrice: '1000.50',
    discountType: 'FIXED_AMOUNT',
    discountValue: '100.00',
    maxDiscountAmount: null,
    isCheckoutQuote: false,
  },
  items: [
    {
      itemsetOrder: 1,
      itemOrder: 1,
      quantity: 2,
      anchorEligible: true,
      discountEligible: true,
      unitPriceSnapshot: '1000.50',
      product: {
        productId: '9007199254740993',
        name: 'Sản phẩm thành phần',
        sku: null,
        categoryId: 'cat/A',
        imageUrl: null,
        currentPrice: '2000.00', availableQuantity: '10',
      },
    },
  ],
};

test('card preserves API prices, quantity, rank and encoded product link', () => {
  render(<ComboCard combo={combo} rank={2} productId="sku/A"/>);
  expect(screen.getByText('Gợi ý 2')).toBeVisible();
  expect(screen.queryByText(/Mua cùng đơn/i)).not.toBeInTheDocument();
  expect(screen.getByText('Sản phẩm thành phần × 2')).toBeVisible();
  expect(screen.getByText(/9.007.199.254.740.993,99 VND/)).toBeVisible();
  expect(screen.getByText(/không phải báo giá thanh toán/)).toBeVisible();
  expect(screen.getByRole('link', { name: 'Xem chi tiết Combo' })).toHaveAttribute('href', '/combos/combo%2FA?productId=sku%2FA');
  expect(screen.queryByRole('button', { name: /giỏ hàng/i })).not.toBeInTheDocument();
});
test('null prices/images do not produce fake prices or broken source URLs', () => {
  render(<ComboCard combo={{ ...combo, pricing: { ...combo.pricing, referenceSalePrice: null } }} rank={1} productId="p"/>);
  expect(screen.getByText(/Giá Combo tham chiếu: Chưa có giá/)).toBeVisible();
  expect(screen.getByRole('article')).toBeVisible();
});

test('combo items use the same public product-image endpoint as the catalog', () => {
  const item = combo.items[0];
  const imageUrl = '/uploads/products/sample.jpg';
  render(<ComboCard combo={{ ...combo, items: [{ ...item, product: { ...item.product, imageUrl } }] }}
    rank={1} productId={item.product.productId}/>);
  expect(screen.getByRole('img', { name: item.product.name })).toHaveAttribute('src', imageUrl);
});
