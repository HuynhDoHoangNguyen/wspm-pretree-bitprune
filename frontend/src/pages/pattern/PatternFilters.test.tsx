import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import PatternExplorer from './PatternExplorer';
import { validateFilters } from './PatternFiltersPanel';
import { PatternClient, PatternClientError, PatternPage, PatternFilters, DictionaryPage } from './PatternClient';

const option = (id = 1) => ({ itemId: id, productId: `p${id}`, nameSnapshot: `Tên snapshot ${id}`, skuSnapshot: `SKU-${id}` });
const page = (r = 'A', p = 0): PatternPage => ({ runId: r, runStatus: 'SUCCEEDED', resultState: 'READY',
  runPatternTotal: '21', filteredTotal: '21', page: p, size: 20, items: [],
  weightFilter: { enabled: true, reason: null, policy: 'TEST_ONLY' } });
const dictionary = (r: string, q: string, p: number, size: number): DictionaryPage => ({ runId: r, query: q, page: p, size,
  resultState: 'READY', dictionaryTotal: '21', matchedTotal: '21', items: [option(q ? 2 : 1)] });
function client(): jest.Mocked<PatternClient> {
  return { runs: jest.fn().mockResolvedValue({ page: 0, size: 20, total: '2', items: [{ runId: 'A', label: 'Run A' }, { runId: 'B', label: 'Run B' }] }),
    list: jest.fn().mockImplementation((r, p) => Promise.resolve(page(r, p))),
    dictionary: jest.fn().mockImplementation((r, q, p, s) => Promise.resolve(dictionary(r, q, p, s))),
    detail: jest.fn() };
}
async function choose() {
  await screen.findByRole('option', { name: 'Run A' });
  await act(async () => { fireEvent.change(screen.getByLabelText('Mining run'), { target: { value: 'A' } }); });
  await screen.findByText('Run: A — SUCCEEDED');
}
async function input(label: string, value: string) {
  await act(async () => { fireEvent.change(screen.getByLabelText(label), { target: { value } }); });
}
async function click(label: string) { await act(async () => { fireEvent.click(screen.getByRole('button', { name: label })); }); }

test('dictionary search/page leaves applied Pattern page unchanged; selected items survive', async () => {
  const api = client(); render(<PatternExplorer client={api}/>); await choose();
  fireEvent.click(screen.getByRole('checkbox', { name: /Tên snapshot 1/ }));
  await click('Trang sau');
  const calls = api.list.mock.calls.length;
  await input('Tìm sản phẩm theo tên hoặc SKU snapshot', 'SKU-2');
  fireEvent.click(await screen.findByRole('checkbox', { name: /Tên snapshot 2/ }));
  await click('Dictionary sau');
  expect(api.list).toHaveBeenCalledTimes(calls);
  expect(api.dictionary).toHaveBeenLastCalledWith('A', 'SKU-2', 1, 20, expect.objectContaining({ signal: expect.anything() }));
  expect(screen.getByText('Đã chọn: 2')).toBeInTheDocument();
  await input('Support tối thiểu (sequence)', '9007199254740993');
  await click('Áp dụng');
  expect(api.list).toHaveBeenLastCalledWith('A', 0, 20, { itemIds: ['1', '2'], supportMin: '9007199254740993' }, expect.objectContaining({ signal: expect.anything() }));
  await click('Bỏ lọc');
  expect(api.list).toHaveBeenLastCalledWith('A', 0, 20, undefined, expect.objectContaining({ signal: expect.anything() }));
  expect(screen.getByText('Đã chọn: 0')).toBeInTheDocument();
});

test('inclusive equal bounds, single bounds, empty omission and reversed validation', () => {
  expect(validateFilters([], { supportMin: '4', supportMax: '4', weightMin: '0.4', weightMax: '0.4' }, true))
    .toEqual({ supportMin: '4', supportMax: '4', weightMin: '0.4', weightMax: '0.4' });
  expect(validateFilters([], { supportMin: '', supportMax: '9223372036854775807', weightMin: '', weightMax: '' }, true))
    .toEqual({ supportMax: '9223372036854775807' });
  for (const [min, max] of [['5', '4'], ['1.5', ''], ['-1', ''], ['9223372036854775808', '']]) {
    expect(() => validateFilters([], { supportMin: min, supportMax: max, weightMin: '', weightMax: '' }, true)).toThrow();
  }
  expect(() => validateFilters([], { supportMin: '', supportMax: '', weightMin: '0.6', weightMax: '0.5' }, true)).toThrow();
});

test('invalid draft and server membership error remain editable', async () => {
  const api = client(); api.list.mockImplementation((r, p, s, f) => f
    ? Promise.reject(new PatternClientError(400, 'ITEM_NOT_IN_RUN_DICTIONARY')) : Promise.resolve(page(r, p)));
  render(<PatternExplorer client={api}/>); await choose();
  await input('Support tối thiểu (sequence)', '5'); await input('Support tối đa (sequence)', '4');
  await click('Áp dụng');
  expect(await screen.findByText('Support tối thiểu không được lớn hơn tối đa.')).toBeInTheDocument();
  expect(api.list).toHaveBeenCalledTimes(1);
  await input('Support tối đa (sequence)', '6'); await click('Áp dụng');
  expect(await screen.findByText(/ITEM_NOT_IN_RUN_DICTIONARY:/)).toBeInTheDocument();
  expect(screen.getByLabelText('Support tối thiểu (sequence)')).toHaveValue('5');
});

