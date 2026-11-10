import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider, setLogger } from 'react-query';
import { authSession } from 'auth/session';
import { runResultApi, WdmApiError } from 'api/admin/data-mining';
import { listMiningRuns } from 'tv3/pages/mining-run/MiningRun.api';
import PatternsPage from './PatternsPage';

jest.mock('api/admin/data-mining', () => ({ ...jest.requireActual('api/admin/data-mining'),
  runResultApi: { patterns: jest.fn(), detail: jest.fn() } }));
jest.mock('tv3/pages/mining-run/MiningRun.api', () => ({ listMiningRuns: jest.fn() }));
const patterns = runResultApi.patterns as jest.MockedFunction<typeof runResultApi.patterns>;
const detail = runResultApi.detail as jest.MockedFunction<typeof runResultApi.detail>;
const list = listMiningRuns as jest.MockedFunction<typeof listMiningRuns>;
const runId = '10000000-0000-4000-8000-000000000001';
const otherRun = '20000000-0000-4000-8000-000000000002';
const patternId = '18446744073709551615';
const result = { runId, executionStatus: 'SUCCEEDED', publicationStatus: 'COMMITTED' as const,
  manifestVersion: 'WDM_RESULT_MANIFEST_V1', resultSha256: 'a'.repeat(64), committedAt: '2026-11-09T10:00:00Z',
  provenance: { version: 'RUN_PROVENANCE_V1' as const, conversionFormatVersion: 'CONVERSION_ARTIFACT_V1', snapshotId: runId,
    attemptId: runId, validationSha256: 'b'.repeat(64), sequenceSha256: 'c'.repeat(64), mappingSha256: 'd'.repeat(64), sequenceCount: '100' },
  storedPatternCount: '1', storedPatternItemCount: '2', storedRunItemCount: '2', completeness: 'COMPLETE_VERIFIED' as const,
  stagingReadable: false, publishedReadable: true, eligibleForCombo: true };
const pattern = { patternId, runId, canonicalPattern: '<(1)(2)>', canonicalPatternHash: 'e'.repeat(64), supportAbs: '53',
  supportRatio: .53, weightedSupport: 2.5474406991260925, patternWeight: .375,
  patternWeightPolicy: 'EQ2_EQ3_ARITHMETIC_MEAN_V1', itemsetCount: 2, patternItemCount: 2 };
const page = { result, items: [pattern], page: 1, size: 20, total: '1' };
const run = { runId, algorithm: 'WSPM_PRETREE' as const, status: 'SUCCEEDED' as const,
  datasetSource: 'fixture.csv', snapshotId: runId, minsupMode: 'PERCENT', minsupConfigured: 2, effectiveThreshold: 2,
  patternCount: 40, candidateCount: 1125, totalExecutionMs: 495, peakSampledJvmHeapBytes: null,
  createdAt: '2026-11-09T10:00:00Z', startedAt: null, finishedAt: null };
