import React from 'react';
import { runInThisContext } from 'vm';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import { setWdmAuthAdapter } from 'api/admin/data-mining';
import { runId, runStatus } from 'api/admin/data-mining/mining-run-test-data';
import WorkspaceList from './WorkspaceList';
import MiningRunDetailPage from './MiningRunDetailPage';
import PatternsPage from './PatternsPage';
import PatternDetailPage from './PatternDetailPage';
import { detailFixture } from 'api/admin/data-mining/pattern-detail-test-data';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
Object.assign(globalThis, runInThisContext('({ Headers, Response })'));
const time = '2026-11-08T00:00:00Z';
const row = { runId, snapshotId: runId, sourceName: 'orders.csv', algorithm: 'WSPM_PRETREE', status: 'SUCCEEDED',
  minsupMode: 'PERCENT', configuredValue: 14, patternCount: '9007199254740993', createdAt: time, startedAt: null, finishedAt: null };
function response(data: unknown) { return new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'history-request' }),
  { status: 200, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'history-request' } }); }
beforeEach(() => {
  (useSharedAuth as unknown as jest.Mock).mockReturnValue({ status: 'authenticated', accessToken: 'token', user: { userId: 'admin', roles: ['ADMIN'] }, forbidden: false });
  setWdmAuthAdapter({ getAccessToken: () => 'token' });
  Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) });
  Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: jest.fn(() => ({ observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn() })) });
});
afterEach(() => { setWdmAuthAdapter(null); jest.restoreAllMocks(); });
test('history -> detail -> explorer -> detail -> back preserves filters through actual GET clients/decoders', async () => {
  const calls: string[] = [];
  const metadata = { runId, executionStatus: 'SUCCEEDED', publicationStatus: 'NOT_PUBLISHED',
    manifestVersion: null, resultSha256: null, committedAt: null, completeness: 'UNVERIFIED', stagingReadable: true, publishedReadable: false, eligibleForCombo: false,
    provenance: { version: 'RUN_PROVENANCE_V1', conversionFormatVersion: 'CONVERSION_ARTIFACT_V1', snapshotId: runId, attemptId: runId,
      validationSha256: 'a'.repeat(64), sequenceSha256: 'b'.repeat(64), mappingSha256: 'c'.repeat(64), sequenceCount: '3' },
    storedPatternCount: '1', storedPatternItemCount: '4', storedRunItemCount: '3' };
  const fetchMock = jest.fn(async (url: string, options: RequestInit) => {
    calls.push(url); expect(options.method).toBe('GET'); expect(options.body).toBeUndefined();
    expect((options.headers as Headers).get('Authorization')).toBe('Bearer token');
    if (url.includes('/results')) return response(metadata);
    if (url.includes('/patterns/')) {
      const detail = detailFixture();
      return response({ ...detail, result: metadata, pattern: { ...detail.pattern, runId } });
    }
    if (url.includes('/patterns?')) return response({ result: metadata, page: 1, size: 20, total: '1', items: [{
      ...detailFixture().pattern, runId }] });
    if (url.endsWith('/storage')) return response({ snapshotId: runId, sourceName: 'orders.csv', root: '/test', binding: 'CURRENT_SNAPSHOT_PUBLICATION', verification: 'METADATA_ONLY_NOT_INTEGRITY_VERIFIED', files: [] });
    if (url.includes('/mining-runs?')) return response({ items: [row], page: 2, size: 20, total: 21 });
    if (url.endsWith('/mining-runs/' + runId)) return response(runStatus('SUCCEEDED'));
    throw new Error('Unexpected request');
  });
  const previous = global.fetch; global.fetch = fetchMock as typeof fetch;
  try {
    render(<MemoryRouter initialEntries={['/admin/mining-runs?tab=history&page=2&status=SUCCEEDED&snapshotId=' + runId]}><Routes>
      <Route path="/admin/mining-runs" element={<WorkspaceList runs/>}/><Route path="/admin/mining-runs/:runId" element={<MiningRunDetailPage/>}/>
      <Route path="/admin/patterns" element={<PatternsPage/>}/>
      <Route path="/admin/patterns/:runId/:patternId" element={<PatternDetailPage/>}/>
    </Routes></MemoryRouter>);
    await screen.findByText('orders.csv'); expect(screen.getByText('Patterns (engine): 9007199254740993')).toBeVisible();
    expect(screen.getByText('Minsup phần trăm · 14%')).toBeVisible();
    expect(calls.filter(c => c.includes('/results'))).toHaveLength(0);
    fireEvent.click(screen.getByRole('link', { name: 'Xem run' })); await screen.findByText('Chưa công bố');
    expect(within(screen.getByRole('region', { name: 'Lưu trữ và công bố kết quả' })).getByText(/Không dùng số hàng/)).toBeVisible();
    fireEvent.click(screen.getByRole('link', { name: 'Xem pattern staging' }));
    await screen.findByRole('table', { name: 'Pattern staging' });
    expect(screen.getByText('<(1 2)(1)(3)>')).toBeVisible(); expect(screen.getByText('1.5')).toBeVisible();
    const patternReads = calls.filter(c => c.includes('/patterns'));
    expect(patternReads).toHaveLength(1);
    expect(Object.fromEntries(new URL(patternReads[0], 'https://test.invalid').searchParams)).toEqual({ view: 'staging', page: '1', size: '20' });
    fireEvent.click(screen.getByRole('link', { name: 'Xem chi tiết pattern 18446744073709551615' }));
    await screen.findByRole('region', { name: 'Cấu trúc temporal' });
    expect(screen.getAllByText('Historical product 1')).toHaveLength(2);
    const detailReads = calls.filter(c => c.includes('/patterns/'));
    expect(detailReads).toEqual(['/api/v1/admin/mining-runs/' + runId + '/patterns/18446744073709551615?view=staging']);
    fireEvent.click(screen.getByRole('link', { name: 'Về Pattern Explorer' })); await screen.findByRole('table', { name: 'Pattern staging' });
    fireEvent.click(screen.getByRole('link', { name: 'Chi tiết run' })); await screen.findByText('Chưa công bố');
    fireEvent.click(screen.getByRole('link', { name: 'Lịch sử run' })); await screen.findByText('orders.csv');
    await waitFor(() => expect(calls.filter(c => c.includes('/mining-runs?'))).toHaveLength(2));
    const histories = calls.filter(c => c.includes('/mining-runs?')); expect(histories[1]).toBe(histories[0]);
    expect(histories[0]).toContain('page=2'); expect(histories[0]).toContain('snapshotId=' + runId);
  } finally { global.fetch = previous; }
});
test('snapshot filter validates UUID, resets pagination and clear restores all snapshots', async () => {
  const calls: string[] = []; const previous = global.fetch;
  global.fetch = jest.fn(async (url: string) => { calls.push(url); return response({ items: [], page: 1, size: 20, total: 0 }); }) as typeof fetch;
  try {
    render(<MemoryRouter initialEntries={['/admin/mining-runs?tab=history&page=2']}><WorkspaceList runs/></MemoryRouter>);
    await screen.findByText(/Chưa có run phù hợp/);
    fireEvent.change(screen.getByLabelText('Snapshot ID nguồn'), { target: { value: 'invalid' } });
    fireEvent.submit(screen.getByRole('form', { name: 'Lọc lịch sử theo snapshot' }));
    expect(screen.getByText('Nhập UUID snapshot chuẩn, chữ thường.')).toBeVisible(); expect(calls).toHaveLength(1);
    fireEvent.change(screen.getByLabelText('Snapshot ID nguồn'), { target: { value: runId } });
    fireEvent.submit(screen.getByRole('form', { name: 'Lọc lịch sử theo snapshot' }));
    await waitFor(() => expect(calls).toHaveLength(2)); expect(calls[1]).toContain('page=1'); expect(calls[1]).toContain('snapshotId=' + runId);
    fireEvent.click(screen.getByRole('button', { name: 'Bỏ lọc snapshot' }));
    await waitFor(() => expect(calls).toHaveLength(3)); expect(calls[2]).not.toContain('snapshotId');
  } finally { global.fetch = previous; }
});
