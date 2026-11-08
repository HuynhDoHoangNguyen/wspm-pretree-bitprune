import React from 'react';
import { render, screen, within } from '@testing-library/react';
import { legacyMeasuredRun, measuredRun, runStatus } from 'api/admin/data-mining/mining-run-test-data';
import MiningRunMetricsPanel from './MiningRunMetricsPanel';

beforeEach(() => Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) }));
it.each(['WSPM_PRETREE', 'WSPM_BITPRUNE'] as const)('shows exact nine observations, units and scopes for %s', algorithm => {
  render(<MiningRunMetricsPanel report={measuredRun(algorithm)}/>);
  expect(screen.getByRole('heading', { name: 'Số đo lần chạy' })).toBeVisible();
  expect(within(screen.getByRole('region', { name: 'Số đo lần chạy' })).getAllByRole('group')).toHaveLength(9);
  expect(screen.getByText('18.446.744.073.709.551.615')).toHaveAttribute('value', '18446744073709551615');
  expect(screen.getByText('9.007.199.254.740.993')).toHaveAttribute('value', '9007199254740993');
  expect(screen.getByText('JVM heap lấy mẫu trong thuật toán (bytes)')).toBeVisible();
  expect(screen.getByText(/0 ms có thể là phép đo dưới 1 ms/)).toBeVisible();
  expect(screen.getByText(/scope thời gian có phần lồng nhau/)).toBeVisible();
  expect(screen.getByText(/Không phải RSS, RAM vật lý/)).toBeVisible();
  expect(screen.queryByText('Phạm vi đo chưa xác định')).not.toBeInTheDocument();
});
it('preserves historical values but never assigns current worker/heap/counter scope', () => {
  render(<MiningRunMetricsPanel report={legacyMeasuredRun()}/>);
  expect(screen.getByText('Phạm vi đo chưa xác định')).toBeVisible(); expect(screen.getByText('18.446.744.073.709.551.615')).toBeVisible();
  expect(screen.queryByText(/JVM heap lấy mẫu trong thuật toán/)).not.toBeInTheDocument();
  expect(screen.queryByText(/loại trừ F1/)).not.toBeInTheDocument(); expect(screen.queryByText('Chưa có bước ghi kết quả')).not.toBeInTheDocument();
});
it.each(['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'LEGACY_UNVERIFIED'] as const)('renders absent metrics for %s without false zero', state => {
  const report = runStatus(state); report.patternCount = null;
  render(<MiningRunMetricsPanel report={report}/>);
  expect(screen.getAllByText('Chưa có số đo')).toHaveLength(9); expect(screen.queryByText('0', { exact: true })).not.toBeInTheDocument();
});
it('shows a measured zero output distinctly from missing output', () => {
  const report = measuredRun(); report.metrics.outputIoMs = '0';
  render(<MiningRunMetricsPanel report={report}/>);
  const row = screen.getByRole('group', { name: 'Ghi output' });
  expect(within(row).getByText('0')).toHaveAttribute('value', '0'); expect(screen.queryByText('Chưa có bước ghi kết quả')).not.toBeInTheDocument();
});
