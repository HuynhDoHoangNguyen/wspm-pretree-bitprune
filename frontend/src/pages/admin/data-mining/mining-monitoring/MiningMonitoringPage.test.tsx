import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { getMiningRun, listMiningRuns } from '../../../../api/admin/data-mining/history/MiningRun.api';
import type { MiningRunDetail, MiningRunPage } from '../../../../api/admin/data-mining/history/MiningRun.types';
import MiningMonitoringPage from './MiningMonitoringPage';
import { authSession } from 'auth/session';
import { MiningRunApiError } from '../../../../api/admin/data-mining/history/MiningRun.api';
import { setWdmAuthAdapter } from 'api/admin/data-mining';

jest.mock('../../../../api/admin/data-mining/history/MiningRun.api', () => ({
  ...jest.requireActual('../../../../api/admin/data-mining/history/MiningRun.api'), listMiningRuns: jest.fn(), getMiningRun: jest.fn(),
}));
const list = listMiningRuns as jest.MockedFunction<typeof listMiningRuns>;
const detail = getMiningRun as jest.MockedFunction<typeof getMiningRun>;
const run: MiningRunDetail = {
  runId: 'api-run', algorithm: 'WSPM_PRETREE', status: 'SUCCEEDED', datasetSource: 'ORDERS', snapshotId: 'snapshot',
  minsupMode: 'ABSOLUTE', minsupConfigured: 1, effectiveThreshold: 1, patternCount: 12, candidateCount: 30,
  totalExecutionMs: 120, peakSampledJvmHeapBytes: 2048, createdAt: '2026-11-06T00:00:00Z',
  startedAt: '2026-11-06T00:00:01Z', finishedAt: '2026-11-06T00:00:02Z', minsupRelativeFraction: null,
  maxW: null, minW: null, meanW: null, datasetIoMs: null, preprocessingMs: null, dfsMiningMs: null,
  completeAlgorithmMs: null, outputIoMs: null, errorMessage: null, sequenceCount: null, distinctItemCount: null,
  validationStatus: null,
};
const page = (items: MiningRunDetail[] = []): MiningRunPage => ({ items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 });
function show() { return render(<MemoryRouter><MiningMonitoringPage/></MemoryRouter>); }
const admin = { userId: 'admin-1', username: 'unit', email: null, displayName: 'Admin', status: 'ACTIVE' as const,
  roles: ['ADMIN'], emailVerified: false };
beforeEach(() => {
  authSession.unauthorized();
  authSession.useStore.setState({ status: 'authenticated', user: admin, accessToken: 'test', forbidden: false, error: null });
  list.mockReset(); detail.mockReset(); list.mockResolvedValue(page()); detail.mockResolvedValue(run);
});
afterEach(() => { jest.useRealTimers(); });

test('production Monitoring loads the shared list API and shows actual empty data without fixtures', async () => {
  show();
  expect(screen.getByRole('button', { name: 'Làm mới' })).toBeDisabled();
  expect(screen.getByRole('status')).toHaveTextContent('Đang tải danh sách RUN');
  expect(await screen.findByText('Chưa có RUN để theo dõi.')).toBeInTheDocument();
  expect(list).toHaveBeenCalledWith({ page: 0, size: 20, algorithm: '', status: '' }, expect.any(AbortSignal));
  expect(detail).not.toHaveBeenCalled();
  expect(screen.queryByText(/Dữ liệu minh họa/)).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Xem lịch sử RUN' })).toHaveAttribute('href', '/admin/mining-run-history');
});

test('production Monitoring shows API detail, timestamps and provided metrics without inventing absent values', async () => {
  list.mockResolvedValue(page([run])); show();
  expect(await screen.findByText('Tổng thực thi cuối cùng: 120 ms')).toBeInTheDocument();
  expect(screen.getByText('Số pattern: 12')).toBeInTheDocument();
  expect(screen.getByText('Đỉnh JVM heap lấy mẫu: 2.048 bytes')).toBeInTheDocument();
  expect(screen.getAllByText(/2026-11-06/).length).toBeGreaterThan(0);
  expect(detail).toHaveBeenCalledWith('api-run', expect.any(AbortSignal));
  expect(screen.getAllByText('Chưa có dữ liệu').length).toBeGreaterThan(0);
});

test('manual refresh blocks repeated clicks and never polls or refetches on focus', async () => {
  show(); await screen.findByText('Chưa có RUN để theo dõi.');
  jest.useFakeTimers();
  act(() => { jest.advanceTimersByTime(300000); window.dispatchEvent(new Event('focus')); });
  expect(list).toHaveBeenCalledTimes(1);
  jest.useRealTimers();
  let finish: (value: MiningRunPage) => void = () => undefined;
  list.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const refresh = screen.getByRole('button', { name: 'Làm mới' });
  fireEvent.click(refresh); fireEvent.click(refresh); fireEvent.click(refresh);
  expect(refresh).toBeDisabled();
  await waitFor(() => expect(list).toHaveBeenCalledTimes(2));
  expect(screen.queryByText('Chưa có RUN để theo dõi.')).not.toBeInTheDocument();
  await act(async () => { finish(page()); });
  expect(refresh).toBeEnabled();
  expect(list).toHaveBeenCalledTimes(2);
});

test('list failure retries through the same API and recovers to empty', async () => {
  list.mockRejectedValueOnce(new Error('Unavailable')); show();
  expect(await screen.findByText('Không thể tải danh sách RUN')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByText('Chưa có RUN để theo dõi.')).toBeInTheDocument();
  expect(list).toHaveBeenCalledTimes(2);
});