const clients: QueryClient[] = [];
beforeAll(() => {
  setLogger({ log: () => undefined, warn: () => undefined, error: () => undefined });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: (query: string) => ({
    matches: false, media: query, onchange: null, addListener: jest.fn(), removeListener: jest.fn(),
    addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn(),
  }) });
  Object.defineProperty(window, 'ResizeObserver', { configurable: true, value: class {
    observe() { return undefined; } unobserve() { return undefined; } disconnect() { return undefined; }
  } });
});
afterAll(() => setLogger(console));
beforeEach(() => {
  authSession.unauthorized();
  authSession.useStore.setState({ status: 'authenticated', forbidden: false, error: null, accessToken: 'test-access',
    user: { userId: 'admin', username: 'fixture', email: null, displayName: 'Admin', status: 'ACTIVE', roles: ['ADMIN'], emailVerified: false } });
  list.mockReset(); patterns.mockReset(); detail.mockReset();
  list.mockResolvedValue({ items: [run, { ...run, runId: otherRun, algorithm: 'WSPM_BITPRUNE' }], page: 0, size: 20, totalElements: 21, totalPages: 2 });
  patterns.mockResolvedValue(page);
  detail.mockResolvedValue({ result, pattern, itemsets: [1, 2].map(i => ({ itemsetOrder: i, items: [{
    itemOrder: 1, itemId: i, productId: 'product-' + i, productNameSnapshot: 'Historical name ' + i,
    skuSnapshot: 'Historical SKU ' + i, itemWeight: i * .25, occurrenceCount: '9007199254740993', sequenceSupport: '53',
  }] })) });
});
afterEach(() => { clients.forEach(c => c.clear()); clients.length = 0; });
function show(path = '/admin/patterns?runId=' + runId) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, cacheTime: 0 } } }); clients.push(client);
  return { client, ...render(<QueryClientProvider client={client}><MantineProvider><MemoryRouter initialEntries={[path]}>
    <PatternsPage/></MemoryRouter></MantineProvider></QueryClientProvider>) };
}
test('loads published results explicitly even when URL requests staging', async () => {
  show('/admin/patterns?runId=' + runId + '&view=staging');
  const table = await screen.findByRole('table', { name: 'Patterns đã công bố' });
  expect(within(table).getByText('ID: ' + patternId)).toBeInTheDocument();
  expect(within(table).getByText('2.5474406991260925')).toBeInTheDocument();
  expect(patterns).toHaveBeenCalledWith(runId, 1, expect.anything(), 'published');
  expect(list).toHaveBeenCalledWith({ page: 0, size: 20, status: 'SUCCEEDED', algorithm: '' }, expect.anything());
});
test('renders loading while pattern read is pending', () => {
  patterns.mockReturnValue(new Promise(() => undefined)); show();
  expect(screen.getByText('Đang tải patterns đã công bố…')).toBeInTheDocument();
});
test('selects a RUN without creating one and starts pattern page one', async () => {
  show('/admin/patterns');
  await screen.findByRole('option', { name: 'WSPM_PRETREE · ' + runId });
  expect(patterns).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('Chọn RUN đã hoàn tất'), { target: { value: runId } });
  await waitFor(() => expect(patterns).toHaveBeenCalledWith(runId, 1, expect.anything(), 'published'));
});
test('keeps RUN and pattern pagination bases separate', async () => {
  patterns.mockImplementation(async (id, p = 1) => ({ ...page, result: { ...result, runId: id, storedPatternCount: '40' }, page: p,
    total: '40', items: Array.from({ length: 20 }, (_, i) => ({ ...pattern, patternId: String((p - 1) * 20 + i + 1) })) }));
  show(); await screen.findByRole('table', { name: 'Patterns đã công bố' });
  fireEvent.click(screen.getByRole('button', { name: 'Trang pattern sau' }));
  await waitFor(() => expect(patterns).toHaveBeenCalledWith(runId, 2, expect.anything(), 'published'));
  fireEvent.click(screen.getByRole('button', { name: 'Trang RUN sau' }));
  await waitFor(() => expect(list).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }), expect.anything()));
});
test('opens inline detail preserving itemset boundaries and large counts', async () => {
  show(); fireEvent.click(await screen.findByRole('button', { name: 'Xem pattern ' + patternId }));
  const table = await screen.findByRole('table', { name: 'Itemset 2' });
  expect(within(table).getByText('Historical name 2')).toBeInTheDocument();
  expect(within(table).getByText('9007199254740993')).toBeInTheDocument();
  expect(screen.getByRole('table', { name: 'Itemset 1' })).toBeInTheDocument();
  expect(detail).toHaveBeenCalledWith(runId, patternId, expect.anything(), 'published');
});
test('renders explicit published zero and empty RUN state', async () => {
  list.mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  patterns.mockResolvedValue({ ...page, items: [], total: '0', result: { ...result, storedPatternCount: '0', eligibleForCombo: false } });
  show(); expect(await screen.findByText('Kết quả đã công bố có 0 pattern.')).toBeInTheDocument();
  expect(screen.getByText('Chưa có RUN thành công trên trang này.')).toBeInTheDocument();
});
test('shows publication failure and request ID without staging fallback', async () => {
  patterns.mockRejectedValue(new WdmApiError('http', 409, 'INVALID_RESOURCE_STATE', 'Unavailable', 'p3-request'));
  show(); expect(await screen.findByText('Mã yêu cầu: p3-request')).toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  expect(patterns).toHaveBeenCalledTimes(1);
});
test('invalid URL page prevents pattern request', () => {
  show('/admin/patterns?runId=' + runId + '&page=0');
  expect(screen.getByText('Trang pattern phải từ 1 đến 100000.')).toBeInTheDocument();
  expect(patterns).not.toHaveBeenCalled();
});
test.each(['anonymous', 'customer'] as const)('blocks %s before any protected read', state => {
  if (state === 'anonymous') authSession.unauthorized();
  else authSession.useStore.setState({ user: { ...authSession.useStore.getState().user!, roles: ['CUSTOMER'] } });
  show(); expect(screen.getByRole('alert')).toBeInTheDocument();
  expect(list).not.toHaveBeenCalled(); expect(patterns).not.toHaveBeenCalled();
});
test('logout hides results and clears page caches', async () => {
  const { client } = show(); await screen.findByRole('table', { name: 'Patterns đã công bố' });
  act(() => authSession.unauthorized());
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  expect(client.getQueriesData(['pattern-reader'])).toHaveLength(0);
});
test('unmount aborts pending pattern read and removes its cache', async () => {
  patterns.mockReturnValue(new Promise(() => undefined));
  const { unmount, client } = show();
  await waitFor(() => expect(patterns).toHaveBeenCalledTimes(1));
  const signal = patterns.mock.calls[0][2]!; unmount();
  expect(signal.aborted).toBe(true); expect(client.getQueriesData(['pattern-reader'])).toHaveLength(0);
});
