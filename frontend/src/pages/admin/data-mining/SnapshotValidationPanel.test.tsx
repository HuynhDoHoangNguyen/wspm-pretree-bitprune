import React from 'react';
import { runInThisContext } from 'vm';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { useSharedAuth } from 'auth/session';
import { setWdmAuthAdapter, SnapshotDetail, ValidationReport } from 'api/admin/data-mining';
import { pendingDetail, reportData } from 'api/admin/data-mining/validation-test-data';
import SnapshotValidationPanel from './SnapshotValidationPanel';

jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
Object.assign(globalThis, runInThisContext('({ Headers, Response })'));
const identity = useSharedAuth as unknown as jest.Mock;
let fetchMock: jest.Mock, session: AbortController;
const complete = jest.fn<Promise<void>, [AbortSignal]>(() => Promise.resolve());
function json(data: unknown) {
  return new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'req-1' }),
    { headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'req-1' } });
}
function failure(status: number) {
  return new Response(JSON.stringify({ status, code: 'INVALID_RESOURCE_STATE', message: 'private-server-value',
    path: '/api/v1/admin/dataset-snapshots/id/validation', requestId: 'req-1', details: [] }),
  { status, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'req-1' } });
}
function page(detail: SnapshotDetail = pendingDetail()) {
  return <MantineProvider><SnapshotValidationPanel detail={detail} onComplete={complete}/></MantineProvider>;
}
function saved() { return { ...pendingDetail(), validationStatus: 'VALID' as const }; }
beforeEach(() => {
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false,
    addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() })) });
  fetchMock = jest.fn(); session = new AbortController(); complete.mockClear();
  Object.defineProperty(globalThis, 'fetch', { configurable: true, writable: true, value: fetchMock });
  identity.mockReturnValue({ user: { userId: 'admin-1', roles: ['ADMIN'] }, status: 'authenticated', forbidden: false });
  setWdmAuthAdapter({ getAccessToken: () => 'admin-token', getSessionSignal: () => session.signal });
});
afterEach(() => { setWdmAuthAdapter(null); jest.restoreAllMocks(); });

it('starts only on explicit action, blocks duplicate clicks and retains clean-subset meaning', async () => {
  let finish!: (value: Response) => void;
  fetchMock.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  render(page()); expect(fetchMock).not.toHaveBeenCalled();
  const button = screen.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true });
  fireEvent.click(button); fireEvent.click(button);
  expect(fetchMock).toHaveBeenCalledTimes(1); expect(button).toBeDisabled();
  expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
  await act(async () => { finish(json(reportData())); });
  expect(await screen.findByText('Có dữ liệu sạch sử dụng được')).toBeInTheDocument();
  expect(screen.getByText(/tệp gốc vẫn có thể chứa đơn/)).toBeInTheDocument();
  expect(within(screen.getByRole('group', { name: 'Dòng loại theo đơn' })).getByText('3')).toBeInTheDocument();
  expect(complete).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('button', { name: 'Kiểm tra dữ liệu', exact: true })).not.toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it('automatically reads an existing report without POST and preserves exact counters/time', async () => {
  const report = reportData(); report.counts.inputRecords = '9007199254740993';
  fetchMock.mockResolvedValue(json(report));
  render(page(saved()));
  await screen.findByText('Có dữ liệu sạch sử dụng được');
  expect(screen.getByText('9007199254740993')).toBeInTheDocument();
  expect(screen.getByText(/2026-11-05T01:02:03.123456Z/)).toBeInTheDocument();
  expect(fetchMock.mock.calls[0][1].method).toBe('GET'); expect(complete).not.toHaveBeenCalled();
});

