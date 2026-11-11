import assert from 'node:assert/strict';
import { createHash, randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync, unlinkSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { execFileSync, spawnSync } from 'node:child_process';
import { runConfigurationCases } from './configuration-cases.mjs';

const base = '/api/v1/admin/dataset-snapshots';
const unknown = '00000000-0000-4000-8000-000000000000';
const header = 'order_id,order_item_id,customer_code,ordered_at,status,product_id,sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code\r\n';
const hash = bytes => createHash('sha256').update(bytes).digest('hex');

/** Only synthetic order fixtures are inserted; every snapshot must originate from the real API. */
export async function runDatasetCases({ browser, httpsOrigin: origin, sql, temp, logDirectory, root }) {
  const results = [], contexts = [];
  const password = randomBytes(18).toString('base64url');
  const evidence = { gate: 'WDM-01.8 + WDM-02.6 + WDM-02.7 + WDM-03.6 + WDM-03.7 + WDM-03.8 + UI/UX refresh + WDM-04.6', browser: browser.version(), cases: results };
  const save = () => writeFileSync(path.join(logDirectory, 'RESULT.json'), JSON.stringify(evidence, null, 2));
  const count = () => Number(sql('SELECT COUNT(*) FROM DATASET_SNAPSHOT;'));
  async function context() {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, acceptDownloads: true });
    ctx.setDefaultTimeout(15000); ctx.setDefaultNavigationTimeout(20000);
    contexts.push(ctx); return ctx;
  }
  async function test(name, work) {
    const started = Date.now();
    try {
      await work(); results.push({ name, status: 'PASS', elapsedMs: Date.now() - started });
      console.log('PASS Dataset ' + results.length + ': ' + name);
    } catch (error) {
      evidence.status = 'FAIL';
      results.push({ name, status: 'FAIL', errorType: error.name, elapsedMs: Date.now() - started });
      console.error('FAILED CASE: ' + name);
      // Assertion values can contain credentials or private paths. Keep only source locations.
      console.error((error.stack || '').split('\n').filter(line => line.trim().startsWith('at ')).slice(0, 3).join('\n'));
      throw error;
    } finally {
      save();
      for (const ctx of contexts.splice(0)) await ctx.close();
    }
  }
  function envelope(response, value) {
    assert.equal(value.code, 'SUCCESS');
    assert.ok(response.headers()['x-request-id']);
    assert.equal(value.requestId, response.headers()['x-request-id']);
    assert.ok(response.headers()['cache-control'].includes('no-store'));
    return value.data;
  }
  async function authPost(ctx, endpoint, data) {
    const csrf = await ctx.request.get(origin + '/api/v1/auth/csrf');
    assert.equal(csrf.status(), 200);
    const token = envelope(csrf, await csrf.json()).token;
    return ctx.request.post(origin + '/api/v1/auth/' + endpoint, {
      headers: { Origin: origin, 'X-XSRF-TOKEN': token }, data,
    });
  }
  async function login(ctx, username = 'dataset.admin') {
    const page = await ctx.newPage();
    await page.goto(origin + (username === 'dataset.admin' ? '/admin/datasets' : '/signin'));
    await page.getByLabel('Tên tài khoản', { exact: false }).fill(username);
    await page.getByLabel('Mật khẩu', { exact: false }).fill(password);
    const granted = page.waitForResponse(r => r.url().endsWith('/api/v1/auth/login') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    const response = await granted; assert.equal(response.status(), 200);
    const grant = envelope(response, await response.json());
    if (username === 'dataset.admin') {
      await page.getByRole('heading', { name: 'Từ dữ liệu đơn hàng đến combo sản phẩm', exact: true }).waitFor();
      await page.getByRole('link', { name: 'Bắt đầu với dữ liệu', exact: true }).click();
      await page.getByRole('heading', { name: 'Dữ liệu khai phá', exact: true }).waitFor();
      await page.getByRole('button', { name: '+ Tạo dữ liệu mới', exact: true }).click();
    }
    else await page.waitForURL(origin + '/');
    return { page, token: grant.accessToken };
  }
  const headers = token => ({ Authorization: 'Bearer ' + token });
  async function get(ctx, suffix, token, status) {
    const response = await ctx.request.get(origin + base + suffix, { headers: token ? headers(token) : {} });
    assert.equal(response.status(), status);
    assert.ok(response.headers()['x-request-id']);
    assert.ok(response.headers()['cache-control'].includes('no-store'));
    return response;
  }
  async function upload(ctx, token, bytes, name = 'fixture.csv') {
    return ctx.request.post(origin + base, { headers: headers(token),
      multipart: { file: { name, mimeType: 'text/csv', buffer: bytes } } });
  }
  function idOf(value) { assert.match(value, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/); return value; }
  function record(id) {
    idOf(id);
    return JSON.parse(sql(`SELECT JSON_OBJECT('id',snapshot_id,'sourceType',source_type,
      'name',source_name,'path',raw_file_path,'sha',raw_sha256,'validation',validation_status,
      'count',CAST(sequence_count AS CHAR),'report',conversion_report_json,
      'fromTime',from_time,'toTime',to_time,'createdAt',CAST(created_at AS CHAR))
      FROM DATASET_SNAPSHOT WHERE snapshot_id='${id}';`));
  }
  function rawPath(id) { return path.join(temp, 'snapshots', idOf(id), 'raw.csv'); }
  async function openTab(page, tab) {
    const labels = { source: /^1\. Dữ liệu gốc/, validation: /^2\. Kiểm tra dữ liệu/, conversion: /^3\. Chuyển đổi/ };
    await page.getByRole('tab', { name: labels[tab] }).click();
  }
  async function openRaw(page) {
    await openTab(page, 'source');
    const summary = page.getByText('Thông tin kỹ thuật & checksum', { exact: true });
    if (!(await summary.evaluate(element => element.parentElement.open))) await summary.click();
  }
  let snapshot, exported;

  await test('isolated synthetic historical orders and real Auth registration', async () => {
    assert.equal(count(), 0);
    const ctx = await context();
    const users = {};
    for (const role of ['customer', 'admin']) {
      const response = await authPost(ctx, 'register', { username: 'dataset.' + role, password,
        email: 'dataset.' + role + '@example.test', displayName: 'Dataset fixture ' + role });
      assert.equal(response.status(), 201);
      users[role] = idOf(envelope(response, await response.json()).userId);
    }
    sql(`INSERT INTO USER_ROLE(user_id,role_id) VALUES('${users.admin}','admin-role');
      UPDATE CUSTOMER SET anonymous_code='dataset-shopper' WHERE user_id='${users.customer}';
      INSERT INTO CATEGORY(category_id,name,slug) VALUES('dataset-cat','Synthetic','dataset-cat');
      INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price)
        VALUES('dataset-product','CURRENT-SKU','Current catalog name','dataset-product','dataset-cat',999);
      INSERT INTO ORDERS(order_id,order_number,customer_id,ordered_at,status,source,currency_code)
        SELECT fixture.id,fixture.id,c.customer_id,fixture.at,fixture.status,'IMPORT','VND'
        FROM CUSTOMER c CROSS JOIN (
          SELECT 'before' id,'2026-10-31 16:59:59.999999' at,'COMPLETED' status
          UNION ALL SELECT 'at-start','2026-10-31 17:00:00.000000','COMPLETED'
          UNION ALL SELECT 'middle','2026-11-01 12:00:00.000000','COMPLETED'
          UNION ALL SELECT 'at-end','2026-11-01 16:59:59.999999','COMPLETED'
          UNION ALL SELECT 'after','2026-11-01 17:00:00.000000','COMPLETED'
          UNION ALL SELECT 'cancelled','2026-11-01 12:00:00.000000','CANCELLED') fixture
        WHERE c.user_id='${users.customer}';
      INSERT INTO ORDER_ITEM(order_item_id,order_id,product_id,quantity,list_unit_price,allocated_discount_amount,line_total,sku_snapshot,product_name_snapshot)
        VALUES(1,'before','dataset-product',2,100,10,190,'HISTORY-SKU','Historical name'),
          (9007199254740993,'at-start','dataset-product',2,100,10,190,'HISTORY-SKU','Bàn phím "Cơ", tiếng Việt'),
          (3,'middle','dataset-product',2,100,10,190,'HISTORY-SKU','Historical name'),
          (2,'middle','dataset-product',2,100,10,190,'HISTORY-SKU','Historical name'),
          (4,'at-end','dataset-product',2,100,10,190,'HISTORY-SKU','Historical name'),
          (5,'after','dataset-product',2,100,10,190,'HISTORY-SKU','Historical name'),
          (6,'cancelled','dataset-product',2,100,10,190,'HISTORY-SKU','Historical name');`);
    assert.equal(sql('SELECT COUNT(*) FROM ORDERS;'), '6');
    assert.equal(sql('SELECT COUNT(*) FROM ORDER_ITEM;'), '7');
    assert.equal(sql('SELECT HEX(product_name_snapshot) FROM ORDER_ITEM WHERE order_item_id=9007199254740993;'),
      Buffer.from('Bàn phím "Cơ", tiếng Việt', 'utf8').toString('hex').toUpperCase());
  });

  await test('real UI export uses UTC+07 bounds, historical values and exact CSV bytes', async () => {
    const ctx = await context(), { page } = await login(ctx);
    await page.getByLabel('Từ ngày', { exact: false }).fill('2026-11-01');
    await page.getByLabel('Đến hết ngày', { exact: false }).fill('2026-11-01');
    const downloaded = page.waitForEvent('download');
    const responseReady = page.waitForResponse(r => r.url().includes('/api/v1/admin/order-exports?'));
    await page.getByRole('button', { name: 'Export CSV', exact: true }).click();
    const response = await responseReady, download = await downloaded;
    assert.equal(response.status(), 200);
    const query = new URL(response.url()).searchParams;
    assert.equal(query.get('fromTime'), '2026-10-31T17:00:00.000Z');
    assert.equal(query.get('toTime'), '2026-11-01T17:00:00.000Z');
    assert.match(download.suggestedFilename(), /^order-export-[0-9a-f-]{36}\.csv$/);
    exported = readFileSync(await download.path());
    const expected = Buffer.from('\uFEFF' + header + [
      'at-start,9007199254740993,dataset-shopper,2026-10-31T17:00:00.000000Z',
      'middle,2,dataset-shopper,2026-11-01T12:00:00.000000Z',
      'middle,3,dataset-shopper,2026-11-01T12:00:00.000000Z',
      'at-end,4,dataset-shopper,2026-11-01T16:59:59.999999Z',
    ].map((prefix, index) => prefix + ',COMPLETED,dataset-product,HISTORY-SKU,'
      + (index === 0 ? '"Bàn phím ""Cơ"", tiếng Việt"' : 'Historical name')
      + ',2,100.00,10.00,190.00,VND\r\n').join(''));
    assert.deepEqual(exported.subarray(0, 3), Buffer.from([0xef, 0xbb, 0xbf]));
    assert.equal(exported.toString('utf8').includes('"Bàn phím ""Cơ"", tiếng Việt"'), true);
    assert.deepEqual(exported, expected);
    assert.equal(count(), 0);
    evidence.export = { sha256: hash(exported), sizeBytes: exported.length, rows: 4,
      fromTime: query.get('fromTime'), toTime: query.get('toTime'), snapshotCount: count() };
    // The browser download itself is later selected via the file picker, not reserialized.
    await download.saveAs(path.join(temp, 'browser-export.csv'));
  });

  await test('real file picker upload commits one snapshot, detail reload and raw download preserve bytes', async () => {
    const ctx = await context(), { page, token } = await login(ctx);
    let uploads = 0;
    page.on('request', r => { if (r.url() === origin + base && r.method() === 'POST') uploads++; });
    await page.getByLabel('Tệp CSV', { exact: true }).setInputFiles(path.join(temp, 'browser-export.csv'));
    const received = page.waitForResponse(r => r.url() === origin + base && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Upload CSV', exact: true }).click();
    const response = await received; assert.equal(response.status(), 201);
    snapshot = envelope(response, await response.json());
    idOf(snapshot.snapshotId);
    assert.equal(snapshot.validationStatus, 'PENDING');
    assert.equal(snapshot.conversionStatus, 'NOT_STARTED');
    assert.equal(snapshot.statisticsStatus, 'NOT_COMPUTED');
    assert.equal(snapshot.sequenceCount, null);
    assert.equal(snapshot.rawSha256, hash(exported));
    assert.equal(snapshot.rawSizeBytes, String(exported.length));
    assert.equal(snapshot.fromTime, null); assert.equal(snapshot.toTime, null);
    assert.equal(count(), 1); assert.equal(uploads, 1);
    const before = record(snapshot.snapshotId);
    assert.equal(before.sourceType, 'CSV'); assert.equal(before.validation, 'PENDING'); assert.equal(before.count, '0');
    assert.equal(before.path, snapshot.snapshotId + '/raw.csv'); assert.equal(before.sha, hash(exported));
    assert.equal(before.report.provenance.sourceTimeZone, null);
    assert.equal(before.report.provenance.rawSizeBytes, String(exported.length));
    assert.equal(before.report.conversionStatus, 'NOT_STARTED'); assert.equal(before.report.statisticsStatus, 'NOT_COMPUTED');
    assert.deepEqual(readFileSync(rawPath(snapshot.snapshotId)), exported);
    await page.getByRole('link', { name: 'Xem chi tiết snapshot', exact: true }).click();
    await page.getByRole('heading', { name: 'Chi tiết snapshot', exact: true }).waitFor();
    await openRaw(page);
    await page.getByText(hash(exported), { exact: true }).waitFor();
    await page.reload(); await openRaw(page); await page.getByText(hash(exported), { exact: true }).waitFor();
    await page.goto(origin + '/admin/datasets/' + snapshot.snapshotId);
    await openRaw(page);
    await page.getByText(hash(exported), { exact: true }).waitFor();
    const detailResponse = await get(ctx, '/' + snapshot.snapshotId, token, 200);
    const detail = envelope(detailResponse, await detailResponse.json());
    assert.equal(detail.snapshotId, snapshot.snapshotId);
    assert.equal(detail.rawSha256, before.sha);
    assert.equal(detail.rawAvailability, 'AVAILABLE');
    assert.equal(detail.sequenceCount, null);
    assert.equal(detail.fromTime, null); assert.equal(detail.toTime, null);
    assert.equal(detail.createdAt, snapshot.createdAt);
    assert.equal(detail.provenance.sourceTimeZone, null);
    assert.equal(Object.hasOwn(detail, 'rawFilePath'), false);
    assert.equal(Object.hasOwn(detail, 'conversionReportJson'), false);
    assert.equal(JSON.stringify(detail).includes(before.path), false);
    const sequence = page.locator('dl > div').filter({ has: page.locator('dt', { hasText: 'Số sequence' }) });
    assert.equal(await sequence.locator('dd').innerText(), 'Chưa xác định');
    assert.equal((await page.locator('body').innerText()).includes(before.path), false);
    const rawDownload = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Tải CSV gốc', exact: true }).click();
    const raw = await rawDownload;
    assert.equal(raw.suggestedFilename(), 'snapshot-' + snapshot.snapshotId + '-raw.csv');
    assert.deepEqual(readFileSync(await raw.path()), exported);
    assert.deepEqual(record(snapshot.snapshotId), before);
    assert.equal(count(), 1); assert.equal(uploads, 1);
    evidence.snapshot = { id: snapshot.snapshotId, sha256: snapshot.rawSha256, sizeBytes: snapshot.rawSizeBytes,
      validation: snapshot.validationStatus, sequenceCount: snapshot.sequenceCount, uploadRequests: uploads, readOnly: true };
  });

  await test('anonymous and CUSTOMER denied by UI and API before malformed input', async () => {
    const ctx = await context(), page = await ctx.newPage();
    await page.goto(origin + '/admin/datasets');
    await page.getByRole('heading', { name: 'Đăng nhập Admin', exact: true }).waitFor();
    await get(ctx, '/bad-id', null, 401);
    const denied = await ctx.request.post(origin + base, { data: 'invalid multipart' });
    assert.equal(denied.status(), 401);
    const customer = await login(ctx, 'dataset.customer');
    await customer.page.goto(origin + '/admin/datasets');
    await customer.page.getByRole('alert').filter({ hasText: 'không có quyền ADMIN' }).waitFor();
    assert.equal(await customer.page.getByRole('button', { name: 'Upload CSV', exact: true }).count(), 0);
    await get(ctx, '/bad-id/artifacts/raw', customer.token, 403);
    assert.equal((await ctx.request.post(origin + base, { headers: headers(customer.token), data: 'bad multipart' })).status(), 403);
    assert.equal(count(), 1);
    assert.deepEqual(readdirSync(path.join(temp, 'snapshots')), [snapshot.snapshotId]);
  });

  await test('invalid uploads and IDs reject without creating snapshots', async () => {
    const ctx = await context(), { page, token } = await login(ctx);
    await get(ctx, '/bad-id', token, 400); await get(ctx, '/' + unknown, token, 404);
    assert.equal((await upload(ctx, token, Buffer.alloc(0))).status(), 400);
    assert.equal((await upload(ctx, token, Buffer.from('text'), 'fixture.txt')).status(), 415);
    assert.equal((await upload(ctx, token, Buffer.alloc(4097, 65))).status(), 413);
    const extra = await ctx.request.post(origin + base, { headers: headers(token), multipart: {
      file: { name: 'fixture.csv', mimeType: 'text/csv', buffer: exported }, extra: 'unexpected',
    } });
    assert.equal(extra.status(), 400);
    await page.goto(origin + '/admin/datasets/' + unknown);
    await page.getByRole('alert').filter({ hasText: 'Không tìm thấy snapshot này.' }).waitFor();
    assert.equal(count(), 1);
  });

  await test('same-size tamper and missing raw deny download while keeping metadata unchanged', async () => {
    const ctx = await context(), { page, token } = await login(ctx);
    const id = snapshot.snapshotId, before = record(id);
    const bytes = readFileSync(rawPath(id)), tampered = Buffer.from(bytes); tampered[0] ^= 1;
    writeFileSync(rawPath(id), tampered);
    await page.goto(origin + '/admin/datasets/' + id);
    await openRaw(page);
    await page.getByText(snapshot.rawSha256, { exact: true }).waitFor();
    let downloads = 0; page.on('download', () => downloads++);
    const failed = page.waitForResponse(r => r.url().endsWith('/artifacts/raw'));
    await page.getByRole('button', { name: 'Tải CSV gốc', exact: true }).click();
    assert.equal((await failed).status(), 503);
    await page.getByRole('alert').filter({ hasText: 'Chưa thể tải CSV gốc' }).waitFor();
    assert.equal(downloads, 0); assert.deepEqual(record(id), before);
    assert.deepEqual(readFileSync(rawPath(id)), tampered);
    // Only this runner's own artifact is removed; no storage sweeper or dev file access.
    unlinkSync(rawPath(id));
    await page.reload();
    await page.getByRole('alert').filter({ hasText: 'CSV gốc chưa sẵn sàng' }).waitFor();
    assert.equal(await page.getByRole('button', { name: 'Tải CSV gốc', exact: true }).isDisabled(), true);
    const detail = await get(ctx, '/' + id, token, 200);
    assert.equal(envelope(detail, await detail.json()).rawAvailability, 'MISSING');
    await get(ctx, '/' + id + '/artifacts/raw', token, 404);
    assert.deepEqual(record(id), before); assert.equal(count(), 1);
  });

  await test('duplicate upload clicks are blocked and logout cancels late real export bytes', async () => {
    const ctx = await context(), { page } = await login(ctx);
    await page.getByLabel('Tệp CSV', { exact: true }).setInputFiles(path.join(temp, 'browser-export.csv'));
    let release, arrived;
    const blocked = new Promise(resolve => { release = resolve; });
    const started = new Promise(resolve => { arrived = resolve; });
    let requests = 0;
    await page.route('**/api/v1/admin/dataset-snapshots', async route => {
      // Continue the browser's original multipart stream. route.fetch() can omit
      // file bodies; it is not an equivalent replay of a browser file upload.
      requests++; arrived(); await blocked;
      await route.continue();
    });
    try {
      await page.getByRole('button', { name: 'Upload CSV', exact: true }).click();
      await started;
      // Browser dispatches two rapid submit events even though the rendered button is disabled.
      await page.getByRole('form', { name: 'Upload CSV', exact: true }).evaluate(form => {
        form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
        form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      });
      assert.equal(count(), 1);
      assert.equal(requests, 1);
    } finally { release(); }
    await page.getByRole('link', { name: 'Xem chi tiết snapshot', exact: true }).waitFor();
    await page.unrouteAll({ behavior: 'wait' });
    assert.equal(count(), 2);
    assert.equal(requests, 1);
    await page.getByLabel('Từ ngày', { exact: false }).fill('2026-11-01');
    await page.getByLabel('Đến hết ngày', { exact: false }).fill('2026-11-01');
    let releaseExport, exportArrived;
    const delayed = new Promise(resolve => { releaseExport = resolve; });
    const exporting = new Promise(resolve => { exportArrived = resolve; });
    let downloads = 0, exports = 0; page.on('download', () => downloads++);
    await page.route('**/api/v1/admin/order-exports?*', async route => {
      exports++; const response = await route.fetch(); exportArrived(); await delayed;
      try { await route.fulfill({ response }); } catch { /* session aborted request */ }
    });
    try {
      await page.getByRole('button', { name: 'Export CSV', exact: true }).click(); await exporting;
      await page.getByTitle('Đăng xuất', { exact: true }).click();
      await page.waitForURL(origin + '/');
    } finally { releaseExport(); }
    await page.unrouteAll({ behavior: 'wait' });
    assert.equal(downloads, 0); assert.equal(exports, 1); assert.equal(count(), 2);
  });
  evidence.finalSnapshotCount = count();
  await test('WDM-02.6 valid report, one bodyless POST, metadata refresh and GET-only reload', async () => {
    const ctx = await context(), { page, token } = await login(ctx);
    const acquired = await upload(ctx, token, exported, 'validation-valid.csv');
    assert.equal(acquired.status(), 201);
    const receipt = envelope(acquired, await acquired.json()), id = idOf(receipt.snapshotId);
    const before = record(id);
    await page.goto(origin + '/admin/datasets/' + id);
    await openTab(page, 'validation');
    const panel = page.getByRole('region', { name: 'Kiểm tra dữ liệu', exact: true });
    const button = panel.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true });
    await button.waitFor();
    let posts = 0, release, arrived;
    page.on('request', request => { if (request.method() === 'POST' && request.url() === origin + base + '/' + id + '/validation') posts++; });
    const held = new Promise(resolve => { release = resolve; });
    const committed = new Promise(resolve => { arrived = resolve; });
    await page.route('**' + base + '/' + id + '/validation', async route => {
      assert.equal(route.request().method(), 'POST');
      assert.equal(route.request().postData(), null);
      const response = await route.fetch(); assert.equal(response.status(), 200);
      arrived(); await held;
      await route.fulfill({ response });
    });
    try {
      await button.click(); await committed;
      assert.equal(await button.isDisabled(), true);
      await button.dispatchEvent('click'); await button.dispatchEvent('click');
      assert.equal(posts, 1);
      assert.equal(record(id).validation, 'VALID');
    } finally { release(); }
    await panel.getByText('Có dữ liệu sạch sử dụng được', { exact: true }).waitFor();
    await page.getByText('VALID — Có dữ liệu sạch sử dụng được', { exact: true }).waitFor();
    assert.equal(await panel.getByRole('group', { name: 'Dòng giữ lại', exact: true }).getByText('4', { exact: true }).count(), 1);
    await panel.getByText('Không có thông báo kiểm tra.', { exact: true }).waitFor();
    await page.unrouteAll({ behavior: 'wait' });
    const read = page.waitForResponse(r => r.request().method() === 'GET' && r.url().includes('/' + id + '/validation?'));
    await page.reload(); assert.equal((await read).status(), 200);
    await page.getByText('Có dữ liệu sạch sử dụng được', { exact: true }).waitFor();
    const after = record(id);
    assert.equal(after.sha, before.sha); assert.equal(after.path, before.path); assert.equal(after.count, '0');
    assert.deepEqual(after.report.provenance, before.report.provenance);
    const raw = await get(ctx, '/' + id + '/artifacts/raw', token, 200);
    assert.deepEqual(await raw.body(), exported);
    evidence.validation = { validSnapshot: id, postCount: posts, kept: '4', rawSha256: hash(exported) };
  });

  await test('WDM-02.6 recovers committed dirty-input report after lost POST response without replay', async () => {
    const ctx = await context(), { page, token } = await login(ctx);
    const line = (order, item, customer = 'source-A', product = 'dataset-product') =>
      `${order},${item},${customer},2026-11-01T00:00:00.000000Z,COMPLETED,${product},OLD-SKU,Historical,1,100.00,0.00,100.00,VND\r\n`;
    const bytes = Buffer.from(header + line('unknown', 201, 'source-A', 'missing-product')
      + line('conflict', 202) + line('conflict', 203, 'source-B') + line('duplicate', 204) + line('duplicate', 204));
    const response = await upload(ctx, token, bytes, 'validation-dirty.csv');
    const receipt = envelope(response, await response.json()), id = idOf(receipt.snapshotId);
    await page.goto(origin + '/admin/datasets/' + id);
    await openTab(page, 'validation');
    const panel = page.getByRole('region', { name: 'Kiểm tra dữ liệu', exact: true });
    let posts = 0;
    page.on('request', request => { if (request.method() === 'POST' && request.url() === origin + base + '/' + id + '/validation') posts++; });
    await page.route('**' + base + '/' + id + '/validation', async route => {
      const committed = await route.fetch(); assert.equal(committed.status(), 200);
      assert.equal(envelope(committed, await committed.json()).validationStatus, 'VALID');
      await route.abort('failed'); // Real server committed; only the browser response is lost.
    });
    await panel.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }).click();
    await panel.getByText(/Kết nối bị gián đoạn/).waitFor();
    assert.equal(await panel.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }).isDisabled(), true);
    assert.equal(posts, 1); assert.equal(record(id).validation, 'VALID');
    await page.unrouteAll({ behavior: 'wait' });
    await panel.getByRole('button', { name: 'Kiểm tra lại trạng thái', exact: true }).click();
    await panel.getByText('Có dữ liệu sạch sử dụng được', { exact: true }).waitFor();
    await page.getByText('VALID — Có dữ liệu sạch sử dụng được', { exact: true }).waitFor();
    for (const [label, value] of [['Dòng giữ lại', '1'], ['Dòng loại theo đơn', '3'], ['Dòng lặp đã khử', '1']]) {
      assert.equal(await panel.getByRole('group', { name: label, exact: true }).getByText(value, { exact: true }).count(), 1);
    }
    for (const code of ['PRODUCT_NOT_FOUND', 'ORDER_FIELD_CONFLICT', 'DUPLICATE_LINE_ID']) await panel.getByText(code, { exact: true }).waitFor();
    assert.equal(posts, 1);
    assert.equal(record(id).report.validation.manifest.length, 5);
    assert.equal(hash(readFileSync(rawPath(id))), hash(bytes));
    evidence.validation.recoveredSnapshot = id;
  });

  await test('WDM-02.6 INVALID structural, empty and blocked reports keep null counts and decoder accuracy', async () => {
    const ctx = await context(), { page, token } = await login(ctx);
    const blocked = Buffer.from(header + ' ,301,source-A,2026-11-01T00:00:00.000000Z,COMPLETED,dataset-product,SKU,Name,1,100.00,0.00,100.00,VND\r\n');
    for (const [name, bytes, outcome] of [['utf8', Buffer.from([0xff]), 'NOT_RUN_STRUCTURAL_ERROR'],
      ['empty', Buffer.from(header), 'EMPTY_INPUT'], ['blocked', blocked, 'BLOCKED_UNATTRIBUTABLE_ORDER']]) {
      const acquired = await upload(ctx, token, bytes, name + '.csv');
      const receipt = envelope(acquired, await acquired.json()), id = idOf(receipt.snapshotId);
      await page.goto(origin + '/admin/datasets/' + id);
      await openTab(page, 'validation');
      const panel = page.getByRole('region', { name: 'Kiểm tra dữ liệu', exact: true });
      const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/' + id + '/validation'));
      await panel.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }).click();
      const completed = await response; assert.equal(completed.status(), 200);
      const report = envelope(completed, await completed.json());
      assert.equal(report.validationStatus, 'INVALID'); assert.equal(report.cleaningOutcome, outcome);
      assert.equal(Object.hasOwn(report, 'manifest'), false);
      await panel.getByText('Chưa có dữ liệu sạch sử dụng được', { exact: true }).waitFor();
      assert.equal(record(id).validation, 'INVALID');
      if (name === 'utf8') {
        await panel.getByText(/Đây là tiền tố đã đọc, không phải tổng dataset/).waitFor();
        await panel.getByText(/không xác định chính xác byte hoặc bản ghi lỗi/).waitFor();
        await panel.getByRole('group', { name: 'Dòng đầu vào', exact: true }).getByText('Chưa xác định', { exact: true }).waitFor();
      }
      if (name === 'blocked') await panel.getByRole('group', { name: 'Tổng số đơn', exact: true }).getByText('Chưa xác định', { exact: true }).waitFor();
    }
  });

  await test('WDM-02.7 real Auth and API reject unauthorized, pending, invalid and forged status', async () => {
    const anon = await context();
    const denied = await anon.request.post(origin + base + '/bad/conversion', { data: '{}' });
    assert.equal(denied.status(), 401);
    const customer = await context(), customerLogin = await login(customer, 'dataset.customer');
    assert.equal((await customer.request.post(origin + base + '/bad/conversion', {
      headers: headers(customerLogin.token), data: '{}',
    })).status(), 403);
    for (const suffix of ['/conversion', '/artifacts/sequence', '/artifacts/mapping']) {
      assert.equal((await anon.request.get(origin + base + '/bad' + suffix + '?force=true')).status(), 401);
      assert.equal((await customer.request.get(origin + base + '/bad' + suffix + '?force=true', {
        headers: headers(customerLogin.token),
      })).status(), 403);
    }
    const ctx = await context(), { token } = await login(ctx);
    async function attempt(id, status, code) {
      const response = await ctx.request.post(origin + base + '/' + id + '/conversion', { headers: headers(token) });
      assert.equal(response.status(), status);
      const error = await response.json(); assert.equal(error.code, code);
      assert.equal(error.requestId, response.headers()['x-request-id']);
      assert.ok(response.headers()['cache-control'].includes('no-store'));
    }
    await attempt('bad', 400, 'VALIDATION_FAILED');
    await attempt(unknown, 404, 'RESOURCE_NOT_FOUND');
    const acquired = await upload(ctx, token, exported, 'gate-pending.csv');
    const id = idOf(envelope(acquired, await acquired.json()).snapshotId), pending = record(id);
    await attempt(id, 409, 'INVALID_RESOURCE_STATE'); assert.deepEqual(record(id), pending);
    // Isolated negative corruption fixture only; positive cases use the actual pipeline.
    sql(`UPDATE DATASET_SNAPSHOT SET validation_status='VALID' WHERE snapshot_id='${id}';`);
    const forged = record(id); await attempt(id, 409, 'INVALID_RESOURCE_STATE'); assert.deepEqual(record(id), forged);
    const invalid = await upload(ctx, token, Buffer.from([0xff]), 'gate-invalid.csv');
    const invalidId = idOf(envelope(invalid, await invalid.json()).snapshotId);
    const validated = await ctx.request.post(origin + base + '/' + invalidId + '/validation', { headers: headers(token) });
    assert.equal(validated.status(), 200);
    assert.equal(envelope(validated, await validated.json()).validationStatus, 'INVALID');
    const before = record(invalidId); await attempt(invalidId, 409, 'INVALID_RESOURCE_STATE');
    assert.deepEqual(record(invalidId), before);
  });

  await test('WDM-03.6 real conversion, verified GET/downloads and idempotency; artifact/raw tamper fail closed', async () => {
    const ctx = await context(), { token } = await login(ctx);
    const acquired = await upload(ctx, token, exported, 'gate-ready.csv');
    const id = idOf(envelope(acquired, await acquired.json()).snapshotId);
    const validated = await ctx.request.post(origin + base + '/' + id + '/validation', { headers: headers(token) });
    assert.equal(validated.status(), 200);
    assert.equal(envelope(validated, await validated.json()).validationStatus, 'VALID');
    const before = record(id), conversionPath = '/' + id + '/conversion';
    const notStarted = await get(ctx, conversionPath, token, 200);
    assert.equal(envelope(notStarted, await notStarted.json()).conversionStatus, 'NOT_STARTED');
    assert.deepEqual(record(id), before);
    await get(ctx, '/' + id + '/artifacts/sequence', token, 409);
    const response = await ctx.request.post(origin + base + conversionPath, { headers: headers(token) });
    assert.equal(response.status(), 200);
    const converted = envelope(response, await response.json());
    assert.equal(converted.conversionStatus, 'SUCCEEDED'); assert.equal(converted.statisticsStatus, 'COMPUTED');
    assert.deepEqual(converted.statistics, { sequenceCount: '1', singleItemsetSequenceCount: '0', multiItemsetSequenceCount: '1',
      itemsetCount: '3', distinctItemCount: '1', totalItemOccurrences: '3', minSequenceLength: '3', maxSequenceLength: '3', averageSequenceLength: 3 });
    const publicJson = JSON.stringify(converted);
    for (const privateField of ['attemptId', 'validationSha256', 'reference', 'manifest', '/conversions/']) assert.equal(publicJson.includes(privateField), false);
    const published = record(id), attempt = idOf(published.report.conversion.attemptId);
    assert.equal(published.report.conversionStatus, 'SUCCEEDED'); assert.equal(published.count, '1');
    const privateDirectory = path.join(temp, 'snapshots', id, 'conversions', attempt);
    assert.deepEqual(readdirSync(privateDirectory).sort(), ['mapping.json', 'sequences.spmf']);
    const bytesByKind = {};
    for (const kind of ['sequence', 'mapping']) {
      assert.equal(converted[kind].downloadUrl, base + '/' + id + '/artifacts/' + kind);
      const download = await get(ctx, '/' + id + '/artifacts/' + kind, token, 200);
      assert.equal(download.headers()['x-content-type-options'], 'nosniff');
      assert.match(download.headers()['content-disposition'], /^attachment;/);
      const bytes = await download.body(); bytesByKind[kind] = bytes;
      assert.equal(hash(bytes), converted[kind].sha256); assert.equal(String(bytes.length), converted[kind].sizeBytes);
      assert.equal(download.headers()['content-length'], String(bytes.length));
      assert.deepEqual(bytes, readFileSync(path.join(privateDirectory, kind === 'sequence' ? 'sequences.spmf' : 'mapping.json')));
    }
    const dictionary = JSON.parse(bytesByKind.mapping.toString('utf8'));
    assert.equal(dictionary.formatVersion, 'CONVERSION_ARTIFACT_V1'); assert.equal(dictionary.items.length, 1);
    assert.equal(dictionary.items[0].productId, 'dataset-product');
    const item = dictionary.items[0].itemId;
    assert.equal(bytesByKind.sequence.toString('utf8'), `${item} -1 ${item} -1 ${item} -1 -2\n`);
    const status = await get(ctx, conversionPath, token, 200);
    assert.deepEqual(envelope(status, await status.json()), converted);
    const repeated = await ctx.request.post(origin + base + conversionPath, { headers: headers(token) });
    assert.equal(repeated.status(), 200); assert.deepEqual(envelope(repeated, await repeated.json()), converted);
    assert.deepEqual(record(id), published); assert.deepEqual(readFileSync(rawPath(id)), exported);
    assert.deepEqual(readdirSync(path.join(temp, 'snapshots', id, 'conversions')), [attempt]);
    // Keep the previous pre-claim raw-integrity assertion on a separate actual validated snapshot.
    const rawUpload = await upload(ctx, token, exported, 'raw-admission-tamper.csv');
    const rawId = idOf(envelope(rawUpload, await rawUpload.json()).snapshotId);
    assert.equal((await ctx.request.post(origin + base + '/' + rawId + '/validation', { headers: headers(token) })).status(), 200);
    const rawBefore = record(rawId);
    const corrupt = Buffer.from(exported); corrupt[corrupt.length - 3] ^= 1;
    writeFileSync(rawPath(rawId), corrupt);
    const unavailable = await ctx.request.post(origin + base + '/' + rawId + '/conversion', { headers: headers(token) });
    assert.equal(unavailable.status(), 503);
    assert.equal((await unavailable.json()).message, 'Conversion admission could not be completed.');
    assert.deepEqual(record(rawId), rawBefore); assert.deepEqual(readdirSync(path.dirname(rawPath(rawId))), ['raw.csv']);
    const mappingPath = path.join(privateDirectory, 'mapping.json'), tampered = Buffer.from(bytesByKind.mapping); tampered[0] ^= 1;
    writeFileSync(mappingPath, tampered);
    for (const suffix of ['/conversion', '/artifacts/sequence', '/artifacts/mapping']) {
      const failed = await get(ctx, '/' + id + suffix, token, 503);
      assert.equal((await failed.json()).code, 'SERVICE_UNAVAILABLE'); assert.equal(failed.headers()['content-disposition'], undefined);
    }
    assert.equal((await ctx.request.post(origin + base + conversionPath, { headers: headers(token) })).status(), 503);
    assert.deepEqual(record(id), published);
    evidence.conversionApi = { snapshotId: id, conversion: 'SUCCEEDED', post: 200, repeatedPost: 200, get: 200,
      sequenceSha256: converted.sequence.sha256, mappingSha256: converted.mapping.sha256,
      statistics: converted.statistics, artifactTamper: 503, rawAdmissionTamper: 503,
      finalE2E: 'NOT VERIFIED — WDM-03.8' };
  });

  await test('WDM-03.7 UI validation/conversion, duplicate exclusion, stats, preview, downloads and GET-only reload', async () => {
    const ctx = await context(), { page, token } = await login(ctx);
    const acquired = await upload(ctx, token, exported, 'ui-conversion.csv');
    const id = idOf(envelope(acquired, await acquired.json()).snapshotId), before = record(id);
    await page.goto(origin + '/admin/datasets/' + id);
    await openTab(page, 'conversion');
    const panel = page.getByRole('region', { name: 'Chuyển đổi dữ liệu', exact: true });
    const button = panel.getByRole('button', { name: 'Chuyển đổi dữ liệu', exact: true });
    await panel.getByText('Trạng thái conversion: NOT_STARTED', { exact: true }).waitFor();
    assert.equal(await button.isDisabled(), true);
    await openTab(page, 'validation');
    await page.getByRole('region', { name: 'Kiểm tra dữ liệu', exact: true })
      .getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }).click();
    await page.getByText('VALID — Có dữ liệu sạch sử dụng được', { exact: true }).waitFor();
    await page.getByRole('button', { name: 'Tiếp tục chuyển đổi', exact: true }).click();
    await button.waitFor();
    let posts = 0, reads = 0, previews = 0;
    const endpoint = origin + base + '/' + id + '/conversion';
    page.on('request', request => {
      if (request.url() === endpoint) { if (request.method() === 'POST') posts++; else if (request.method() === 'GET') reads++; }
      if (request.url().endsWith('/' + id + '/artifacts/sequence')) previews++;
    });
    let release, committed;
    const held = new Promise(resolve => { release = resolve; });
    const done = new Promise(resolve => { committed = resolve; });
    await page.route('**' + base + '/' + id + '/conversion', async route => {
      if (route.request().method() !== 'POST') return route.continue();
      assert.equal(route.request().postData(), null);
      const response = await route.fetch(); assert.equal(response.status(), 200); committed();
      await held; await route.fulfill({ response });
    });
    try {
      await button.click(); await done; assert.equal(await button.isDisabled(), true);
      await button.dispatchEvent('click'); await button.dispatchEvent('click'); assert.equal(posts, 1);
    } finally { release(); }
    await panel.getByRole('heading', { name: 'Thống kê sequence', exact: true }).waitFor();
    assert.equal(reads, 1); assert.equal(previews, 0);
    const published = record(id), attempt = idOf(published.report.conversion.attemptId);
    const response = await get(ctx, '/' + id + '/conversion', token, 200), converted = envelope(response, await response.json());
    for (const [label, expected] of [['Số sequence', '1'], ['Sequence có một itemset', '0'], ['Sequence có nhiều itemset', '1'],
      ['Số itemset', '3'], ['Số item phân biệt', '1'], ['Tổng số lần xuất hiện item', '3'], ['Độ dài sequence nhỏ nhất', '3'],
      ['Độ dài sequence lớn nhất', '3'], ['Độ dài sequence trung bình', '3']]) {
      assert.equal(await panel.getByRole('group', { name: label, exact: true }).getByText(expected, { exact: true }).count(), 1);
    }
    await panel.getByRole('button', { name: 'Xem trước SPMF', exact: true }).click();
    const content = panel.getByLabel('Nội dung xem trước SPMF'); await content.waitFor();
    const directory = path.join(temp, 'snapshots', id, 'conversions', attempt);
    assert.equal(await content.textContent(), readFileSync(path.join(directory, 'sequences.spmf'), 'utf8'));
    assert.equal(previews, 1);
    for (const kind of ['sequence', 'mapping']) {
      const downloaded = page.waitForEvent('download');
      await panel.getByRole('button', { name: kind === 'sequence' ? 'Tải SPMF' : 'Tải mapping JSON', exact: true }).click();
      const file = await downloaded, bytes = readFileSync(await file.path());
      assert.equal(file.suggestedFilename(), 'snapshot-' + id + (kind === 'sequence' ? '.spmf' : '-mapping.json'));
      assert.equal(hash(bytes), converted[kind].sha256); assert.equal(String(bytes.length), converted[kind].sizeBytes);
      assert.deepEqual(bytes, readFileSync(path.join(directory, kind === 'sequence' ? 'sequences.spmf' : 'mapping.json')));
    }
    await page.unrouteAll({ behavior: 'wait' }); await page.reload();
    await panel.getByRole('heading', { name: 'Thống kê sequence', exact: true }).waitFor();
    assert.equal(posts, 1); assert.equal(reads, 2); assert.equal(await content.count(), 0);
    assert.deepEqual(record(id), published); assert.deepEqual(readFileSync(rawPath(id)), exported);
    for (const key of ['path', 'sha', 'name', 'createdAt', 'fromTime', 'toTime']) assert.deepEqual(published[key], before[key]);
    assert.deepEqual(readdirSync(path.join(temp, 'snapshots', id, 'conversions')), [attempt]);
    evidence.conversionUi = { snapshotId: id, postCount: posts, getCount: reads, statistics: converted.statistics,
      sequenceSha256: converted.sequence.sha256, mappingSha256: converted.mapping.sha256, preview: 'exact server bytes', reload: 'GET only' };
  });

  await test('WDM-03.7 UI recovers a committed conversion after lost POST response through GET without replay', async () => {
    const ctx = await context(), { page, token } = await login(ctx);
    const acquired = await upload(ctx, token, exported, 'conversion-lost-response.csv');
    const id = idOf(envelope(acquired, await acquired.json()).snapshotId);
    assert.equal((await ctx.request.post(origin + base + '/' + id + '/validation', { headers: headers(token) })).status(), 200);
    await page.goto(origin + '/admin/datasets/' + id);
    const panel = page.getByRole('region', { name: 'Chuyển đổi dữ liệu', exact: true });
    let posts = 0;
    await page.route('**' + base + '/' + id + '/conversion', async route => {
      if (route.request().method() !== 'POST') return route.continue();
      posts++; const response = await route.fetch(); assert.equal(response.status(), 200);
      await route.abort('failed');
    });
    await panel.getByRole('button', { name: 'Chuyển đổi dữ liệu', exact: true }).click();
    await panel.getByRole('heading', { name: 'Thống kê sequence', exact: true }).waitFor();
    assert.equal(posts, 1); const published = record(id);
    assert.equal(published.report.conversionStatus, 'SUCCEEDED');
    assert.deepEqual(readFileSync(rawPath(id)), exported);
    await page.unrouteAll({ behavior: 'wait' }); await page.reload();
    await panel.getByRole('heading', { name: 'Thống kê sequence', exact: true }).waitFor();
    assert.equal(posts, 1); assert.deepEqual(record(id), published);
    evidence.conversionUi.lostPostRecovery = { snapshotId: id, postCount: posts, recovery: 'verified GET', rawSha256: hash(exported) };
  });

  await test('WDM-03.7 logout suppresses a delayed real artifact download', async () => {
    const ctx = await context(), { page } = await login(ctx), id = evidence.conversionUi.snapshotId;
    await page.goto(origin + '/admin/datasets/' + id);
    const panel = page.getByRole('region', { name: 'Chuyển đổi dữ liệu', exact: true });
    await panel.getByRole('heading', { name: 'Thống kê sequence', exact: true }).waitFor();
    const before = record(id); let downloads = 0, release, received;
    page.on('download', () => downloads++);
    const held = new Promise(resolve => { release = resolve; }), done = new Promise(resolve => { received = resolve; });
    await page.route('**' + base + '/' + id + '/artifacts/sequence', async route => {
      const response = await route.fetch(); assert.equal(response.status(), 200); received(); await held;
      try { await route.fulfill({ response }); } catch { /* logout aborted this browser request */ }
    });
    await panel.getByRole('button', { name: 'Tải SPMF', exact: true }).click(); await done;
    try { await page.getByTitle('Đăng xuất', { exact: true }).click(); await page.waitForURL(origin + '/'); }
    finally { release(); }
    await page.unrouteAll({ behavior: 'wait' }); assert.equal(downloads, 0); assert.deepEqual(record(id), before);
    evidence.conversionUi.logoutDownloadSuppressed = true;
  });

  await test('WDM-03.8 actual browser downloads round-trip through mining-core against rich independent and shuffled fixtures', async () => {
    const resources = path.join(root, 'backend/src/test/resources/wdm/csv-v1');
    const oracle = JSON.parse(readFileSync(path.join(resources, 'round-trip-expected.json'), 'utf8'));
    const classpath = path.join(root, 'mining-core/target/classes');
    const verifier = path.join(root, 'frontend/test/e2e/java/SpmfRoundTripVerifier.java');
    const java = execFileSync('java', ['--version'], { encoding: 'utf8', timeout: 10000, windowsHide: true });
    assert.match(java, /(?:openjdk|java) 21\./);
    // Seed catalog/registry only. Every snapshot below originates from the real UI upload.
    sql(`INSERT INTO CATEGORY(category_id,name,slug) VALUES('rt-category','Synthetic','rt-category');`);
    for (const id of oracle.catalogProducts) {
      assert.match(id, /^rt-[a-z0-9]+$/);
      sql(`INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price)
        VALUES('${id}','${id}','Synthetic','${id}','rt-category',999);`);
    }
    const maximum = Number(sql('SELECT COALESCE(MAX(item_id),0) FROM ITEM_MAPPING;'));
    const retainedItem = maximum + 42;
    sql(`INSERT INTO ITEM_MAPPING(product_id,item_id) VALUES('rt-unused',${maximum + 17}),('rt-p2',${retainedItem});`);
    const counts = [['inputRecords', 'Dòng đầu vào'], ['keptRecords', 'Dòng giữ lại'], ['rejectedRecords', 'Dòng loại theo đơn'],
      ['deduplicatedRecords', 'Dòng lặp đã khử'], ['keptOrders', 'Đơn giữ lại'], ['rejectedOrders', 'Đơn bị loại']];
    const statistics = [['sequenceCount', 'Số sequence'], ['singleItemsetSequenceCount', 'Sequence có một itemset'],
      ['multiItemsetSequenceCount', 'Sequence có nhiều itemset'], ['itemsetCount', 'Số itemset'], ['distinctItemCount', 'Số item phân biệt'],
      ['totalItemOccurrences', 'Tổng số lần xuất hiện item'], ['minSequenceLength', 'Độ dài sequence nhỏ nhất'],
      ['maxSequenceLength', 'Độ dài sequence lớn nhất'], ['averageSequenceLength', 'Độ dài sequence trung bình']];
    const fixtureResults = [];
    let originalSequence, originalMapping, wrongExpectationsChecked = false;
    for (const filename of ['round-trip.csv', 'round-trip-shuffled.csv']) {
      const ctx = await context(), { page, token } = await login(ctx);
      const source = path.join(resources, filename), raw = readFileSync(source);
      const uploaded = page.waitForResponse(r => r.url() === origin + base && r.request().method() === 'POST');
      await page.getByLabel('Tệp CSV', { exact: true }).setInputFiles(source);
      await page.getByRole('button', { name: 'Upload CSV', exact: true }).click();
      const response = await uploaded; assert.equal(response.status(), 201);
      const id = idOf(envelope(response, await response.json()).snapshotId), before = record(id);
      await page.getByRole('link', { name: 'Xem chi tiết snapshot', exact: true }).click();
      await openTab(page, 'validation');
      const validationPanel = page.getByRole('region', { name: 'Kiểm tra dữ liệu', exact: true });
      await validationPanel.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }).click();
      await validationPanel.getByText('Có dữ liệu sạch sử dụng được', { exact: true }).waitFor();
      for (const [key, label] of counts) assert.equal(await validationPanel.getByRole('group', { name: label, exact: true })
        .getByText(oracle.counts[key], { exact: true }).count(), 1);
      const validated = record(id).report.validation;
      await page.getByRole('button', { name: 'Tiếp tục chuyển đổi', exact: true }).click();
      const panel = page.getByRole('region', { name: 'Chuyển đổi dữ liệu', exact: true });
      let posts = 0;
      page.on('request', request => { if (request.url() === origin + base + '/' + id + '/conversion' && request.method() === 'POST') posts++; });
      await panel.getByRole('button', { name: 'Chuyển đổi dữ liệu', exact: true }).click();
      await panel.getByRole('heading', { name: 'Thống kê sequence', exact: true }).waitFor();
      for (const [key, label] of statistics) assert.equal(await panel.getByRole('group', { name: label, exact: true })
        .getByText(String(oracle.statistics[key]), { exact: true }).count(), 1);
      const status = await get(ctx, '/' + id + '/conversion', token, 200), converted = envelope(status, await status.json());
      assert.deepEqual(converted.statistics, oracle.statistics);
      const published = record(id), attempt = idOf(published.report.conversion.attemptId);
      const directory = path.join(temp, 'snapshots', id, 'conversions', attempt), downloads = {};
      for (const kind of ['mapping', 'sequence']) {
        const arrived = page.waitForEvent('download');
        await panel.getByRole('button', { name: kind === 'mapping' ? 'Tải mapping JSON' : 'Tải SPMF', exact: true }).click();
        const file = await arrived, filePath = await file.path(), bytes = readFileSync(filePath);
        assert.equal(file.suggestedFilename(), 'snapshot-' + id + (kind === 'sequence' ? '.spmf' : '-mapping.json'));
        assert.equal(hash(bytes), converted[kind].sha256); assert.equal(String(bytes.length), converted[kind].sizeBytes);
        assert.deepEqual(bytes, readFileSync(path.join(directory, kind === 'sequence' ? 'sequences.spmf' : 'mapping.json')));
        downloads[kind] = { bytes, filePath };
      }
      const document = JSON.parse(downloads.mapping.bytes.toString('utf8'));
      assert.equal(document.formatVersion, 'CONVERSION_ARTIFACT_V1'); assert.equal(document.policyVersion, 'GLOBAL_PRODUCT_ITEM_INT_V1');
      const mapping = new Map(), items = new Set(); let previous = 0;
      for (const entry of document.items) {
        assert.match(entry.productId, /^rt-p[123]$/); assert.equal(Number.isInteger(entry.itemId), true);
        assert.ok(entry.itemId > previous); previous = entry.itemId;
        assert.equal(mapping.has(entry.productId), false); assert.equal(items.has(entry.itemId), false);
        mapping.set(entry.productId, entry.itemId); items.add(entry.itemId);
        assert.equal(Number(sql(`SELECT item_id FROM ITEM_MAPPING WHERE product_id='${entry.productId}';`)), entry.itemId);
      }
      assert.deepEqual([...mapping.keys()].sort(), ['rt-p1', 'rt-p2', 'rt-p3']); assert.equal(mapping.get('rt-p2'), retainedItem);
      assert.equal(sql("SELECT COUNT(*) FROM ITEM_MAPPING WHERE product_id='rt-p4';"), '0');
      // Only resolve hand-authored product membership to IDs; never derive order/boundaries from production output.
      const layout = oracle.sequences.map(sequence => sequence.itemsets.map(itemset => itemset.map(product => {
        assert.ok(mapping.has(product)); return mapping.get(product);
      }).sort((a, b) => a - b)));
      const layoutText = sequences => sequences.map(sequence => sequence.map(items => items.join(',')).join(';')).join('\n') + '\n';
      const expectedPath = path.join(temp, id + '-expected.txt'); writeFileSync(expectedPath, layoutText(layout));
      const verifierArgs = ['--class-path', classpath, verifier, downloads.sequence.filePath, expectedPath];
      const verified = execFileSync('java', verifierArgs, { encoding: 'utf8', timeout: 60000, windowsHide: true });
      assert.equal(verified.trim(), 'PASS mining-core browser round-trip: sequences=3');
      if (!wrongExpectationsChecked) {
        const wrong = [
          [layout[0], layout[2], layout[1]], // Same sequence/item totals, wrong customer/SID layout.
          [[layout[0][1], layout[0][0], layout[0][2]], layout[1], layout[2]], // Same totals, wrong temporal order.
          [[...layout[0]], [layout[1][0].slice(0, 1), layout[1][0].slice(1)], [[...layout[2][0], ...layout[2][1]].sort((a, b) => a - b)]],
        ]; // The third preserves total sequences/itemsets/items but exchanges basket/temporal boundaries.
        for (const bad of wrong) {
          writeFileSync(expectedPath, layoutText(bad));
          const denied = spawnSync('java', verifierArgs, { encoding: 'utf8', timeout: 60000, windowsHide: true });
          assert.equal(denied.error, undefined); assert.equal(denied.status, 1);
          assert.equal(denied.stderr.trim(), 'FAIL mining-core browser round-trip verification.');
        }
        wrongExpectationsChecked = true;
      }
      await page.reload(); await panel.getByRole('heading', { name: 'Thống kê sequence', exact: true }).waitFor(); assert.equal(posts, 1);
      const repeated = await ctx.request.post(origin + base + '/' + id + '/conversion', { headers: headers(token) });
      assert.equal(repeated.status(), 200); assert.deepEqual(envelope(repeated, await repeated.json()), converted);
      assert.deepEqual(record(id), published); assert.deepEqual(published.report.validation, validated);
      assert.deepEqual(readFileSync(rawPath(id)), raw); assert.equal(hash(raw), before.sha);
      for (const field of ['path', 'sha', 'name', 'createdAt', 'fromTime', 'toTime']) assert.deepEqual(published[field], before[field]);
      assert.deepEqual(readdirSync(path.join(temp, 'snapshots', id, 'conversions')), [attempt]);
      if (!originalSequence) { originalSequence = downloads.sequence.bytes; originalMapping = downloads.mapping.bytes; }
      else { assert.deepEqual(downloads.sequence.bytes, originalSequence); assert.deepEqual(downloads.mapping.bytes, originalMapping); }
      fixtureResults.push({ filename, snapshotId: id, sourceSha256: hash(raw), sourceSizeBytes: String(raw.length),
        sequenceSha256: converted.sequence.sha256, mappingSha256: converted.mapping.sha256, coreParser: 'PASS',
        exactStructure: '3 SIDs / 6 itemsets / 8 occurrences', postCountBeforeExplicitIdempotencyCheck: posts });
      await ctx.close(); contexts.splice(contexts.indexOf(ctx), 1);
    }
    evidence.roundTrip = { fixtures: fixtureResults, statistics: oracle.statistics, wrongEqualCountExpectationsRejected: 3,
      parser: 'unchanged mining-core SpmfSequenceParser', java: java.split('\n')[0], shuffledCanonicalBytes: 'identical' };
    evidence.conversionApi.finalE2E = 'PASS — WDM-03.8; see roundTrip';
  });

  await test('UI/UX Admin home, upload next step, shared accounts, responsive tabs and dark tables', async () => {
    const ctx = await context(), { page } = await login(ctx);
    await page.goto(origin + '/admin');
    await page.getByRole('heading', { name: 'Từ dữ liệu đơn hàng đến combo sản phẩm', exact: true }).waitFor();
    assert.equal(await page.getByRole('region', { name: /^Bước/ }).count(), 7);
    const combo = page.getByRole('region', { name: 'Bước 7: Tạo combo', exact: true });
    assert.equal(await combo.getByText('Chưa khả dụng', { exact: true }).count(), 1);
    assert.equal(await combo.getByRole('link').count(), 0);
    await page.screenshot({ path: path.join(logDirectory, 'ui-admin-home.png'), fullPage: true });
    await page.getByRole('link', { name: 'Bắt đầu với dữ liệu', exact: true }).click();
    await page.getByRole('button', { name: '+ Tạo dữ liệu mới', exact: true }).click();
    const uploaded = page.waitForResponse(r => r.url() === origin + base && r.request().method() === 'POST');
    await page.getByLabel('Tệp CSV', { exact: true }).setInputFiles(path.join(root, 'backend/src/test/resources/wdm/csv-v1/round-trip.csv'));
    await page.getByRole('button', { name: 'Upload CSV', exact: true }).click();
    const response = await uploaded; assert.equal(response.status(), 201);
    const id = idOf(envelope(response, await response.json()).snapshotId);
    const receipt = page.getByRole('region', { name: 'Snapshot đã tạo', exact: true });
    await receipt.getByText('Đã lưu dữ liệu thành công', { exact: true }).waitFor();
    await page.screenshot({ path: path.join(logDirectory, 'ui-upload-result.png'), fullPage: true });
    await receipt.getByRole('link', { name: 'Tiếp tục kiểm tra dữ liệu', exact: true }).click();
    assert.ok(page.url().endsWith('/' + id + '?tab=validation'));
    assert.equal(await page.getByRole('tab', { name: /^2\. Kiểm tra dữ liệu/ }).getAttribute('aria-selected'), 'true');
    await page.goto(origin + '/admin/account');
    await page.getByRole('heading', { name: 'Thông tin tài khoản', exact: true }).waitFor();
    await page.screenshot({ path: path.join(logDirectory, 'ui-admin-account.png'), fullPage: true });
    const richId = evidence.roundTrip.fixtures[0].snapshotId;
    await page.goto(origin + '/admin/datasets/' + richId);
    await page.getByRole('heading', { name: 'Thống kê sequence', exact: true }).waitFor();
    await page.screenshot({ path: path.join(logDirectory, 'ui-snapshot-desktop.png'), fullPage: true });
    const requests = [];
    page.on('request', request => { if (request.method() === 'POST' && request.url().includes('/' + richId + '/')) requests.push(request.url()); });
    await openTab(page, 'source'); await openTab(page, 'validation'); await openTab(page, 'conversion');
    assert.equal(requests.length, 0);
    await page.setViewportSize({ width: 390, height: 844 }); await openTab(page, 'validation');
    const table = page.getByRole('table', { name: 'Danh sách thông báo kiểm tra', exact: true });
    await table.waitFor();
    const style = await table.locator('thead th').first().evaluate(element => {
      const value = getComputedStyle(element); return { background: value.backgroundColor, position: value.position, border: value.borderBottomWidth };
    });
    await page.screenshot({ path: path.join(logDirectory, 'ui-snapshot-mobile.png'), fullPage: true });
    assert.notEqual(style.background, 'rgba(0, 0, 0, 0)'); assert.equal(style.position, 'sticky'); assert.equal(style.border, '2px');
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.getByTitle('Thay đổi chế độ màu', { exact: true }).click();
    await page.screenshot({ path: path.join(logDirectory, 'ui-snapshot-dark.png'), fullPage: true });
    const customer = await context(), { page: customerPage } = await login(customer, 'dataset.customer');
    await customerPage.goto(origin + '/user');
    await customerPage.getByRole('heading', { name: 'Thông tin tài khoản', exact: true }).waitFor();
    await customerPage.screenshot({ path: path.join(logDirectory, 'ui-client-account.png'), fullPage: true });
    evidence.uiRefresh = { adminHome: '/admin', nextStepDeepLink: 'validation', tabSwitchPostCount: 0,
      sharedAccounts: ['ADMIN', 'CUSTOMER'], mobileViewport: '390x844', tableHeader: style, screenshots: 7 };
  });

  await runConfigurationCases({ test, context, login, origin, sql, temp, root, logDirectory, evidence });
  evidence.status = 'PASS'; save();
  console.log('Dataset browser cases: ' + results.length + '/' + results.length + ' PASS.');
}
