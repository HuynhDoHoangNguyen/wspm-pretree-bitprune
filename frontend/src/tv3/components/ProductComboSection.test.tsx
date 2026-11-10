import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { act, fireEvent, render as renderUI, screen, waitFor } from '@testing-library/react';
import { listPublicCombos } from '../api/combos';
import { PublicCombo, PublicTopK } from '../models/combos';
import ProductComboSection from './ProductComboSection';
jest.mock('../api/combos', () => ({ ...jest.requireActual('../api/combos'), listPublicCombos: jest.fn() }));
const list = listPublicCombos as jest.MockedFunction<typeof listPublicCombos>;
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

const result = (productId: string): PublicTopK => ({ productId, requestedK: 4, effectiveK: 4,
  evaluatedAt: combo.evaluatedAt, provenance: combo.provenance.batch, items: [{ rank: 1, combo }] });
beforeEach(() => list.mockReset());
test('loads exact product and renders API order without ranking locally', async () => {
  list.mockResolvedValue({ ...result('p'), items: [{ rank: 1, combo: { ...combo, comboId: 'z', name: 'First API' } },
    { rank: 2, combo: { ...combo, comboId: 'a', name: 'Second API' } }] });
  render(<ProductComboSection productId="p"/>);
  expect(screen.getByText('Đang tải Combo…')).toBeVisible();
  expect(await screen.findByText('First API')).toBeVisible();
  expect(screen.getAllByRole('heading', { level: 3 }).map(node => node.textContent)).toEqual(['First API', 'Second API']);
  expect(list).toHaveBeenCalledWith('p', expect.any(AbortSignal));
});
test('true empty has no fixture cards', async () => {
  list.mockResolvedValue({ ...result('p'), items: [] });
  render(<ProductComboSection productId="p"/>);
  await waitFor(() => expect(screen.queryByText('Đang tải Combo…')).not.toBeInTheDocument());
  expect(screen.queryByRole('region', { name: 'Combo gợi ý' })).not.toBeInTheDocument();
  expect(screen.queryByRole('article')).not.toBeInTheDocument();
});
test('error offers retry and recovers', async () => {
  list.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(result('p'));
  render(<ProductComboSection productId="p"/>);
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByText(combo.name)).toBeVisible();
  expect(list).toHaveBeenCalledTimes(2);
});
test('product change aborts and ignores late success, unmount aborts next request', async () => {
  let resolveOld!: (value: PublicTopK) => void;
  list.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }))
    .mockResolvedValueOnce({ ...result('new'), items: [] });
  const view = render(<ProductComboSection productId="old"/>);
  const oldSignal = list.mock.calls[0][1];
  view.rerender(<ProductComboSection productId="new"/>);
  expect(oldSignal.aborted).toBe(true);
  await waitFor(() => expect(screen.queryByText('Đang tải Combo…')).not.toBeInTheDocument());
  await act(async () => resolveOld(result('old')));
  expect(screen.queryByText(combo.name)).not.toBeInTheDocument();
  const nextSignal = list.mock.calls[1][1];
  view.unmount();
  expect(nextSignal.aborted).toBe(true);
});
test('changing a loaded product hides its cards immediately', async () => {
  list.mockResolvedValueOnce(result('old')).mockReturnValueOnce(new Promise(() => undefined));
  const view = render(<ProductComboSection productId="old"/>);
  await screen.findByText(combo.name);
  view.rerender(<ProductComboSection productId="new"/>);
  expect(screen.queryByText(combo.name)).not.toBeInTheDocument();
  expect(screen.getByText('Đang tải Combo…')).toBeVisible();
});
