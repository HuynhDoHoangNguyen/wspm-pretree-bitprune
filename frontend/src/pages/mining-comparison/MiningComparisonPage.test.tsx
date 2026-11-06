import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { getMiningRun, listMiningRuns } from '../mining-run/MiningRun.api';
import type { MiningRunDetail } from '../mining-run/MiningRun.types';
import MiningComparison from './MiningComparison';
import MiningComparisonPage from './MiningComparisonPage';

jest.mock('../mining-run/MiningRun.api', () => ({ listMiningRuns: jest.fn(), getMiningRun: jest.fn() }));
const list = listMiningRuns as jest.MockedFunction<typeof listMiningRuns>;
const detail = getMiningRun as jest.MockedFunction<typeof getMiningRun>;
const resizeObserver = Object.getOwnPropertyDescriptor(window, 'ResizeObserver');
beforeAll(() => Object.defineProperty(window, 'ResizeObserver', {
  configurable: true,
  value: class { observe() { return undefined; } unobserve() { return undefined; } disconnect() { return undefined; } },
}));
afterAll(() => {
  if (resizeObserver) Object.defineProperty(window, 'ResizeObserver', resizeObserver);
  else Reflect.deleteProperty(window, 'ResizeObserver');
});
function run(runId: string, algorithm: MiningRunDetail['algorithm']): MiningRunDetail {
  return {
    runId, algorithm, status: 'SUCCEEDED', datasetSource: 'ORDERS', snapshotId: 'same-snapshot',
    minsupMode: 'ABSOLUTE', minsupConfigured: 1, effectiveThreshold: 1, minsupRelativeFraction: null,
    patternCount: 12, candidateCount: 30, totalExecutionMs: 120, peakSampledJvmHeapBytes: 2048,
    createdAt: '2026-11-06T00:00:00Z', startedAt: '2026-11-06T00:00:01Z', finishedAt: '2026-11-06T00:00:02Z',
    maxW: 1, minW: .1, meanW: .5, datasetIoMs: 10, preprocessingMs: 20, dfsMiningMs: 50,
    completeAlgorithmMs: 90, outputIoMs: null, errorMessage: null, sequenceCount: 100,
    distinctItemCount: 20, validationStatus: 'VALID',
  };
}
const a = run('api-a', 'WSPM_PRETREE'), b = run('api-b', 'WSPM_BITPRUNE'), b2 = run('api-b2', 'WSPM_BITPRUNE');
const page = (items: MiningRunDetail[], index = 0, totalPages = 1) => ({ items, page: index, size: 100, totalElements: items.length, totalPages });
beforeEach(() => {
  list.mockReset(); detail.mockReset(); list.mockResolvedValue(page([]));
  detail.mockImplementation(async id => [a, b, b2].find(r => r.runId === id) as MiningRunDetail);
});
function show() { return render(<MiningComparisonPage/>); }

test('comparison production loads the existing API and renders empty without details or fixtures', async () => {
  show(); expect(screen.getByRole('status')).toHaveTextContent('Đang tải RUN so sánh');
  expect(await screen.findByText('Chưa có RUN để so sánh.')).toBeInTheDocument();
  expect(list).toHaveBeenCalledWith({ page: 0, size: 100, algorithm: '', status: '' }, expect.any(AbortSignal));
  expect(detail).not.toHaveBeenCalled();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  expect(screen.queryByText(/Dữ liệu minh họa/)).not.toBeInTheDocument();
});

test('comparison production includes later list pages and fetches details only for the selected pair', async () => {
  list.mockResolvedValueOnce(page([a], 0, 2)).mockResolvedValueOnce(page([b, b2], 1, 2));
  show();
  const table = await screen.findByRole('table');
  expect(list).toHaveBeenCalledTimes(2);
  expect(list.mock.calls[1][0].page).toBe(1);
  expect(detail.mock.calls.map(([id]) => id)).toEqual(['api-a', 'api-b']);
  expect(screen.getByLabelText('RUN B — WSPM_BitPrune')).toHaveValue('api-b');
  expect(within(table).getByRole('row', { name: /Tổng thực thi/ })).toHaveTextContent('120120');
  expect(within(table).getByRole('row', { name: /Đỉnh JVM heap lấy mẫu/ })).toHaveTextContent('2.0482.048');
  expect(within(table).getByRole('row', { name: /Dataset I\/O/ })).toHaveTextContent('1010');
  expect(within(table).getByRole('row', { name: /Output I\/O/ })).toHaveTextContent('Chưa có dữ liệu');
  expect(screen.getByText('Chưa đủ dữ liệu để so sánh hợp lệ')).toBeInTheDocument();
  expect(screen.queryByText(/Speedup|Runtime Reduction|Memory Reduction/)).not.toBeInTheDocument();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
});

