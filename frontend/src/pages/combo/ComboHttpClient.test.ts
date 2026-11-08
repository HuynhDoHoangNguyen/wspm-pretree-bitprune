import { createComboHttpClient } from './ComboHttpClient';
import { createWdmApiClient } from '../../api/admin/data-mining/client';

const data = { data: { comboId: 'C/1', name: 'Combo', comboKind: 'SAME_ORDER_BUNDLE', comboStatus: 'DRAFT', batchId: 'B', batchStatus: 'DRAFT', runId: 'R', runStatus: 'SUCCEEDED', createdAt: '2026-11-06T00:00:00.000000Z',
  review: { reviewedBy: null, reviewerDisplayName: null, reviewedAt: null, reviewNote: null }, metrics: { supportAbs: '9007199254740993', supportRatio: .5, patternWeight: .2 }, explanation: 'Explanation', currencyCode: 'VND', referenceOriginalPrice: '9007199254740993.01', referenceSalePrice: '9007199254740993.01', rankingPolicy: 'R', patternWeightPolicy: 'W', topK: '1' },
assessment: { primaryPatternId: '18446744073709551615', sourceCount: '1', allowedActions: [], blockingIssues: [{ code: 'REVIEW_POLICY_UNCONFIRMED' }], warnings: [] },
itemsets: [{ itemsetOrder: 1, items: [{ itemsetOrder: 1, itemOrder: 1, productId: 'P', quantity: '1', unitPriceSnapshot: '1.00', anchorEligible: true, discountEligible: false, nameSnapshot: 'Historic', skuSnapshot: 'SKU' }] }],
sources: [{ patternId: '18446744073709551615', primary: true, sourceRunId: 'R', canonicalPattern: '<(1)>', metrics: { supportAbs: '9007199254740993', supportRatio: .5, patternWeight: .2 }, weightedSupport: .1, patternWeightPolicy: 'W' }],
reviewToken: null, notePolicy: { confirmed: false, rejectRequired: true, maxCodePoints: 1000 } };
const response = (value: any, status = 200) => ({ ok: status === 200, status, headers: new Headers({ 'Content-Type': 'application/json', 'X-Request-Id': 'rid' }), text: async () => JSON.stringify(status === 200 ? { code: 'SUCCESS', message: 'Request completed successfully.', requestId: 'rid', data: value } : { status, code: 'STALE_REVIEW_DATA', message: 'Changed', path: '/api/v1/admin/combos/C/review', requestId: 'rid', details: [] }) } as Response);
function setup(){const fetchImpl = jest.fn();const denial = jest.fn();const transport = createWdmApiClient({ fetchImpl, getAuthAdapter: () => ({ getAccessToken: () => 'fixture-token', onUnauthorized: denial, onForbidden: denial }) });return { fetchImpl, denial, api: createComboHttpClient(transport) };}
test('batch history uses bounded server paging and rejects malformed totals', async () => {
  const { api, fetchImpl } = setup();
  fetchImpl.mockResolvedValue(response({ page: 0, size: 20, total: '9007199254740993', items: [] }));
  expect((await api.batches!(0, 20)).total).toBe('9007199254740993');
  expect(fetchImpl.mock.calls[0][0]).toContain('/api/v1/admin/combos/batches?page=0&size=20');
  fetchImpl.mockResolvedValue(response({ page: 0, size: 20, total: -1, items: [] }));
  await expect(api.batches!(0, 20)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
});
test('detail unwraps exact strings, paths, blocked policy and itemsets through shared transport', async () => {
  const { api, fetchImpl } = setup();fetchImpl.mockResolvedValue(response(data));const result = await api.detail('C/1');expect(result).toEqual(data);expect(fetchImpl.mock.calls[0][0]).toBe('/api/v1/admin/combos/C%2F1');expect(fetchImpl.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer fixture-token');
});
test.each(['support', 'money', 'source'])('rejects lossy numeric %s', async field => {
  const { api, fetchImpl } = setup();const bad = JSON.parse(JSON.stringify(data));if (field === 'support') bad.data.metrics.supportAbs = Number('9007199254740993');if (field === 'money') bad.data.referenceOriginalPrice = 1;if (field === 'source') bad.sources[0].patternId = 2;
  fetchImpl.mockResolvedValue(response(bad));await expect(api.detail('C/1')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
});
test('list encodes three filters and keeps distinct totals', async () => {
  const { api, fetchImpl } = setup();const filters = { batchId: 'B & X', status: 'DRAFT', comboKind: 'MIXED_SEQUENCE' };fetchImpl.mockResolvedValue(response({ page: 1, size: 20, scopeTotal: '9007199254740993', filteredTotal: '21', appliedFilters: filters, items: [] }));await api.list(filters, 1, 20);expect(fetchImpl.mock.calls[0][0]).toContain('/combos/batches/B%20%26%20X/combos?');expect(fetchImpl.mock.calls[0][0]).toContain('page=1');
});
test('POST sends decision/note/token only; 409 retains common metadata', async () => {
  const { api, fetchImpl } = setup();fetchImpl.mockResolvedValue(response({}, 409));const command = { decision: 'APPROVE' as const, reviewNote: null, expectedReviewToken: 'CR1:fixture' };
  await expect(api.review('C/1', command)).rejects.toMatchObject({ status: 409, code: 'STALE_REVIEW_DATA', requestId: 'rid', path: '/api/v1/admin/combos/C/review' });expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).toEqual(command);expect(fetchImpl.mock.calls[0][1].credentials).toBe('omit');
});
test.each([401, 403])('denial %s uses shared auth callback', async status => {
  const { api, fetchImpl, denial } = setup();fetchImpl.mockResolvedValue(response({}, status));await expect(api.detail('C/1')).rejects.toMatchObject({ status });expect(denial).toHaveBeenCalledTimes(1);
});
test('abort suppresses late success with shared transport', async () => {
  const { api, fetchImpl } = setup();let finish!: (v: Response) => void;fetchImpl.mockImplementation(() => new Promise(r => { finish = r; }));const c = new AbortController();const pending = api.detail('C/1', { signal: c.signal });c.abort();finish(response(data));await expect(pending).rejects.toMatchObject({ code: 'REQUEST_ABORTED' });
});
