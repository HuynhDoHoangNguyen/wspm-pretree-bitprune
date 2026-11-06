import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { readFileSync } from 'fs';
import path from 'path';
import { runInNewContext } from 'vm';
import ts from 'typescript';
import MiningMonitoringPreview from './MiningMonitoringPreview';

jest.mock('stores/use-admin-auth-store', () => ({ __esModule: true, default: () => { throw new Error('Unexpected authentication'); } }));
jest.mock('../mining-run/MiningRun.api', () => ({
  listMiningRuns: () => { throw new Error('Unexpected list API'); },
  getMiningRun: () => { throw new Error('Unexpected detail API'); },
}));
const originalFetch = global.fetch;
const originalEvents = global.EventSource;
const originalSocket = global.WebSocket;
let network: jest.Mock;
const originalHistoryFlag = process.env.REACT_APP_ENABLE_MINING_PREVIEW;
beforeEach(() => {
  network = jest.fn(() => { throw new Error('Unexpected network access'); });
  global.fetch = network;
  global.EventSource = network as unknown as typeof EventSource;
  global.WebSocket = network as unknown as typeof WebSocket;
});
afterEach(() => {
  expect(network).not.toHaveBeenCalled();
  global.fetch = originalFetch;
  global.EventSource = originalEvents;
  global.WebSocket = originalSocket;
  if (originalHistoryFlag === undefined) delete process.env.REACT_APP_ENABLE_MINING_PREVIEW;
  else process.env.REACT_APP_ENABLE_MINING_PREVIEW = originalHistoryFlag;
});

test('offline preview supports empty, loading, list error and detail error with retries', async () => {
  render(<MemoryRouter><MiningMonitoringPreview/></MemoryRouter>);
  expect(screen.getByText('Dữ liệu minh họa — không phải kết quả Mining thực tế.')).toBeInTheDocument();
  await screen.findByRole('region', { name: 'Chi tiết RUN đang theo dõi' });
  const select = screen.getByLabelText('Tình huống preview');
  fireEvent.change(select, { target: { value: 'loading' } });
  expect(screen.getByRole('status')).toHaveTextContent('Đang tải danh sách RUN');
  expect(screen.queryByRole('region', { name: 'Chi tiết RUN đang theo dõi' })).not.toBeInTheDocument();
  fireEvent.change(select, { target: { value: 'empty' } });
  expect(await screen.findByText('Chưa có RUN để theo dõi.')).toBeInTheDocument();
  fireEvent.change(select, { target: { value: 'error' } });
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại', exact: true }));
  await screen.findByRole('region', { name: 'Chi tiết RUN đang theo dõi' });
  fireEvent.change(select, { target: { value: 'detail-error' } });
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại chi tiết' }));
  expect(await screen.findByRole('region', { name: 'Chi tiết RUN đang theo dõi' })).toBeInTheDocument();
});

test.each([
  ['queued', 'Đang chờ thực thi'], ['running', 'Đang thực thi thuật toán'],
  ['succeeded', 'Hoàn thành thành công'], ['failed', 'Thực thi thất bại'],
])('scenario %s uses contract status and clears the prior state', async (scenario, label) => {
  render(<MemoryRouter><MiningMonitoringPreview/></MemoryRouter>);
  await screen.findByRole('region', { name: 'Chi tiết RUN đang theo dõi' });
  fireEvent.change(screen.getByLabelText('Tình huống preview'), { target: { value: scenario } });
  await screen.findByRole('region', { name: 'Chi tiết RUN đang theo dõi' });
  expect(screen.getByText(label, { exact: true })).toBeInTheDocument();
  expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
});

