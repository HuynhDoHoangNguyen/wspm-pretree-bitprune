import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { authSession } from 'auth/session';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { QueryClient, QueryClientProvider, setLogger } from 'react-query';
import MiningRunHistory from './MiningRunHistory';
import { getMiningRun, listMiningRuns, MiningRunApiError } from './MiningRun.api';
import { MiningRunDetail, MiningRunPage, MiningRunSummary } from './MiningRun.types';

jest.mock('./MiningRun.api', () => ({
  ...jest.requireActual('./MiningRun.api'),
  listMiningRuns: jest.fn(),
  getMiningRun: jest.fn(),
}));

const list = listMiningRuns as jest.MockedFunction<typeof listMiningRuns>;
const detail = getMiningRun as jest.MockedFunction<typeof getMiningRun>;
const clients: QueryClient[] = [];
const run: MiningRunSummary = {
  runId: 'run-101', algorithm: 'WSPM_PRETREE', status: 'SUCCEEDED', datasetSource: 'UPLOAD',
  snapshotId: 'snapshot-1', minsupMode: 'ABSOLUTE', minsupConfigured: 1, effectiveThreshold: 1,
  patternCount: 0, candidateCount: null, totalExecutionMs: 0, peakSampledJvmHeapBytes: null,
  createdAt: '2026-11-03T23:59:59.123456Z', startedAt: null, finishedAt: null,
};
const page: MiningRunPage = { items: [run], page: 0, size: 20, totalElements: 21, totalPages: 2 };
const runDetail: MiningRunDetail = {
  ...run, minsupRelativeFraction: null, maxW: null, minW: null, meanW: null,
  datasetIoMs: null, preprocessingMs: 0, dfsMiningMs: null, completeAlgorithmMs: null,
  outputIoMs: null, errorMessage: null, sequenceCount: null, distinctItemCount: null,
  validationStatus: null,
};

beforeAll(() => {
  setLogger({ log: () => undefined, warn: () => undefined, error: () => undefined });
  // jsdom has no layout observer; retain the real Mantine components under test.
  Object.defineProperty(window, 'ResizeObserver', {
    configurable: true,
    value: class {
      observe = () => undefined;
      unobserve = () => undefined;
      disconnect = () => undefined;
    },
  });
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (query: string) => ({
      matches: false, media: query, onchange: null,
      addListener: jest.fn(), removeListener: jest.fn(),
      addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn(),
    }),
  });
});
afterAll(() => { setLogger(console); });
beforeEach(() => {
  authSession.unauthorized();
  authSession.useStore.setState({ status: 'authenticated', forbidden: false, error: null, accessToken: 'test-access', user: {
    userId: 'admin-1', username: 'fixture', email: null, displayName: 'Admin', status: 'ACTIVE', roles: ['ADMIN'], emailVerified: false,
  } });
  list.mockReset();
  detail.mockReset();
  list.mockResolvedValue(page);
  detail.mockResolvedValue(runDetail);
});
afterEach(() => { clients.forEach(client => client.clear()); clients.length = 0; });

function show() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, cacheTime: 0 } },
  });
  clients.push(client);
  return render(
    <QueryClientProvider client={client}>
      <MantineProvider><MemoryRouter><MiningRunHistory/></MemoryRouter></MantineProvider>
    </QueryClientProvider>
  );
}

test('renders loading before data arrives', () => {
  list.mockReturnValue(new Promise(() => undefined));
  show();
  expect(screen.getByText('Đang tải lịch sử RUN…')).toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
});

test('renders empty results and no page buttons', async () => {
  list.mockResolvedValue({ ...page, items: [], totalElements: 0, totalPages: 0 });
  show();
  expect(await screen.findByText('Không có RUN phù hợp.')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '2' })).not.toBeInTheDocument();
});

test('keeps UTC microseconds and zero measurements in the list', async () => {
  show();
  const table = await screen.findByRole('table', { name: 'Lịch sử RUN' });
  expect(within(table).getByText(run.createdAt)).toBeInTheDocument();
  expect(within(table).getAllByText('0')).toHaveLength(2);
  expect(screen.getByText('Tổng: 21 RUN')).toBeInTheDocument();
});

