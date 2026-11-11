import React from 'react';
import { render, screen } from '@testing-library/react';
import { runStatus } from 'api/admin/data-mining/mining-run-test-data';
import MiningRunStatusPanel from './MiningRunStatusPanel';
beforeEach(() => Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) }));
it.each(['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'LEGACY_UNVERIFIED'] as const)('renders %s faithfully with raw UTC/null values', status => {
  render(<MiningRunStatusPanel report={runStatus(status)}/>);
  expect(screen.getByText('2026-11-07T05:00:00.123456Z')).toBeVisible(); expect(screen.getAllByText('Chưa có dữ liệu').length).toBeGreaterThan(0);
  expect(screen.queryByText(/hoàn tất với 0 patterns/) !== null).toBe(status === 'SUCCEEDED');
});
it('leaves counters to the metrics panel and ignores raw failure message text', () => {
  const report = runStatus('FAILED'); report.patternCount = '18446744073709551615'; report.failure = { code: 'UNKNOWN', message: 'SQL /private' };
  render(<MiningRunStatusPanel report={report}/>); expect(screen.queryByText(report.patternCount)).not.toBeInTheDocument(); expect(screen.queryByText(/SQL/)).not.toBeInTheDocument();
});
