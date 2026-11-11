import React from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import { miningRunApi, WdmApiError } from 'api/admin/data-mining';
import { runId, runStatus, measuredRun } from 'api/admin/data-mining/mining-run-test-data';
import MiningRunDetailPage from './MiningRunDetailPage';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
beforeEach(() => {
  Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) });
  (useSharedAuth as unknown as jest.Mock).mockReturnValue({ status: 'authenticated', user: { userId: 'admin', roles: ['ADMIN'] }, forbidden: false });
});
afterEach(() => jest.restoreAllMocks());
function page(id = runId) { return <MemoryRouter initialEntries={['/admin/mining-runs/' + id]}><Routes>
  <Route path="/admin/mining-runs/:runId" element={<MiningRunDetailPage/>}/></Routes></MemoryRouter>; }
it('direct route reads once, copies a known URL and never creates', async () => {
  const get = jest.spyOn(miningRunApi, 'read').mockResolvedValue(runStatus('SUCCEEDED')), post = jest.spyOn(miningRunApi, 'create');
  render(page()); expect(await screen.findByText(/hoàn tất với 0 patterns/)).toBeVisible();
  expect(screen.getByRole('button', { name: 'Sao chép link lần chạy' })).toBeVisible(); expect(get).toHaveBeenCalledTimes(1); expect(post).not.toHaveBeenCalled();
});
it('groups readable update time and dataset navigation without rendering a long raw URL', async () => {
  const report = runStatus('SUCCEEDED'); jest.spyOn(miningRunApi, 'read').mockResolvedValue(report);
  render(page()); await screen.findByText(/hoàn tất với 0 patterns/);
  const summary = screen.getByRole('region', { name: 'Tổng quan lần chạy' });
  expect(within(summary).getByRole('heading', { name: 'Chi tiết lần chạy khai phá' })).toBeVisible();
  expect(within(summary).getByText(runId)).toBeVisible();
  expect(screen.queryByText(window.location.origin + '/admin/mining-runs/' + runId)).not.toBeInTheDocument();
  const time = within(summary).getByLabelText('Thời điểm cập nhật');
  expect(time.getAttribute('datetime')).toMatch(/Z$/); expect(time).not.toHaveTextContent('T');
  expect(within(summary).getByText('(UTC+07)')).toBeVisible();
  const related = screen.getByRole('region', { name: 'Dữ liệu liên quan' });
  expect(within(related).getByRole('link', { name: 'Dataset nguồn' })).toHaveAttribute('href', '/admin/datasets/' + report.snapshotId);
  expect(within(related).getByRole('link', { name: 'Các run cùng dataset' })).toHaveAttribute('href', '/admin/mining-runs?tab=history&snapshotId=' + report.snapshotId);
});
it('manual GET error preserves last snapshot with pause label and requestId', async () => {
  jest.spyOn(miningRunApi, 'read').mockResolvedValueOnce(runStatus('SUCCEEDED')).mockRejectedValueOnce(new WdmApiError('http', 503, 'SERVICE_UNAVAILABLE', 'SQL private', 'req-1'));
  render(page()); await screen.findByText(/hoàn tất với 0 patterns/); fireEvent.click(screen.getByRole('button', { name: /Đọc lại/ }));
  expect(await screen.findByText(/Theo dõi đã tạm dừng/)).toBeVisible(); expect(screen.getByText(/hoàn tất với 0 patterns/)).toBeVisible();
  expect(screen.getByText(/req-1/)).toBeVisible(); expect(screen.queryByText(/SQL/)).not.toBeInTheDocument();
});
it('invalid direct ID never reaches API', async () => {
  const get = jest.spyOn(miningRunApi, 'read'); render(page('invalid')); await act(async () => { await Promise.resolve(); });
  expect(get).not.toHaveBeenCalled(); expect(screen.getByRole('button', { name: /Đọc lại/ })).toBeDisabled();
});
it('uses one coherent report for status and all metrics, retaining both after failed manual GET', async () => {
  const get = jest.spyOn(miningRunApi, 'read').mockResolvedValueOnce(measuredRun('WSPM_BITPRUNE'))
    .mockRejectedValueOnce(new WdmApiError('http', 503, 'SERVICE_UNAVAILABLE', 'private', 'metrics-request'));
  render(page()); await screen.findByText('18.446.744.073.709.551.615');
  expect(screen.getByText('WSPM_BITPRUNE')).toBeVisible(); expect(screen.getByText('Extensions đã sinh WSPM_BitPrune')).toBeVisible();
  expect(get).toHaveBeenCalledTimes(1); expect(within(screen.getByRole('region', { name: 'Số đo lần chạy' })).getAllByRole('group')).toHaveLength(9);
  fireEvent.click(screen.getByRole('button', { name: /Đọc lại/ })); await screen.findByText(/Theo dõi đã tạm dừng/);
  expect(screen.getByText('18.446.744.073.709.551.615')).toBeVisible(); expect(screen.getByText(/hoàn tất với 0 patterns/)).toBeVisible();
  expect(get).toHaveBeenCalledTimes(2); expect(within(screen.getByRole('group', { name: 'Tổng scope worker' })).getByText('10')).toHaveAttribute('value', '10');
});
it('clears the protected status and metrics together after identity loss', async () => {
  const get = jest.spyOn(miningRunApi, 'read').mockResolvedValue(measuredRun()); const view = render(page());
  await screen.findByText('18.446.744.073.709.551.615');
  (useSharedAuth as unknown as jest.Mock).mockReturnValue({ status: 'anonymous', user: null }); view.rerender(page());
  await act(async () => { await Promise.resolve(); });
  expect(screen.queryByText('Số đo lần chạy')).not.toBeInTheDocument(); expect(screen.queryByText('Trạng thái lần chạy')).not.toBeInTheDocument();
  expect(get).toHaveBeenCalledTimes(1);
});
