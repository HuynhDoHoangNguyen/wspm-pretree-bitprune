import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import path from 'node:path';

const previewPath = '/api/v1/admin/mining-runs/preview';
const validationPath = '/api/v1/admin/mining-runs/configuration-validation';
const base = '/api/v1/admin/dataset-snapshots';
const sha = bytes => createHash('sha256').update(bytes).digest('hex');

/** Real application/transport only: routes delay/drop actual backend responses, never invent success. */
export async function runConfigurationCases({ test, context, login, origin, sql, temp, root, logDirectory, evidence }) {
  const resources = path.join(root, 'backend/src/test/resources/wdm/csv-v1');
  const oracle = JSON.parse(readFileSync(path.join(resources, 'round-trip-expected.json'), 'utf8'));
  const captures = [], coreJar = path.join(root, 'mining-core/target/mining-core-1.0-SNAPSHOT.jar');
  const backendJar = path.join(root, 'backend/target/backend-1.0-SNAPSHOT.jar');
  const verifier = path.join(root, 'frontend/test/e2e/java/MiningThresholdVerifier.java');
  const spmf = path.join(temp, 'configuration-downloaded.spmf'), rows = path.join(temp, 'configuration-captured.tsv');
  const summary = { matrix: [], verifier: null, negativeControls: 0 };
  evidence.configuration = summary;
  let id, published, converted;
  function uuid(value) { assert.match(value, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/); return value; }
  function envelope(response, value, status) {
    assert.equal(response.status(), status); const headers = response.headers();
    assert.equal(value.requestId, headers['x-request-id']); assert.ok(headers['cache-control'].includes('no-store'));
    if (status === 200) { assert.equal(value.code, 'SUCCESS'); return value.data; }
    assert.equal(value.status, status); return value;
  }
  const data = (algorithm = 'WSPM_PRETREE', mode = 'PERCENT', value = 14, selected = id) => ({ snapshotId: selected, algorithm, minsupMode: mode, minsupValue: value });
  async function post(ctx, token, endpoint, input) {
    return ctx.request.post(origin + endpoint, { headers: token ? { Authorization: 'Bearer ' + token } : {}, data: input });
  }
  function snapshot(selected = id) { return sql(`SELECT * FROM DATASET_SNAPSHOT WHERE snapshot_id='${uuid(selected)}';`); }
  function record(selected = id) {
    return JSON.parse(sql(`SELECT JSON_OBJECT('count',CAST(sequence_count AS CHAR),'report',conversion_report_json,
      'sequenceSha',sequence_sha256,'mappingSha',mapping_sha256) FROM DATASET_SNAPSHOT WHERE snapshot_id='${uuid(selected)}';`));
  }
  function files() {
    const result = {};
    function walk(dir) {
      for (const entry of readdirSync(dir, { withFileTypes: true })) {
        assert.equal(entry.isSymbolicLink(), false); const file = path.join(dir, entry.name);
        if (entry.isDirectory()) walk(file); else result[path.relative(temp, file)] = sha(readFileSync(file));
      }
    }
    walk(path.join(temp, 'snapshots')); return result;
  }
  function business() { return { snapshot: snapshot(), mapping: sql('SELECT * FROM ITEM_MAPPING ORDER BY item_id;'), files: files(), runs: sql('SELECT COUNT(*) FROM MINING_RUN;') }; }
  async function session() { const ctx = await context(); return { ctx, ...await login(ctx) }; }
  async function upload(page) {
    await page.goto(origin + '/admin/datasets');
    await page.getByLabel('Tệp CSV', { exact: true }).setInputFiles(path.join(resources, 'round-trip.csv'));
    const arrived = page.waitForResponse(r => r.url() === origin + base && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Upload CSV', exact: true }).click();
    const response = await arrived; assert.equal(response.status(), 201);
    const body = await response.json(); assert.equal(body.requestId, response.headers()['x-request-id']);
    return uuid(body.data.snapshotId);
  }
  async function open(page, selected = id) { await page.goto(origin + '/admin/mining-runs?snapshotId=' + selected); await page.getByRole('form', { name: 'Cấu hình khai phá', exact: true }).waitFor(); }
  async function configure(page, algorithm, mode, value) {
    await page.getByLabel('Thuật toán', { exact: true }).click();
    await page.getByRole('option', { name: algorithm === 'WSPM_PRETREE' ? 'WSPM_PreTree' : 'WSPM_BitPrune', exact: true }).click();
    await page.getByLabel('Mode minsup', { exact: true }).click();
    await page.getByRole('option', { name: mode === 'PERCENT' ? 'Phần trăm (PERCENT)' : 'Tuyệt đối (ABSOLUTE)', exact: true }).click();
    await page.getByLabel('Giá trị minsup', { exact: false }).fill(value);
  }
  async function click(page, action, expected, request) {
    const endpoint = action === 'preview' ? previewPath : validationPath;
    const arrived = page.waitForResponse(r => r.url() === origin + endpoint && r.request().method() === 'POST');
    await page.getByRole('button', { name: action === 'preview' ? 'Xem trước dữ liệu' : 'Kiểm tra cấu hình', exact: true }).click();
    const response = await arrived, sent = response.request().postDataJSON();
    assert.deepEqual(Object.keys(sent).sort(), ['algorithm', 'minsupMode', 'minsupValue', 'snapshotId']);
    // JSON numbers serialize -0 as 0. Compare the actual wire contract, not JS signed-zero identity.
    assert.deepEqual(sent, JSON.parse(JSON.stringify(request)));
    const result = envelope(response, await response.json(), expected);
    if (expected === 200) {
      assert.equal(result.snapshotId, id); assert.equal(result.algorithm, sent.algorithm);
      assert.equal(result.minimumSupport.mode, sent.minsupMode); assert.equal(result.minimumSupport.configuredValue, sent.minsupValue);
      assert.equal(result.sequenceSha256, published.sequenceSha); assert.equal(result.mappingSha256, published.mappingSha);
      assert.equal(result.artifactVersion, converted.artifactVersion);
      if (action === 'preview') {
        assert.deepEqual(result.statistics, oracle.statistics); assert.equal(result.executionEligibility, 'NOT_EVALUATED');
        await page.getByRole('heading', { name: 'Thống kê dataset', exact: true }).waitFor();
      } else { assert.equal(result.configurationStatus, 'SUPPORTED'); assert.equal(result.sequenceCount, '3'); await page.getByText('Cấu hình thuộc phạm vi hỗ trợ', { exact: true }).waitFor(); }
      const read = label => page.getByText(label, { exact: true }).locator('xpath=following-sibling::dd[1]').innerText();
      assert.equal(await read('Giá trị đã cấu hình'), String(result.minimumSupport.configuredValue));
      assert.equal(await read('Tỷ lệ đã resolve'), result.minimumSupport.relativeFraction === null ? 'Không áp dụng' : String(result.minimumSupport.relativeFraction));
      assert.equal(await read('Ngưỡng hiệu dụng'), String(result.minimumSupport.effectiveThreshold));
      captures.push([sent.minsupMode, String(sent.minsupValue), '3', String(result.minimumSupport.configuredValue),
        String(result.minimumSupport.relativeFraction), String(result.minimumSupport.effectiveThreshold), '200'].join('\t'));
    } else if (expected === 422) {
      assert.equal(result.code, 'UNSUPPORTED_MINING_CONFIGURATION');
      assert.equal(result.details[0].field, 'minsupValue'); assert.equal(result.details[0].code, 'ZERO_EFFECTIVE_THRESHOLD');
      await page.getByText('Cấu hình ngoài phạm vi hỗ trợ', { exact: true }).waitFor();
      captures.push([sent.minsupMode, String(sent.minsupValue), '3', '-', '-', '-', '422'].join('\t'));
    }
    return result;
  }
  function verify(file = rows, expectedCount = '3') {
    return spawnSync('java', ['--class-path', coreJar, verifier, spmf, file, expectedCount, backendJar, coreJar],
      { encoding: 'utf8', timeout: 30000, windowsHide: true });
  }

  await test('WDM-04.6 selection through real upload/conversion/detail; prefill and reload never POST', async () => {
    // Catalog seed only; do not depend on earlier cases having inserted the fixture products.
    sql("INSERT IGNORE INTO CATEGORY(category_id,name,slug) VALUES('cfg-category','Synthetic configuration','cfg-category');");
    for (const product of oracle.catalogProducts) {
      assert.match(product, /^rt-[a-z0-9]+$/);
      if (sql(`SELECT COUNT(*) FROM PRODUCT WHERE product_id='${product}';`) === '0') {
        sql(`INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES('${product}','${product}','Synthetic','${product}','cfg-category',999);`);
      }
    }
    const { page } = await session(); id = await upload(page);
    await page.getByRole('link', { name: 'Xem chi tiết snapshot', exact: true }).click();
    await page.getByRole('tab', { name: /^2\. Kiểm tra dữ liệu/ }).click();
    await page.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }).click();
    await page.getByText('Có dữ liệu sạch sử dụng được', { exact: true }).waitFor();
    await page.getByRole('button', { name: 'Tiếp tục chuyển đổi', exact: true }).click();
    await page.getByRole('button', { name: 'Chuyển đổi dữ liệu', exact: true }).click();
    await page.getByRole('heading', { name: 'Thống kê sequence', exact: true }).waitFor();
    published = record(); const attempt = uuid(published.report.conversion.attemptId);
    const download = page.waitForEvent('download'); await page.getByRole('button', { name: 'Tải SPMF', exact: true }).click();
    const bytes = readFileSync(await (await download).path()); writeFileSync(spmf, bytes); assert.equal(sha(bytes), published.sequenceSha);
    assert.equal(sha(readFileSync(path.join(temp, 'snapshots', id, 'conversions', attempt, 'sequences.spmf'))), sha(bytes));
    let posts = 0; page.on('request', r => { if ([origin + previewPath, origin + validationPath].includes(r.url()) && r.method() === 'POST') posts++; });
    await page.getByRole('link', { name: 'Cấu hình khai phá', exact: true }).click();
    await page.getByRole('form', { name: 'Cấu hình khai phá', exact: true }).waitFor();
    assert.equal(await page.getByLabel('Snapshot ID', { exact: false }).inputValue(), id);
    await page.reload(); await page.getByRole('form', { name: 'Cấu hình khai phá', exact: true }).waitFor(); assert.equal(posts, 0);
    // Read the real conversion receipt, not arbitrary database labels, for public artifact version.
    const { ctx, token } = await session();
    const response = await ctx.request.get(origin + base + '/' + id + '/conversion', { headers: { Authorization: 'Bearer ' + token } });
    converted = envelope(response, await response.json(), 200); assert.deepEqual(converted.statistics, oracle.statistics);
    summary.snapshotId = id; summary.sequenceSha256 = published.sequenceSha; summary.mappingSha256 = published.mappingSha;
    summary.prefillPostCount = posts; summary.sequenceBytes = bytes.length;
  });

  await test('WDM-04.6 both algorithms and modes: real UI/API/core tuple and immutable business data', async () => {
    const before = business(), { page } = await session(); await open(page);
    for (const algorithm of ['WSPM_PRETREE', 'WSPM_BITPRUNE']) for (const mode of ['PERCENT', 'ABSOLUTE']) {
      await configure(page, algorithm, mode, '14'); const request = data(algorithm, mode, 14);
      await click(page, 'preview', 200, request); await click(page, 'validate', 200, request);
      summary.matrix.push({ algorithm, mode, input: '14', validationStatus: 200 });
    }
    assert.deepEqual(business(), before); assert.equal(before.runs, '0');
    await page.screenshot({ path: path.join(logDirectory, 'configuration-supported-desktop.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.screenshot({ path: path.join(logDirectory, 'configuration-supported-mobile.png'), fullPage: true });
  });

  await test('WDM-04.6 zero/underflow versus tiny positive, fractional and max thresholds; Java negative controls', async () => {
    const before = business(), { page } = await session(); await open(page);
    for (const algorithm of ['WSPM_PRETREE', 'WSPM_BITPRUNE']) for (const mode of ['PERCENT', 'ABSOLUTE']) {
      for (const value of ['0', '-0.0', '1e-999', '4.9e-324', '0.25', '100']) {
        await configure(page, algorithm, mode, value); const request = data(algorithm, mode, Number(value));
        await click(page, 'preview', 200, request);
        const expected = ['0', '-0.0', '1e-999'].includes(value) || (mode === 'PERCENT' && value === '4.9e-324') ? 422 : 200;
        await click(page, 'validate', expected, request);
        summary.matrix.push({ algorithm, mode, input: value, sentValue: request.minsupValue, validationStatus: expected });
      }
    }
    await configure(page, 'WSPM_BITPRUNE', 'ABSOLUTE', '1.7976931348623157e308');
    await click(page, 'validate', 200, data('WSPM_BITPRUNE', 'ABSOLUTE', Number.MAX_VALUE));
    writeFileSync(rows, captures.join('\n') + '\n'); const verified = verify(); assert.equal(verified.status, 0); assert.match(verified.stdout, /^PASS core threshold verification:/);
    const wrong = path.join(temp, 'configuration-wrong.tsv'), first = captures[0].split('\t'); first[5] = '0';
    writeFileSync(wrong, first.join('\t') + '\n'); assert.equal(verify(wrong).status, 1); assert.equal(verify(rows, '4').status, 1);
    summary.verifier = { capturedTuples: captures.length, result: verified.stdout.trim(), coreJarSha256: sha(readFileSync(coreJar)) }; summary.negativeControls = 2;
    assert.deepEqual(business(), before);
  });

  await test('WDM-04.6 local input, real Auth and request/resource errors preserve classifications', async () => {
    const { ctx, page, token } = await session(); await open(page);
    let posts = 0; page.on('request', r => { if (r.url().startsWith(origin + '/api/v1/admin/mining-runs/') && r.method() === 'POST') posts++; });
    for (const value of ['', ' ', 'NaN', 'Infinity', '0x10', '-1', '101']) {
      await page.getByLabel('Giá trị minsup', { exact: false }).fill(value); await page.getByRole('button', { name: 'Kiểm tra cấu hình', exact: true }).click();
      await page.getByText('Nhập số hữu hạn từ 0 đến 100.', { exact: true }).waitFor();
    }
    assert.equal(posts, 0);
    const anonymous = await context(); assert.equal((await post(anonymous, null, validationPath, '{')).status(), 401);
    const customer = await context(), grant = await login(customer, 'dataset.customer');
    assert.equal((await post(customer, grant.token, validationPath, { invalid: true })).status(), 403);
    for (const [input, status] of [[{ invalid: true }, 400], [data('WSPM_PRETREE', 'PERCENT', 0, '00000000-0000-4000-8000-000000000000'), 404], [{ ...data(), minWeight: 1 }, 400]]) {
      const response = await post(ctx, token, validationPath, input); envelope(response, await response.json(), status);
    }
    const malformed = await ctx.request.post(origin + validationPath, { headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, data: '{' });
    envelope(malformed, await malformed.json(), 400);
    assert.equal((await ctx.request.post(origin + validationPath, { headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'text/plain' }, data: '{}' })).status(), 415);
    assert.equal((await ctx.request.get(origin + validationPath, { headers: { Authorization: 'Bearer ' + token } })).status(), 405);
    const query = await post(ctx, token, validationPath + '?force=true', data()); envelope(query, await query.json(), 400);
    summary.localInvalidPostCount = posts;
  });

  await test('WDM-04.6 double submission and lost real response: no automatic replay, explicit retry only', async () => {
    const before = business(), { page } = await session(); await open(page); await configure(page, 'WSPM_PRETREE', 'PERCENT', '14');
    let release, ready; const arrived = new Promise(resolve => { ready = resolve; }), held = new Promise(resolve => { release = resolve; }); let posts = 0;
    page.on('request', r => { if (r.url() === origin + validationPath && r.method() === 'POST') posts++; });
    await page.route('**' + validationPath, async route => { const response = await route.fetch(); assert.equal(response.status(), 200); ready(); await held; await route.abort('failed'); }, { times: 1 });
    const button = page.getByRole('button', { name: 'Kiểm tra cấu hình', exact: true });
    try { await button.evaluate(element => { element.click(); element.click(); }); await arrived; assert.equal(posts, 1); }
    finally { release(); }
    await page.getByRole('alert').waitFor(); assert.equal(posts, 1);
    await click(page, 'validate', 200, data()); assert.equal(posts, 2);
    summary.retryPostCounts = [1, posts]; assert.deepEqual(business(), before);
  });

  await test('WDM-04.6 edit/snapshot change/unmount suppress delayed real supported responses', async () => {
    const { page } = await session(); await open(page);
    for (const change of ['edit', 'snapshot', 'unmount']) {
      await open(page); await configure(page, 'WSPM_PRETREE', 'PERCENT', '14');
      let release, ready; const arrived = new Promise(resolve => { ready = resolve; }), held = new Promise(resolve => { release = resolve; });
      await page.route('**' + validationPath, async route => { const response = await route.fetch(); assert.equal(response.status(), 200); ready(); await held; try { await route.fulfill({ response }); } catch { /* Aborted client connection. */ } }, { times: 1 });
      try {
        await page.getByRole('button', { name: 'Kiểm tra cấu hình', exact: true }).click(); await arrived;
        if (change === 'edit') await page.getByLabel('Giá trị minsup', { exact: false }).fill('15');
        else if (change === 'snapshot') await page.getByLabel('Snapshot ID', { exact: false }).fill('00000000-0000-4000-8000-000000000000');
        else await page.getByRole('link', { name: 'Dữ liệu khai phá', exact: true }).first().click();
      } finally { release(); }
      await page.unrouteAll({ behavior: 'wait' });
      assert.equal(await page.getByText('Cấu hình thuộc phạm vi hỗ trợ', { exact: true }).count(), 0);
      if (change !== 'unmount') assert.equal(await page.getByRole('button', { name: 'Kiểm tra cấu hình', exact: true }).isEnabled(), true);
    }
  });

  await test('WDM-04.6 logout suppresses delayed real validation and never replays its POST', async () => {
    const { page } = await session(); await open(page); await configure(page, 'WSPM_PRETREE', 'PERCENT', '14');
    let release, ready; const arrived = new Promise(resolve => { ready = resolve; }), held = new Promise(resolve => { release = resolve; }); let posts = 0;
    page.on('request', r => { if (r.url() === origin + validationPath && r.method() === 'POST') posts++; });
    await page.route('**' + validationPath, async route => { const response = await route.fetch(); assert.equal(response.status(), 200); ready(); await held; try { await route.fulfill({ response }); } catch { /* Aborted by logout. */ } }, { times: 1 });
    try {
      await page.getByRole('button', { name: 'Kiểm tra cấu hình', exact: true }).click(); await arrived;
      await page.getByTitle('Đăng xuất', { exact: true }).click(); await page.waitForURL(origin + '/');
    } finally { release(); }
    await page.unrouteAll({ behavior: 'wait' }); assert.equal(posts, 1); assert.equal(await page.getByText('Cấu hình thuộc phạm vi hỗ trợ', { exact: true }).count(), 0);
    summary.logoutPostCount = posts;
  });

  await test('WDM-04.6 pending/tampered publication fails closed; restored data and supported reads remain immutable', async () => {
    const { ctx, page, token } = await session(); const pending = await upload(page);
    let response = await post(ctx, token, validationPath, data('WSPM_PRETREE', 'PERCENT', 0, pending)); envelope(response, await response.json(), 409);
    await open(page); const before = business(); const attempt = uuid(published.report.conversion.attemptId);
    for (const file of [path.join(temp, 'snapshots', id, 'raw.csv'), path.join(temp, 'snapshots', id, 'conversions', attempt, 'sequences.spmf'), path.join(temp, 'snapshots', id, 'conversions', attempt, 'mapping.json')]) {
      const original = readFileSync(file);
      try {
        writeFileSync(file, 'tampered'); await configure(page, 'WSPM_PRETREE', 'PERCENT', '0');
        await click(page, 'validate', 503, data('WSPM_PRETREE', 'PERCENT', 0));
        await page.getByText(/Chưa thể đọc hoặc xác minh dữ liệu/).waitFor();
        assert.equal(await page.getByText('Cấu hình thuộc phạm vi hỗ trợ', { exact: true }).count(), 0);
      } finally { writeFileSync(file, original); }
    }
    try {
      sql(`UPDATE DATASET_SNAPSHOT SET sequence_count=sequence_count+1 WHERE snapshot_id='${uuid(id)}';`);
      response = await post(ctx, token, validationPath, data('WSPM_PRETREE', 'PERCENT', 0)); envelope(response, await response.json(), 409);
    } finally { sql(`UPDATE DATASET_SNAPSHOT SET sequence_count=3 WHERE snapshot_id='${uuid(id)}';`); }
    assert.deepEqual(business(), before); assert.equal(before.runs, '0');
    await configure(page, 'WSPM_PRETREE', 'PERCENT', '14'); await click(page, 'validate', 200, data());
    assert.deepEqual(business(), before); summary.businessImmutable = true;
  });
}
