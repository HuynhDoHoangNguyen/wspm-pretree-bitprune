import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import ComboAdmin from './ComboAdmin';
import { ComboClient, ComboClientError, ComboDetail, ComboPage, ReviewResult, unavailableComboClient } from './ComboClient';

const audit = { reviewedBy: 'winner-id', reviewerDisplayName: null, reviewedAt: '2026-11-05T02:00:00.000000Z', reviewNote: 'Saved reason' };
const detail: ComboDetail = {
  data: { comboId: 'C1', name: 'Demo', comboKind: 'SEQUENTIAL_CROSS_SELL', comboStatus: 'DRAFT', batchId: 'B1', batchStatus: 'ACTIVE',
    runId: 'R1', runStatus: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00.000000Z', review: { reviewedBy: null, reviewerDisplayName: null, reviewedAt: null, reviewNote: null },
    metrics: { supportAbs: '9007199254740993', supportRatio: .5, patternWeight: .4 }, explanation: 'Fixture only', currencyCode: 'VND',
    referenceOriginalPrice: '2.00', referenceSalePrice: '2.00', rankingPolicy: 'TEST', patternWeightPolicy: 'TEST', topK: '1' },
  assessment: { primaryPatternId: '18446744073709551615', sourceCount: '2', allowedActions: ['APPROVE', 'REJECT'], blockingIssues: [], warnings: [] },
  itemsets: [{ itemsetOrder: 1, items: [{ itemsetOrder: 1, itemOrder: 1, productId: 'L', quantity: '1', unitPriceSnapshot: '1.00', anchorEligible: true, discountEligible: false, nameSnapshot: 'Laptop snapshot', skuSnapshot: 'L' }] },
    { itemsetOrder: 2, items: [{ itemsetOrder: 2, itemOrder: 1, productId: 'M', quantity: '1', unitPriceSnapshot: '1.00', anchorEligible: false, discountEligible: false, nameSnapshot: 'Mouse snapshot', skuSnapshot: 'M' }] }],
  sources: [{ patternId: '18446744073709551615', primary: true, sourceRunId: 'R1', canonicalPattern: '<(1)(2)>', metrics: { supportAbs: '9007199254740993', supportRatio: .5, patternWeight: .4 }, weightedSupport: 4, patternWeightPolicy: 'TEST' },
    { patternId: '2', primary: false, sourceRunId: 'R1', canonicalPattern: '<(1 2)>', metrics: { supportAbs: '8', supportRatio: .2, patternWeight: .8 }, weightedSupport: 2, patternWeightPolicy: 'TEST' }], reviewToken: 'server-fixture-token',
};
const page = (p = 0, total = '21'): ComboPage => ({ page: p, size: 20, scopeTotal: '9007199254740993', filteredTotal: total, appliedFilters: {}, items: [detail] });
function client(): jest.Mocked<ComboClient> {
  return { reviewReady: true, notePolicy: { confirmed: true, rejectRequired: true, maxCodePoints: 1000 },
    list: jest.fn().mockImplementation((f, p) => Promise.resolve({ ...page(p), appliedFilters: f })),
    detail: jest.fn().mockResolvedValue(detail), review: jest.fn().mockResolvedValue({ outcome: 'BLOCKED', currentStatus: 'DRAFT', storedAudit: null, issues: ['TEST_BLOCKER'] }) };
}
async function open() { await act(async () => { fireEvent.click(await screen.findByRole('button', { name: 'Demo (C1)' })); }); await screen.findByRole('group', { name: 'Itemset 1' }); }
async function approve() {
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Duyệt' })); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' })); });
}
test('runtime unavailable has no fixture fallback', async () => {
  render(<ComboAdmin client={unavailableComboClient}/>);expect(await screen.findByText(/Chưa thể kết nối: API Combo/)).toBeInTheDocument();
  expect(screen.queryByText('Demo (C1)')).not.toBeInTheDocument();
});
test('three filters, exact totals, paging and separate statuses', async () => {
  const api = client();render(<ComboAdmin client={api}/>);await screen.findByRole('button', { name: 'Demo (C1)' });
  expect(screen.getByText(/9007199254740993/)).toBeInTheDocument();expect(screen.getByRole('cell', { name: 'Bản nháp' })).toBeInTheDocument();expect(screen.getByText(/Batch đang kích hoạt/)).toBeInTheDocument();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Trang sau' })); });expect(api.list).toHaveBeenLastCalledWith({}, 1, 20, expect.any(Object));
  fireEvent.change(screen.getByLabelText('Batch ID'), { target: { value: 'B1' } });
  fireEvent.change(screen.getByLabelText('Trạng thái Combo'), { target: { value: 'DRAFT' } });
  fireEvent.change(screen.getByLabelText('Loại Combo'), { target: { value: 'SAME_ORDER_BUNDLE' } });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Áp dụng bộ lọc' })); });
  expect(api.list).toHaveBeenLastCalledWith({ batchId: 'B1', status: 'DRAFT', comboKind: 'SAME_ORDER_BUNDLE' }, 0, 20, expect.any(Object));
});
test.each([['0', '0', 'Chưa có Combo trong phạm vi đã chọn.'], ['4', '0', 'Không có Combo khớp bộ lọc.']])('empty states %s/%s', async (scope, filtered, message) => {
  const api = client();api.list.mockResolvedValue({ ...page(), scopeTotal: scope, filteredTotal: filtered, items: [] });render(<ComboAdmin client={api}/>);
  expect(await screen.findByText(message)).toBeInTheDocument();
});
test('load error remains error rather than empty', async () => {
  const api = client();api.list.mockRejectedValue(new ComboClientError(403, 'FORBIDDEN'));render(<ComboAdmin client={api}/>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Bạn không có quyền Admin');expect(screen.queryByText(/Chưa có Combo trong/)).not.toBeInTheDocument();
});
test('detail shows ordered groups, source roles, audit and blocking reason', async () => {
  const api = client();api.detail.mockResolvedValue({ ...detail, data: { ...detail.data, review: audit }, assessment: { ...detail.assessment, allowedActions: [], blockingIssues: [{ code: 'POLICY_UNCONFIRMED' }] } });
  render(<ComboAdmin client={api}/>);await open();expect(screen.getByRole('group', { name: 'Itemset 2' })).toHaveTextContent('Neo: Không');
  expect(screen.getByText(/Nguồn chính: 18446744073709551615/)).toBeInTheDocument();expect(screen.getByText(/Nguồn phụ: 2/)).toBeInTheDocument();
  expect(screen.getByText(/Người review: winner-id/)).toBeInTheDocument();expect(screen.getByText('Chặn: POLICY_UNCONFIRMED')).toBeInTheDocument();expect(screen.getByRole('button', { name: 'Duyệt' })).toBeDisabled();
});
test.each(['actions', 'token', 'api', 'policy'])('review disabled when missing %s', async missing => {
  const api = client();api.detail.mockResolvedValue({ ...detail, reviewToken: missing === 'token' ? undefined : detail.reviewToken,
    assessment: { ...detail.assessment, allowedActions: missing === 'actions' ? [] : ['APPROVE', 'REJECT'] } });
  if (missing === 'api') Object.defineProperty(api, 'reviewReady', { value: false });
  if (missing === 'policy') Object.defineProperty(api, 'notePolicy', { value: undefined });
  render(<ComboAdmin client={api}/>);await open();expect(screen.getByRole('button', { name: 'Duyệt' })).toBeDisabled();expect(api.review).not.toHaveBeenCalled();
});
test.each(['APPLIED', 'ALREADY_DECIDED', 'BLOCKED', 'READY_TO_APPLY'])('outcome %s never invents local audit or status', async outcome => {
  const api = client();api.review.mockResolvedValue({ outcome, currentStatus: 'APPROVED', storedAudit: audit, issues: [] });
  render(<ComboAdmin client={api}/>);await open();await approve();
  expect(api.review).toHaveBeenCalledWith('C1', { decision: 'APPROVE', reviewNote: null, expectedReviewToken: 'server-fixture-token' }, expect.any(Object));
  expect(Object.keys(api.review.mock.calls[0][1]).sort()).toEqual(['decision', 'expectedReviewToken', 'reviewNote']);
  if (outcome === 'APPLIED') expect(await screen.findByText('Vừa xử lý thành công.')).toBeInTheDocument();
  else expect(screen.queryByText('Vừa xử lý thành công.')).not.toBeInTheDocument();
  if (outcome === 'ALREADY_DECIDED') expect(await screen.findByText(/Đã có quyết định này/)).toBeInTheDocument();
  if (outcome === 'APPLIED' || outcome === 'ALREADY_DECIDED') expect(screen.getByText(/Người review: winner-id/)).toBeInTheDocument();
  else expect(api.list).toHaveBeenCalledTimes(1);
});
test('confirmed test note policy validates rejection and emoji codepoints', async () => {
  const api = client();render(<ComboAdmin client={api}/>);await open();fireEvent.click(screen.getByRole('button', { name: 'Từ chối' }));
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' })); });expect(api.review).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('Ghi chú review'), { target: { value: String.fromCodePoint(0x1f600).repeat(1001) } });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' })); });expect(api.review).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('Ghi chú review'), { target: { value: '  reason  ' } });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' })); });expect(api.review.mock.calls[0][1].reviewNote).toBe('reason');
});
test.each(['CONFLICT', 'STALE_REVIEW_DATA'])('conflict %s reloads and recovers last page', async outcome => {
  const api = client();render(<ComboAdmin client={api}/>);await screen.findByRole('button', { name: 'Demo (C1)' });
  fireEvent.change(screen.getByLabelText('Trạng thái Combo'), { target: { value: 'DRAFT' } });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Áp dụng bộ lọc' })); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Trang sau' })); });await open();
  api.list.mockImplementation((f, p) => Promise.resolve({ ...page(p, '0'), appliedFilters: f, items: [] }));
  api.detail.mockResolvedValue({ ...detail, data: { ...detail.data, comboStatus: 'APPROVED', review: audit }, assessment: { ...detail.assessment, allowedActions: [] } });
  api.review.mockResolvedValue({ outcome: outcome === 'CONFLICT' ? outcome : 'BLOCKED', currentStatus: 'APPROVED', storedAudit: audit, issues: outcome === 'CONFLICT' ? [] : [outcome] });
  await approve();await waitFor(() => expect(api.list).toHaveBeenLastCalledWith({ status: 'DRAFT' }, 0, 20, expect.any(Object)));expect(await screen.findByText('Trang 1')).toBeInTheDocument();
  expect(api.detail.mock.calls.length).toBeLessThanOrEqual(2);
  expect(screen.queryByRole('heading', { name: 'Chi tiết Combo C1' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Demo (C1)' })).not.toBeInTheDocument();
});
test.each(['UNKNOWN', 'network'])('%s forces readback before resubmit', async outcome => {
  const api = client();render(<ComboAdmin client={api}/>);await open();
  let resolveDetail!: (d: ComboDetail) => void;api.detail.mockImplementation(() => new Promise(r => { resolveDetail = r; }));
  if (outcome === 'network') api.review.mockRejectedValue(new Error('offline'));else api.review.mockResolvedValue({ outcome, currentStatus: null, storedAudit: null, issues: [] });
  await approve();expect(screen.queryByRole('button', { name: 'Duyệt' })).not.toBeInTheDocument();
  await act(async () => { resolveDetail({ ...detail, data: { ...detail.data, comboStatus: 'APPROVED', review: audit }, assessment: { ...detail.assessment, allowedActions: [] } }); });
  expect(screen.getByRole('button', { name: 'Duyệt' })).toBeDisabled();expect(api.review).toHaveBeenCalledTimes(1);
});
test('old list cannot overwrite new filters', async () => {
  const api = client();let resolveOld!: (p: ComboPage) => void;api.list.mockImplementationOnce(() => new Promise(r => { resolveOld = r; }));
  render(<ComboAdmin client={api}/>);fireEvent.change(screen.getByLabelText('Batch ID'), { target: { value: 'B2' } });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Áp dụng bộ lọc' })); });
  await act(async () => { resolveOld({ ...page(), scopeTotal: '999', items: [] }); });
  expect(screen.queryByText(/Trong phạm vi: 999/)).not.toBeInTheDocument();expect(screen.getByRole('button', { name: 'Demo (C1)' })).toBeInTheDocument();
});
test('pending review refreshes current filters and disables double submit', async () => {
  const api = client();let finish!: (r: ReviewResult) => void;api.review.mockImplementation(() => new Promise(r => { finish = r; }));
  render(<ComboAdmin client={api}/>);await open();await approve();expect(screen.getByRole('button', { name: 'Đang gửi…' })).toBeDisabled();
  fireEvent.change(screen.getByLabelText('Batch ID'), { target: { value: 'CURRENT' } });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Áp dụng bộ lọc' })); });
  await act(async () => { finish({ outcome: 'APPLIED', currentStatus: 'APPROVED', storedAudit: audit, issues: [] }); });
  expect(api.list).toHaveBeenLastCalledWith({ batchId: 'CURRENT' }, 0, 20, expect.any(Object));expect(api.review).toHaveBeenCalledTimes(1);
});
test('late detail is discarded after filter change closes it', async () => {
  const api = client();let finish!: (d: ComboDetail) => void;api.detail.mockImplementationOnce(() => new Promise(r => { finish = r; }));
  render(<ComboAdmin client={api}/>);fireEvent.click(await screen.findByRole('button', { name: 'Demo (C1)' }));
  fireEvent.change(screen.getByLabelText('Batch ID'), { target: { value: 'NEW' } });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Áp dụng bộ lọc' })); });
  await act(async () => { finish(detail); });expect(screen.queryByRole('group', { name: 'Itemset 1' })).not.toBeInTheDocument();
});
test('raw unknown status is displayed and never treated as draft', async () => {
  const api = client();const unknown = { ...detail, data: { ...detail.data, comboStatus: 'PENDING' } };
  api.list.mockResolvedValue({ ...page(), items: [unknown] });api.detail.mockResolvedValue(unknown);
  render(<ComboAdmin client={api}/>);await open();expect(screen.getAllByText(/Không xác định: PENDING/).length).toBeGreaterThan(0);
  expect(screen.getByRole('button', { name: 'Duyệt' })).toBeDisabled();
});

test('successful review removes stale selection when the row leaves DRAFT filter', async () => {
  const api = client();render(<ComboAdmin client={api}/>);await screen.findByRole('button', { name: 'Demo (C1)' });
  fireEvent.change(screen.getByLabelText('Trạng thái Combo'), { target: { value: 'DRAFT' } });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Áp dụng bộ lọc' })); });await open();
  api.list.mockResolvedValue({ ...page(0, '0'), appliedFilters: { status: 'DRAFT' }, items: [] });
  api.review.mockResolvedValue({ outcome: 'APPLIED', currentStatus: 'APPROVED', storedAudit: audit, issues: [] });
  await approve();await screen.findByText('Không có Combo khớp bộ lọc.');
  expect(screen.queryByRole('heading', { name: 'Chi tiết Combo C1' })).not.toBeInTheDocument();
  expect(screen.getByText(/Người review: winner-id/)).toBeInTheDocument();
});
test('HTTP stale conflict reloads once without automatic resubmit', async () => {
  const api = client();render(<ComboAdmin client={api}/>);await open();api.review.mockRejectedValue(new ComboClientError(409, 'STALE_REVIEW_DATA'));
  await approve();expect(await screen.findByText(/Quyết định hoặc dữ liệu đã thay đổi/)).toBeInTheDocument();
  expect(api.review).toHaveBeenCalledTimes(1);await waitFor(() => expect(api.detail).toHaveBeenCalledTimes(2));
});
test('request signals abort when filters change and unmount, including pending review', async () => {
  const api = client();let finish!: (r: ReviewResult) => void;api.review.mockImplementation(() => new Promise(r => { finish = r; }));
  const mounted = render(<ComboAdmin client={api}/>);await open();await approve();
  const sendSignal = api.review.mock.calls[0][2]?.signal;const listSignal = api.list.mock.calls[0][3]?.signal;
  mounted.unmount();expect(sendSignal?.aborted).toBe(true);expect(listSignal?.aborted).toBe(true);
  await act(async () => { finish({ outcome: 'APPLIED', currentStatus: 'APPROVED', storedAudit: audit, issues: [] }); });
  expect(api.list).toHaveBeenCalledTimes(1);
});