it('renders incomplete prefix/null counts and honest decoder accuracy', async () => {
  const report = reportData(); report.validationStatus = 'INVALID'; report.cleaningOutcome = 'NOT_RUN_STRUCTURAL_ERROR'; report.countsComplete = false;
  Object.keys(report.counts).forEach(key => { (report.counts as Record<string, unknown>)[key] = null; });
  report.counts.parsedRecords = '5'; report.counts.issueCount = '1';
  report.issues = { page: 0, size: 20, totalElements: '1', totalPages: '1', content: [{ stage: 'STRUCTURE', code: 'CSV_INVALID_UTF8',
    field: null, positionAccuracy: 'DECODER_CURSOR', positions: [{ recordNumber: 1, startLine: 1, endLine: 1 }] }] };
  fetchMock.mockResolvedValue(json(report));
  render(page({ ...pendingDetail(), validationStatus: 'INVALID' }));
  expect(await screen.findByText(/Đã đọc 5 bản ghi hoàn chỉnh trước lỗi/)).toBeInTheDocument();
  expect(within(screen.getByRole('group', { name: 'Dòng đầu vào' })).getByText('Chưa xác định')).toBeInTheDocument();
  expect(screen.getByText(/không xác định chính xác byte hoặc bản ghi lỗi/)).toBeInTheDocument();
});

it.each(['EMPTY_INPUT', 'EMPTY_AFTER_CLEANING', 'BLOCKED_UNATTRIBUTABLE_ORDER'] as const)('explains INVALID %s', async outcome => {
  const report = reportData(); report.validationStatus = 'INVALID'; report.cleaningOutcome = outcome;
  report.counts.keptRecords = '0'; report.counts.keptOrders = '0';
  report.counts.issueCount = '1'; report.issues.totalElements = '1';
  report.issues.content = [{ stage: 'CLEANING', code: outcome, field: null, positionAccuracy: 'RECORD_RANGE', positions: [] }];
  fetchMock.mockResolvedValue(json(report)); render(page({ ...pendingDetail(), validationStatus: 'INVALID' }));
  expect(await screen.findByText('Chưa có dữ liệu sạch sử dụng được')).toBeInTheDocument();
  expect(screen.getByText(outcome)).toBeInTheDocument();
  expect(screen.getByText('Không có vị trí dòng cụ thể.')).toBeInTheDocument();
});

it('paginates diagnostics by GET and never reruns validation', async () => {
  const first = reportData(); first.counts.issueCount = '21'; first.issues.totalElements = '21'; first.issues.totalPages = '2';
  first.issues.content = Array.from({ length: 20 }, (_, index) => ({ ...first.issues.content[0], positions: [{ recordNumber: index + 2, startLine: index + 2, endLine: index + 2 }] }));
  const next: ValidationReport = { ...first, issues: { ...first.issues, page: 1, content: [{ ...first.issues.content[0], code: 'FUTURE_SAFE_CODE' }] } };
  fetchMock.mockResolvedValueOnce(json(first)).mockResolvedValueOnce(json(next)); render(page(saved()));
  await screen.findByText('Có dữ liệu sạch sử dụng được');
  fireEvent.click(screen.getByRole('button', { name: 'Trang thông báo tiếp' }));
  expect(await screen.findByText('FUTURE_SAFE_CODE')).toBeInTheDocument();
  expect(screen.getByText(/Trang 2 \/ 2/)).toBeInTheDocument();
  expect(fetchMock.mock.calls[1][0]).toContain('page=1&size=20');
  expect(fetchMock.mock.calls.every(call => call[1].method === 'GET')).toBe(true);
});

it('keeps large position groups bounded and explicitly shows the displayed range', async () => {
  const report = reportData(); report.counts.issueCount = '1'; report.issues.totalElements = '1';
  report.issues.content = [{ ...report.issues.content[0], positions: Array.from({ length: 12 }, (_, index) =>
    ({ recordNumber: index + 2, startLine: index + 2, endLine: index + 2 })) }];
  fetchMock.mockResolvedValue(json(report)); render(page(saved()));
  await screen.findByText('12 vị trí; đang xem 1–5');
  expect(screen.queryByText('Bản ghi 7, dòng 7')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Vị trí tiếp cho thông báo 1' }));
  expect(screen.getByText('12 vị trí; đang xem 6–10')).toBeInTheDocument();
  expect(screen.getByText('Bản ghi 7, dòng 7')).toBeInTheDocument();
  expect(screen.queryByText('Bản ghi 2, dòng 2')).not.toBeInTheDocument();
});

it('requires GET after ambiguous POST, retains that requirement across failed GET, and never retries automatically', async () => {
  fetchMock.mockResolvedValueOnce(failure(503)).mockResolvedValueOnce(failure(503)).mockResolvedValueOnce(failure(409));
  render(page()); fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }));
  await screen.findByText(/Máy chủ có thể đã lưu báo cáo/);
  expect(screen.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true })).toBeDisabled();
  expect(fetchMock).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra lại trạng thái' }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Kiểm tra lại trạng thái' })).not.toBeDisabled());
  expect(screen.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra lại trạng thái' }));
  await screen.findByText(/Có thể đang xử lý/);
  expect(screen.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true })).not.toBeDisabled();
  expect(fetchMock.mock.calls.map(call => call[1].method)).toEqual(['POST', 'GET', 'GET']);
  expect(screen.queryByText('private-server-value')).not.toBeInTheDocument();
});

