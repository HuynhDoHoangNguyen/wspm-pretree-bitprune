import React from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import MiningComparison from './MiningComparison';
import { comparisonFixture } from './MiningComparison.fixtures';

// Give real Recharts a stable viewport in jsdom; axes/bars/tooltips remain real.
jest.mock('recharts', () => ({ ...jest.requireActual('recharts'), ResponsiveContainer: ({ children }: { children: React.ReactElement }) =>
  jest.requireActual('react').cloneElement(children, { width: 500, height: 280 }) }));
jest.setTimeout(15000);
const resizeObserver = Object.getOwnPropertyDescriptor(window, 'ResizeObserver');
beforeAll(() => Object.defineProperty(window, 'ResizeObserver', {
  configurable: true,
  value: class { observe() { return undefined; } unobserve() { return undefined; } disconnect() { return undefined; } },
}));
afterAll(() => {
  if (resizeObserver) Object.defineProperty(window, 'ResizeObserver', resizeObserver);
  else Reflect.deleteProperty(window, 'ResizeObserver');
});

function show() {
  return render(<MiningComparison source={{ load: async () => comparisonFixture }} preview/>);
}
function row(name: string | RegExp) {
  return within(screen.getByRole('table', { name: 'Bảng so sánh tổng hợp' })).getByRole('row', { name });
}

test('selects a valid pair, labels measurement scopes, renders KPI, charts and exact source values', async () => {
  show();
  expect(await screen.findByLabelText('RUN A — WSPM_PreTree')).toHaveValue('preview-pretree');
  expect(screen.getByLabelText('RUN B — WSPM_BitPrune')).toHaveValue('preview-bitprune');
  expect(row(/Tổng thực thi/)).toHaveTextContent('1.200600-600 ms');
  expect(row(/Đỉnh JVM heap/)).toHaveTextContent('67.108.86433.554.432-33.554.432 bytes');
  expect(row(/Số pattern/)).toHaveTextContent('24240');
  expect(row(/Speedup/)).toHaveTextContent('2 ×');
  expect(row(/Runtime Reduction/)).toHaveTextContent('50 %');
  expect(row(/Memory Reduction/)).toHaveTextContent('50 %');
  expect(screen.getAllByRole('img', { name: /PreTree.*BitPrune/ })).toHaveLength(3);
  expect(screen.getByRole('img', { name: /Thời gian tổng thực thi: PreTree 1.200 ms, BitPrune 600 ms/ })).toBeInTheDocument();
  expect(screen.getByText(/Số pattern bằng nhau chưa chứng minh/)).toBeInTheDocument();
  expect(screen.queryByText(/NaN|Infinity/)).not.toBeInTheDocument();
});

test.each(['preview-other-dataset', 'preview-other-minsup', 'preview-other-parameters', 'preview-running', 'preview-pattern-mismatch', 'preview-no-evidence'])(
  'changing to %s clears previous performance charts and derived metrics', async runId => {
    show();
    await screen.findByLabelText('RUN B — WSPM_BitPrune');
    fireEvent.change(screen.getByLabelText('RUN B — WSPM_BitPrune'), { target: { value: runId } });
    expect(screen.getByText('RUN không đủ điều kiện so sánh')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(row(/Speedup/)).toHaveTextContent('Chưa có dữ liệu');
    expect(row(/Memory Reduction/)).toHaveTextContent('Chưa có dữ liệu');
  }
);

test('missing values, zeros and negative reductions render without fabricated metrics', async () => {
  show();
  const select = await screen.findByLabelText('RUN B — WSPM_BitPrune');
  fireEvent.change(select, { target: { value: 'preview-missing-metrics' } });
  expect(row(/Tổng thực thi/)).toHaveTextContent('Chưa có dữ liệu');
  expect(row(/Speedup/)).toHaveTextContent('Chưa có dữ liệu');
  expect(screen.getAllByText('Chưa có dữ liệu hợp lệ cho biểu đồ này.')).toHaveLength(2);
  fireEvent.change(select, { target: { value: 'preview-zero' } });
  expect(row(/Tổng thực thi/)).toHaveTextContent('1.2000-1.200 ms');
  expect(row(/Speedup/)).toHaveTextContent('Chưa có dữ liệu');
  fireEvent.change(screen.getByLabelText('RUN A — WSPM_PreTree'), { target: { value: 'preview-zero-baseline' } });
  expect(row(/Memory Reduction/)).toHaveTextContent('Chưa có dữ liệu');
  fireEvent.change(screen.getByLabelText('RUN A — WSPM_PreTree'), { target: { value: 'preview-pretree' } });
  fireEvent.change(select, { target: { value: 'preview-slower' } });
  expect(row(/Runtime Reduction/)).toHaveTextContent('-50 %');
  expect(row(/Memory Reduction/)).toHaveTextContent('-50 %');
  expect(screen.queryByText(/NaN|Infinity/)).not.toBeInTheDocument();
});
