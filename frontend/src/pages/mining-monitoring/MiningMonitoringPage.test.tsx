import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { getMiningRun, listMiningRuns } from '../mining-run/MiningRun.api';
import type { MiningRunDetail, MiningRunPage } from '../mining-run/MiningRun.types';
import MiningMonitoringPage from './MiningMonitoringPage';

jest.mock('../mining-run/MiningRun.api', () => ({ listMiningRuns: jest.fn(), getMiningRun: jest.fn() }));
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
beforeEach(() => { list.mockReset(); detail.mockReset(); list.mockResolvedValue(page()); detail.mockResolvedValue(run); });
afterEach(() => { jest.useRealTimers(); });

test('production Monitoring loads the shared list API and shows actual empty data without fixtures', async () => {
  show();
  expect(screen.getByRole('button', { name: 'Làm mới' })).toBeDisabled();
  expect(screen.getByRole('status')).toHaveTextContent('Đang tải danh sách RUN');
  expect(await screen.findByText('Chưa có RUN để theo dõi.')).toBeInTheDocument();
  expect(list).toHaveBeenCalledWith({ page: 0, size: 20, algorithm: '', status: '' }, expect.any(AbortSignal));
  expect(detail).not.toHaveBeenCalled();
  expect(screen.queryByText(/Dữ liệu minh họa/)).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Xem lịch sử RUN' })).toHaveAttribute('href', '/admin/mining-runs');
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