test.each(['error', 'disabled'])('weight recovery %s removes only weight once', async mode => {
  const api = client();
  api.list.mockImplementation((r, p, s, f?: PatternFilters) => {
    if (f?.weightMin) return mode === 'error'
      ? Promise.reject(new PatternClientError(409, 'WEIGHT_FILTER_UNAVAILABLE'))
      : Promise.resolve({ ...page(r, p), weightFilter: { enabled: false, reason: 'POLICY_NOT_CONFIRMED', policy: null } });
    return Promise.resolve({ ...page(r, p), weightFilter: f
      ? { enabled: false, reason: 'POLICY_NOT_CONFIRMED', policy: null }
      : { enabled: true, reason: null, policy: 'TEST_ONLY' } });
  });
  render(<PatternExplorer client={api}/>); await choose();
  fireEvent.click(screen.getByRole('checkbox', { name: /Tên snapshot 1/ }));
  await input('Support tối thiểu (sequence)', '3'); await input('Trọng số mẫu tối thiểu', '0.4');
  await click('Áp dụng');
  await screen.findByText(/Đã bỏ cận trọng số và tải lại:/);
  await waitFor(() => expect(api.list).toHaveBeenCalledTimes(3));
  expect(api.list).toHaveBeenLastCalledWith('A', 0, 20, { itemIds: ['1'], supportMin: '3' }, expect.objectContaining({ signal: expect.anything() }));
  expect(screen.getByLabelText('Trọng số mẫu tối thiểu')).toBeDisabled();
  expect(screen.getByLabelText('Trọng số mẫu tối thiểu')).toHaveValue('');
  expect(screen.getByText('Đã chọn: 1')).toBeInTheDocument();
});

test('disabled capability rejects local weight use and zero matches differs from zero run', async () => {
  const api = client(); api.list.mockResolvedValue({ ...page(), filteredTotal: '0',
    weightFilter: { enabled: false, reason: 'POLICY_NOT_CONFIRMED', policy: null } });
  render(<PatternExplorer client={api}/>); await choose();
  expect(screen.getByLabelText('Trọng số mẫu tối thiểu')).toBeDisabled();
  expect(await screen.findByText('Không có Pattern khớp bộ lọc.')).toBeInTheDocument();
  expect(screen.queryByText('Run thành công, không có Pattern.')).not.toBeInTheDocument();
});

test('old dictionary error cannot replace new run or selections', async () => {
  let reject: (e: Error) => void = () => undefined;
  const api = client(); api.dictionary.mockImplementation((r, q, p, s) => r === 'A' && q === 'late'
    ? new Promise((resolve, fail) => { reject = fail; }) : Promise.resolve(dictionary(r, q, p, s)));
  render(<PatternExplorer client={api}/>); await choose();
  fireEvent.click(screen.getByRole('checkbox', { name: /Tên snapshot 1/ }));
  await input('Support tối thiểu (sequence)', '4'); await click('Áp dụng');
  await input('Tìm sản phẩm theo tên hoặc SKU snapshot', 'late');
  await act(async () => { fireEvent.change(screen.getByLabelText('Mining run'), { target: { value: 'B' } }); });
  await screen.findByText('Run: B — SUCCEEDED');
  await act(async () => { reject(new Error('late failure')); });
  expect(screen.getByText('Đã chọn: 0')).toBeInTheDocument();
  expect(screen.getByLabelText('Support tối thiểu (sequence)')).toHaveValue('');
  expect(screen.getByLabelText('Tìm sản phẩm theo tên hoặc SKU snapshot')).toHaveValue('');
  expect(screen.queryByText('Không tải được dictionary.')).not.toBeInTheDocument();
  expect(api.list).toHaveBeenLastCalledWith('B', 0, 20, undefined, expect.objectContaining({ signal: expect.anything() }));
});
test('equal inclusive bounds are sent unchanged to backend', async () => {
  const api = client(); render(<PatternExplorer client={api}/>); await choose();
  await input('Support tối thiểu (sequence)', '4'); await input('Support tối đa (sequence)', '4');
  await input('Trọng số mẫu tối thiểu', '0.4'); await input('Trọng số mẫu tối đa', '0.4');
  await click('Áp dụng');
  expect(api.list).toHaveBeenLastCalledWith('A', 0, 20,
    { supportMin: '4', supportMax: '4', weightMin: '0.4', weightMax: '0.4' }, expect.objectContaining({ signal: expect.anything() }));
});
test('late filtered Pattern response cannot replace newer applied bounds', async () => {
  let finish: (value: PatternPage) => void = () => undefined;
  const api = client(); api.list.mockImplementation((r, p, s, f?: PatternFilters) => f?.supportMin === '2'
    ? new Promise(resolve => { finish = resolve; }) : Promise.resolve({ ...page(r, p), filteredTotal: f ? '7' : '21' }));
  render(<PatternExplorer client={api}/>); await choose();
  await input('Support tối thiểu (sequence)', '2'); await click('Áp dụng');
  await input('Support tối thiểu (sequence)', '3'); await click('Áp dụng');
  await screen.findByText(/tổng khớp: 7/);
  await act(async () => { finish({ ...page(), filteredTotal: '19' }); });
  expect(screen.getByText(/tổng khớp: 7/)).toBeInTheDocument();
  expect(screen.queryByText(/tổng khớp: 19/)).not.toBeInTheDocument();
});
