import { build } from 'esbuild';
import { chromium } from 'playwright-core';
import { createServer } from 'node:http';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

// Browser UI + mocked HTTP contract smoke. Does not start backend/MySQL or use owner credentials/data.
const here = path.dirname(fileURLToPath(import.meta.url)), root = path.resolve(here, '../../..');
const metricsMode = process.argv.includes('--metrics');
const evidenceRoot = path.join(root, '.agent-workspace/phase-wdm');
mkdirSync(evidenceRoot, { recursive: true });
const evidence = mkdtempSync(path.join(evidenceRoot, metricsMode ? 'WDM-05.9-browser-' : 'WDM-05.8-browser-'));
console.log('Evidence directory: ' + path.relative(root, evidence));
const bundle = await build({ entryPoints: [path.join(here, 'execution-ui-harness.tsx')], bundle: true, write: false, platform: 'browser',
  tsconfig: path.join(root, 'frontend/tsconfig.json'), define: { 'process.env.NODE_ENV': '"test"', 'process.env.REACT_APP_WDM_API_BASE_URL': '"/api"' } });
const server = createServer((req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  if (req.url === '/app.js') { res.setHeader('Content-Type', 'application/javascript'); res.end(bundle.outputFiles[0].contents); }
  else { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end('<!doctype html><html lang="vi"><meta charset="utf-8"><div id="root"></div><script src="/app.js"></script></html>'); }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const base = 'http://127.0.0.1:' + server.address().port;
const id = '11111111-2222-4333-8444-555555555555', now = '2026-11-07T05:00:00.123456Z';
const config = { mode: 'PERCENT', configuredValue: 14, relativeFraction: .14, effectiveThreshold: .42 };
const validation = { snapshotId: id, algorithm: 'WSPM_PRETREE', artifactVersion: 'CONVERSION_ARTIFACT_V1', sequenceSha256: 'a'.repeat(64),
  mappingSha256: 'b'.repeat(64), minimumSupport: config, sequenceCount: '3', configurationStatus: 'SUPPORTED' };
function status(state) { return { runId: id, snapshotId: id, algorithm: 'WSPM_PRETREE', status: state,
  configuration: { minsupMode: 'PERCENT', configuredValue: 14, relativeFraction: .14, effectiveThreshold: .42, maxW: .6, minW: .4, meanW: .5 },
  createdAt: now, startedAt: now, finishedAt: state === 'SUCCEEDED' ? now : null, patternCount: state === 'SUCCEEDED' ? '0' : null, candidateCount: null,
  metrics: { datasetIoMs: null, preprocessingMs: null, dfsMiningMs: null, completeAlgorithmMs: null, outputIoMs: null, totalExecutionMs: null, peakSampledJvmHeapBytes: null },
  measurement: { contractVersion: null, timeUnit: 'MILLISECONDS', memoryUnit: 'BYTES', datasetIoScope: null, totalExecutionScope: null, heapScope: null }, failure: null }; }
function metricFixture(mode) {
  if (mode === 'missing') return { ...status('SUCCEEDED'), patternCount: null };
  const report = { ...status(mode === 'legacy' ? 'LEGACY_UNVERIFIED' : 'SUCCEEDED'), algorithm: mode === 'bitprune' ? 'WSPM_BITPRUNE' : 'WSPM_PRETREE',
    patternCount: '0', candidateCount: '9007199254740993', metrics: { datasetIoMs: '0', preprocessingMs: '2', dfsMiningMs: '3', completeAlgorithmMs: '6',
      outputIoMs: mode === 'output-zero' ? '0' : null, totalExecutionMs: '10', peakSampledJvmHeapBytes: '18446744073709551615' },
    measurement: { contractVersion: 'WDM-05.6', timeUnit: 'MILLISECONDS', memoryUnit: 'BYTES', datasetIoScope: 'WORKER_VERIFIED_RAW_SEQUENCE_MAPPING_READS',
      totalExecutionScope: 'POST_CLAIM_REFRESH_THROUGH_ENGINE_RETURN', heapScope: 'ALGORITHM_JVM_WIDE_USED_HEAP_SAMPLING' } };
  if (mode === 'legacy') report.measurement = status('LEGACY_UNVERIFIED').measurement;
  return report;
}
let browser;
try {
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  page.setDefaultTimeout(15000);
  let posts = 0, gets = 0, uncertain = false, readError = false, metricMode = null; const requests = [], errors = [];
  const cases = ['supported create/double click', 'queued/running/succeeded polling', 'direct refresh GET only', 'read error retains last snapshot', 'lost POST never replays', 'customer guard'];
  page.on('pageerror', e => { errors.push(e.message); process.stdout.write('Browser error: ' + e.message + '\n'); });
  await page.route('**/api/**', async route => {
    const req = route.request(), endpoint = new URL(req.url()).pathname; requests.push({ method: req.method(), endpoint });
    assert.equal(req.headers().authorization, 'Bearer fixture-only-token');
    const reply = (data, http = 200) => route.fulfill({ status: http, contentType: 'application/json', headers: { 'X-Request-Id': 'browser-id' },
      body: JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'browser-id' }) });
    if (req.method() === 'GET' && endpoint.endsWith('/mining-execution/capacity')) return reply({ enabled: false, scope: 'CURRENT_BACKEND_INSTANCE', observedAt: now, snapshot: null });
    if (req.method() === 'GET' && endpoint.endsWith('/dataset-snapshots/' + id + '/storage')) return reply({ snapshotId: id, sourceName: 'Fixture snapshot', root: '/fixture-only/snapshots',
      binding: 'CURRENT_SNAPSHOT_PUBLICATION', verification: 'METADATA_ONLY_NOT_INTEGRITY_VERIFIED', files: [] });
    if (req.method() === 'GET' && endpoint.endsWith('/dataset-snapshots/' + id)) return reply({ snapshotId: id, sourceName: 'Fixture snapshot', sourceType: 'CSV', validationStatus: 'VALID',
      conversionStatus: 'SUCCEEDED', statisticsStatus: 'COMPUTED', sequenceCount: '3', rawSha256: 'a'.repeat(64), rawSizeBytes: '64', fromTime: null, toTime: null, createdAt: now,
      rawAvailability: 'AVAILABLE', provenance: { contractVersion: null, acquisition: null, sourceTimeZone: null, timeField: null, statusPolicy: null } });
    if (endpoint.endsWith('/configuration-validation')) return reply(validation);
    if (req.method() === 'POST' && endpoint.endsWith('/mining-runs')) {
      posts++; assert.deepEqual(req.postDataJSON(), { snapshotId: id, algorithm: 'WSPM_PRETREE', minsupMode: 'PERCENT', minsupValue: 14 });
      if (uncertain) return route.abort();
      return reply({ runId: id, snapshotId: id, algorithm: 'WSPM_PRETREE', status: 'QUEUED', createdAt: now }, 202);
    }
    if (req.method() === 'GET' && endpoint.endsWith('/' + id)) {
      gets++;
      if (readError) return route.fulfill({ status: 503, contentType: 'application/json', headers: { 'X-Request-Id': 'browser-id' },
        body: JSON.stringify({ status: 503, code: 'SERVICE_UNAVAILABLE', message: 'Service is temporarily unavailable.', path: endpoint, requestId: 'browser-id', details: [] }) });
      return reply(metricMode ? metricFixture(metricMode) : status(gets === 1 ? 'QUEUED' : gets === 2 ? 'RUNNING' : 'SUCCEEDED'));
    }
    throw new Error('Unexpected fixture request: ' + endpoint);
  });
  await page.goto(base + '/admin/mining-runs?snapshotId=' + id);
  await page.getByLabel(/Giá trị minsup/).fill('14');
  assert.equal(await page.getByRole('button', { name: 'Tạo lần chạy', exact: true }).isDisabled(), true);
  await page.getByRole('button', { name: 'Kiểm tra cấu hình', exact: true }).click();
  await page.getByRole('button', { name: 'Tạo lần chạy', exact: true }).waitFor();
  await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent === 'Tạo lần chạy' && !b.disabled));
  await page.getByRole('button', { name: 'Tạo lần chạy', exact: true }).evaluate(button => { button.click(); button.click(); });
  await page.waitForURL('**/admin/mining-runs/' + id);
  await page.getByText(/hoàn tất với 0 patterns/).waitFor(); assert.equal(posts, 1); assert.equal(gets, 3);
  const header = page.getByRole('region', { name: 'Tổng quan lần chạy', exact: true });
  await header.getByRole('heading', { name: 'Chi tiết lần chạy khai phá', exact: true }).waitFor();
  assert.equal(await header.getByRole('button', { name: 'Sao chép link lần chạy', exact: true }).isVisible(), true);
  assert.equal(await header.locator('time').getAttribute('datetime') !== null, true);
  assert.equal(await header.getByText(base + '/admin/mining-runs/' + id, { exact: true }).count(), 0);
  assert.equal(await page.getByRole('region', { name: 'Dữ liệu liên quan', exact: true }).getByRole('link', { name: 'Dataset nguồn', exact: true }).getAttribute('href'), '/admin/datasets/' + id);
  await page.screenshot({ path: path.join(evidence, 'completed.png'), fullPage: true });
  await header.screenshot({ path: path.join(evidence, 'header-desktop.png') });
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
  await page.screenshot({ path: path.join(evidence, 'completed-mobile.png'), fullPage: true });
  await header.screenshot({ path: path.join(evidence, 'header-mobile.png') });
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.reload(); await page.getByText(/hoàn tất với 0 patterns/).waitFor(); assert.equal(posts, 1);
  readError = true; await page.getByRole('button', { name: /Đọc lại/ }).click(); await page.getByText(/Theo dõi đã tạm dừng/).waitFor();
  assert.equal(await page.getByText(/hoàn tất với 0 patterns/).isVisible(), true); readError = false;
  uncertain = true; await page.goto(base + '/admin/mining-runs?snapshotId=' + id);
  await page.getByLabel(/Giá trị minsup/).fill('14'); await page.getByRole('button', { name: 'Kiểm tra cấu hình', exact: true }).click();
  await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent === 'Tạo lần chạy' && !b.disabled));
  await page.getByRole('button', { name: 'Tạo lần chạy', exact: true }).click(); await page.getByText('Chưa xác định kết quả tạo run', { exact: true }).waitFor();
  assert.equal(await page.getByRole('button', { name: 'Tạo lần chạy', exact: true }).isDisabled(), true); assert.equal(posts, 2);
  const beforeGuard = requests.length; await page.goto(base + '/admin/mining-runs/' + id + '?fixtureRole=CUSTOMER');
  await page.getByText('Không có quyền truy cập', { exact: true }).waitFor(); assert.equal(requests.length, beforeGuard);
  if (metricsMode) {
    for (const mode of ['pretree', 'bitprune']) {
      metricMode = mode; const before = gets;
      await page.goto(base + '/admin/mining-runs/' + id); await page.getByText('18.446.744.073.709.551.615', { exact: true }).waitFor();
      assert.equal(gets, before + 1); assert.equal(await page.locator('[data-metric]').count(), 9);
      assert.equal(await page.locator('[data-metric="candidateCount"] data').getAttribute('value'), '9007199254740993');
      assert.equal(await page.locator('[data-metric="peakSampledJvmHeapBytes"] data').getAttribute('value'), '18446744073709551615');
      assert.equal(await page.locator('[data-metric="datasetIoMs"] data').getAttribute('value'), '0');
      await page.getByText(mode === 'pretree' ? 'Ứng viên WSPM_PreTree' : 'Extensions đã sinh WSPM_BitPrune', { exact: true }).waitFor();
      await page.getByText('Chưa có bước ghi kết quả', { exact: true }).waitFor();
    }
    await page.screenshot({ path: path.join(evidence, 'metrics-current.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
    await page.screenshot({ path: path.join(evidence, 'metrics-mobile.png'), fullPage: true }); await page.setViewportSize({ width: 1280, height: 900 });
    metricMode = 'legacy'; await page.goto(base + '/admin/mining-runs/' + id);
    await page.getByText('Phạm vi đo chưa xác định', { exact: true }).waitFor();
    assert.equal(await page.getByText('18.446.744.073.709.551.615', { exact: true }).isVisible(), true);
    assert.equal(await page.getByText('JVM heap lấy mẫu trong thuật toán (bytes)', { exact: true }).count(), 0);
    assert.equal(await page.getByText('Chưa có bước ghi kết quả', { exact: true }).count(), 0);
    await page.screenshot({ path: path.join(evidence, 'metrics-legacy.png'), fullPage: true });
    metricMode = 'missing'; await page.goto(base + '/admin/mining-runs/' + id); await page.getByText('Run đã hoàn tất; bản ghi chưa có số đo.', { exact: true }).waitFor();
    assert.equal(await page.getByText('Chưa có số đo', { exact: true }).count(), 9);
    metricMode = 'output-zero'; await page.goto(base + '/admin/mining-runs/' + id);
    await page.locator('[data-metric="outputIoMs"] data[value="0"]').waitFor();
    readError = true; const beforeError = gets; await page.getByRole('button', { name: /Đọc lại/ }).click(); await page.getByText(/Theo dõi đã tạm dừng/).waitFor();
    assert.equal(gets, beforeError + 1); assert.equal(await page.locator('[data-metric="outputIoMs"] data').getAttribute('value'), '0');
    assert.equal(await page.locator('[data-metric="peakSampledJvmHeapBytes"] data').getAttribute('value'), '18446744073709551615');
    cases.push('verified metrics both algorithms', 'legacy unverified scopes', 'missing versus measured zero', 'stale metrics same snapshot');
  }
  assert.deepEqual(errors, []);
  writeFileSync(path.join(evidence, 'RESULT.json'), JSON.stringify({ status: 'PASS', scope: 'Actual UI and shared client/guard with fixture identity and mocked HTTP; no real backend/DB/Auth verification',
    cases, requests }, null, 2));
  process.stdout.write('PASS: ' + cases.length + ' browser UI/HTTP-fixture groups\n');
} finally { await browser?.close(); await new Promise(resolve => server.close(resolve)); }
