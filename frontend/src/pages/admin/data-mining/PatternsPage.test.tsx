import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import { runResultApi, WdmApiError } from 'api/admin/data-mining';
import { PatternPage } from 'api/admin/data-mining/run-results';
import PatternsPage from './PatternsPage';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
const id = '10000000-0000-4000-8000-000000000001', other = id.replace(/1$/, '2');
const identity = useSharedAuth as unknown as jest.Mock;
function pageData(page = 1, total = '2', runId = id): PatternPage {
  const n = Math.max(0, Math.min(20, Number(total) - (page - 1) * 20));
  return { page, size: 20, total, result: { runId, executionStatus: 'SUCCEEDED', publicationStatus: 'NOT_PUBLISHED',
    manifestVersion: null, resultSha256: null, committedAt: null,
    provenance: { version: 'RUN_PROVENANCE_V1', conversionFormatVersion: 'CONVERSION_V1', snapshotId: id, attemptId: id,
      validationSha256: 'a'.repeat(64), sequenceSha256: 'b'.repeat(64), mappingSha256: 'c'.repeat(64), sequenceCount: '4' }, storedPatternCount: total,
    storedPatternItemCount: '4', storedRunItemCount: '2', completeness: 'UNVERIFIED', stagingReadable: true,
    publishedReadable: false, eligibleForCombo: false }, items: Array.from({ length: n }, (_, i) => ({
    patternId: i === 0 ? '18446744073709551615' : String(i), runId, canonicalPattern: i === 0 ? '<(1)(2)>' : '<(1 2)>',
    canonicalPatternHash: 'a'.repeat(64), supportAbs: '3', supportRatio: 0.75, weightedSupport: 0.375,
    patternWeight: 0.5, patternWeightPolicy: 'EQ2_EQ3_ARITHMETIC_MEAN_V1', itemsetCount: i === 0 ? 2 : 1, patternItemCount: 2 })) };
}
function Controls() {
  const location = useLocation(), navigate = useNavigate();
  return <><output aria-label="URL">{location.pathname + location.search}</output>
    <button onClick={() => navigate(-1)}>Browser Back</button>
    <button onClick={() => navigate('/admin/patterns?runId=' + other)}>Other run</button>
    <output aria-label="route state">{JSON.stringify(location.state)}</output></>;
}
function App({ entry = '/admin/patterns?runId=' + id }: { entry?: string }) {
  return <MemoryRouter initialEntries={[{ pathname: entry.split('?')[0], search: entry.includes('?') ? '?' + entry.split('?')[1] : '',
    state: { runReturnTo: { returnTo: '/admin/mining-runs?tab=history&page=3' } } }]}><Controls/><PatternsPage/></MemoryRouter>;
}
beforeEach(() => {
  jest.spyOn(runResultApi, 'metadata').mockImplementation(async run => pageData(1, '2', run).result);
  identity.mockReturnValue({ status: 'authenticated', accessToken: 'token', user: { userId: 'admin', roles: ['ADMIN'] }, forbidden: false });
  Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) });
});
afterEach(() => { jest.restoreAllMocks(); });
test('auto view opens verified published results and preserves published paging', async () => {
  const published = pageData(1, '21');
  published.result = { ...published.result, publicationStatus: 'COMMITTED', completeness: 'COMPLETE_VERIFIED',
    stagingReadable: false, publishedReadable: true, eligibleForCombo: true,
    manifestVersion: 'WDM_RESULT_MANIFEST_V1', resultSha256: 'd'.repeat(64), committedAt: '2026-11-08T10:00:00Z' };
  jest.mocked(runResultApi.metadata).mockResolvedValue(published.result);
  const read = jest.spyOn(runResultApi, 'patterns').mockResolvedValue(published);
  render(<App/>);
  await screen.findByRole('table', { name: 'Pattern đã công bố' });
  expect(read).toHaveBeenCalledWith(id, 1, expect.any(AbortSignal), 'published');
  expect(read.mock.calls.some(call => call[3] === 'staging')).toBe(false);
  fireEvent.click(screen.getByRole('button', { name: 'Trang sau' }));
  expect(screen.getByLabelText('URL')).toHaveTextContent('view=published');
  await screen.findByRole('table', { name: 'Pattern đã công bố' });
  expect(read).toHaveBeenLastCalledWith(id, 2, expect.any(AbortSignal), 'published');
});
test('metadata failure does not fall back to a staging request', async () => {
  jest.mocked(runResultApi.metadata).mockRejectedValue(new WdmApiError('http', 409, 'CONFLICT', 'unavailable'));
  const read = jest.spyOn(runResultApi, 'patterns');
  render(<App/>);
  await screen.findByRole('alert');
  expect(read).not.toHaveBeenCalled();
});
test('renders canonical boundaries, exact ID, separate measures and staging warning', async () => {
  const read = jest.spyOn(runResultApi, 'patterns').mockResolvedValue(pageData());
  render(<App/>); expect(screen.getByText('Đang tải pattern…')).toBeVisible();
  await screen.findByRole('table', { name: 'Pattern staging' });
  expect(screen.getByText('<(1)(2)>')).toBeVisible(); expect(screen.getByText('<(1 2)>')).toBeVisible();
  expect(screen.getByText('ID: 18446744073709551615')).toBeVisible();
  expect(screen.getByText('2 itemset · 2 item')).toBeVisible();
  expect(screen.getByText(/chưa đủ điều kiện bàn giao Combo/)).toBeVisible();
  expect(read).toHaveBeenCalledWith(id, 1, expect.any(AbortSignal), 'staging');
  expect(screen.getByRole('button', { name: 'Trang sau' })).toBeDisabled();
});
test.each(['', '?runId=bad'])('missing or invalid run does not request; form validates then opens', async suffix => {
  const read = jest.spyOn(runResultApi, 'patterns').mockResolvedValue(pageData());
  render(<App entry={'/admin/patterns' + suffix}/>); expect(read).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Mở kết quả' })); expect(read).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('Run ID'), { target: { value: id } });
  fireEvent.click(screen.getByRole('button', { name: 'Mở kết quả' })); await screen.findByRole('table');
  expect(screen.getByLabelText('URL')).toHaveTextContent('runId=' + id + '&page=1');
});
test('server pagination, browser Back, out of range recovery and detail return state', async () => {
  const read = jest.spyOn(runResultApi, 'patterns').mockImplementation(async (run, page) => pageData(page, '21', run));
  render(<App/>); await screen.findByRole('table');
  expect(screen.getByRole('link', { name: 'Lịch sử run' })).toHaveAttribute('href', '/admin/mining-runs?tab=history&page=3');
  fireEvent.click(screen.getByRole('button', { name: 'Trang sau' })); await screen.findByText('Trang 2');
  expect(read).toHaveBeenLastCalledWith(id, 2, expect.any(AbortSignal), 'staging');
  expect(screen.getByLabelText('route state')).toHaveTextContent('page=3');
  fireEvent.click(screen.getByText('Browser Back')); await screen.findByText('Trang 1');
  fireEvent.click(screen.getByRole('link', { name: 'Chi tiết run' }));
  expect(screen.getByLabelText('URL')).toHaveTextContent('/admin/mining-runs/' + id);
  expect(screen.getByLabelText('route state')).toHaveTextContent('returnTo');
});
test.each(['0', '-1', 'abc', '100001', '99999999999999999999'])('invalid page %s falls back to 1', async page => {
  const read = jest.spyOn(runResultApi, 'patterns').mockResolvedValue(pageData());
  render(<App entry={'/admin/patterns?runId=' + id + '&page=' + page}/>); await screen.findByRole('table');
  expect(read).toHaveBeenCalledWith(id, 1, expect.any(AbortSignal), 'staging');
});
test.each([1, 3])('empty page %s offers honest empty wording and recovery', async page => {
  jest.spyOn(runResultApi, 'patterns').mockResolvedValue(pageData(page, '0'));
  render(<App entry={'/admin/patterns?runId=' + id + '&page=' + page}/>);
  await screen.findByText(page === 1 ? /Điều này không chứng minh engine/ : /Trang này không có pattern/);
  if (page > 1) { fireEvent.click(screen.getByRole('button', { name: 'Trang đầu' })); await screen.findByText('Trang 1'); }
});
test.each([401, 403, 404, 409, 503, null])('error %s hides diagnostics and supports retry', async status => {
  jest.spyOn(runResultApi, 'patterns').mockRejectedValueOnce(new WdmApiError(status ? 'http' : 'network', status, 'SAFE', 'SQL secret', 'req-patterns'))
    .mockResolvedValueOnce(pageData());
  render(<App/>); await screen.findByText('Mã yêu cầu: req-patterns');
  expect(screen.queryByText(/SQL secret/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' })); await screen.findByRole('table');
});
test('refresh failure removes old rows', async () => {
  jest.spyOn(runResultApi, 'patterns').mockResolvedValueOnce(pageData()).mockRejectedValueOnce(new WdmApiError('response', 200, 'INVALID', 'secret'));
  render(<App/>); await screen.findByRole('table'); fireEvent.click(screen.getByText('Làm mới danh sách'));
  await screen.findByText(/Phản hồi của máy chủ/); expect(screen.queryByRole('table')).not.toBeInTheDocument();
});
test('changing run aborts and ignores late response; form resets pagination', async () => {
  let resolve!: (value: PatternPage) => void;
  const read = jest.spyOn(runResultApi, 'patterns').mockImplementationOnce(() => new Promise(done => { resolve = done; }))
    .mockResolvedValueOnce(pageData(1, '0', other));
  render(<App entry={'/admin/patterns?runId=' + id + '&page=2'}/>);
  await waitFor(() => expect(read).toHaveBeenCalledTimes(1));
  fireEvent.change(screen.getByLabelText('Run ID'), { target: { value: other } });
  fireEvent.click(screen.getByText('Mở kết quả')); await screen.findByText(/Điều này không chứng minh engine/);
  expect(read.mock.calls[0][2]?.aborted).toBe(true);
  await act(async () => { resolve(pageData(2, '21')); }); expect(screen.queryByRole('table')).not.toBeInTheDocument();
  expect(read).toHaveBeenLastCalledWith(other, 1, expect.any(AbortSignal), 'staging');
});
test.each(['logout', 'forbidden', 'role', 'token'])('session %s clears rows and aborts request', async change => {
  const read = jest.spyOn(runResultApi, 'patterns').mockResolvedValueOnce(pageData()).mockImplementation(() => new Promise(() => undefined));
  const view = render(<App/>); await screen.findByRole('table');
  identity.mockReturnValue(change === 'logout' ? { status: 'anonymous' } : { status: 'authenticated', accessToken: 'new-token',
    user: { userId: 'admin', roles: change === 'role' ? ['CUSTOMER'] : ['ADMIN'] }, forbidden: change === 'forbidden' });
  view.rerender(<App/>); expect(screen.queryByRole('table')).not.toBeInTheDocument();
  expect(read.mock.calls[0][2]?.aborted).toBe(true);
});
test('unmount aborts request', async () => {
  const read = jest.spyOn(runResultApi, 'patterns').mockImplementation(() => new Promise(() => undefined));
  const view = render(<App/>); await waitFor(() => expect(read).toHaveBeenCalledTimes(1));
  view.unmount(); expect(read.mock.calls[0][2]?.aborted).toBe(true);
});