test('comparison production clears the previous pair, aborts stale detail and reads the new selection', async () => {
  list.mockResolvedValue(page([a, b, b2]));
  let finish: (value: MiningRunDetail) => void = () => undefined;
  detail.mockImplementation(id => id === b.runId ? new Promise(resolve => { finish = resolve; }) : Promise.resolve(a.runId === id ? a : b2));
  show();
  await waitFor(() => expect(detail).toHaveBeenCalledTimes(2));
  const signal = detail.mock.calls[0][1];
  fireEvent.change(screen.getByLabelText('RUN B — WSPM_BitPrune'), { target: { value: 'api-b2' } });
  expect(await screen.findByRole('table')).toBeInTheDocument();
  expect(signal?.aborted).toBe(true);
  expect(detail).toHaveBeenCalledWith('api-b2', expect.any(AbortSignal));
  await act(async () => { finish({ ...b, totalExecutionMs: 999999 }); });
  expect(screen.queryByText(/999.999/)).not.toBeInTheDocument();
  expect(screen.getByLabelText('RUN B — WSPM_BitPrune')).toHaveValue('api-b2');
});

test('comparison production list errors retry the shared API and do not fallback to fixtures', async () => {
  list.mockRejectedValueOnce(new Error('Unavailable')); show();
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByText('Chưa có RUN để so sánh.')).toBeInTheDocument();
  expect(list).toHaveBeenCalledTimes(2);
});

test('comparison production detail errors retry the selected pair without refetching lists', async () => {
  list.mockResolvedValue(page([a, b])); detail.mockRejectedValueOnce(new Error('Unavailable')); show();
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại chi tiết' }));
  expect(await screen.findByRole('table')).toBeInTheDocument();
  expect(list).toHaveBeenCalledTimes(1);
  expect(detail).toHaveBeenCalledTimes(4);
});

test('comparison production never treats injected PreviewPairCheck as verified evidence', async () => {
  render(<MiningComparison source={{ load: async () => ({ runs: [a, b], previewChecks: [{
    origin: 'PREVIEW_ONLY', preTreeRunId: a.runId, bitPruneRunId: b.runId,
    configurationVerified: true, measurementConditionsVerified: true, fullPatternSupportMatch: true,
  }] }) }}/>);
  expect(await screen.findByText('Chưa đủ dữ liệu để so sánh hợp lệ')).toBeInTheDocument();
  expect(screen.getByText(/toàn bộ cấu hình mining; DTO chưa có K/)).toBeInTheDocument();
  expect(screen.getByText(/Chưa xác nhận cùng điều kiện đo/)).toBeInTheDocument();
  expect(screen.getByText(/Chưa xác nhận toàn bộ pattern/)).toBeInTheDocument();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.queryByText(/Speedup|Runtime Reduction|Memory Reduction/)).not.toBeInTheDocument();
});

test.each([
  [{ snapshotId: 'other' }, /Dataset\/snapshot không trùng/],
  [{ effectiveThreshold: 2 }, /Minsup cấu hình\/ngưỡng hiệu lực khác nhau/],
  [{ patternCount: 13 }, /Số pattern khác nhau/],
  [{ status: 'RUNNING' }, /Cả hai RUN phải hoàn thành/],
] as const)('comparison production rejects incompatible metadata %j', async (changes, reason) => {
  list.mockResolvedValue(page([a, b])); detail.mockImplementation(async id => id === a.runId ? a : { ...b, ...changes });
  show();
  expect(await screen.findByText(reason)).toBeInTheDocument();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.queryByText(/Speedup|Runtime Reduction|Memory Reduction/)).not.toBeInTheDocument();
});

test('comparison production with only one algorithm explains the missing pair without requesting details', async () => {
  list.mockResolvedValue(page([a])); show();
  expect(await screen.findByText(/Không có RUN phù hợp:/)).toBeInTheDocument();
  expect(detail).not.toHaveBeenCalled();
});
