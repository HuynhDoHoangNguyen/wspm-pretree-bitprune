import { chromium } from 'playwright-core';
import { createServer } from 'node:http';
import { readFileSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';

// Actual production UI with mocked HTTP; no backend, database or business writes.
const buildDir = path.resolve(process.env.WDM_E2E_BUILD_DIR || 'frontend/build');
const evidence = path.resolve(process.env.WDM_NAV_EVIDENCE_DIR || '.agent-workspace/frontend-navigation-' + Date.now());
assert.ok(existsSync(path.join(buildDir, 'index.html')), 'Build the frontend first');
mkdirSync(evidence, { recursive: true });
const mime = { '.js': 'application/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon' };
const server = createServer((req, res) => {
  const requested = path.resolve(buildDir, '.' + new URL(req.url, 'http://localhost').pathname);
  if (!requested.startsWith(buildDir + path.sep) && requested !== buildDir) { res.writeHead(403).end(); return; }
  const file = existsSync(requested) && path.extname(requested) ? requested : path.join(buildDir, 'index.html');
  try { res.setHeader('Content-Type', mime[path.extname(file)] || 'text/html; charset=utf-8'); res.end(readFileSync(file)); }
  catch { res.writeHead(404).end(); }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const base = 'http://127.0.0.1:' + server.address().port;
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const id = '11111111-2222-4333-8444-555555555555';
const now = '2026-11-10T00:00:00Z';
const user = { userId: 'navigation-smoke', username: 'smoke', email: null, displayName: 'Navigation smoke', status: 'ACTIVE', roles: ['ADMIN'], emailVerified: false };
const results = [], errors = [], businessWrites = [];
try {
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    const context = await browser.newContext({ viewport });
    const page = await context.newPage();
    page.setDefaultTimeout(15000);
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/api/**', async route => {
      const request = route.request(), url = new URL(request.url()), endpoint = url.pathname;
      if (request.method() !== 'GET' && !endpoint.startsWith('/api/v1/auth/')) businessWrites.push(endpoint);
      let data;
      if (endpoint.endsWith('/auth/csrf')) data = { headerName: 'X-XSRF-TOKEN', token: 'smoke-csrf' };
      else if (endpoint.endsWith('/auth/refresh')) data = { accessToken: 'smoke-token', tokenType: 'Bearer', expiresIn: 900, expiresAt: '2030-01-01T00:00:00Z', user };
      else if (endpoint.endsWith('/auth/me')) data = user;
      else if (endpoint === '/api/v1/admin/dataset-snapshots') data = { page: Number(url.searchParams.get('page') || 1), size: 20, total: 50,
        items: [{ snapshotId: id, sourceName: 'navigation.csv', sourceType: 'CSV', validationStatus: 'PENDING', conversionStatus: 'NOT_STARTED', sequenceCount: null, distinctItemCount: null, createdAt: now }] };
      else if (endpoint === '/api/v1/admin/mining-runs') data = { page: Number(url.searchParams.get('page') || 1), size: 20, total: 0, items: [] };
      else if (endpoint === '/api/v1/admin/mining-run-history') data = { page: 0, size: 20, totalElements: 0, totalPages: 0, items: [] };
      else if (endpoint.endsWith('/mining-execution/capacity')) data = { enabled: false, scope: 'CURRENT_BACKEND_INSTANCE', observedAt: now, snapshot: null };
      else if (endpoint === '/api/v1/admin/combos/batches') data = { page: Number(url.searchParams.get('page') || 0), size: 20, total: '0', items: [] };
      else if (endpoint === '/api/v1/categories' || endpoint.endsWith('/latest') || endpoint.endsWith('/categories')) data = [];
      if (data !== undefined) await route.fulfill({ status: 200, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'navigation-smoke' },
        body: JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'navigation-smoke' }) });
      else await route.fulfill({ status: 503, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'navigation-smoke' },
        body: JSON.stringify({ status: 503, code: 'SMOKE_UNAVAILABLE', message: 'Read unavailable in navigation smoke.', path: endpoint, requestId: 'navigation-smoke', details: [] }) });
    });
    const mobile = viewport.width < 992;
    async function openMenu() {
      const nav = page.getByRole('navigation', { name: 'Dữ liệu & Khai phá' });
      if (!await nav.isVisible()) await page.getByRole('button', { name: /Toggle navigation|Mở|navigation/i }).first().click();
      await nav.waitFor({ state: 'visible' }); return nav;
    }
    await page.goto(base + '/admin/datasets?search=navigation&page=2');
    await page.getByRole('heading', { name: 'Dữ liệu khai phá', exact: true }).waitFor();
    const titleSize = await page.getByRole('heading', { name: 'Dữ liệu khai phá', exact: true }).evaluate(node => getComputedStyle(node).fontSize);
    assert.equal(titleSize, '24px');
    let nav = await openMenu();
    assert.equal(await nav.locator('[aria-current="page"]').innerText(), 'Danh sách dữ liệu');
    await nav.getByRole('link', { name: 'Tạo dữ liệu mới', exact: true }).click();
    await page.getByRole('form', { name: 'Upload CSV', exact: true }).waitFor();
    assert.equal(new URL(page.url()).searchParams.get('search'), 'navigation');
    assert.equal(new URL(page.url()).searchParams.get('page'), '2');
    if (mobile) assert.equal(await nav.isVisible(), false);
    assert.equal(await page.getByRole('tablist', { name: 'Dữ liệu khai phá' }).count(), 0);
    await page.reload();
    await page.getByRole('heading', { name: 'Dữ liệu khai phá', exact: true }).waitFor();
    nav = await openMenu();
    assert.equal(await nav.locator('[aria-current="page"]').innerText(), 'Tạo dữ liệu mới');
    await nav.getByRole('link', { name: 'Danh sách dữ liệu', exact: true }).click();
    await page.getByRole('link', { name: 'Xem chi tiết', exact: true }).waitFor();
    await page.getByRole('link', { name: 'Xem chi tiết', exact: true }).click();
    await page.getByRole('heading', { name: 'Chi tiết snapshot', exact: true }).waitFor();
    await page.getByRole('link', { name: 'Về trang dữ liệu', exact: true }).click();
    assert.equal(new URL(page.url()).searchParams.get('page'), '2');
    assert.equal(new URL(page.url()).searchParams.get('search'), 'navigation');
    await page.screenshot({ path: path.join(evidence, mobile ? 'mobile-datasets.png' : 'desktop-datasets.png'), fullPage: true });
    const destinations = [
      ['/admin/mining-runs?tab=history&snapshotId=' + id, 'Lần chạy khai phá', 'Lịch sử lần chạy'],
      ['/admin/mining-runs?snapshotId=' + id, 'Lần chạy khai phá', 'Tạo lần chạy'],
      ['/admin/patterns', 'Mẫu khai phá', 'Tra cứu Pattern'],
      ['/admin/combos', 'Quản lý Combo', 'Danh sách batch & duyệt'],
      ['/admin/combos?tab=generate&runId=' + id, 'Quản lý Combo', 'Tạo đề xuất Combo'],
      ['/admin/mining-run-history', 'Lịch sử Mining RUN', 'Lịch sử lần chạy'],
      ['/admin/mining-monitoring', 'Giám sát quá trình chạy Mining', 'Giám sát thực thi'],
      ['/admin/mining-comparison', 'So sánh thuật toán Mining', 'So sánh thuật toán'],
    ];
    for (const [url, heading, active] of destinations) {
      await page.goto(base + url);
      await page.getByRole('heading', { name: heading, exact: true }).waitFor();
      nav = await openMenu();
      assert.equal(await nav.locator('[aria-current="page"]').innerText(), active);
      assert.equal(await nav.locator('[aria-current="page"]').count(), 1);
    }
    results.push({ viewport, checked: destinations.length + 1, headerFontSize: titleSize, preservedDatasetContext: true });
    await context.close();
  }
  assert.deepEqual(errors, [], 'No uncaught browser errors');
  assert.deepEqual(businessWrites, [], 'Navigation must not create datasets, runs or Combos');
  writeFileSync(path.join(evidence, 'result.json'), JSON.stringify({ results, errors, businessWrites, scope: 'Production UI with mocked HTTP' }, null, 2));
  console.log(JSON.stringify({ status: 'PASS', evidence, results }));
} finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
