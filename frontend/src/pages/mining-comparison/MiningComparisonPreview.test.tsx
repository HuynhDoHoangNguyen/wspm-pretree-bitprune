import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { readFileSync } from 'fs';
import path from 'path';
import { runInNewContext } from 'vm';
import ts from 'typescript';
import MiningComparisonPreview from './MiningComparisonPreview';

jest.mock('recharts', () => ({ ...jest.requireActual('recharts'), ResponsiveContainer: ({ children }: { children: React.ReactElement }) =>
  jest.requireActual('react').cloneElement(children, { width: 500, height: 280 }) }));
jest.mock('stores/use-admin-auth-store', () => ({ __esModule: true, default: () => { throw new Error('Preview accessed authentication'); } }));
jest.mock('../mining-run/MiningRun.api', () => ({
  listMiningRuns: () => { throw new Error('Preview accessed real API'); },
  getMiningRun: () => { throw new Error('Preview accessed real API'); },
}));
jest.setTimeout(15000);
const resizeObserver = Object.getOwnPropertyDescriptor(window, 'ResizeObserver');
beforeAll(() => Object.defineProperty(window, 'ResizeObserver', {
  configurable: true,
  value: class { observe() {} unobserve() {} disconnect() {} },
}));
afterAll(() => {
  if (resizeObserver) Object.defineProperty(window, 'ResizeObserver', resizeObserver);
  else Reflect.deleteProperty(window, 'ResizeObserver');
});
const originalFetch = global.fetch;
const originalEvents = global.EventSource;
let network: jest.Mock;
beforeEach(() => {
  network = jest.fn(() => { throw new Error('Unexpected preview network'); });
  global.fetch = network;
  global.EventSource = network as unknown as typeof EventSource;
});
afterEach(() => {
  expect(network).not.toHaveBeenCalled();
  global.fetch = originalFetch;
  global.EventSource = originalEvents;
});

test('offline preview supports loading, empty, error, retry and no suitable pair', async () => {
  render(<MiningComparisonPreview/>);
  expect(screen.getByText('Dữ liệu minh họa — không phải kết quả Mining thực tế.')).toBeInTheDocument();
  await screen.findByLabelText('RUN A — WSPM_PreTree');
  const select = screen.getByLabelText('Tình huống preview');
  fireEvent.change(select, { target: { value: 'loading' } });
  expect(screen.getByRole('status')).toHaveTextContent('Đang tải RUN so sánh');
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  fireEvent.change(select, { target: { value: 'empty' } });
  expect(await screen.findByText('Chưa có RUN để so sánh.')).toBeInTheDocument();
  fireEvent.change(select, { target: { value: 'no-pair' } });
  expect(await screen.findByText(/Không có RUN phù hợp:/)).toBeInTheDocument();
  fireEvent.change(select, { target: { value: 'error' } });
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByLabelText('RUN A — WSPM_PreTree')).toHaveValue('preview-pretree');
});

const appSource = readFileSync(path.join(process.cwd(), 'src/App.tsx'), 'utf8');
const declaration = appSource.match(/const MiningComparisonPreview = [\s\S]*?;\r?\n/)?.[0];
test.each([
  ['development', 'true', true], ['development', 'false', false], ['development', undefined, false],
  ['production', 'true', false], ['test', 'true', false],
])('preview guard for %s / %s', (mode, flag, enabled) => {
  expect(declaration).toBeDefined();
  const lazy = jest.fn(() => ({}));
  const result = { enabled: false };
  const js = ts.transpileModule(`${declaration}\nresult.enabled = MiningComparisonPreview !== null;`, {
    compilerOptions: { target: ts.ScriptTarget.ES2017, module: ts.ModuleKind.CommonJS },
  }).outputText;
  runInNewContext(js, { process: { env: { NODE_ENV: mode, REACT_APP_ENABLE_MINING_COMPARISON_PREVIEW: flag } }, React: { lazy }, result });
  expect(result.enabled).toBe(enabled);
  expect(lazy).toHaveBeenCalledTimes(enabled ? 1 : 0);
});

test('preview route is guarded, independent of Client/Admin, and preserves the TV3-05 route', () => {
  expect(appSource).toMatch(/\{MiningComparisonPreview && <Route path="\/__preview\/mining-comparison"/);
  expect(appSource.indexOf('path="/__preview/mining-comparison"')).toBeLessThan(appSource.indexOf('path="/" element={<Client/>}'));
  expect(appSource).toContain('path="/__preview/mining-runs"');
  expect(appSource).toContain("import('pages/mining-comparison/MiningComparisonPreview')");
  expect(appSource).not.toContain("import MiningComparisonPreview from");
});
