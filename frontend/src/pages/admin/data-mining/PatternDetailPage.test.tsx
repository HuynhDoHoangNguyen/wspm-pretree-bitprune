import React from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useNavigate, useLocation } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import { runResultApi, WdmApiError } from 'api/admin/data-mining';
import { PatternDetail, readPatternDetail } from 'api/admin/data-mining/run-results';
import { detailFixture, detailRunId as run, detailPatternId as id } from 'api/admin/data-mining/pattern-detail-test-data';
import PatternDetailPage from './PatternDetailPage';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
const identity = useSharedAuth as unknown as jest.Mock;
function Controls() {
  const navigate = useNavigate(), location = useLocation();
  return <><button onClick={() => navigate('/admin/patterns/' + run + '/1')}>Other pattern</button>
    <button onClick={() => navigate('/admin/patterns/' + run.replace(/1$/, '2') + '/' + id)}>Other run</button>
    <output aria-label="URL">{location.pathname + location.search}</output><output aria-label="state">{JSON.stringify(location.state)}</output></>;
}
function App({ path = '/admin/patterns/' + run + '/' + id + '?page=3' }: { path?: string }) {
  return <MemoryRouter initialEntries={[{ pathname: path.split('?')[0], search: path.includes('?') ? '?' + path.split('?')[1] : '',
    state: { runReturnTo: { returnTo: '/admin/mining-runs?tab=history&page=4' } } }]}><Controls/><Routes>
      <Route path="/admin/patterns/:runId/:patternId" element={<PatternDetailPage/>}/>
      <Route path="/admin/patterns" element={<p>Explorer destination</p>}/>
    </Routes></MemoryRouter>;
}
beforeEach(() => {
  identity.mockReturnValue({ status: 'authenticated', accessToken: 'token', user: { userId: 'admin', roles: ['ADMIN'] }, forbidden: false });
  Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) });
});
afterEach(async () => { await act(async () => {}); jest.restoreAllMocks(); });
test('direct route renders structure, repeated snapshot items, exact counts and historical provenance', async () => {
  jest.spyOn(runResultApi, 'detail').mockResolvedValue(detailFixture()); render(<App/>);
  expect(screen.getByText('Đang tải chi tiết pattern…')).toBeVisible();
  await screen.findByRole('region', { name: 'Cấu trúc temporal' });
  expect(screen.getAllByText('Historical product 1')).toHaveLength(2);
  expect(screen.getAllByText('9007199254740993')).toHaveLength(4);
  expect(screen.getByText(id)).toBeVisible(); expect(screen.getByText('<(1 2)(1)(3)>')).toBeVisible();
  expect(screen.getByText('3 itemset · 4 item')).toBeVisible();
  expect(within(screen.getByRole('region', { name: 'Itemset 2' })).getByText('OLD-SKU-1')).toBeVisible();
  expect(screen.getByRole('region', { name: 'Provenance lịch sử' })).toBeVisible();
  expect(screen.getByText('b'.repeat(64))).toBeVisible(); expect(screen.getByText(/chưa đủ điều kiện bàn giao Combo/)).toBeVisible();
  expect(runResultApi.detail).toHaveBeenCalledWith(run, id, expect.any(AbortSignal), 'staging');
  fireEvent.click(screen.getByRole('link', { name: 'Về Pattern Explorer' }));
  expect(screen.getByLabelText('URL')).toHaveTextContent('runId=' + run + '&page=3');
  expect(screen.getByLabelText('state')).toHaveTextContent('page=4');
});
test.each(['bad/1', run + '/0', run + '/18446744073709551616'])('invalid IDs %s do not request', async ids => {
  const read = jest.spyOn(runResultApi, 'detail'); render(<App path={'/admin/patterns/' + ids}/>);
  expect(screen.getByText(/Run ID hoặc Pattern ID chưa hợp lệ/)).toBeVisible(); expect(read).not.toHaveBeenCalled();
});
test.each([401, 403, 404, 409, 413, 503, null])('safe error %s clears detail and retries', async status => {
  jest.spyOn(runResultApi, 'detail').mockRejectedValueOnce(new WdmApiError(status ? 'http' : 'network', status, 'SAFE', 'SQL /secret', 'req-detail'))
    .mockResolvedValueOnce(detailFixture());
  render(<App/>); await screen.findByText('Mã yêu cầu: req-detail'); expect(screen.queryByText(/SQL \/secret/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' })); await screen.findByRole('region', { name: 'Cấu trúc temporal' });
});
test('refresh failure clears old pattern and provenance', async () => {
  jest.spyOn(runResultApi, 'detail').mockResolvedValueOnce(detailFixture()).mockRejectedValueOnce(new WdmApiError('response', 200, 'INVALID', 'secret'));
  render(<App/>); await screen.findByText('<(1 2)(1)(3)>'); fireEvent.click(screen.getByText('Làm mới chi tiết'));
  await screen.findByText(/Phản hồi của máy chủ/); expect(screen.queryByRole('region', { name: 'Cấu trúc temporal' })).not.toBeInTheDocument();
  expect(screen.queryByRole('region', { name: 'Provenance lịch sử' })).not.toBeInTheDocument();
});
test.each(['Other pattern', 'Other run'])('%s aborts old request and ignores late response', async action => {
  let finish!: (data: PatternDetail) => void;
  const read = jest.spyOn(runResultApi, 'detail').mockImplementationOnce(() => new Promise(done => { finish = done; }))
    .mockRejectedValueOnce(new WdmApiError('http', 404, 'NOT_FOUND', 'secret'));
  render(<App/>); fireEvent.click(screen.getByText(action)); await screen.findByText(/Pattern không tồn tại/);
  expect(read.mock.calls[0][2]?.aborted).toBe(true);
  await act(async () => { finish(detailFixture()); }); expect(screen.queryByText('<(1 2)(1)(3)>')).not.toBeInTheDocument();
});
test.each(['logout', 'role', 'token', 'identity', 'forbidden'])('session %s clears detail immediately', async change => {
  const read = jest.spyOn(runResultApi, 'detail').mockResolvedValueOnce(detailFixture()).mockImplementation(() => new Promise(() => undefined));
  const view = render(<App/>); await screen.findByText('<(1 2)(1)(3)>');
  identity.mockReturnValue(change === 'logout' ? { status: 'anonymous' } : { status: 'authenticated', accessToken: change === 'token' ? 'new' : 'token',
    user: { userId: change === 'identity' ? 'other' : 'admin', roles: change === 'role' ? ['CUSTOMER'] : ['ADMIN'] }, forbidden: change === 'forbidden' });
  view.rerender(<App/>); expect(screen.queryByText('<(1 2)(1)(3)>')).not.toBeInTheDocument(); expect(read.mock.calls[0][2]?.aborted).toBe(true);
});
test('unmount aborts pending read', () => {
  const read = jest.spyOn(runResultApi, 'detail').mockImplementation(() => new Promise(() => undefined));
  const view = render(<App/>); view.unmount(); expect(read.mock.calls[0][2]?.aborted).toBe(true);
});
test('large structure has explicit itemset/item pages and long canonical preview with complete copy', async () => {
  const data = detailFixture(), base = data.itemsets[0].items[0];
  data.itemsets = Array.from({ length: 11 }, (_, n) => ({ itemsetOrder: n + 1, items: n === 0
    ? Array.from({ length: 151 }, (_, i) => ({ ...base, itemOrder: i + 1, itemId: i + 1, productNameSnapshot: 'Historical item ' + (i + 1) }))
    : [{ ...base }] }));
  data.itemsets.slice(1).forEach(set => { set.items = [{ ...data.itemsets[0].items[1], itemOrder: 1 }]; });
  data.pattern.canonicalPattern = '<' + data.itemsets.map(set => '(' + set.items.map(i => i.itemId).join(' ') + ')').join('') + '>';
  data.pattern.itemsetCount = 11; data.pattern.patternItemCount = 161;
  data.result.storedPatternItemCount = '161'; data.result.storedRunItemCount = '151';
  expect(readPatternDetail(data, run, id)).toEqual(data);
  const writeText = jest.fn().mockResolvedValue(undefined); Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
  jest.spyOn(runResultApi, 'detail').mockResolvedValue(data); render(<App/>);
  await screen.findByRole('region', { name: 'Itemset 1' });
  expect(screen.queryByRole('region', { name: 'Itemset 11' })).not.toBeInTheDocument(); expect(screen.queryByText('Historical item 51')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Item tiếp' })); expect(screen.getByText('Historical item 51')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'Item trước' })); expect(screen.getByText('Historical item 1')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'Itemset tiếp' })); expect(screen.getByRole('region', { name: 'Itemset 11' })).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'Itemset trước' })); expect(screen.getByRole('region', { name: 'Itemset 1' })).toBeVisible();
  expect(screen.getByText(/bản xem trước/)).toBeVisible(); fireEvent.click(screen.getByRole('button', { name: 'Sao chép Canonical pattern' }));
  await screen.findByText('Đã sao chép.'); expect(writeText).toHaveBeenCalledWith(data.pattern.canonicalPattern);
});
