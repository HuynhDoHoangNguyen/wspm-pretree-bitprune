import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import { comboGenerationApi, GenerationPreview } from 'api/admin/data-mining/combo-generation';
import NativeGenerationPanel from './NativeGenerationPanel';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
const run = '10000000-0000-4000-8000-000000000001';
const ready = (): GenerationPreview => ({ status: 'READY', issues: [], examined: 20, accepted: 10, rejected: 10, groups: 8, plannedGroups: 8, eligible: 5,
  topPatternIds: ['1'], anchors: [{ productId: 'K', name: 'Keyboard' }], reasonCounts: {}, referencePrices: { '1': '400.00' }, contextToken: 'signed',
  policyVersion: 'TV2_SELECTED_CATEGORIES_V3', proposals: [{ sourcePatternId: '1', canonicalPattern: '<(12)(27)>', comboKind: 'SEQUENTIAL_CROSS_SELL', total: '400.00',
    items: [{ itemsetOrder: 1, itemOrder: 1, itemId: 12, productId: 'K', name: 'Keyboard', sku: 'KEY', quantity: 3, required: true, unitPrice: '100.00', lineTotal: '300.00' },
      { itemsetOrder: 2, itemOrder: 1, itemId: 27, productId: 'B', name: 'Bag', sku: 'BAG', quantity: 1, required: false, unitPrice: '100.00', lineTotal: '100.00' }] }] });
