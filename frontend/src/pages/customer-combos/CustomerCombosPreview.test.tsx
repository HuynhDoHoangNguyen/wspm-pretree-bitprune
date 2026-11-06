import React from 'react';
import { ColorSchemeProvider } from '@mantine/core';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { readFileSync } from 'fs';
import path from 'path';
import { runInNewContext } from 'vm';
import ts from 'typescript';
import CustomerCombosPreview from './CustomerCombosPreview';

jest.mock('stores/use-auth-store', () => ({ __esModule: true, default: () => { throw new Error('Preview accessed authentication'); } }));

const originalFetch = global.fetch;
beforeAll(() => Object.defineProperty(window, 'ResizeObserver', { configurable: true,
  value: class { observe() { return undefined; } unobserve() { return undefined; } disconnect() { return undefined; } },
}));
const originalEventSource = global.EventSource;
let network: jest.Mock;
beforeEach(() => {
  network = jest.fn(() => { throw new Error('Preview attempted network access'); });
  global.fetch = network;
  global.EventSource = network as unknown as typeof EventSource;
});
afterEach(() => {
  expect(network).not.toHaveBeenCalled();
  global.fetch = originalFetch;
  global.EventSource = originalEventSource;
});

function show(url = '/__preview/customer-combos') {
  window.history.replaceState({}, '', url);
  return render(<ColorSchemeProvider colorScheme="light" toggleColorScheme={() => undefined}><BrowserRouter><Routes>
    <Route path="/__preview/customer-combos" element={<CustomerCombosPreview/>}/>
    <Route path="/__preview/customer-combos/:comboId" element={<CustomerCombosPreview/>}/>
  </Routes></BrowserRouter></ColorSchemeProvider>);
}

function visit(url: string) {
  act(() => {
    window.history.pushState({}, '', url);
    window.dispatchEvent(new PopStateEvent('popstate'));
  });
}