const appSource = readFileSync(path.join(process.cwd(), 'src/App.tsx'), 'utf8');
const declaration = appSource.match(/const MiningMonitoringPreview = [\s\S]*?;\r?\n/)?.[0];
test.each([
  ['development', 'true', true], ['development', 'false', false], ['development', undefined, false],
  ['production', 'true', false], ['test', 'true', false],
])('preview registration guard for %s / %s', (mode, flag, enabled) => {
  expect(declaration).toBeDefined();
  const lazy = jest.fn(() => ({}));
  const result = { enabled: false };
  const js = ts.transpileModule(`${declaration}\nresult.enabled = MiningMonitoringPreview !== null;`, {
    compilerOptions: { target: ts.ScriptTarget.ES2017, module: ts.ModuleKind.CommonJS },
  }).outputText;
  runInNewContext(js, { process: { env: { NODE_ENV: mode, REACT_APP_ENABLE_MINING_MONITORING_PREVIEW: flag } }, React: { lazy }, result });
  expect(result.enabled).toBe(enabled);
  expect(lazy).toHaveBeenCalledTimes(enabled ? 1 : 0);
});

test('route is outside network layouts; existing preview routes remain present', () => {
  expect(appSource).toMatch(/\{MiningMonitoringPreview && <Route path="\/__preview\/mining-monitoring"/);
  expect(appSource.indexOf('path="/__preview/mining-monitoring"')).toBeLessThan(appSource.indexOf('path="/" element={<Client/>}'));
  ['mining-runs', 'mining-comparison', 'customer-combos'].forEach(route => expect(appSource).toContain(`path="/__preview/${route}"`));
  expect(appSource).not.toContain('import MiningMonitoringPreview from');
});

test('enabled history preview navigates to TV3-05 preview without production login', async () => {
  process.env.REACT_APP_ENABLE_MINING_PREVIEW = 'true';
  render(<MemoryRouter initialEntries={['/__preview/mining-monitoring']}><Routes>
    <Route path="/__preview/mining-monitoring" element={<MiningMonitoringPreview/>}/>
    <Route path="/__preview/mining-runs" element={<h1>Lịch sử RUN Preview</h1>}/>
    <Route path="/admin/mining-runs" element={<h1>Production login must not be visited</h1>}/>
  </Routes></MemoryRouter>);
  const link = screen.getByRole('link', { name: 'Xem lịch sử RUN' });
  expect(link).toHaveAttribute('href', '/__preview/mining-runs');
  fireEvent.click(link);
  expect(await screen.findByRole('heading', { name: 'Lịch sử RUN Preview' })).toBeInTheDocument();
  expect(screen.queryByText('Production login must not be visited')).not.toBeInTheDocument();
});

test.each(['false', undefined])('disabled history preview (%s) cannot navigate to production', async flag => {
  if (flag === undefined) delete process.env.REACT_APP_ENABLE_MINING_PREVIEW;
  else process.env.REACT_APP_ENABLE_MINING_PREVIEW = flag;
  render(<MemoryRouter><MiningMonitoringPreview/></MemoryRouter>);
  const button = screen.getByRole('button', { name: 'Xem lịch sử RUN' });
  expect(button).toBeDisabled();
  expect(screen.queryByRole('link', { name: 'Xem lịch sử RUN' })).not.toBeInTheDocument();
  expect(button).toHaveAccessibleDescription(/REACT_APP_ENABLE_MINING_PREVIEW=true/);
  await screen.findByRole('region', { name: 'Chi tiết RUN đang theo dõi' });
});

test('switching preview scenario discards selected detail and reopens only the new fixture', async () => {
  render(<MemoryRouter><MiningMonitoringPreview/></MemoryRouter>);
  await screen.findByRole('region', { name: 'Chi tiết RUN đang theo dõi' });
  fireEvent.click(screen.getByRole('button', { name: 'Xem chi tiết monitor-succeeded' }));
  await screen.findByText('Số pattern: 24');
  fireEvent.change(screen.getByLabelText('Tình huống preview'), { target: { value: 'failed' } });
  expect(screen.queryByText('Số pattern: 24')).not.toBeInTheDocument();
  await screen.findByText('RUN thất bại');
  expect(screen.getByText('RUN: monitor-failed')).toBeInTheDocument();
});