it('recovers a committed report by GET after network failure, without a second POST', async () => {
  fetchMock.mockRejectedValueOnce(new Error('private-network-detail')).mockResolvedValueOnce(json(reportData()));
  render(page()); fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }));
  await screen.findByText(/Kết nối bị gián đoạn/);
  fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra lại trạng thái' }));
  await screen.findByText('Có dữ liệu sạch sử dụng được');
  expect(fetchMock.mock.calls.map(call => call[1].method)).toEqual(['POST', 'GET']);
  expect(complete).toHaveBeenCalledTimes(1); // recovered report also refreshes previously PENDING metadata
  expect(screen.queryByText('private-network-detail')).not.toBeInTheDocument();
});

it.each([400, 401, 403, 404])('renders safe HTTP %s errors without exposing server text', async status => {
  fetchMock.mockResolvedValue(failure(status)); render(page(saved()));
  expect(await screen.findByRole('alert')).toHaveTextContent('Mã yêu cầu: req-1');
  expect(screen.queryByText('private-server-value')).not.toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it('rejects mismatched report binding and does not refresh metadata or render its result', async () => {
  fetchMock.mockResolvedValue(json({ ...reportData(), rawSha256: 'b'.repeat(64) })); render(page());
  fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }));
  await screen.findByText(/Phản hồi báo cáo không đúng contract/);
  expect(screen.queryByText('Có dữ liệu sạch sử dụng được')).not.toBeInTheDocument();
  expect(complete).not.toHaveBeenCalled();
});

it('aborts on unmount and does not treat browser cancellation as server cancellation', async () => {
  let finish!: (value: Response) => void;
  fetchMock.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  const view = render(page()); fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }));
  const signal = fetchMock.mock.calls[0][1].signal as AbortSignal;
  expect(screen.getByText(/ngừng chờ không hủy xử lý trên máy chủ/)).toBeInTheDocument();
  view.unmount(); expect(signal.aborted).toBe(true);
  await act(async () => { finish(json(reportData())); });
  expect(complete).not.toHaveBeenCalled();
});

it('ignores late results for another snapshot and clears cached reports on identity change', async () => {
  let finish!: (value: Response) => void;
  fetchMock.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const view = render(page()); fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }));
  const other = { ...pendingDetail(), snapshotId: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee' };
  const signal = fetchMock.mock.calls[0][1].signal as AbortSignal;
  view.rerender(page(other)); expect(signal.aborted).toBe(true);
  await act(async () => { finish(json(reportData())); });
  expect(screen.queryByText('Có dữ liệu sạch sử dụng được')).not.toBeInTheDocument();
  fetchMock.mockResolvedValue(json({ ...reportData(), snapshotId: other.snapshotId }));
  fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra lại trạng thái' }));
  await screen.findByText('Có dữ liệu sạch sử dụng được');
  session.abort(); identity.mockReturnValue({ user: null, status: 'anonymous', forbidden: false });
  view.rerender(page(other));
  const panel = within(screen.getByRole('region', { name: 'Kiểm tra dữ liệu' }));
  expect(panel.queryByText('Có dữ liệu sạch sử dụng được')).not.toBeInTheDocument();
});