test('offline preview shows four approved combos and selection changes the results', async () => {
  show();
  expect(screen.getByText(/Dữ liệu preview chỉ mang tính minh họa/)).toBeInTheDocument();
  expect(screen.getAllByRole('option').length).toBeGreaterThanOrEqual(5);
  expect(await screen.findAllByRole('article')).toHaveLength(4);
  expect(screen.queryByText('Combo chưa duyệt')).not.toBeInTheDocument();
  expect(screen.queryByText('Bộ laptop đầy đủ')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Sản phẩm'), { target: { value: 'keyboard' } });
  expect(await screen.findByRole('article', { name: 'Bộ phụ kiện bàn làm việc' })).toBeInTheDocument();
  expect(screen.getAllByRole('article')).toHaveLength(2);
  expect(screen.queryByRole('article', { name: 'Bộ làm việc linh hoạt' })).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Sản phẩm'), { target: { value: 'speaker' } });
  expect(await screen.findByText('Chưa có combo gợi ý')).toBeInTheDocument();
}, 15000);

test('offline scenarios support loading, empty, error and retry', async () => {
  show();
  await screen.findAllByRole('article');
  const select = screen.getByLabelText('Tình huống preview');
  fireEvent.change(select, { target: { value: 'loading' } });
  expect(screen.getByRole('status', { name: 'Đang tải combo' })).toBeInTheDocument();
  expect(screen.queryByRole('article')).not.toBeInTheDocument();
  fireEvent.change(select, { target: { value: 'empty' } });
  expect(await screen.findByText('Chưa có combo gợi ý')).toBeInTheDocument();
  fireEvent.change(select, { target: { value: 'error' } });
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại' }));
  expect(await screen.findAllByRole('article')).toHaveLength(4);
});

test('navigates to a dedicated detail URL and returns without losing product selection', async () => {
  show();
  await screen.findAllByRole('article');
  fireEvent.change(screen.getByLabelText('Sản phẩm'), { target: { value: 'keyboard' } });
  const card = await screen.findByRole('article', { name: 'Bộ phụ kiện bàn làm việc' });
  fireEvent.click(within(card).getByRole('button', { name: 'Xem chi tiết Bộ phụ kiện bàn làm việc' }));
  expect(window.location.pathname + window.location.search).toBe('/__preview/customer-combos/c6?productId=keyboard');
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  await screen.findByRole('heading', { level: 1, name: 'Bộ phụ kiện bàn làm việc' });
  const detail = screen.getByRole('main');
  expect(within(detail).getByRole('heading', { name: 'Bộ phụ kiện bàn làm việc' })).toBeInTheDocument();
  expect(within(detail).getByText('Mã combo: c6')).toBeInTheDocument();
  expect(within(detail).getByText('Chuột không dây')).toBeInTheDocument();
  expect(within(detail).getByText('Bàn phím cơ')).toBeInTheDocument();
  expect(within(detail).getByText(/Mã sản phẩm: PK-002/)).toBeInTheDocument();
  expect(within(detail).getByText(/Giá combo:/)).toHaveTextContent('1.550.000');
  expect(within(detail).getByText(/Giá tham chiếu:/)).toHaveTextContent('1.650.000');
  expect(within(detail).getByText(/Tiết kiệm/)).toHaveTextContent('100.000');
  expect(within(detail).getByText('Giảm 6,1%')).toBeInTheDocument();
  const items = within(detail).getByRole('region', { name: 'Sản phẩm trong combo' });
  expect(within(items).getAllByRole('img').map(item => item.getAttribute('aria-label'))).toEqual([
    'Ảnh minh họa Chuột không dây', 'Ảnh minh họa Bàn phím cơ',
  ]);
  fireEvent.click(within(detail).getByRole('link', { name: '← Quay lại danh sách Combo' }));
  await screen.findAllByRole('article');
  expect(screen.getByLabelText('Sản phẩm')).toHaveValue('keyboard');
  expect(screen.getAllByRole('article')).toHaveLength(2);
});

test('direct detail URL and remount after refresh resolve the fixture without temporary state', async () => {
  const view = show('/__preview/customer-combos/c1?productId=laptop-air');
  await screen.findByRole('heading', { name: 'Bộ làm việc linh hoạt' });
  expect(screen.getByText('Số chuỗi hỗ trợ (minh họa): 24')).toBeInTheDocument();
  expect(screen.getByText('Trọng số mẫu (minh họa): 0.7')).toBeInTheDocument();
  const url = window.location.pathname + window.location.search;
  view.unmount();
  show(url);
  expect(await screen.findByText('Mã combo: c1')).toBeInTheDocument();
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

test('browser Back and Forward restore URL, selection and detail', async () => {
  show('/__preview/customer-combos?productId=keyboard');
  await screen.findAllByRole('article');
  fireEvent.click(screen.getByRole('button', { name: 'Xem chi tiết Bộ phụ kiện bàn làm việc' }));
  await screen.findByText('Mã combo: c6');
  act(() => window.history.back());
  await waitFor(() => expect(screen.getByLabelText('Sản phẩm')).toHaveValue('keyboard'));
  act(() => window.history.forward());
  expect(await screen.findByText('Mã combo: c6')).toBeInTheDocument();
  expect(window.location.search).toBe('?productId=keyboard');
});

test.each(['missing', 'draft'])('unknown or unapproved combo %s is not displayed', async id => {
  show(`/__preview/customer-combos/${id}`);
  expect(await screen.findByText('Không tìm thấy combo')).toBeInTheDocument();
  expect(screen.queryByText(/Giá combo:/)).not.toBeInTheDocument();
});

test('detail scenarios support loading, error and retry without showing a previous combo', async () => {
  show('/__preview/customer-combos/c1');
  await screen.findByText('Mã combo: c1');
  visit('/__preview/customer-combos/c4?detailState=loading');
  expect(screen.getByRole('status', { name: 'Đang tải chi tiết combo' })).toBeInTheDocument();
  expect(screen.queryByText('Mã combo: c1')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Tình huống chi tiết preview'), { target: { value: 'error' } });
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByText('Mã combo: c4')).toBeInTheDocument();
  expect(screen.queryByText(/Số chuỗi hỗ trợ/)).not.toBeInTheDocument();
  expect(screen.queryByText(/Giá tham chiếu/)).not.toBeInTheDocument();
  visit('/__preview/customer-combos/missing');
  expect(screen.queryByText('Mã combo: c4')).not.toBeInTheDocument();
  expect(await screen.findByText('Không tìm thấy combo')).toBeInTheDocument();
});

// Evaluate the actual App declaration without mounting unrelated legacy pages.
const appSource = readFileSync(path.join(process.cwd(), 'src/App.tsx'), 'utf8');
const declaration = appSource.match(/const CustomerCombosPreview = [\s\S]*?;\r?\n/)?.[0];
test.each([
  ['development', 'true', true], ['development', 'false', false],
  ['development', undefined, false], ['production', 'true', false], ['test', 'true', false],
])('App preview registration guard: %s / %s', (mode, flag, enabled) => {
  expect(declaration).toBeDefined();
  const lazy = jest.fn(() => ({}));
  const js = ts.transpileModule(`${declaration}\nresult.enabled = CustomerCombosPreview !== null;`, {
    compilerOptions: { target: ts.ScriptTarget.ES2017, module: ts.ModuleKind.CommonJS },
  }).outputText;
  const result = { enabled: false };
  runInNewContext(js, { process: { env: { NODE_ENV: mode, REACT_APP_ENABLE_CUSTOMER_COMBO_PREVIEW: flag } }, React: { lazy }, result });
  expect(result.enabled).toBe(enabled);
  expect(lazy).toHaveBeenCalledTimes(enabled ? 1 : 0);
});

test('App mounts the guarded preview outside the Client layout', () => {
  expect(appSource).toMatch(/\{CustomerCombosPreview && <Route path="\/__preview\/customer-combos"/);
  expect(appSource.indexOf('path="/__preview/customer-combos"')).toBeLessThan(appSource.indexOf('path="/" element={<Client/>}'));
  expect(appSource).toMatch(/\{CustomerCombosPreview && <Route path="\/__preview\/customer-combos\/:comboId"/);
  expect(appSource.indexOf('path="/__preview/customer-combos/:comboId"')).toBeLessThan(appSource.indexOf('path="/" element={<Client/>}'));
});
