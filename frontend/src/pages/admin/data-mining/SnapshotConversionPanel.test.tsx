import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { useSharedAuth } from 'auth/session';
import { conversionApi, SnapshotDetail, ValidationReport, WdmApiError } from 'api/admin/data-mining';
import { pendingDetail, reportData } from 'api/admin/data-mining/validation-test-data';
import { conversionData } from 'api/admin/data-mining/conversion-test-data';
import { downloadDatasetFile } from './downloadDatasetFile';
import SnapshotConversionPanel from './SnapshotConversionPanel';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
jest.mock('./downloadDatasetFile', () => ({ downloadDatasetFile: jest.fn() }));
const identity = useSharedAuth as unknown as jest.Mock;
const complete = jest.fn<Promise<void>, [AbortSignal]>(() => Promise.resolve());
let read: jest.SpyInstance, start: jest.SpyInstance, preview: jest.SpyInstance, download: jest.SpyInstance;
const detail = (): SnapshotDetail => ({ ...pendingDetail(), validationStatus: 'VALID' });
function page(d = detail(), report: ValidationReport | null = reportData()) {
  return <MantineProvider><SnapshotConversionPanel detail={d} validationReport={report} onComplete={complete}/></MantineProvider>;
}
const button = () => screen.getByRole('button', { name: 'Chuyển đổi dữ liệu', exact: true });
beforeEach(() => {
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false,
    addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() })) });
  identity.mockReturnValue({ user: { userId: 'admin-1' }, status: 'authenticated', forbidden: false });
  complete.mockClear(); (downloadDatasetFile as jest.Mock).mockClear();
  read = jest.spyOn(conversionApi, 'read').mockResolvedValue(conversionData('NOT_STARTED'));
  start = jest.spyOn(conversionApi, 'start').mockResolvedValue(conversionData());
  preview = jest.spyOn(conversionApi, 'preview').mockResolvedValue({ text: '1 -1 -2\n', truncated: false });
  download = jest.spyOn(conversionApi, 'download').mockResolvedValue({ blob: new Blob(['data']), filename: 'test.spmf',
    contentType: 'text/plain', contentDisposition: null, requestId: 'req-1' });
});
afterEach(() => { jest.restoreAllMocks(); jest.useRealTimers(); });
it('only GETs on mount; explicit POST locks immediately and refreshes verified result/metadata', async () => {
  let finish!: (value: ReturnType<typeof conversionData>) => void;
  start.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  render(page()); await waitFor(() => expect(button()).toBeEnabled()); expect(start).not.toHaveBeenCalled();
  fireEvent.click(button()); fireEvent.click(button()); expect(start).toHaveBeenCalledTimes(1);
  expect(button()).toBeDisabled(); read.mockResolvedValue(conversionData());
  await act(async () => finish(conversionData()));
  expect(await screen.findByText('Thống kê sequence')).toBeInTheDocument();
  expect(read).toHaveBeenCalledTimes(2); expect(complete).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('button', { name: 'Chuyển đổi dữ liệu', exact: true })).not.toBeInTheDocument();
});
it.each(['missing', 'invalid', 'incomplete', 'wrong-id', 'wrong-sha', 'wrong-size', 'not-ready', 'metadata-pending'])(
  'requires a current bound validation report (%s)', async reason => {
    const report = reportData(), d = detail();
    if (reason === 'invalid') report.validationStatus = 'INVALID';
    if (reason === 'incomplete') report.countsComplete = false;
    if (reason === 'wrong-id') report.snapshotId = 'another';
    if (reason === 'wrong-sha') report.rawSha256 = 'b'.repeat(64);
    if (reason === 'wrong-size') report.rawSizeBytes = '1';
    if (reason === 'not-ready') report.cleaningOutcome = 'EMPTY_INPUT';
    if (reason === 'metadata-pending') d.validationStatus = 'PENDING';
    render(page(d, reason === 'missing' ? null : report)); await screen.findByText('Trạng thái conversion: NOT_STARTED');
    expect(button()).toBeDisabled(); fireEvent.click(button()); expect(start).not.toHaveBeenCalled();
  });
