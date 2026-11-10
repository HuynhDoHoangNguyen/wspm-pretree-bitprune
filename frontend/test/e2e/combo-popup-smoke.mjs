import { chromium } from 'playwright-core';
import { createServer } from 'node:http';
import { readFileSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';

// Production UI with mocked HTTP only; never contacts a backend or creates an order.
const build = path.resolve(process.env.WDM_E2E_BUILD_DIR || 'frontend/build');
const evidence = path.resolve(process.env.WDM_COMBO_EVIDENCE_DIR || '.agent-workspace/combo-popup-' + Date.now());
mkdirSync(evidence, { recursive: true });
const mime = { '.js': 'application/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png' };
const server = createServer((request, response) => {
  const file = path.resolve(build, '.' + new URL(request.url, 'http://localhost').pathname);
  if (!file.startsWith(build + path.sep)) { response.writeHead(403).end(); return; }
  const target = existsSync(file) && path.extname(file) ? file : path.join(build, 'index.html');
  try { response.setHeader('Content-Type', mime[path.extname(target)] || 'text/html; charset=utf-8'); response.end(readFileSync(target)); }
  catch { response.writeHead(404).end(); }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const origin = 'http://127.0.0.1:' + server.address().port;
const at = '2026-11-10T00:00:00Z';
const product = (productId, name) => ({ productId, name, sku: null, categoryId: 'cat', imageUrl: null, currentPrice: '100.10', availableQuantity: '4' });
const item = (productId, name, itemsetOrder, itemOrder) => ({ itemsetOrder, itemOrder, quantity: 1, anchorEligible: true, discountEligible: true,
  unitPriceSnapshot: '100.10', product: product(productId, name) });
const combo = (comboId, name, kind, items) => ({ comboId, name, kind, status: 'PUBLISHED', explanation: null,
  validFrom: null, validTo: null, publishedAt: at, evaluatedAt: at,
  provenance: { batch: { batchId: 'batch', runId: 'run', snapshotId: 'snapshot', algorithm: 'WSPM_PRETREE', rankingPolicy: 'SUPPORT_THEN_PATTERN_WEIGHT_V1', patternWeightPolicy: 'policy' }, primaryPatternId: '1', canonicalPattern: '<(1 2)>' },
  metrics: { supportAbs: 2, supportRatio: 0.5, sequenceCount: 4, patternWeight: 0.5 },
  pricing: { basis: 'PUBLISH_SNAPSHOT', currencyCode: 'VND', referenceOriginalPrice: '200.20', referenceSalePrice: '200.20', discountType: 'NONE', discountValue: null, maxDiscountAmount: null, isCheckoutQuote: false }, items });
const bundle = combo('bundle', 'Combo mua cùng mới nhất', 'SAME_ORDER_BUNDLE', [item('Mouse', 'Chuột', 1, 1), item('Bag', 'Balo', 1, 2)]);
const sequential = combo('sequence', 'Gợi ý theo thứ tự', 'SEQUENTIAL_CROSS_SELL', [item('Laptop', 'Laptop đã mua', 1, 1), item('Mouse', 'Chuột', 2, 1)]);
const mixed = combo('mixed', 'Gợi ý nhóm tiếp theo', 'MIXED_SEQUENCE', [item('Laptop', 'Laptop đã mua', 1, 1), item('Mouse', 'Chuột', 2, 1), item('Bag', 'Balo', 2, 2)]);
const topFive = Array.from({ length: 5 }, (_, i) => combo('top-' + i, 'Top K ' + (i + 1), 'SAME_ORDER_BUNDLE',
  [item('Laptop', 'Laptop', 1, 1), item('Extra-' + i, 'Phụ kiện ' + i, 1, 2)]));
const match = (c, rank = 1) => ({ rank, matchedItemsets: 1, nextItemsetOrder: 2,
  suggestedProductIds: c.items.filter(i => i.itemsetOrder === 2).map(i => i.product.productId), combo: c });
const errors = [], writes = [], results = [];
const browser = await chromium.launch({ channel: 'msedge', headless: true });
try {
  for (const width of [1440, 390]) {
    const context = await browser.newContext({ viewport: { width, height: 900 } });
    const page = await context.newPage(); page.setDefaultTimeout(15000);
    let authenticated = false;
    page.on('pageerror', error => errors.push(error.message));
    await context.route('**/api/**', async route => {
      const request = route.request(), url = new URL(request.url()), endpoint = url.pathname;
      let status = 200, data;
      if (request.method() !== 'GET' && !endpoint.startsWith('/api/v1/auth/')) writes.push(endpoint);
      const user = { userId: 'customer', username: 'test', email: null, displayName: 'Customer', status: 'ACTIVE', roles: ['CUSTOMER'], emailVerified: false };
      if (endpoint.endsWith('/auth/csrf')) data = { headerName: 'X-XSRF-TOKEN', token: 'csrf' };
      else if (endpoint.endsWith('/auth/refresh')) {
        status = authenticated ? 200 : 401;
        data = { accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 900, expiresAt: '2030-01-01T00:00:00Z', user };
      } else if (endpoint.endsWith('/auth/me')) data = user;
      else if (endpoint.endsWith('/combos/latest')) data = [bundle];
      else if (endpoint.endsWith('/combos/personalized')) {
        status = authenticated ? 200 : 401;
        const id = url.searchParams.get('comboId');
        data = { policy: 'HISTORY_PREFIX_V1', evaluatedAt: at, items: id ? [match(id === 'mixed' ? mixed : sequential)] : [match(sequential), match(mixed, 2)] };
      } else if (endpoint.endsWith('/combos/bundle')) data = bundle;
      else if (endpoint.endsWith('/combos/sequence') || endpoint.endsWith('/combos/mixed')) status = 404;
      else if (endpoint === '/api/v1/recommendations/combos') {
        assert.equal(url.searchParams.has('k'), false, 'Client must use the active batch cap');
        const items = url.searchParams.get('productId') === 'Laptop' ? topFive.map((combo, i) => ({ rank: i + 1, combo })) : [];
        data = { productId: url.searchParams.get('productId'), requestedK: 5, effectiveK: 5,
          evaluatedAt: at, provenance: items.length ? bundle.provenance.batch : null, items };
      }
      else if (endpoint === '/api/v1/products/Mouse' || endpoint === '/api/v1/products/Laptop') {
        const id = endpoint.split('/').pop();
        data = { productId: id, productName: id, categoryId: 'cat', productStatus: 'ACTIVE', imageUrl: null, createdAt: at, price: 100.10, images: [], information: null };
      }
      else if (endpoint === '/api/v1/products') data = [{ productId: 'Mouse', productName: 'Chuột', categoryId: 'cat', productStatus: 'ACTIVE', imageUrl: null, createdAt: at, price: 100.10 }];
      else if (endpoint === '/api/v1/categories') data = [];
      else status = 503;
      const body = status === 200 ? { code: 'SUCCESS', message: 'Request completed successfully.', requestId: 'combo-smoke', data }
        : { status, code: status === 404 ? 'RESOURCE_NOT_FOUND' : 'SMOKE_UNAVAILABLE', message: 'Unavailable', path: endpoint, requestId: 'combo-smoke', details: [] };
      await route.fulfill({ status, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'combo-smoke', 'Cache-Control': 'no-store' }, body: JSON.stringify(body) });
    });
    await page.goto(origin + '/products?q=Chuột&page=1');
    const latest = page.getByLabel('Top Combo mới nhất', { exact: true });
    await latest.getByRole('link', { name: 'Xem chi tiết Combo', exact: true }).waitFor();
    assert.equal(await page.getByText('Gợi ý theo thứ tự', { exact: true }).count(), 0);
    const before = page.url();
    await latest.getByRole('link', { name: 'Xem chi tiết Combo', exact: true }).click();
    let dialog = page.getByRole('dialog');
    await dialog.getByRole('checkbox', { name: 'Chọn Chuột' }).waitFor();
    assert.equal(page.url(), before);
    assert.equal(await dialog.getByRole('checkbox').count(), 2);
    assert.equal(await dialog.getByRole('checkbox', { checked: true }).count(),0);
    await dialog.getByRole('checkbox', { name: 'Chọn Chuột' }).check();
    await dialog.getByText('Tổng giá đang chọn: 100,1 VND', { exact: true }).waitFor();
    assert.equal(await dialog.getByText(/Batch:|Snapshot:|Trọng số mẫu|Support/).count(), 0);
    assert.equal(await dialog.getByRole('button', { name: 'Thêm vào giỏ hàng',exact:true }).isDisabled(),true);
    const bounds = await dialog.boundingBox(); assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width + 1);
    await page.screenshot({ path: path.join(evidence, 'bundle-' + width + '.png'), fullPage: true });
    await page.goBack(); await dialog.waitFor({ state: 'detached' }); assert.equal(page.url(), before);
    await latest.getByRole('link', { name: 'Xem chi tiết Combo', exact: true }).click();
    await page.getByRole('dialog').getByRole('checkbox').first().waitFor();
    await page.keyboard.press('Escape'); await page.getByRole('dialog').waitFor({ state: 'detached' });
    await page.goto(origin + '/combos/mixed');
    await page.getByRole('dialog').getByText(/Combo không khả dụng/).waitFor();
    assert.equal(await page.getByRole('checkbox').count(), 0);
    authenticated = true;
    for (const [id, count] of [['sequence', 1], ['mixed', 2]]) {
      await page.goto(origin + '/combos/' + id);
      dialog = page.getByRole('dialog');
      await dialog.getByRole('checkbox', { name: 'Chọn Chuột' }).waitFor();
      assert.equal(await dialog.getByRole('checkbox').count(), count);
      assert.equal(await dialog.getByRole('checkbox', { name: 'Chọn Laptop đã mua' }).count(), 0);
      await page.screenshot({ path: path.join(evidence, id + '-' + width + '.png'), fullPage: true });
      await dialog.getByRole('button', { name: 'Đóng', exact: true }).click();
      await page.waitForURL(origin + '/products');
    }
    await page.goto(origin + '/products/Laptop');
    const recommendations = page.getByTestId('product-combos');
    await recommendations.getByRole('checkbox', { name: 'Chọn Chuột' }).first().waitFor();
    await recommendations.getByRole('button', { name: 'Xem thêm gợi ý (5)' }).click();
    await recommendations.getByRole('heading', { name: 'Top K 5', exact: true }).waitFor();
    assert.equal(await recommendations.getByRole('article').count(), 7, 'Two personal plus five public recommendations');
    const expandedBounds = await recommendations.boundingBox();
    assert.ok(expandedBounds.x >= 0 && expandedBounds.x + expandedBounds.width <= width + 1);
    await page.screenshot({ path: path.join(evidence, 'topk-expanded-' + width + '.png'), fullPage: true });
    await recommendations.getByRole('button', { name: 'Thu gọn', exact: true }).click();
    assert.equal(await recommendations.getByRole('article').count(), 2);
    await page.goto(origin + '/products/Mouse');
    await page.getByRole('heading', { name: 'Mouse', exact: true }).waitFor();
    await page.waitForFunction(() => !document.body.innerText.includes('Đang đối chiếu lịch sử mua'));
    assert.equal(await page.getByTestId('product-combos').count(), 0, 'Target product must not suggest itself');
    await page.screenshot({ path: path.join(evidence, 'product-detail-' + width + '.png'), fullPage: true });
    results.push({ width, publicBundle: true, personalSequence: true, personalMixed: true, guestBlocked: true, backAndEscape: true });
    await context.close();
  }
  assert.deepEqual(errors, []); assert.deepEqual(writes, []);
  writeFileSync(path.join(evidence, 'result.json'), JSON.stringify({ results, errors, writes, scope: 'Production UI with mocked HTTP' }, null, 2));
  console.log(JSON.stringify({ status: 'PASS', evidence, results }));
} finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
