import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { act, fireEvent, render as renderUI, screen } from '@testing-library/react';
import { ComboApiError, readPublicCombo } from '../api/combos';
import { PublicCombo } from '../models/combos';
import ComboDetailPage from './ComboDetailPage';
jest.mock('../api/combos', () => ({ ...jest.requireActual('../api/combos'), readPublicCombo: jest.fn() }));
const read = readPublicCombo as jest.MockedFunction<typeof readPublicCombo>;
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
        currentPrice: '2000.00',
      },
    },
  ],
};

beforeEach(() => read.mockReset());
test('renders exact API fields, provenance, grouping and safe back navigation', async () => {
  read.mockResolvedValue(combo);
  render(<ComboDetailPage comboId={combo.comboId} productId="sku/A"/>);
  expect(screen.getByText('Đang tải chi tiết Combo…')).toBeVisible();
  expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(combo.name);
  expect(read).toHaveBeenCalledWith(combo.comboId, expect.any(AbortSignal));
  expect(screen.getByText('Nhóm sản phẩm 1')).toBeVisible();
  expect(screen.getByText(/Số lượng: 2/)).toBeVisible();
  expect(screen.getByText(/9.007.199.254.740.993,99 VND/)).toBeVisible();
  expect(screen.getByText(/Batch: batch · Run: run · Snapshot: snapshot/)).toBeVisible();
  expect(screen.getByRole('link', { name: 'Về sản phẩm' })).toHaveAttribute('href', '/products/sku%2FA');
  expect(screen.getByRole('link', { name: 'Sản phẩm thành phần' })).toHaveAttribute('href', '/products/9007199254740993');
  expect(screen.queryByRole('button', { name: /giỏ hàng/i })).not.toBeInTheDocument();
});
test.each([undefined, '', ' ', 'x'.repeat(41)])('missing/invalid back ID falls back to product list', productId => {
  read.mockReturnValue(new Promise(() => undefined));
  render(<ComboDetailPage comboId="id" productId={productId}/>);
  expect(screen.getByRole('link', { name: 'Về sản phẩm' })).toHaveAttribute('href', '/products');
});
test('404 is a public not-found state without fixture fallback', async () => {
  read.mockRejectedValue(new ComboApiError(404, 'RESOURCE_NOT_FOUND'));
  render(<ComboDetailPage comboId="missing"/>);
  expect(await screen.findByText('Không tìm thấy Combo công khai.')).toBeVisible();
  expect(screen.queryByText(combo.name)).not.toBeInTheDocument();
});
test('error supports retry', async () => {
  read.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(combo);
  render(<ComboDetailPage comboId={combo.comboId}/>);
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(combo.name);
});
test('changed combo ignores stale result and aborts on unmount', async () => {
  let finish!: (value: PublicCombo) => void;
  read.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
    .mockResolvedValueOnce({ ...combo, comboId: 'next', name: 'Next Combo' });
  const view = render(<ComboDetailPage comboId="old"/>);
  const oldSignal = read.mock.calls[0][1];
  view.rerender(<ComboDetailPage comboId="next"/>);
  expect(oldSignal.aborted).toBe(true);
  await screen.findByText('Next Combo');
  await act(async () => finish(combo));
  expect(screen.queryByText(combo.name)).not.toBeInTheDocument();
  const signal = read.mock.calls[1][1];
  view.unmount();
  expect(signal.aborted).toBe(true);
});
