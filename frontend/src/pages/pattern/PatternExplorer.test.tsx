import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import PatternExplorer from './PatternExplorer';
import { PatternClient, PatternClientError, PatternDetail, PatternPage } from './PatternClient';

const summary = { patternId: '18446744073709551615', runId: 'A', canonicalPattern: '<(1)(2)>',
  supportAbs: '9007199254740993', supportRatio: null, weightedSupport: null, patternWeight: .4,
  patternWeightPolicy: 'P', patternItemCount: 2, itemsetCount: 2 };
const page = (r = 'A', p = 0): PatternPage => ({ runId: r, runStatus: 'SUCCEEDED', resultState: 'READY',
  runPatternTotal: '21', filteredTotal: '21', page: p, size: 20, items: [{ ...summary, runId: r }] });
const mapped = { itemId: 1, itemOrder: 1, mappingStatus: 'MAPPED' as const,
  productId: 'p', nameSnapshot: 'Snapshot A', skuSnapshot: 'SKU-A', itemWeight: null };
const detail: PatternDetail = { ...summary, sequenceCount: '9007199254740995', integrityStatus: 'INCOMPLETE',
  issues: [{ code: 'MISSING_RUN_DICTIONARY_ITEM', itemsetOrder: 2, itemOrder: 1, itemId: 2 }],
  itemsets: [{ itemsetOrder: 1, items: [mapped] }, { itemsetOrder: 2, items: [{ ...mapped,
    itemId: 2, mappingStatus: 'MISSING', productId: null, nameSnapshot: null, skuSnapshot: null }] }] };
function client(): jest.Mocked<PatternClient> {
  return { runs: jest.fn().mockResolvedValue([{ runId: 'A', label: 'Run A' }, { runId: 'B', label: 'Run B' }]),
    dictionary: jest.fn().mockImplementation((r, q, p, s) => Promise.resolve({ runId: r, query: q, page: p, size: s,
      resultState: 'READY', dictionaryTotal: '0', matchedTotal: '0', items: [] })),
    list: jest.fn().mockImplementation((r, p) => Promise.resolve(page(r, p))), detail: jest.fn().mockResolvedValue(detail) };
}
async function choose() {
  await screen.findByRole('option', { name: 'Run A' });
  await act(async () => { fireEvent.change(screen.getByLabelText('Mining run'), { target: { value: 'A' } }); });
}
test('runtime has no mock fallback', async () => {
  render(<PatternExplorer/>);
  expect(await screen.findByText(/API chọn run và API Pattern/)).toBeInTheDocument();
  expect(screen.getByLabelText('Mining run')).toBeDisabled();
});
test('ordered groups, missing item and exact IDs; paging closes detail', async () => {
  const api = client(); render(<PatternExplorer client={api}/>); await choose();
  fireEvent.click(await screen.findByRole('button', { name: summary.patternId }));
  expect(await screen.findByRole('group', { name: 'Itemset 1' })).toHaveTextContent('Snapshot A');
  expect(screen.getByRole('group', { name: 'Itemset 2' })).toHaveTextContent('Item #2');
  expect(screen.getByText(/Weighted support: Không khả dụng/)).toBeInTheDocument();
  expect(screen.getByText(/9007199254740995/)).toBeInTheDocument();
  expect(api.detail).toHaveBeenCalledWith('A', summary.patternId);
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Trang sau' })); });
  await screen.findByText('Trang 2');
  expect(screen.queryByText(/Chi tiết Pattern/)).not.toBeInTheDocument();
  expect(api.list).toHaveBeenCalledWith('A', 1, 20);
});
test('bundle stays a single group', async () => {
  const api = client(); api.detail.mockResolvedValue({ ...detail, canonicalPattern: '<(1 2)>',
    integrityStatus: 'COMPLETE', issues: [], itemsets: [{ itemsetOrder: 1,
      items: [mapped, { ...mapped, itemOrder: 2, itemId: 2, nameSnapshot: 'Snapshot B' }] }] });
  render(<PatternExplorer client={api}/>); await choose();
  fireEvent.click(await screen.findByRole('button', { name: summary.patternId }));
  const group = await screen.findByRole('group', { name: 'Itemset 1' });
  expect(group).toHaveTextContent('Snapshot A'); expect(group).toHaveTextContent('Snapshot B');
  expect(screen.queryByRole('group', { name: 'Itemset 2' })).not.toBeInTheDocument();
});
test.each(['NOT_READY', 'FAILED', 'UNVERIFIED'] as const)('unavailable %s has null totals', async state => {
  const api = client(); api.list.mockResolvedValue({ ...page(), resultState: state,
    runPatternTotal: null, filteredTotal: null, items: [] });
  render(<PatternExplorer client={api}/>); await choose();
  expect(await screen.findByText(/Tổng Pattern của run: Chưa khả dụng/)).toBeInTheDocument();
  expect(screen.queryByText('Run thành công, không có Pattern.')).not.toBeInTheDocument();
});
test('READY zero differs from loading/error', async () => {
  const api = client(); api.list.mockResolvedValue({ ...page(), runPatternTotal: '0', filteredTotal: '0', items: [] });
  render(<PatternExplorer client={api}/>); await choose();
  expect(await screen.findByText('Run thành công, không có Pattern.')).toBeInTheDocument();
});
test.each([401, 403, 404, 409, 500])('HTTP %s is not empty success', async status => {
  const api = client(); api.list.mockRejectedValue(new PatternClientError(status, 'ERROR'));
  render(<PatternExplorer client={api}/>); await choose();
  expect(await screen.findByRole('button', { name: 'Tải lại danh sách' })).toBeInTheDocument();
  expect(screen.queryByText('Run thành công, không có Pattern.')).not.toBeInTheDocument();
});
test('late old run response cannot overwrite new run', async () => {
  let finish: (p: PatternPage) => void = () => undefined;
  const api = client(); api.list.mockImplementation((r, p) => r === 'A'
    ? new Promise(resolve => { finish = resolve; }) : Promise.resolve(page(r, p)));
  render(<PatternExplorer client={api}/>); await choose();
  await screen.findByText('Đang tải Pattern…');
  fireEvent.change(screen.getByLabelText('Mining run'), { target: { value: 'B' } });
  await screen.findByText('Run: B — SUCCEEDED');
  await act(async () => { finish(page('A')); });
  expect(screen.getByText('Run: B — SUCCEEDED')).toBeInTheDocument();
  expect(api.list).toHaveBeenCalledWith('B', 0, 20);
});
test('switching run resets a nonzero page and ignores pending detail', async () => {
  let finish: (value: PatternDetail) => void = () => undefined;
  const api = client(); api.detail.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  render(<PatternExplorer client={api}/>); await choose();
  await screen.findByRole('button', { name: summary.patternId });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Trang sau' })); });
  fireEvent.click(await screen.findByRole('button', { name: summary.patternId }));
  await screen.findByText('Đang tải chi tiết…');
  await act(async () => { fireEvent.change(screen.getByLabelText('Mining run'), { target: { value: 'B' } }); });
  await screen.findByText('Run: B — SUCCEEDED');
  await act(async () => { finish(detail); });
  expect(api.list).toHaveBeenCalledWith('B', 0, 20);
  expect(screen.queryByText(/Chi tiết Pattern/)).not.toBeInTheDocument();
  expect(screen.queryByText(/Snapshot A/)).not.toBeInTheDocument();
});