test('uses zero-based pages and resets the page when filters or page size change', async () => {
  list.mockImplementation(async params => ({ ...page, page: params.page, size: params.size }));
  show();
  fireEvent.click(await screen.findByRole('button', { name: '2' }));
  await waitFor(() => expect(list.mock.calls[list.mock.calls.length - 1][0].page).toBe(1));
  fireEvent.change(screen.getByLabelText('Thuật toán'), { target: { value: 'WSPM_BITPRUNE' } });
  await waitFor(() => expect(list.mock.calls[list.mock.calls.length - 1][0]).toEqual({
    page: 0, size: 20, algorithm: 'WSPM_BITPRUNE', status: '',
  }));
  fireEvent.change(screen.getByLabelText('Trạng thái'), { target: { value: 'LEGACY_UNVERIFIED' } });
  await waitFor(() => expect(list.mock.calls[list.mock.calls.length - 1][0].status).toBe('LEGACY_UNVERIFIED'));
  fireEvent.change(screen.getByLabelText('Số RUN mỗi trang'), { target: { value: '50' } });
  await waitFor(() => expect(list.mock.calls[list.mock.calls.length - 1][0].size).toBe(50));
  fireEvent.click(screen.getByRole('button', { name: 'Xóa bộ lọc' }));
  await waitFor(() => expect(screen.getByLabelText('Thuật toán')).toHaveValue(''));
  expect(screen.getByLabelText('Trạng thái')).toHaveValue('');
  expect(screen.getByLabelText('Số RUN mỗi trang')).toHaveValue('20');
});

test('shows a safe error and supports an explicit retry', async () => {
  list.mockRejectedValueOnce(new Error('private diagnostic content'));
  show();
  expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tải dữ liệu');
  expect(screen.queryByText('private diagnostic content')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByRole('table', { name: 'Lịch sử RUN' })).toBeInTheDocument();
});