test('detail failure can retry without reloading the list', async () => {
  list.mockResolvedValue(page([run])); detail.mockRejectedValueOnce(new Error('Unavailable')); show();
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại chi tiết' }));
  expect(await screen.findByText('Số pattern: 12')).toBeInTheDocument();
  expect(list).toHaveBeenCalledTimes(1);
  expect(detail).toHaveBeenCalledTimes(2);
});

test('refresh aborts pending detail and reloads current API status instead of displaying a late response', async () => {
  let finish: (value: MiningRunDetail) => void = () => undefined;
  const running = { ...run, status: 'RUNNING' as const, finishedAt: null };
  list.mockResolvedValueOnce(page([running])).mockResolvedValue(page([run]));
  detail.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  show(); await waitFor(() => expect(detail).toHaveBeenCalledTimes(1));
  const signal = detail.mock.calls[0][1];
  fireEvent.click(screen.getByRole('button', { name: 'Làm mới' }));
  expect(await screen.findByText('Số pattern: 12')).toBeInTheDocument();
  expect(signal?.aborted).toBe(true);
  await act(async () => { finish(running); });
  expect(screen.queryByLabelText('Mining đang chạy')).not.toBeInTheDocument();
  expect(list).toHaveBeenCalledTimes(2);
  expect(detail).toHaveBeenCalledTimes(2);
});

test('missing selected RUN has a safe recovery state and can retry detail', async () => {
  list.mockResolvedValue(page([run]));
  detail.mockRejectedValueOnce(new MiningRunApiError(404));
  show();
  expect(await screen.findByText('Không tìm thấy RUN')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại chi tiết' }));
  expect(await screen.findByText('Số pattern: 12')).toBeVisible();
  expect(list).toHaveBeenCalledTimes(1);
});

test('changing ADMIN identity clears completed detail and ignores a previous pending list', async () => {
  list.mockResolvedValueOnce(page([run]));
  const view = show();
  await screen.findByText('Số pattern: 12');
  let finish!: (value: MiningRunPage) => void;
  list.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  fireEvent.click(screen.getByRole('button', { name: 'Làm mới' }));
  await waitFor(() => expect(list).toHaveBeenCalledTimes(2));
  const oldSignal = list.mock.calls[1][1];
  act(() => authSession.useStore.setState({ user: { ...admin, userId: 'admin-2' } }));
  expect(oldSignal?.aborted).toBe(true);
  expect(await screen.findByText('Chưa có RUN để theo dõi.')).toBeVisible();
  await act(async () => finish(page([run])));
  expect(screen.queryByText('Số pattern: 12')).not.toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  view.unmount();
});

test('logout removes private data, aborts pending detail and same-user relogin loads afresh', async () => {
  list.mockResolvedValueOnce(page([run]));
  let finish!: (value: MiningRunDetail) => void;
  detail.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  show();
  await waitFor(() => expect(detail).toHaveBeenCalledTimes(1));
  const oldSignal = detail.mock.calls[0][1];
  act(() => authSession.unauthorized());
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  expect(oldSignal?.aborted).toBe(true);
  act(() => authSession.useStore.setState({ status: 'authenticated', user: admin, accessToken: 'new-token', error: null }));
  await screen.findByText('Chưa có RUN để theo dõi.');
  await act(async () => finish(run));
  expect(screen.queryByText('Số pattern: 12')).not.toBeInTheDocument();
});

test('batched session clear and same-user relogin cannot reuse completed private state', async () => {
  list.mockResolvedValueOnce(page([run]));
  show();
  await screen.findByText('Số pattern: 12');
  act(() => {
    authSession.unauthorized();
    authSession.useStore.setState({ status: 'authenticated', user: admin, accessToken: 'new-token', error: null });
  });
  expect(screen.queryByText('Số pattern: 12')).not.toBeInTheDocument();
  expect(await screen.findByText('Chưa có RUN để theo dõi.')).toBeVisible();
});

test('unmount aborts the pending list and ignores its eventual result', async () => {
  let finish!: (value: MiningRunPage) => void;
  list.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const view = show();
  await waitFor(() => expect(list).toHaveBeenCalledTimes(1));
  const signal = list.mock.calls[0][1];
  view.unmount();
  expect(signal?.aborted).toBe(true);
  await act(async () => finish(page([run])));
  expect(detail).not.toHaveBeenCalled();
});

test('production adapter reuses the actual RUN client and WDM transport without a second envelope parser', async () => {
  const originalFetch = global.fetch;
  const fetcher = jest.fn().mockResolvedValue({
    ok: true, status: 200, body: null,
    headers: { get: (name: string) => name.toLowerCase() === 'content-type' ? 'application/json' : name === 'X-Request-Id' ? 'monitor-request' : null },
    text: async () => JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', requestId: 'monitor-request', data: page() }),
  } as unknown as Response);
  global.fetch = fetcher;
  setWdmAuthAdapter({ getAccessToken: authSession.token, getSessionSignal: authSession.signal,
    onUnauthorized: authSession.unauthorized, onForbidden: authSession.forbidden });
  const actual = jest.requireActual('../../../../api/admin/data-mining/history/MiningRun.api') as typeof import('../../../../api/admin/data-mining/history/MiningRun.api');
  list.mockImplementation(actual.listMiningRuns);
  try {
    show();
    expect(await screen.findByText('Chưa có RUN để theo dõi.')).toBeVisible();
    expect(fetcher).toHaveBeenCalledWith('/api/v1/admin/mining-run-history?page=0&size=20', expect.objectContaining({
      method: 'GET', signal: expect.any(AbortSignal),
    }));
    expect((fetcher.mock.calls[0][1].headers as Headers).get('Authorization')).toBe('Bearer test');
  } finally {
    global.fetch = originalFetch;
    setWdmAuthAdapter(null);
  }
});
