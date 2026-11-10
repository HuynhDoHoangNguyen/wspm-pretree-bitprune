import { ComboApiError, displayMoney, listPublicCombos, readPublicCombo } from './combos';
import { PublicCombo } from '../models/combos';
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

const signal = new AbortController().signal;
const originalFetch = global.fetch;
const network = jest.fn();
const envelope = (data: unknown) => ({ code: 'SUCCESS', message: 'Request completed successfully.', requestId: 'request', data });
function response(data: unknown, status = 200) {
  return { ok: status === 200, status, headers: { get: () => 'request' }, json: async () => envelope(data) };
}
const topK = { productId: '9007199254740993', requestedK: 4, effectiveK: 4, evaluatedAt: combo.evaluatedAt,
  provenance: combo.provenance.batch, items: [{ rank: 1, combo }] };
beforeEach(() => { global.fetch = network; network.mockReset(); });
afterAll(() => { global.fetch = originalFetch; });

test('Top-K uses exact string ID, relative URL, k=4 and AbortSignal', async () => {
  network.mockResolvedValue(response(topK));
  expect(await listPublicCombos(topK.productId, signal)).toEqual(topK);
  expect(network).toHaveBeenCalledWith('/api/v1/recommendations/combos?productId=9007199254740993&k=4',
    expect.objectContaining({ signal, redirect: 'error' }));
});
test('detail encodes ID and never sends productId query', async () => {
  network.mockResolvedValue(response(combo));
  expect(await readPublicCombo(combo.comboId, signal)).toEqual(combo);
  expect(network.mock.calls[0][0]).toBe('/api/v1/recommendations/combos/combo%2FA');
});
test('preserves decimal strings and nullable prices/explanation', async () => {
  const value = { ...combo, explanation: null, pricing: { ...combo.pricing, referenceSalePrice: null } };
  network.mockResolvedValue(response(value));
  expect(await readPublicCombo(combo.comboId, signal)).toEqual(value);
  expect(displayMoney(combo.pricing.referenceOriginalPrice, 'VND')).toBe('9.007.199.254.740.993,99 VND');
  expect(displayMoney(null, 'VND')).toBe('Chưa có giá');
});
test('accepts true empty without active batch', async () => {
  network.mockResolvedValue(response({ ...topK, provenance: null, effectiveK: 0, items: [] }));
  expect((await listPublicCombos(topK.productId, signal)).items).toEqual([]);
});
test.each([404, 503])('retains HTTP error status %s without fallback', async status => {
  network.mockResolvedValue(response(null, status));
  await expect(readPublicCombo('missing', signal)).rejects.toMatchObject({ status });
});
test('rejects invalid envelope and request ID', async () => {
  network.mockResolvedValue({ ...response(combo), headers: { get: () => 'other' } });
  await expect(readPublicCombo(combo.comboId, signal)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
});
test.each([
  { ...combo, status: 'APPROVED' }, { ...combo, kind: 'SEQUENTIAL_CROSS_SELL' },
  { ...combo, metrics: { ...combo.metrics, supportAbs: 9007199254740992 } },
  { ...combo, pricing: { ...combo.pricing, referenceSalePrice: 1000 } },
])('rejects private, unsupported or lossy data', async value => {
  network.mockResolvedValue(response(value));
  await expect(readPublicCombo(combo.comboId, signal)).rejects.toBeInstanceOf(ComboApiError);
});
test('rejects wrong product, rank and over-cap response', async () => {
  for (const value of [{ ...topK, productId: 'other' }, { ...topK, effectiveK: 0 },
    { ...topK, items: [{ rank: 2, combo }] }]) {
    network.mockResolvedValue(response(value));
    await expect(listPublicCombos(topK.productId, signal)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  }
});
test('rejects invalid IDs before network access and propagates abort', async () => {
  await expect(readPublicCombo(' ', signal)).rejects.toMatchObject({ status: 400 });
  expect(network).not.toHaveBeenCalled();
  network.mockRejectedValue(new DOMException('Aborted', 'AbortError'));
  await expect(readPublicCombo('id', signal)).rejects.toMatchObject({ name: 'AbortError' });
});
