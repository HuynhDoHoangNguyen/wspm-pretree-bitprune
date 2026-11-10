import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import { workspaceApi, WdmApiError } from 'api/admin/data-mining';
import { CapacityPanel, StoragePanel, ReadError } from './WorkspacePanels';
import WorkspaceList from './WorkspaceList';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
const id = '10000000-0000-4000-8000-000000000001', time = '2026-11-07T00:00:00Z';
const identity = useSharedAuth as unknown as jest.Mock;
beforeEach(() => {
  identity.mockReturnValue({ status: 'authenticated', accessToken: 'test-token', user: { userId: 'admin', roles: ['ADMIN'] }, forbidden: false });
  Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) });
  Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: jest.fn(() => ({ observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn() })) });
});
afterEach(() => jest.restoreAllMocks());
test('server read failure keeps HTTP/request ID and never blames permissions or leaks diagnostics', async () => {
  jest.spyOn(workspaceApi, 'capacity').mockRejectedValue(new WdmApiError('http', 500, 'INTERNAL_ERROR', 'private SQL /server/file', 'req-sort'));
  render(<MemoryRouter><CapacityPanel/></MemoryRouter>);
  await screen.findByText('HTTP 500'); expect(screen.getByText('Mã yêu cầu: req-sort')).toBeVisible();
  expect(screen.getByText(/Đây là lỗi phía backend/)).toBeVisible();
  expect(screen.queryByText(/private SQL/)).not.toBeInTheDocument(); expect(screen.queryByText(/Kiểm tra kết nối hoặc quyền/)).not.toBeInTheDocument();
});
test('invalid successful response is distinguished from HTTP/connection errors', () => {
  render(<ReadError retry={() => undefined} error={new WdmApiError('response', 200, 'INVALID_WORKSPACE_RESPONSE', 'private')}/>);
  expect(screen.getByText('Phản hồi của máy chủ chưa đúng định dạng được hỗ trợ.')).toBeVisible();
});
test('capacity shows retained completions as occupied, and identity loss clears them immediately', async () => {
  const read = jest.spyOn(workspaceApi, 'capacity').mockResolvedValue({ enabled: true, scope: 'CURRENT_BACKEND_INSTANCE', observedAt: time,
    snapshot: { capacity: 2, used: 2, available: 0, closing: false, observedAt: time, entries: [{ runId: id, state: 'COMPLETED' }, { runId: id.replace(/1$/, '2'), state: 'FAILED' }] } });
  const view = render(<MemoryRouter><CapacityPanel/></MemoryRouter>);
  await screen.findByText('Còn 0 slot'); expect(screen.getByText(/không phải số lần được lưu DB/)).toBeInTheDocument();
  fireEvent.click(screen.getByText('Run đang chiếm slot (2)')); expect(screen.getByText('Kết thúc · còn giữ kết quả')).toBeVisible();
  identity.mockReturnValue({ status: 'anonymous', user: null, accessToken: null });
  view.rerender(<MemoryRouter><CapacityPanel/></MemoryRouter>);
  expect(screen.queryByText('Còn 0 slot')).not.toBeInTheDocument(); expect(read).toHaveBeenCalledTimes(1);
});
test('history storage explicitly labels current publication and handles delayed replies after unmount', async () => {
  let resolve: (v: unknown) => void = () => undefined;
  const read = jest.spyOn(workspaceApi, 'storage').mockImplementation(() => new Promise(done => { resolve = done as (v: unknown) => void; }));
  const view = render(<StoragePanel snapshotId={id} historical/>);
  fireEvent.click(screen.getByText('Tệp dữ liệu trên backend'));
  expect(screen.getByText(/Chưa xác minh vị trí tệp lịch sử/)).toBeVisible();
  view.unmount(); expect(read.mock.calls[0][1]?.aborted).toBe(true);
  await act(async () => { resolve({ snapshotId: id, files: [] }); });
});
test('history query restores URL filter and pagination without creation requests', async () => {
  const read = jest.spyOn(workspaceApi, 'runs').mockResolvedValue({ items: [], page: 2, size: 20, total: 21 });
  render(<MemoryRouter initialEntries={['/admin/mining-runs?tab=history&page=2&search=abc&status=FAILED']}><WorkspaceList runs/></MemoryRouter>);
  await screen.findByText(/21 kết quả/);
  expect(read).toHaveBeenCalledWith({ page: '2', size: '20', search: 'abc', status: 'FAILED' }, expect.any(AbortSignal));
  expect(screen.getByLabelText('Tìm Run ID')).toHaveValue('abc');
});
test('legacy snapshot metadata renders without inventing executable UUID routes', async () => {
  jest.spyOn(workspaceApi, 'snapshots').mockResolvedValue({ items: [{ snapshotId: 'legacy-run-1', sourceName: 'old.csv', sourceType: 'LEGACY_PATH', validationStatus: 'LEGACY_UNVERIFIED',
    conversionStatus: null, sequenceCount: null, distinctItemCount: null, createdAt: time }], page: 1, size: 20, total: 1 });
  render(<MemoryRouter><WorkspaceList/></MemoryRouter>);
  await screen.findByText('old.csv'); expect(screen.getByText('Dữ liệu cũ chưa xác minh')).toBeVisible();
  expect(screen.queryByRole('link', { name: 'Xem chi tiết' })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'Cấu hình khai phá' })).not.toBeInTheDocument();
});