test('refresh calls the Admin adapter with current filters/page and reports loading then success', async () => {
  list.mockImplementation(async params => ({ ...page, page: params.page }));
  show();
  await screen.findByRole('table', { name: 'Lịch sử RUN' });
  fireEvent.change(screen.getByLabelText('Thuật toán'), { target: { value: 'WSPM_BITPRUNE' } });
  await waitFor(() => expect(list).toHaveBeenLastCalledWith(
    { page: 0, size: 20, algorithm: 'WSPM_BITPRUNE', status: '' }, expect.anything()
  ));
  fireEvent.change(screen.getByLabelText('Trạng thái'), { target: { value: 'SUCCEEDED' } });
  await waitFor(() => expect(list.mock.calls[list.mock.calls.length - 1][0].status).toBe('SUCCEEDED'));
  fireEvent.click(screen.getByRole('button', { name: '2' }));
  await waitFor(() => expect(list.mock.calls[list.mock.calls.length - 1][0].page).toBe(1));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Làm mới' })).toBeEnabled());
  const calls = list.mock.calls.length;
  let complete: (value: MiningRunPage) => void = () => undefined;
  list.mockReturnValueOnce(new Promise(resolve => { complete = resolve; }));
  fireEvent.click(screen.getByRole('button', { name: 'Làm mới' }));
  expect(list).toHaveBeenCalledTimes(calls + 1);
  expect(list).toHaveBeenLastCalledWith(
    { page: 1, size: 20, algorithm: 'WSPM_BITPRUNE', status: 'SUCCEEDED' }, expect.anything()
  );
  expect(await screen.findByText('Đang tải lịch sử RUN…')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Làm mới' })).toBeDisabled();
  expect(screen.queryByText('Đã làm mới danh sách')).not.toBeInTheDocument();
  await act(async () => { complete({ ...page, page: 1 }); });
  expect(await screen.findByText('Đã làm mới danh sách')).toBeInTheDocument();
  expect(screen.getByLabelText('Thuật toán')).toHaveValue('WSPM_BITPRUNE');
  expect(screen.getByLabelText('Trạng thái')).toHaveValue('SUCCEEDED');
  expect(screen.getByRole('table', { name: 'Lịch sử RUN' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Xem RUN run-101' }));
  expect(await screen.findByRole('table', { name: 'Chi tiết RUN' })).toBeInTheDocument();
});

test('refresh failures use existing error/retry without announcing success', async () => {
  show();
  await screen.findByRole('table', { name: 'Lịch sử RUN' });
  list.mockRejectedValueOnce(new Error('private refresh diagnostic'));
  fireEvent.click(screen.getByRole('button', { name: 'Làm mới' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tải dữ liệu');
  expect(screen.queryByText('Đã làm mới danh sách')).not.toBeInTheDocument();
  expect(screen.queryByText('private refresh diagnostic')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByRole('table', { name: 'Lịch sử RUN' })).toBeInTheDocument();
});

test.each([401, 403, 400])('handles HTTP %s on the list', async status => {
  list.mockRejectedValue(new MiningRunApiError(status));
  show();
  expect(await screen.findByRole('alert')).toBeInTheDocument();
  expect(list).toHaveBeenCalledTimes(1);
});

test('loads detail on demand and distinguishes null from zero without deriving metrics', async () => {
  show();
  fireEvent.click(await screen.findByRole('button', { name: 'Xem RUN run-101' }));
  const table = await screen.findByRole('table', { name: 'Chi tiết RUN' });
  expect(detail.mock.calls[0][0]).toBe('run-101');
  expect(within(table).getByRole('row', { name: 'Số candidate Chưa có dữ liệu' })).toBeInTheDocument();
  expect(within(table).getByRole('row', { name: 'Tiền xử lý (ms) 0' })).toBeInTheDocument();
  expect(within(table).getByRole('row', { name: 'Đỉnh JVM heap lấy mẫu (bytes) Chưa có dữ liệu' })).toBeInTheDocument();
});

test('handles detail loading and a missing RUN', async () => {
  let rejectDetail: (reason: Error) => void = () => undefined;
  detail.mockReturnValue(new Promise((resolve, reject) => { rejectDetail = reject; }));
  show();
  fireEvent.click(await screen.findByRole('button', { name: 'Xem RUN run-101' }));
  expect(await screen.findByText('Đang tải chi tiết RUN…')).toBeInTheDocument();
  rejectDetail(new MiningRunApiError(404));
  expect(await screen.findByText('Không tìm thấy RUN. Dữ liệu có thể đã thay đổi.')).toBeInTheDocument();
});

test('offers recovery when the current page becomes empty', async () => {
  list.mockImplementation(async params => params.page === 0 ? page : {
    ...page, items: [], page: 1, totalPages: 1, totalElements: 1,
  });
  show();
  fireEvent.click(await screen.findByRole('button', { name: '2' }));
  expect(await screen.findByText('Không có RUN phù hợp.')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Về trang đầu' }));
  expect(await screen.findByRole('table', { name: 'Lịch sử RUN' })).toBeInTheDocument();
});

test('does not replace a filtered result with a late response for the previous filter', async () => {
  let finishOld: (value: MiningRunPage) => void = () => undefined;
  list.mockReturnValueOnce(new Promise(resolve => { finishOld = resolve; }));
  list.mockResolvedValue({ ...page, items: [{ ...run, runId: 'filtered-run', algorithm: 'WSPM_BITPRUNE' }] });
  show();
  fireEvent.change(screen.getByLabelText('Thuật toán'), { target: { value: 'WSPM_BITPRUNE' } });
  expect(await screen.findByText('filtered-run')).toBeInTheDocument();
  await act(async () => { finishOld(page); });
  expect(screen.queryByText('run-101')).not.toBeInTheDocument();
  expect(screen.getByText('filtered-run')).toBeInTheDocument();
});

test.each(['anonymous', 'bootstrapping', 'signing-out', 'unavailable'] as const)('does not query while session is %s', status => {
  authSession.useStore.setState({ status, user: null, accessToken: null });
  show();
  expect(list).not.toHaveBeenCalled();
  expect(detail).not.toHaveBeenCalled();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
});

test('does not query for a customer or forbidden admin', () => {
  const user = authSession.useStore.getState().user;
  if (!user) throw new Error('Missing test identity');
  authSession.useStore.setState({ user: { ...user, roles: ['CUSTOMER'] } });
  const view = show();
  expect(list).not.toHaveBeenCalled();
  expect(screen.getByRole('alert')).toBeInTheDocument();
  act(() => { authSession.useStore.setState({ user, forbidden: true }); });
  expect(list).not.toHaveBeenCalled();
  view.unmount();
});

test('clears list/detail cache immediately on logout and never shows the previous admin result after relogin', async () => {
  const view = show();
  await screen.findByRole('table', { name: 'Lịch sử RUN' });
  fireEvent.click(screen.getByRole('button', { name: 'Xem RUN run-101' }));
  await screen.findByRole('table', { name: 'Chi tiết RUN' });
  act(() => { authSession.unauthorized(); });
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  expect(clients[0].getQueryCache().findAll(['mining-runs'])).toHaveLength(0);
  list.mockReturnValueOnce(new Promise(() => undefined));
  act(() => { authSession.useStore.setState({ status: 'authenticated', accessToken: 'second-access', user: {
    userId: 'admin-1', username: 'fixture', email: null, displayName: 'Admin', status: 'ACTIVE', roles: ['ADMIN'], emailVerified: false,
  } }); });
  expect(screen.queryByText('run-101')).not.toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  view.unmount();
});

test('keeps another admin identity isolated and ignores late data after session invalidation', async () => {
  let finish: (value: MiningRunPage) => void = () => undefined;
  list.mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
  show();
  act(() => { authSession.unauthorized(); });
  list.mockResolvedValue({ ...page, items: [{ ...run, runId: 'second-admin-run' }] });
  act(() => { authSession.useStore.setState({ status: 'authenticated', accessToken: 'new-access', user: {
    userId: 'admin-2', username: 'other', email: null, displayName: 'Other', status: 'ACTIVE', roles: ['ADMIN'], emailVerified: false,
  } }); });
  expect(await screen.findByText('second-admin-run')).toBeInTheDocument();
  await act(async () => { finish(page); });
  expect(screen.queryByText('run-101')).not.toBeInTheDocument();
});
