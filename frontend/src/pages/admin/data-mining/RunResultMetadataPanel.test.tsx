import React from 'react';
import { act, fireEvent, render as rtlRender, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import { runResultApi, WdmApiError } from 'api/admin/data-mining';
import { RunResultMetadata } from 'api/admin/data-mining/run-results';
import RunResultMetadataPanel from './RunResultMetadataPanel';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
const render = (ui: React.ReactElement) => rtlRender(ui, { wrapper: MemoryRouter });
const id = '10000000-0000-4000-8000-000000000001', other = id.replace(/1$/, '2');
const identity = useSharedAuth as unknown as jest.Mock;
const metadata = (): RunResultMetadata => ({ runId: id, executionStatus: 'SUCCEEDED', publicationStatus: 'NOT_PUBLISHED',
  manifestVersion: null, resultSha256: null, committedAt: null,
  provenance: { version: 'RUN_PROVENANCE_V1', conversionFormatVersion: 'CONVERSION_V1', snapshotId: id, attemptId: id,
    validationSha256: 'a'.repeat(64), sequenceSha256: 'b'.repeat(64), mappingSha256: 'c'.repeat(64), sequenceCount: '4' },
  storedPatternCount: '9007199254740993', storedPatternItemCount: '18446744073709551615', storedRunItemCount: '3',
  completeness: 'UNVERIFIED', stagingReadable: true, publishedReadable: false, eligibleForCombo: false });
beforeEach(() => {
  identity.mockReturnValue({ status: 'authenticated', accessToken: 'token', user: { userId: 'admin', roles: ['ADMIN'] }, forbidden: false });
  Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) });
});
afterEach(() => jest.restoreAllMocks());
test('renders loading then exact stored counts and independent publication warning', async () => {
  jest.spyOn(runResultApi, 'metadata').mockResolvedValue(metadata());
  render(<RunResultMetadataPanel runId={id}/>);
  expect(screen.getByText('Đang đọc metadata kết quả…')).toBeVisible();
  await screen.findByText('9007199254740993'); expect(screen.getByText('18446744073709551615')).toBeVisible();
  expect(screen.getByText('Chưa công bố')).toBeVisible(); expect(screen.getByText(/Không dùng số hàng/)).toBeVisible();
  fireEvent.click(screen.getByText('Nguồn dữ liệu đã ghi nhận cho run'));
  expect(screen.getByRole('button', { name: 'Sao chép SHA-256 SPMF lịch sử' })).toBeVisible();
  expect(screen.queryByRole('link', { name: /Combo|patterns/i })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Xem pattern staging' })).toHaveAttribute('href', '/admin/patterns?runId=' + id + '&page=1');
});
test.each(['UNVERIFIED', 'COMMITTED'] as const)('does not portray %s as complete', async publicationStatus => {
  jest.spyOn(runResultApi, 'metadata').mockResolvedValue({ ...metadata(), publicationStatus, stagingReadable: false,
    provenance: publicationStatus === 'UNVERIFIED' ? null : metadata().provenance });
  render(<RunResultMetadataPanel runId={id}/>); await screen.findByText('9007199254740993');
  expect(screen.getByText('Tính đầy đủ: chưa xác minh · Chưa đủ điều kiện bàn giao Combo.')).toBeVisible();
  expect(screen.getByText(publicationStatus === 'UNVERIFIED' ? 'Lịch sử chưa xác minh' : 'Có marker công bố · chưa xác minh')).toBeVisible();
  expect(screen.queryByRole('link', { name: 'Xem pattern staging' })).not.toBeInTheDocument();
});
test('zero stored patterns never claims zero engine output', async () => {
  jest.spyOn(runResultApi, 'metadata').mockResolvedValue({ ...metadata(), storedPatternCount: '0', storedPatternItemCount: '0' });
  render(<RunResultMetadataPanel runId={id}/>); await screen.findByText('Chưa công bố');
  expect(screen.getByText(/Không dùng số hàng đã lưu để kết luận engine không tạo patterns/)).toBeVisible();
});
test.each([401, 403, 404, 409, 503])('handles HTTP %s safely and retries GET', async status => {
  const read = jest.spyOn(runResultApi, 'metadata').mockRejectedValueOnce(new WdmApiError('http', status, 'SAFE', 'SQL /private secret', 'req-results'))
    .mockResolvedValueOnce(metadata());
  render(<RunResultMetadataPanel runId={id}/>); await screen.findByText('Mã yêu cầu: req-results');
  expect(screen.queryByText(/SQL|private secret/)).not.toBeInTheDocument();
  expect(screen.getByRole('alert')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' })); await screen.findByText('9007199254740993'); expect(read).toHaveBeenCalledTimes(2);
});
test('refresh failure clears old metadata instead of suggesting fresh publication', async () => {
  jest.spyOn(runResultApi, 'metadata').mockResolvedValueOnce(metadata()).mockRejectedValueOnce(new WdmApiError('network', null, 'NETWORK_ERROR', 'private'));
  render(<RunResultMetadataPanel runId={id}/>); await screen.findByText('9007199254740993');
  fireEvent.click(screen.getByRole('button', { name: 'Làm mới kết quả lưu' })); await screen.findByRole('alert');
  expect(screen.queryByText('9007199254740993')).not.toBeInTheDocument();
});
test('navigation aborts old request and ignores late response', async () => {
  let resolve: (value: RunResultMetadata) => void = () => undefined;
  const read = jest.spyOn(runResultApi, 'metadata').mockImplementationOnce(() => new Promise(done => { resolve = done; }))
    .mockResolvedValueOnce({ ...metadata(), runId: other, storedPatternCount: '7' });
  const view = render(<RunResultMetadataPanel runId={id}/>); view.rerender(<RunResultMetadataPanel runId={other}/>);
  expect(read.mock.calls[0][1]?.aborted).toBe(true); await screen.findByText('7');
  await act(async () => { resolve(metadata()); }); expect(screen.queryByText('9007199254740993')).not.toBeInTheDocument();
});
test.each(['anonymous', 'forbidden', 'token-change'])('identity %s immediately clears protected data', async change => {
  const read = jest.spyOn(runResultApi, 'metadata').mockResolvedValueOnce(metadata()).mockImplementationOnce(() => new Promise(() => undefined));
  const view = render(<RunResultMetadataPanel runId={id}/>); await screen.findByText('9007199254740993');
  identity.mockReturnValue(change === 'anonymous' ? { status: 'anonymous', user: null } : { status: 'authenticated', accessToken: 'new-token', user: { userId: 'admin', roles: ['ADMIN'] }, forbidden: change === 'forbidden' });
  view.rerender(<RunResultMetadataPanel runId={id}/>); expect(screen.queryByText('9007199254740993')).not.toBeInTheDocument();
  expect(read.mock.calls[0][1]?.aborted).toBe(true);
});
test('invalid run ID never requests metadata', () => {
  const read = jest.spyOn(runResultApi, 'metadata'); render(<RunResultMetadataPanel runId="invalid"/>); expect(read).not.toHaveBeenCalled();
});

test('refreshes pending publication and reveals pattern navigation without a reload', async () => {
  jest.useFakeTimers();
  try {
    const published = { ...metadata(), publicationStatus: 'COMMITTED' as const, publishedReadable: true,
      stagingReadable: false, completeness: 'COMPLETE_VERIFIED' as const, eligibleForCombo: true,
      manifestVersion: 'WDM_RESULT_MANIFEST_V1', resultSha256: 'd'.repeat(64), committedAt: '2026-11-08T00:00:00Z' };
    const read = jest.spyOn(runResultApi, 'metadata').mockResolvedValueOnce(metadata()).mockResolvedValue(published);
    render(<RunResultMetadataPanel runId={id}/>);
    expect(await screen.findByText('Chưa công bố')).toBeVisible();
    await act(async () => { jest.advanceTimersByTime(2000); });
    expect(screen.getByRole('link', { name: 'Xem pattern đã công bố' })).toHaveAttribute('href', '/admin/patterns?runId=' + id + '&page=1&view=published');
    await act(async () => { jest.advanceTimersByTime(10000); });
    expect(read).toHaveBeenCalledTimes(2);
  } finally { jest.useRealTimers(); }
});