const buttonCache = new Map<string, HTMLButtonElement>();
beforeEach(() => {
  buttonCache.clear();
  (useSharedAuth as unknown as jest.Mock).mockReturnValue({ status: 'authenticated', accessToken: 'token', user: { userId: 'admin', roles: ['ADMIN'] }, forbidden: false });
  jest.spyOn(comboGenerationApi, 'categoryOptions').mockImplementation(async id => ({ runId: id, policyVersion: 'TV2_SELECTED_CATEGORIES_V3', maxDistinctProducts: 4000, maxQuantity: 4294967295, maxBatchItems: 4000,
    categories: [{ categoryId: 'keyboards', name: 'Keyboard', productCount: 2 }, { categoryId: 'bags', name: 'Bag', productCount: 1 }, { categoryId: 'speakers', name: 'Loa', productCount: 2 }] }));
  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: { randomUUID: () => run } });
  Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: class { observe() { return undefined; } unobserve() { return undefined; } disconnect() { return undefined; } } });
  Element.prototype.scrollIntoView = jest.fn();
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) });
});
afterEach(() => jest.restoreAllMocks());
const open = () => render(<MemoryRouter><NativeGenerationPanel runId={run}/></MemoryRouter>);
test('draft limit is independent, uses confirmed planned count and changing it invalidates saving', async () => {
  const preview = jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValue({ ...ready(), plannedGroups: 3 });
  open(); await choose();
  fireEvent.change(screen.getByLabelText('Số combo nháp muốn lưu'), { target: { value: '3' } });
  fireEvent.click(button('Xem trước Combo'));
  await screen.findByText(/Có 8 Combo hợp lệ; sẽ lưu 3 Combo nháp/);
  expect(preview).toHaveBeenLastCalledWith(run, null, 5, run, expect.any(AbortSignal), expect.any(Object), 5, 3);
  expect(button('Lưu 3 Combo DRAFT')).toBeEnabled();
  fireEvent.change(screen.getByLabelText('Số combo nháp muốn lưu'), { target: { value: '2' } });
  expect(button('Lưu 3 Combo DRAFT')).toBeDisabled();
  expect(screen.getByLabelText('Số đề xuất xem trước')).toHaveValue('5');
  expect(screen.getByLabelText('Top-K gợi ý cho khách')).toHaveValue('5');
});
test('preview K and customer K are configured independently and changing either invalidates save', async () => {
  const preview = jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValue(ready());
  open(); await choose();
  fireEvent.change(screen.getByLabelText('Top-K gợi ý cho khách'), { target: { value: '2' } });
  fireEvent.change(screen.getByLabelText('Số đề xuất xem trước'), { target: { value: '1' } });
  fireEvent.click(button('Xem trước Combo'));
  await screen.findByText(/Đã xét 20/);
  expect(preview).toHaveBeenLastCalledWith(run, null, 1, run, expect.any(AbortSignal), expect.any(Object), 2, 20);
  expect(button('Lưu 8 Combo DRAFT')).toBeEnabled();
  fireEvent.change(screen.getByLabelText('Số đề xuất xem trước'), { target: { value: '10' } });
  expect(button('Lưu 8 Combo DRAFT')).toBeDisabled();
  fireEvent.click(button('Xem trước Combo'));
  await waitFor(() => expect(preview).toHaveBeenLastCalledWith(run, null, 10, run, expect.any(AbortSignal), expect.any(Object), 2, 20));
  fireEvent.change(screen.getByLabelText('Top-K gợi ý cho khách'), { target: { value: '5' } });
  expect(button('Lưu 8 Combo DRAFT')).toBeDisabled();
});
function button(name: string): HTMLButtonElement {
  const cached = buttonCache.get(name);
  const element = cached?.isConnected ? cached : screen.getByRole('button', { name, exact: true });
  if (!(element instanceof HTMLButtonElement)) throw new Error('Button not found: ' + name);
  expect(element).toHaveAccessibleName(name);
  buttonCache.set(name, element);
  return element;
}
async function choose(name = 'Keyboard') {
  await screen.findByText('Chọn ít nhất một danh mục bắt buộc.');
  const input = screen.getByRole('searchbox', { name: 'Danh mục bắt buộc trong Combo' });
  fireEvent.change(input, { target: { value: name } });
  fireEvent.mouseDown(await screen.findByText(new RegExp('^' + name + ' \\(')));
  return input;
}
test('run dictionary selection, quantities and bounds bind preview; extra products and save counts are explicit', async () => {
  const preview = jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValue(ready());
  const write = jest.spyOn(comboGenerationApi, 'submit'); open();
  await choose();
  fireEvent.change(screen.getByLabelText('Số lượng mỗi sản phẩm · Keyboard'), { target: { value: '3' } });
  fireEvent.change(screen.getByLabelText('Số sản phẩm tối đa trong Combo'), { target: { value: '5' } });
  expect(preview).not.toHaveBeenCalled(); expect(screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)')).toBeDisabled();
  fireEvent.click(button('Xem trước Combo'));
  await screen.findByText(/Đang xem 1 đề xuất; sẽ lưu 8 Combo/);
  expect(preview).toHaveBeenCalledWith(run, null, 5, run, expect.any(AbortSignal), { requiredCategories: [{ categoryId: 'keyboards', quantity: 3 }], minDistinctProducts: 2, maxDistinctProducts: 5 }, 5, 20);
  expect(screen.getByText(/Bổ sung từ Pattern.*1 × 100.00 = 100.00/)).toBeVisible();
  expect(write).not.toHaveBeenCalled(); expect(button('Lưu 8 Combo DRAFT')).toBeEnabled();
  fireEvent.change(screen.getByLabelText('Số lượng mỗi sản phẩm · Keyboard'), { target: { value: '4' } });
  expect(screen.queryByText('Lưu 8 Combo DRAFT', { exact: true })).not.toBeInTheDocument();
});
test('multi-select keeps quantities through search, validates integers/bounds and removes products', async () => {
  open(); await choose();
  fireEvent.change(screen.getByLabelText('Số lượng mỗi sản phẩm · Keyboard'), { target: { value: '2' } });
  const input = screen.getByRole('searchbox', { name: 'Danh mục bắt buộc trong Combo' });
  fireEvent.change(input, { target: { value: 'Bag' } }); fireEvent.mouseDown(await screen.findByText(/^Bag \(/));
  expect(screen.getByLabelText('Số lượng mỗi sản phẩm · Keyboard')).toHaveValue('2'); expect(screen.getByLabelText('Số lượng mỗi sản phẩm · Bag')).toHaveValue('1');
  fireEvent.change(screen.getByLabelText('Số lượng mỗi sản phẩm · Bag'), { target: { value: '1.5' } });
  expect(button('Xem trước Combo')).toBeDisabled();
  fireEvent.click(button('Bỏ Bag'));
  fireEvent.change(screen.getByLabelText('Số sản phẩm tối thiểu trong Combo'), { target: { value: '6' } });
  expect(button('Xem trước Combo')).toBeDisabled();
  fireEvent.change(screen.getByLabelText('Số sản phẩm tối đa trong Combo'), { target: { value: '6' } });
  expect(button('Xem trước Combo')).toBeEnabled();
});
test('anchor lists both products of a two-itemset Combo and accepts the later product', async () => {
  const result = ready(); result.anchors.push({ productId: 'B', name: 'Bag' });
  const preview = jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValue(result);
  open(); await choose();
  fireEvent.click(button('Xem trước Combo')); await screen.findByText(/Đã xét 20/);
  const select = screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)');
  expect(select).toHaveTextContent('Keyboard (K)'); expect(select).toHaveTextContent('Bag (B)');
  fireEvent.change(select, { target: { value: 'B' } });
  fireEvent.click(button('Xem trước Combo'));
  await waitFor(() => expect(preview).toHaveBeenLastCalledWith(run, 'B', 5, run, expect.any(AbortSignal), expect.any(Object), 5, 20));
});
test('anchor/K invalidate the viewing context, without modifying required products', async () => {
  const preview = jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValue(ready()); open(); await choose();
  fireEvent.click(button('Xem trước Combo')); await screen.findByText(/Đã xét 20/);
  fireEvent.change(screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)'), { target: { value: 'K' } });
  expect(button('Lưu 8 Combo DRAFT')).toBeDisabled();
  fireEvent.click(button('Xem trước Combo'));
  await waitFor(() => expect(preview).toHaveBeenLastCalledWith(run, 'K', 5, run, expect.any(AbortSignal), expect.objectContaining({ requiredCategories: [{ categoryId: 'keyboards', quantity: 1 }] }), 5, 20));
  await screen.findByRole('button', { name: 'Lưu 8 Combo DRAFT' });
});
test('adding Speaker to a two-product configuration keeps anchor visible and loads only fresh options', async () => {
  const refreshed = { ...ready(), anchors: [{ productId: 'S', name: 'Loa mới' }] };
  const preview = jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValueOnce(ready()).mockResolvedValueOnce(refreshed);
  open(); await choose();
  fireEvent.change(screen.getByLabelText('Số sản phẩm tối đa trong Combo'), { target: { value: '2' } });
  fireEvent.click(button('Xem trước Combo')); await screen.findByText(/Đã xét 20/);
  fireEvent.change(screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)'), { target: { value: 'K' } });
  const input = screen.getByRole('searchbox', { name: 'Danh mục bắt buộc trong Combo' });
  fireEvent.change(input, { target: { value: 'Loa' } }); fireEvent.mouseDown(await screen.findByText(/^Loa \(/));
  const anchor = screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)');
  expect(anchor).toBeVisible(); expect(anchor).toBeDisabled(); expect(anchor).toHaveValue('');
  expect(anchor).not.toHaveTextContent('Keyboard (K)');
  expect(screen.queryByText('Lưu 8 Combo DRAFT', { exact: true })).not.toBeInTheDocument();
  fireEvent.click(button('Xem trước Combo'));
  await waitFor(() => expect(anchor).toBeEnabled()); expect(anchor).toHaveTextContent('Loa mới (S)');
  expect(preview).toHaveBeenLastCalledWith(run, null, 5, run, expect.any(AbortSignal), {
    requiredCategories: [{ categoryId: 'keyboards', quantity: 1 }, { categoryId: 'speakers', quantity: 1 }], minDistinctProducts: 2, maxDistinctProducts: 2,
  }, 5, 20);
});
test('three required categories with max two products explains conflict without requesting preview', async () => {
  const preview = jest.spyOn(comboGenerationApi, 'previewCategories'); open(); await choose();
  fireEvent.change(screen.getByLabelText('Số sản phẩm tối đa trong Combo'), { target: { value: '2' } });
  const input = screen.getByRole('searchbox', { name: 'Danh mục bắt buộc trong Combo' });
  for (const category of ['Bag', 'Loa']) {
    fireEvent.change(input, { target: { value: category } }); fireEvent.mouseDown(await screen.findByText(new RegExp('^' + category + ' \\(')));
  }
  expect(screen.getByText(/Đã chọn 3 danh mục bắt buộc nhưng tối đa chỉ 2 sản phẩm/)).toBeVisible();
  expect(screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)')).toBeVisible();
  expect(button('Xem trước Combo')).toBeDisabled(); expect(preview).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('Số sản phẩm tối đa trong Combo'), { target: { value: '3' } });
  expect(button('Xem trước Combo')).toBeEnabled();
});
test.each(['quantity', 'minimum', 'maximum', 'remove'])('changing %s clears old anchor choices without removing the field', async operation => {
  jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValue(ready()); open(); await choose();
  fireEvent.click(button('Xem trước Combo')); await screen.findByText(/Đã xét 20/);
  fireEvent.change(screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)'), { target: { value: 'K' } });
  if (operation === 'remove') fireEvent.click(button('Bỏ Keyboard'));
  else fireEvent.change(screen.getByLabelText(operation === 'quantity' ? 'Số lượng mỗi sản phẩm · Keyboard'
    : operation === 'minimum' ? 'Số sản phẩm tối thiểu trong Combo' : 'Số sản phẩm tối đa trong Combo'), { target: { value: '3' } });
  const anchor = screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)');
  expect(anchor).toBeVisible(); expect(anchor).toBeDisabled(); expect(anchor).toHaveValue(''); expect(anchor).not.toHaveTextContent('Keyboard (K)');
  expect(screen.queryByText('Lưu 8 Combo DRAFT', { exact: true })).not.toBeInTheDocument();
});
test('empty result retains the anchor field with a reason and can recover', async () => {
  const preview = jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValueOnce({ ...ready(), status: 'EMPTY', groups: 0, plannedGroups: 0, accepted: 0, anchors: [], proposals: [], referencePrices: {}, contextToken: null });
  open(); await choose(); fireEvent.click(button('Xem trước Combo'));
  expect(await screen.findByText(/Không có sản phẩm neo vì chưa có Combo hợp lệ/)).toBeVisible();
  expect(screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)')).toBeDisabled();
  expect(button('Lưu 0 Combo DRAFT')).toBeDisabled();
  preview.mockResolvedValueOnce(ready()); fireEvent.click(button('Xem trước Combo'));
  await waitFor(() => expect(screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)')).toBeEnabled());
});
test('preview failure clears anchor and stale save but retains the field', async () => {
  const preview = jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValueOnce(ready());
  open(); await choose(); fireEvent.click(button('Xem trước Combo')); await screen.findByText(/Đã xét 20/);
  fireEvent.change(screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)'), { target: { value: 'K' } });
  preview.mockRejectedValueOnce(new Error('offline')); fireEvent.click(button('Xem trước Combo'));
  await screen.findByText(/Không thể chuẩn bị Combo/);
  expect(screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)')).toBeDisabled();
  expect(screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)')).toHaveValue('');
  expect(screen.queryByText('Lưu 8 Combo DRAFT', { exact: true })).not.toBeInTheDocument();
});
test('a removed anchor is cleared on refresh and cannot save stale data', async () => {
  const preview = jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValueOnce(ready());
  open(); await choose(); fireEvent.click(button('Xem trước Combo')); await screen.findByText(/Đã xét 20/);
  fireEvent.change(screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)'), { target: { value: 'K' } });
  preview.mockResolvedValueOnce({ ...ready(), status: 'BLOCKED', issues: ['INVALID_ANCHOR_MEMBERSHIP'], anchors: [{ productId: 'S', name: 'Loa' }], contextToken: null });
  fireEvent.click(button('Xem trước Combo'));
  await screen.findByText(/Sản phẩm neo trước đó không còn/);
  expect(screen.getByLabelText('Sản phẩm neo (chỉ lọc xem trước)')).toHaveValue('');
  expect(button('Lưu 8 Combo DRAFT')).toBeDisabled();
});
test('double-submit is blocked; UNKNOWN only reconciles and shows saving feedback on the correct button', async () => {
  jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValue(ready());
  let finish!: (v: any) => void; const write = jest.spyOn(comboGenerationApi, 'submit').mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  const reconcile = jest.spyOn(comboGenerationApi, 'reconcile').mockResolvedValue({ status: 'NO_OP', issues: [], batchId: 'batch', storedGroups: 8 });
  open(); await choose(); fireEvent.click(button('Xem trước Combo')); await screen.findByText(/Đã xét 20/);
  fireEvent.click(button('Lưu 8 Combo DRAFT')); fireEvent.click(button('Lưu 8 Combo DRAFT'));
  expect(write).toHaveBeenCalledTimes(1); expect(screen.getByRole('status')).toHaveTextContent('Đang lưu 8 Combo');
  await act(async () => { finish({ status: 'UNKNOWN', issues: [], batchId: null, storedGroups: 0 }); });
  fireEvent.click(await screen.findByRole('button', { name: 'Kiểm tra kết quả lưu' }));
  expect(await screen.findByRole('link', { name: 'Mở batch đã lưu' })).toHaveAttribute('href', '/admin/combos?batchId=batch');
  expect(reconcile).toHaveBeenCalledWith('signed', expect.any(AbortSignal)); expect(write).toHaveBeenCalledTimes(1);
});
test('run/session changes abort and clear selection and late preview cannot enable saving', async () => {
  let finish!: (v: GenerationPreview) => void;
  const preview = jest.spyOn(comboGenerationApi, 'previewCategories').mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  const view = open(); await choose(); fireEvent.click(button('Xem trước Combo'));
  const signal = preview.mock.calls[0][4];
  view.rerender(<MemoryRouter><NativeGenerationPanel runId={run.replace(/1$/, '2')}/></MemoryRouter>);
  expect(signal?.aborted).toBe(true); await act(async () => { finish(ready()); });
  expect(screen.queryByText('Lưu 8 Combo DRAFT', { exact: true })).not.toBeInTheDocument();
  expect(screen.queryByLabelText('Số lượng mỗi sản phẩm · Keyboard')).not.toBeInTheDocument();
});
test('resource blocker disables save before any write', async () => {
  jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValue({ ...ready(), status: 'BLOCKED', contextToken: null, issues: ['GENERATION_RESOURCE_LIMIT_EXCEEDED'] });
  open(); await choose(); fireEvent.click(button('Xem trước Combo'));
  await screen.findByText(/Đề xuất vượt giới hạn lưu an toàn/); expect(button('Lưu 8 Combo DRAFT')).toBeDisabled();
});
test('logout clears dictionary choices and ignores a late protected preview', async () => {
  let finish!: (v: GenerationPreview) => void;
  const preview = jest.spyOn(comboGenerationApi, 'previewCategories').mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  const view = open(); await choose(); fireEvent.click(button('Xem trước Combo'));
  (useSharedAuth as unknown as jest.Mock).mockReturnValue({ status: 'anonymous', user: null });
  view.rerender(<MemoryRouter><NativeGenerationPanel runId={run}/></MemoryRouter>);
  expect(preview.mock.calls[0][4]?.aborted).toBe(true);
  await act(async () => { finish(ready()); });
  expect(screen.queryByLabelText('Số lượng mỗi sản phẩm · Keyboard')).not.toBeInTheDocument();
  expect(screen.queryByText('Lưu 8 Combo DRAFT', { exact: true })).not.toBeInTheDocument();
  expect(button('Xem trước Combo')).toBeDisabled();
});
test('a rejected save requires refreshed preview before replay', async () => {
  jest.spyOn(comboGenerationApi, 'previewCategories').mockResolvedValue(ready());
  jest.spyOn(comboGenerationApi, 'submit').mockResolvedValue({ status: 'INVALID', issues: ['PAYLOAD_OR_EVIDENCE_INVALID'], batchId: null, storedGroups: 0 });
  open(); await choose();
  fireEvent.click(button('Xem trước Combo')); await waitFor(() => expect(button('Lưu 8 Combo DRAFT')).toBeEnabled());
  fireEvent.click(button('Lưu 8 Combo DRAFT')); await screen.findByText(/Không thể xác minh gói dữ liệu lưu/);
  expect(button('Lưu 8 Combo DRAFT')).toBeDisabled();
});
