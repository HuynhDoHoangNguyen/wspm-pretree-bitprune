import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { setLogger } from 'react-query';
import MiningRunHistoryPreview from './MiningRunHistoryPreview';

// Real Mantine tables/modals can exceed Jest's 5s default on this Windows environment.
jest.setTimeout(15000);

// Rendering a preview must never need an authenticated user or the real API adapter.
jest.mock('auth/session', () => ({
  useSharedAuth: () => { throw new Error('Preview accessed shared authentication'); },
}));
jest.mock('./MiningRun.api', () => ({
  ...jest.requireActual('./MiningRun.api'),
  listMiningRuns: () => { throw new Error('Preview called the real list API'); },
  getMiningRun: () => { throw new Error('Preview called the real detail API'); },
}));

const originalFetch = global.fetch;
let fetchSpy: jest.Mock;
beforeAll(() => {
  setLogger({ log: () => undefined, warn: () => undefined, error: () => undefined });
  Object.defineProperty(window, 'ResizeObserver', {
    configurable: true,
    value: class { observe = () => undefined; unobserve = () => undefined; disconnect = () => undefined; },
  });
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (query: string) => ({
      matches: false, media: query, onchange: null,
      addListener: () => undefined, removeListener: () => undefined,
      addEventListener: () => undefined, removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }),
  });
});
beforeEach(() => {
  fetchSpy = jest.fn(() => { throw new Error('Preview attempted a network request'); });
  global.fetch = fetchSpy;
});
afterEach(() => {
  expect(fetchSpy).not.toHaveBeenCalled();
  global.fetch = originalFetch;
});
afterAll(() => setLogger(console));

function show() {
  return render(<MantineProvider><MiningRunHistoryPreview/></MantineProvider>);
}
function scenario(value: string) {
  fireEvent.change(screen.getByLabelText('Tình huống preview'), { target: { value } });
}

test('renders labelled fixtures, paginates and opens the existing detail view without authentication or network', async () => {
  show();
  expect(screen.getByText('Dữ liệu giả — chỉ kiểm tra giao diện')).toBeInTheDocument();
  const table = await screen.findByRole('table', { name: 'Lịch sử RUN' });
  expect(within(table).getAllByRole('row')).toHaveLength(21);
  expect(screen.getByText('Tổng: 45 RUN')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '2', exact: true }));
  expect(await screen.findByRole('button', { name: 'Xem RUN preview-run-025' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Xem RUN preview-run-045' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Xem RUN preview-run-025' }));
  const detail = await screen.findByRole('table', { name: 'Chi tiết RUN' });
  expect(within(detail).getByText('preview-run-025')).toBeInTheDocument();
  expect(within(detail).getByText('preview-snapshot-21')).toBeInTheDocument();
});

test('filters fixtures, resets pagination, changes page size and refreshes locally', async () => {
  show();
  await screen.findByRole('table', { name: 'Lịch sử RUN' });
  fireEvent.click(screen.getByRole('button', { name: '3', exact: true }));
  await screen.findByRole('button', { name: 'Xem RUN preview-run-005' });
  fireEvent.change(screen.getByLabelText('Thuật toán'), { target: { value: 'WSPM_BITPRUNE' } });
  expect(await screen.findByText('Tổng: 22 RUN')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Trạng thái'), { target: { value: 'FAILED' } });
  expect(await screen.findByText('Tổng: 5 RUN')).toBeInTheDocument();
  const table = screen.getByRole('table', { name: 'Lịch sử RUN' });
  expect(within(table).getAllByRole('row')).toHaveLength(6);
  expect(within(table).getAllByText('FAILED')).toHaveLength(5);
  fireEvent.click(screen.getByRole('button', { name: 'Xóa bộ lọc' }));
  await screen.findByText('Tổng: 45 RUN');
  fireEvent.change(screen.getByLabelText('Số RUN mỗi trang'), { target: { value: '50' } });
  await waitFor(() => expect(within(screen.getByRole('table', { name: 'Lịch sử RUN' })).getAllByRole('row')).toHaveLength(46));
  fireEvent.click(screen.getByRole('button', { name: 'Làm mới' }));
  expect(await screen.findByText('Tổng: 45 RUN')).toBeInTheDocument();
});

test('switches between empty and loading scenarios without reusing stale results', async () => {
  show();
  await screen.findByText('Tổng: 45 RUN');
  scenario('empty');
  expect(await screen.findByText('Không có RUN phù hợp.')).toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  scenario('loading');
  expect(await screen.findByText('Đang tải lịch sử RUN…')).toBeInTheDocument();
  expect(screen.queryByText('Không có RUN phù hợp.')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Làm mới' })).toBeDisabled();
  scenario('data');
  expect(await screen.findByText('Tổng: 45 RUN')).toBeInTheDocument();
});

test('refresh confirms completion while retaining the filtered page, fixtures and detail without network', async () => {
  show();
  await screen.findByText('Tổng: 45 RUN');
  fireEvent.change(screen.getByLabelText('Thuật toán'), { target: { value: 'WSPM_BITPRUNE' } });
  await screen.findByText('Tổng: 22 RUN');
  fireEvent.click(screen.getByRole('button', { name: '2', exact: true }));
  await screen.findByRole('button', { name: 'Xem RUN preview-run-004' });
  fireEvent.click(screen.getByRole('button', { name: 'Làm mới' }));
  expect(await screen.findByText('Đã làm mới danh sách')).toBeInTheDocument();
  expect(screen.getByLabelText('Thuật toán')).toHaveValue('WSPM_BITPRUNE');
  expect(screen.getByLabelText('Trạng thái')).toHaveValue('');
  expect(screen.getByText('Tổng: 22 RUN')).toBeInTheDocument();
  const table = screen.getByRole('table', { name: 'Lịch sử RUN' });
  expect(within(table).getAllByRole('row')).toHaveLength(3);
  expect(within(table).getByText('preview-run-004')).toBeInTheDocument();
  expect(within(table).getByText('preview-run-002')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Xem RUN preview-run-004' }));
  const detail = await screen.findByRole('table', { name: 'Chi tiết RUN' });
  expect(within(detail).getByText('preview-snapshot-42')).toBeInTheDocument();
});

test('list error recovers through the existing retry button', async () => {
  show();
  scenario('error');
  expect(await screen.findByRole('alert', { name: 'Tải dữ liệu thất bại' })).toBeInTheDocument();
  expect(await screen.findByText('Không thể tải dữ liệu. Vui lòng thử lại.')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByText('Tổng: 45 RUN')).toBeInTheDocument();
  expect(screen.queryByText('Tải dữ liệu thất bại')).not.toBeInTheDocument();
});

test('detail loading uses the existing modal', async () => {
  show();
  scenario('detail-loading');
  fireEvent.click(await screen.findByRole('button', { name: 'Xem RUN preview-run-045' }));
  expect(await screen.findByText('Đang tải chi tiết RUN…')).toBeInTheDocument();
});

test('detail error recovers through the existing modal retry', async () => {
  show();
  scenario('detail-error');
  fireEvent.click(await screen.findByRole('button', { name: 'Xem RUN preview-run-045' }));
  expect(await screen.findByText('Không thể tải dữ liệu. Vui lòng thử lại.')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByRole('table', { name: 'Chi tiết RUN' })).toBeInTheDocument();
});
