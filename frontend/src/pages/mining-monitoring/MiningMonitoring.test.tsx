import React from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MiningMonitoring from './MiningMonitoring';
import { monitoringRuns, PREVIEW_OBSERVED_AT } from './MiningMonitoring.fixtures';
import type { MiningMonitoringSource } from './MiningMonitoring.types';
import type { MiningRunDetail } from '../mining-run/MiningRun.types';

function sourceFor(runs = monitoringRuns): MiningMonitoringSource {
  return {
    list: async () => ({ items: runs, page: 0, size: 20, totalElements: runs.length, totalPages: 1 }),
    detail: async id => {
      const run = runs.find(item => item.runId === id);
      if (!run) throw new Error('Unknown RUN');
      return run;
    },
  };
}
function show(source = sourceFor()) {
  return render(<MemoryRouter><MiningMonitoring source={source} observedAt={PREVIEW_OBSERVED_AT}/></MemoryRouter>);
}
function detail() {
  return screen.getByRole('region', { name: 'Chi tiết RUN đang theo dõi' });
}

test('renders the RUN table, running state, timestamp elapsed and history link without fake progress', async () => {
  show();
  expect(screen.getByRole('heading', { level: 1, name: 'Giám sát quá trình chạy Mining' })).toBeInTheDocument();
  const table = await screen.findByRole('table', { name: 'Danh sách RUN theo dõi' });
  expect(within(table).getAllByRole('row')).toHaveLength(8);
  await screen.findByRole('region', { name: 'Chi tiết RUN đang theo dõi' });
  expect(within(detail()).getByText('Đang thực thi thuật toán')).toBeInTheDocument();
  expect(within(detail()).getByText('20.000 ms')).toBeInTheDocument();
  expect(within(detail()).getByLabelText('Mining đang chạy')).toBeInTheDocument();
  expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  expect(detail().textContent).not.toMatch(/\d+\s*%/);
  expect(screen.getByRole('link', { name: 'Xem lịch sử RUN' })).toHaveAttribute('href', '/admin/mining-runs');
});

test('selecting queued, succeeded and failed RUNs renders only relevant execution information', async () => {
  show();
  await screen.findByRole('region', { name: 'Chi tiết RUN đang theo dõi' });
  fireEvent.click(screen.getByRole('button', { name: 'Xem chi tiết monitor-queued' }));
  expect(await screen.findByText('RUN chưa bắt đầu; chưa có thời gian thực thi.')).toBeInTheDocument();
  expect(within(detail()).queryByText(/Số pattern:/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Xem chi tiết monitor-succeeded' }));
  expect(await screen.findByText('Tổng thực thi cuối cùng: 9.700 ms')).toBeInTheDocument();
  expect(within(detail()).getByText('Số pattern: 24')).toBeInTheDocument();
  expect(within(detail()).getByText('Đỉnh JVM heap lấy mẫu: 33.554.432 bytes')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Xem chi tiết monitor-failed' }));
  await screen.findByText('RUN thất bại');
  expect(within(detail()).queryByText(/Số pattern:/)).not.toBeInTheDocument();
  expect(within(detail()).queryByText(/Tổng thực thi cuối cùng:/)).not.toBeInTheDocument();
  expect(within(detail()).getByText(/chi tiết chẩn đoán không được công bố/)).toBeInTheDocument();
});

test('missing timestamps, future timestamps and missing metrics stay unavailable', async () => {
  show();
  await screen.findByRole('region', { name: 'Chi tiết RUN đang theo dõi' });
  for (const id of ['monitor-missing-timestamp', 'monitor-future-start']) {
    fireEvent.click(screen.getByRole('button', { name: `Xem chi tiết ${id}` }));
    await screen.findByRole('region', { name: 'Chi tiết RUN đang theo dõi' });
    expect(within(detail()).getByText('Thời gian theo timestamp')).toBeInTheDocument();
    expect(within(detail()).getAllByRole('definition')[5]).toHaveTextContent('Chưa có dữ liệu');
  }
  fireEvent.click(screen.getByRole('button', { name: 'Xem chi tiết monitor-missing-metrics' }));
  expect(await screen.findByText('Số pattern: Chưa có dữ liệu')).toBeInTheDocument();
  expect(within(detail()).getByText('Tổng thực thi cuối cùng: Chưa có dữ liệu')).toBeInTheDocument();
  expect(screen.queryByText(/NaN|Infinity/)).not.toBeInTheDocument();
});

test('late detail responses cannot replace the currently selected RUN', async () => {
  let finish: (run: MiningRunDetail) => void = () => undefined;
  const source = sourceFor();
  source.detail = jest.fn(id => id === monitoringRuns[0].runId
    ? new Promise<MiningRunDetail>(resolve => { finish = resolve; })
    : Promise.resolve(monitoringRuns[2]));
  show(source);
  await screen.findByText('Đang tải chi tiết RUN…');
  fireEvent.click(screen.getByRole('button', { name: 'Xem chi tiết monitor-succeeded' }));
  await screen.findByText('Số pattern: 24');
  await act(async () => finish(monitoringRuns[0]));
  expect(within(detail()).queryByText('Đang thực thi thuật toán')).not.toBeInTheDocument();
  expect(within(detail()).getByText('Số pattern: 24')).toBeInTheDocument();
});

test('failed RUNs never display arbitrary errors or stale success metrics', async () => {
  show(sourceFor([{ ...monitoringRuns[3], errorMessage: 'secret-token at /private/dataset.csv', patternCount: 999 }]));
  await screen.findByText('RUN thất bại');
  expect(screen.queryByText(/secret-token|private\/dataset/)).not.toBeInTheDocument();
  expect(screen.queryByText(/999/)).not.toBeInTheDocument();
});

test('detail clicks focus and scroll to the panel, identify selection, close and reopen correctly', async () => {
  const descriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView');
  const scroll = jest.fn();
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: scroll });
  try {
    show();
    await screen.findByRole('region', { name: 'Chi tiết RUN đang theo dõi' });
    const button = screen.getByRole('button', { name: 'Xem chi tiết monitor-succeeded' });
    fireEvent.click(button);
    expect(screen.getByRole('region', { name: 'Panel chi tiết RUN' })).toHaveFocus();
    expect(scroll).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
    expect(button).toHaveAttribute('aria-pressed', 'true');
    expect(button).toHaveAttribute('aria-expanded', 'true');
    expect(button).toHaveTextContent('Đang xem chi tiết');
    expect(await screen.findByText('Số pattern: 24')).toBeInTheDocument();
    expect(within(detail()).getByText('monitor-succeeded', { exact: true })).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Đóng chi tiết' }));
    expect(screen.queryByRole('region', { name: 'Panel chi tiết RUN' })).not.toBeInTheDocument();
    expect(button).toHaveFocus();
    expect(button).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(button);
    expect(await screen.findByText('Số pattern: 24')).toBeInTheDocument();
    fireEvent.click(button);
    expect(scroll).toHaveBeenCalledTimes(3);
  } finally {
    if (descriptor) Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', descriptor);
    else Reflect.deleteProperty(HTMLElement.prototype, 'scrollIntoView');
  }
});