it('recovers an uncertain POST with GET, never replays POST', async () => {
  render(page()); await waitFor(() => expect(button()).toBeEnabled());
  start.mockRejectedValue(new WdmApiError('network', null, 'NETWORK_ERROR', 'secret'));
  read.mockResolvedValue(conversionData()); fireEvent.click(button());
  await screen.findByText('Thống kê sequence'); expect(start).toHaveBeenCalledTimes(1); expect(read).toHaveBeenCalledTimes(2);
  expect(screen.queryByText('secret')).not.toBeInTheDocument(); expect(complete).toHaveBeenCalledTimes(1);
});
it('keeps a safe error/request ID after unresolved POST and permits only GET reconciliation', async () => {
  render(page()); await waitFor(() => expect(button()).toBeEnabled());
  start.mockRejectedValue(new WdmApiError('http', 503, 'STORAGE_UNAVAILABLE', 'secret path', 'request-safe'));
  read.mockRejectedValue(new WdmApiError('network', null, 'NETWORK_ERROR', 'secret'));
  fireEvent.click(button()); await screen.findByText('Mã yêu cầu: request-safe');
  expect(screen.queryByText(/secret/)).not.toBeInTheDocument();
  read.mockResolvedValue(conversionData('NOT_STARTED'));
  fireEvent.click(screen.getByRole('button', { name: 'Đọc lại trạng thái conversion' }));
  await screen.findByText('Trạng thái conversion: NOT_STARTED'); expect(button()).toBeDisabled(); expect(start).toHaveBeenCalledTimes(1);
});
it('displays FAILED safely without offering retry', async () => {
  read.mockResolvedValue(conversionData('FAILED')); render(page());
  await screen.findByText(/Chuyển đổi thất bại: CONVERSION_FAILED/);
  expect(screen.queryByRole('button', { name: 'Chuyển đổi dữ liệu', exact: true })).not.toBeInTheDocument();
});
it.each([400, 401, 403, 404, 409, 503])('shows safe HTTP %s and request ID without enabling a POST', async status => {
  read.mockRejectedValue(new WdmApiError('http', status, 'ERROR', 'private-server-path', 'req-safe'));
  render(page()); await screen.findByText('Mã yêu cầu: req-safe');
  expect(screen.queryByText('private-server-path')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Chuyển đổi dữ liệu', exact: true })).not.toBeInTheDocument();
  expect(start).not.toHaveBeenCalled();
});
it('does not deliver bytes when an artifact response fails verification', async () => {
  read.mockResolvedValue(conversionData()); download.mockRejectedValue(new WdmApiError('response', 200, 'INVALID_CONVERSION_ARTIFACT', 'private'));
  render(page()); await screen.findByText('Thống kê sequence');
  fireEvent.click(screen.getByRole('button', { name: 'Tải SPMF' })); await screen.findByRole('alert');
  expect(downloadDatasetFile).not.toHaveBeenCalled(); expect(screen.queryByText('private')).not.toBeInTheDocument();
});
it('renders nine server statistics including exact large counts, hashes and downloads', async () => {
  const report = conversionData(); report.statistics.sequenceCount = '9007199254740993';
  read.mockResolvedValue(report); render(page()); await screen.findByText('Thống kê sequence');
  expect(within(screen.getByRole('group', { name: 'Số sequence' })).getByText('9007199254740993')).toBeInTheDocument();
  expect(screen.getByText(/SHA-256 do máy chủ cung cấp: bbbb/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Tải SPMF' }));
  await waitFor(() => expect(downloadDatasetFile).toHaveBeenCalledTimes(1));
  expect(download.mock.calls[0][1]).toBe('sequence');
  fireEvent.click(screen.getByRole('button', { name: 'Tải mapping JSON' }));
  await waitFor(() => expect(downloadDatasetFile).toHaveBeenCalledTimes(2)); expect(download.mock.calls[1][1]).toBe('mapping');
});
it('previews only on demand, escapes content and labels partial output', async () => {
  read.mockResolvedValue(conversionData()); preview.mockResolvedValue({ text: '<script>1 -1 -2</script>', truncated: true });
  render(page()); await screen.findByText('Thống kê sequence'); expect(preview).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Xem trước SPMF' }));
  await screen.findByText(/Bản xem trước một phần/);
  const content = screen.getByLabelText('Nội dung xem trước SPMF');
  expect(content).toHaveTextContent('<script>1 -1 -2</script>'); expect(content).not.toContainHTML('<script>');
});
it('disables large-file preview while allowing downloads', async () => {
  const report = conversionData(); report.sequence.sizeBytes = '1048577'; read.mockResolvedValue(report); render(page());
  await screen.findByText('Thống kê sequence'); expect(screen.getByRole('button', { name: 'Xem trước SPMF' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Tải SPMF' })).toBeEnabled();
});
it('aborts on unmount/snapshot change and ignores a stale download', async () => {
  read.mockResolvedValue(conversionData()); const view = render(page()); await screen.findByText('Thống kê sequence');
  let finish!: (value: unknown) => void; download.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  fireEvent.click(screen.getByRole('button', { name: 'Tải SPMF' })); const signal = download.mock.calls[0][3] as AbortSignal;
  const next = { ...detail(), snapshotId: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee' };
  read.mockImplementation(() => new Promise(() => undefined)); view.rerender(page(next));
  expect(signal.aborted).toBe(true);
  await act(async () => finish({ blob: new Blob(), filename: 'stale.spmf' })); expect(downloadDatasetFile).not.toHaveBeenCalled();
  view.unmount(); expect((read.mock.calls[1][1] as AbortSignal).aborted).toBe(true);
});
it('aborts on logout and ignores a stale preview', async () => {
  read.mockResolvedValue(conversionData()); const view = render(page()); await screen.findByText('Thống kê sequence');
  let finish!: (value: unknown) => void; preview.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  fireEvent.click(screen.getByRole('button', { name: 'Xem trước SPMF' })); const signal = preview.mock.calls[0][2] as AbortSignal;
  identity.mockReturnValue({ user: null, status: 'anonymous', forbidden: false }); view.rerender(page());
  expect(signal.aborted).toBe(true); await act(async () => finish({ text: 'STALE', truncated: false }));
  expect(screen.queryByText('STALE')).not.toBeInTheDocument(); expect(start).not.toHaveBeenCalled();
});
it('polls RUNNING with nonoverlapping GETs and stops at 60 seconds until manual refresh', async () => {
  jest.useFakeTimers('modern'); read.mockResolvedValue(conversionData('RUNNING')); render(page());
  await act(async () => undefined); expect(read).toHaveBeenCalledTimes(1);
  expect(screen.getByLabelText('Đang chuyển đổi dữ liệu')).toBeInTheDocument();
  let finish!: (value: unknown) => void; read.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  await act(async () => { jest.advanceTimersByTime(2000); }); expect(read).toHaveBeenCalledTimes(2);
  await act(async () => { jest.advanceTimersByTime(10000); }); expect(read).toHaveBeenCalledTimes(2);
  await act(async () => finish(conversionData('RUNNING')));
  for (let tick = 0; tick < 24; tick++) await act(async () => { jest.advanceTimersByTime(2000); });
  expect(screen.getByText(/Đã ngừng tự đọc trạng thái/)).toBeInTheDocument();
  const count = read.mock.calls.length; await act(async () => { jest.advanceTimersByTime(10000); }); expect(read).toHaveBeenCalledTimes(count);
  fireEvent.click(screen.getByRole('button', { name: 'Đọc lại trạng thái conversion' })); await act(async () => undefined);
  expect(read).toHaveBeenCalledTimes(count + 1); expect(start).not.toHaveBeenCalled();
});
