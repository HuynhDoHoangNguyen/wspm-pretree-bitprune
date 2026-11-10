import { ComboApiError, displayMoney, listPublicCombos, readPublicCombo, latestPublicCombos, personalCombos } from './combos';
import { PublicCombo } from '../../models/recommendation/combos';
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

test('latest is a public bounded read; personal uses Bearer and optional product context without userId', async () => {
  network.mockResolvedValue(response([combo]));
  expect(await latestPublicCombos(signal)).toEqual([combo]);
  network.mockResolvedValue(response(Array.from({ length: 6 }, () => combo)));
  await expect(latestPublicCombos(signal)).rejects.toBeInstanceOf(ComboApiError);
  network.mockResolvedValue(response({ policy: 'HISTORY_PREFIX_V1', evaluatedAt: combo.evaluatedAt, items: [] }));
  await personalCombos('test-token', signal, 'product/A');
  expect(network).toHaveBeenLastCalledWith('/api/v1/recommendations/combos/personalized?productId=product%2FA',
    expect.objectContaining({ headers: { Accept: 'application/json', Authorization: 'Bearer test-token' }, cache: 'no-store', signal }));
});

test('accepts ordered temporal kinds and rejects suggested IDs outside the next step', async () => {
  const temporal = { ...combo, kind: 'SEQUENTIAL_CROSS_SELL', items: [combo.items[0],
    { ...combo.items[0], itemsetOrder: 2, product: { ...combo.items[0].product, productId: 'next' } }] };
  network.mockResolvedValue(response(temporal));
  await expect(readPublicCombo(combo.comboId, signal)).rejects.toBeInstanceOf(ComboApiError);
  network.mockResolvedValue(response([temporal]));
  await expect(latestPublicCombos(signal)).rejects.toBeInstanceOf(ComboApiError);
  network.mockResolvedValue(response({ ...topK, items: [{ rank: 1, combo: temporal }] }));
  await expect(listPublicCombos(topK.productId, signal)).rejects.toBeInstanceOf(ComboApiError);
  network.mockResolvedValue(response({ policy: 'HISTORY_PREFIX_V1', evaluatedAt: combo.evaluatedAt,
    items: [{ rank: 1, matchedItemsets: 1, nextItemsetOrder: 2, suggestedProductIds: ['another'], combo: temporal }] }));
  await expect(personalCombos('test-token', signal)).rejects.toBeInstanceOf(ComboApiError);
});

test('Top-K uses exact string ID, relative URL, batch K and AbortSignal', async () => {
  network.mockResolvedValue(response(topK));
  expect(await listPublicCombos(topK.productId, signal)).toEqual(topK);
  expect(network).toHaveBeenCalledWith('/api/v1/recommendations/combos?productId=9007199254740993',
    expect.objectContaining({ signal, redirect: 'error' }));
});
test.each([2, 5, 20])('accepts the batch cap %s without imposing the former four-item cap', async k => {
  const value = { ...topK, requestedK: k, effectiveK: k,
    items: Array.from({ length: k }, (_, i) => ({ rank: i + 1, combo: { ...combo, comboId: 'c' + i } })) };
  network.mockResolvedValue(response(value));
  expect((await listPublicCombos(topK.productId, signal)).items).toHaveLength(k);
});
test('accepts five personal next-step recommendations from the active batch', async () => {
  const temporal: PublicCombo = { ...combo, kind: 'SEQUENTIAL_CROSS_SELL', items: [combo.items[0],
    { ...combo.items[0], itemsetOrder: 2, product: { ...combo.items[0].product, productId: 'next' } }] };
  const items = Array.from({ length: 5 }, (_, i) => ({ rank: i + 1, matchedItemsets: 1, nextItemsetOrder: 2,
    suggestedProductIds: ['next'], combo: { ...temporal, comboId: 'personal-' + i } }));
  network.mockResolvedValue(response({ policy: 'HISTORY_PREFIX_V1', evaluatedAt: combo.evaluatedAt, items }));
  expect((await personalCombos('test-token', signal)).items).toHaveLength(5);
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
test.each([null,'0','8589934590','9007199254740993'])('preserves available stock %s without number conversion',async availableQuantity=>{
  const value={...combo,items:combo.items.map(i=>({...i,product:{...i.product,availableQuantity}}))};
  network.mockResolvedValue(response(value));
  expect((await readPublicCombo(combo.comboId,signal)).items[0].product.availableQuantity).toBe(availableQuantity);
});
test.each([undefined,1,-1,'-1','01','1.5'])('rejects malformed stock %s',async availableQuantity=>{
  network.mockResolvedValue(response({...combo,items:combo.items.map(i=>({...i,product:{...i.product,availableQuantity}}))}));
  await expect(readPublicCombo(combo.comboId,signal)).rejects.toBeInstanceOf(ComboApiError);
});
test('latest accepts five same-order bundles and rejects an over-cap response', async () => {
  const items = Array.from({ length: 5 }, (_, i) => ({ ...combo, comboId: 'latest-' + i }));
  network.mockResolvedValueOnce(response(items));
  expect(await latestPublicCombos(signal)).toHaveLength(5);
  network.mockResolvedValueOnce(response([...items, { ...combo, comboId: 'sixth' }]));
  await expect(latestPublicCombos(signal)).rejects.toBeInstanceOf(ComboApiError);
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
