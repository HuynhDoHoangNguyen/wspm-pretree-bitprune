import assert from 'node:assert/strict';
import { createHash, randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync, unlinkSync, readdirSync } from 'node:fs';
import path from 'node:path';

const base = '/api/v1/admin/dataset-snapshots';
const unknown = '00000000-0000-4000-8000-000000000000';
const header = 'order_id,order_item_id,customer_code,ordered_at,status,product_id,sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code\r\n';
const hash = bytes => createHash('sha256').update(bytes).digest('hex');

/** Only synthetic order fixtures are inserted; every snapshot must originate from the real API. */
export async function runDatasetCases({ browser, httpsOrigin: origin, sql, temp, logDirectory }) {
  const results = [], contexts = [];
  const password = randomBytes(18).toString('base64url');
  const evidence = { gate: 'WDM-01.8', browser: browser.version(), cases: results };
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
    if (username === 'dataset.admin') await page.getByRole('heading', { name: 'Dữ liệu khai phá', exact: true }).waitFor();
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
    await page.getByText(hash(exported), { exact: true }).waitFor();
    await page.reload(); await page.getByText(hash(exported), { exact: true }).waitFor();
    await page.goto(origin + '/admin/datasets/' + snapshot.snapshotId);
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
  evidence.status = 'PASS'; save();
  console.log('Dataset browser cases: ' + results.length + '/' + results.length + ' PASS.');
}
