import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, existsSync, realpathSync, renameSync } from 'node:fs';
import { createHash, randomBytes } from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import path from 'node:path';

const base = '/api/v1/admin/mining-runs', datasets = '/api/v1/admin/dataset-snapshots';
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const uuid = value => { assert.match(value, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/); return value; };
const properties = file => Object.fromEntries(readFileSync(file, 'utf8').split(/\r?\n/).filter(line => line && !line.startsWith('#')).map(line => { const index = line.indexOf('='); return [line.slice(0, index), line.slice(index + 1)]; }));
async function until(check, message, timeout = 60000) {
  const deadline = Date.now() + timeout;
  do { const value = await check(); if (value) return value; await delay(100); } while (Date.now() < deadline);
  throw new Error(message);
}

export async function runEngineCases({ browser, origin, sql, root, temp, logDirectory, verifierClasspath, startBatch, initialDirectory, backendConfiguration }) {
  const contexts = [], cases = [], runs = [], evidence = { gate: 'WDM-05.10', status: 'RUNNING', cases, runs, negativeControls: [] };
  const password = randomBytes(18).toString('base64url'), username = 'engine.admin';
  const resources = path.join(root, 'backend/src/test/resources/wdm/csv-v1');
  const downloadedSequence = path.join(logDirectory, 'published.spmf'), downloadedMapping = path.join(logDirectory, 'published-mapping.json');
  let snapshotId, directory = initialDirectory;
  const save = () => writeFileSync(path.join(logDirectory, 'RESULT.json'), JSON.stringify(evidence, null, 2));
  const rows = () => sql('SELECT COUNT(*) FROM MINING_RUN;');
  const headers = token => ({ Authorization: 'Bearer ' + token });
  function data(algorithm = 'WSPM_PRETREE', mode = 'ABSOLUTE', value = 0.1) { return { snapshotId, algorithm, minsupMode: mode, minsupValue: value }; }
  function envelope(response, body, status) {
    assert.equal(response.status(), status); assert.equal(body.requestId, response.headers()['x-request-id']);
    assert.ok(response.headers()['cache-control'].includes('no-store'));
    if (status < 300) { assert.equal(body.code, 'SUCCESS'); return body.data; }
    assert.equal(body.status, status); return body;
  }
  async function test(name, action) {
    const started = Date.now();
    try { await action(); cases.push({ name, status: 'PASS', elapsedMs: Date.now() - started }); console.log('PASS Engine ' + cases.length + ': ' + name); }
    catch (error) {
      evidence.status = 'FAIL'; cases.push({ name, status: 'FAIL', errorType: error.name }); console.error('FAILED CASE: ' + name);
      let index = 0;
      for (const ctx of contexts) for (const page of ctx.pages()) {
        try { await page.screenshot({ path: path.join(directory, 'failure-' + cases.length + '-' + index++ + '.png'), fullPage: true }); } catch { /* page already closed */ }
      }
      throw error;
    }
    finally { save(); for (const ctx of contexts.splice(0)) await ctx.close(); }
  }
  async function context() {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, acceptDownloads: true });
    ctx.setDefaultTimeout(20000); contexts.push(ctx); return ctx;
  }
  async function login() {
    const ctx = await context(), page = await ctx.newPage();
    await page.goto(origin + '/admin/mining-runs');
    await page.getByLabel('Tên tài khoản', { exact: false }).fill(username);
    await page.getByLabel('Mật khẩu', { exact: false }).fill(password);
    const arrived = page.waitForResponse(r => r.url().endsWith('/api/v1/auth/login') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    const response = await arrived, granted = envelope(response, await response.json(), 200);
    await page.getByRole('heading', { name: 'Từ dữ liệu đơn hàng đến combo sản phẩm', exact: true }).waitFor();
    return { ctx, page, token: granted.accessToken };
  }
  async function prepare(page, request) {
    await page.goto(origin + '/admin/mining-runs?snapshotId=' + snapshotId);
    await page.getByRole('form', { name: 'Cấu hình khai phá', exact: true }).waitFor();
    await page.getByLabel('Thuật toán', { exact: true }).click();
    await page.getByRole('option', { name: request.algorithm === 'WSPM_PRETREE' ? 'WSPM_PreTree' : 'WSPM_BitPrune', exact: true }).click();
    await page.getByLabel('Mode minsup', { exact: true }).click();
    await page.getByRole('option', { name: request.minsupMode === 'ABSOLUTE' ? 'Tuyệt đối (ABSOLUTE)' : 'Phần trăm (PERCENT)', exact: true }).click();
    await page.getByLabel('Giá trị minsup', { exact: false }).fill(String(request.minsupValue));
    const arrived = page.waitForResponse(r => r.url() === origin + base + '/configuration-validation' && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Kiểm tra cấu hình', exact: true }).click();
    const response = await arrived, result = envelope(response, await response.json(), 200);
    assert.equal(result.configurationStatus, 'SUPPORTED');
    await until(() => page.getByRole('button', { name: 'Tạo lần chạy', exact: true }).isEnabled(), 'Create button enabled');
  }
  async function create(page, request) {
    await prepare(page, request);
    const arrived = page.waitForResponse(r => r.url() === origin + base && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Tạo lần chạy', exact: true }).click();
    const response = await arrived; assert.deepEqual(response.request().postDataJSON(), request);
    assert.deepEqual(Object.keys(response.request().postDataJSON()).sort(), ['algorithm', 'minsupMode', 'minsupValue', 'snapshotId']);
    const accepted = envelope(response, await response.json(), 202); uuid(accepted.runId); assert.equal(accepted.status, 'QUEUED');
    assert.equal(accepted.snapshotId, snapshotId); assert.equal(accepted.algorithm, request.algorithm);
    await page.waitForURL(origin + '/admin/mining-runs/' + accepted.runId);
    return { id: accepted.runId, request, page };
  }
  async function status(ctx, token, id) {
    const response = await ctx.request.get(origin + base + '/' + uuid(id), { headers: headers(token) });
    return envelope(response, await response.json(), 200);
  }
  async function terminal(session, run) {
    return until(async () => { const report = await status(session.ctx, session.token, run.id); return ['SUCCEEDED', 'FAILED'].includes(report.status) ? report : null; }, 'Terminal status deadline');
  }
  function inbox(ids) {
    assert.ok(ids.length <= 2); const temporary = path.join(directory, 'INBOX.tmp');
    writeFileSync(temporary, ids.map(uuid).join('\n')); renameSync(temporary, path.join(directory, 'INBOX'));
  }
  async function observed(id) {
    return until(() => {
      assert.equal(existsSync(path.join(directory, 'OBSERVER-ERROR')), false, 'Observer export failed');
      const file = path.join(directory, id + '.properties'); return existsSync(file) ? { file, props: properties(file) } : null;
    }, 'Original retained result export deadline');
  }
  function verify(run, exported, files = {}) {
    const args = ['--class-path', verifierClasspath, path.join(root, 'frontend/test/e2e/java/EngineRunVerifier.java'), files.props || exported.file,
      files.map || path.join(directory, run.id + '.map.tsv'), downloadedSequence, downloadedMapping,
      path.join(directory, 'input-' + run.request.algorithm + '.spmf'), run.id, run.request.algorithm, run.request.minsupMode, String(run.request.minsupValue)];
    return spawnSync('java', args, { encoding: 'utf8', timeout: 60000, windowsHide: true });
  }
  async function success(session, run, { checkUi = true } = {}) {
    const report = await terminal(session, run); assert.equal(report.status, 'SUCCEEDED');
    const exported = await observed(run.id), result = verify(run, exported);
    writeFileSync(path.join(directory, run.id + '-VERIFIER.log'), result.stdout + result.stderr);
    assert.equal(result.status, 0, 'Original map or metadata differs: STOP research gate; inspect verifier log');
    const p = exported.props;
    assert.equal(p['db.snapshot_id'], snapshotId); assert.ok(Number(p.retainedCount) <= 2);
    if (checkUi) await run.page.bringToFront(); // Real UI deliberately pauses polling in hidden tabs.
    for (const [key, dbKey] of Object.entries({ patternCount: 'pattern_count', candidateCount: 'candidate_count', datasetIoMs: 'dataset_io_ms', preprocessingMs: 'preprocessing_ms',
      dfsMiningMs: 'dfs_mining_ms', completeAlgorithmMs: 'complete_algorithm_ms', outputIoMs: 'output_io_ms', totalExecutionMs: 'total_execution_ms', peakSampledJvmHeapBytes: 'peak_sampled_jvm_heap_bytes' })) {
      const value = key.endsWith('Count') ? report[key] : report.metrics[key];
      assert.equal(value === null ? 'null' : value, p['db.' + dbKey]);
      if (checkUi) {
        if (key === 'patternCount') await run.page.getByText('Khai phá hoàn tất', { exact: true }).waitFor();
        const element = run.page.locator('[data-metric="' + key + '"] data');
        if (value === null) assert.equal(await element.count(), 0); else {
          assert.equal(await element.getAttribute('value'), value);
          assert.equal(await element.innerText(), value.replace(/\B(?=(\d{3})+(?!\d))/g, '.'));
        }
      }
    }
    assert.equal(report.configuration.configuredValue, run.request.minsupValue);
    assert.equal(report.configuration.effectiveThreshold, Number(p['db.effective_threshold']));
    assert.equal(report.measurement.contractVersion, 'WDM-05.6');
    if (checkUi) {
      const read = label => run.page.getByText(label, { exact: true }).locator('xpath=following-sibling::dd[1]').innerText();
      assert.equal(await read('Ngưỡng hiệu dụng'), String(report.configuration.effectiveThreshold));
      if (report.patternCount === '0') await run.page.getByText(/Khai phá hoàn tất với 0 patterns/).waitFor();
      await run.page.screenshot({ path: path.join(directory, run.id + '-desktop.png'), fullPage: true });
      await run.page.setViewportSize({ width: 390, height: 844 });
      assert.equal(await run.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      await run.page.screenshot({ path: path.join(directory, run.id + '-mobile.png'), fullPage: true });
    }
    const item = { runId: run.id, snapshotId, request: run.request, batch: path.basename(directory), patternCount: report.patternCount,
      candidateCount: report.candidateCount, metrics: report.metrics, status: report.status, mapSha256: p['map.sha256'], verifier: result.stdout.trim(), uiVerified: checkUi };
    runs.push(item); save(); return exported;
  }

  await test('real Auth and browser upload -> validation -> conversion -> artifact download', async () => {
    const ctx = await context();
    const csrf = await ctx.request.get(origin + '/api/v1/auth/csrf'); const csrfToken = envelope(csrf, await csrf.json(), 200).token;
    const registered = await ctx.request.post(origin + '/api/v1/auth/register', { headers: { Origin: origin, 'X-XSRF-TOKEN': csrfToken },
      data: { username, email: 'engine.admin@example.test', password, displayName: 'Synthetic Engine Gate' } });
    const user = envelope(registered, await registered.json(), 201);
    // UUID produced by actual registration, then grant ADMIN only in the owned schema.
    const userId = uuid(user.userId); sql(`INSERT INTO USER_ROLE(user_id,role_id) VALUES('${userId}','admin-role');`);
    sql("INSERT INTO CATEGORY(category_id,name,slug) VALUES('engine-category','Synthetic Engine','engine-category');");
    for (const product of JSON.parse(readFileSync(path.join(resources, 'round-trip-expected.json'), 'utf8')).catalogProducts) {
      assert.match(product, /^rt-[a-z0-9]+$/);
      sql(`INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES('${product}','${product}','Synthetic','${product}','engine-category',999);`);
    }
    const session = await login(), page = session.page;
    await page.goto(origin + '/admin/datasets');
    await page.getByRole('tab', { name: 'Tạo dữ liệu mới · Xuất đơn hàng hoặc upload CSV', exact: true }).click();
    await page.getByLabel('Tệp CSV', { exact: true }).setInputFiles(path.join(resources, 'round-trip.csv'));
    const arrived = page.waitForResponse(r => r.url() === origin + datasets && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Upload CSV', exact: true }).click();
    const uploaded = await arrived; snapshotId = uuid(envelope(uploaded, await uploaded.json(), 201).snapshotId);
    await page.getByRole('link', { name: 'Xem chi tiết snapshot', exact: true }).click();
    await page.getByRole('tab', { name: /^2\. Kiểm tra dữ liệu/ }).click();
    await page.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }).click();
    await page.getByText('Có dữ liệu sạch sử dụng được', { exact: true }).waitFor();
    await page.getByRole('button', { name: 'Tiếp tục chuyển đổi', exact: true }).click();
    await page.getByRole('button', { name: 'Chuyển đổi dữ liệu', exact: true }).click();
    await page.getByRole('heading', { name: 'Thống kê sequence', exact: true }).waitFor();
    const download = page.waitForEvent('download'); await page.getByRole('button', { name: 'Tải SPMF', exact: true }).click();
    writeFileSync(downloadedSequence, readFileSync(await (await download).path()));
    const mapping = await session.ctx.request.get(origin + datasets + '/' + snapshotId + '/artifacts/mapping', { headers: headers(session.token) });
    assert.equal(mapping.status(), 200); writeFileSync(downloadedMapping, await mapping.body());
    const hashes = sql(`SELECT sequence_sha256,mapping_sha256,raw_sha256 FROM DATASET_SNAPSHOT WHERE snapshot_id='${snapshotId}';`).split('\t');
    assert.equal(sha(readFileSync(downloadedSequence)), hashes[0]); assert.equal(sha(readFileSync(downloadedMapping)), hashes[1]);
    assert.equal(sha(readFileSync(path.join(resources, 'round-trip.csv'))), hashes[2]);
    evidence.snapshot = { snapshotId, sequenceSha256: hashes[0], mappingSha256: hashes[1], rawSha256: hashes[2], sequenceCount: '3' };
  });

  await test('workspace snapshot library and storage metadata use real read APIs; refresh keeps source discoverable', async () => {
    const session = await login(), page = session.page;
    await page.goto(origin + '/admin/datasets');
    const region = page.getByRole('region', { name: 'Danh sách snapshot', exact: true }); await region.waitFor();
    await page.getByLabel('Tìm tên file hoặc Snapshot ID', { exact: true }).fill(snapshotId);
    await page.getByRole('form', { name: 'Tìm snapshot', exact: true }).getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
    await page.waitForURL(url => url.searchParams.get('search') === snapshotId);
    await page.reload(); await region.waitFor();
    assert.equal(await page.getByLabel('Tìm tên file hoặc Snapshot ID', { exact: true }).inputValue(), snapshotId);
    await region.getByRole('link', { name: 'Xem chi tiết', exact: true }).click();
    await page.getByText('Tệp dữ liệu trên backend', { exact: true }).click();
    await page.getByText('SPMF · sequences.spmf', { exact: true }).waitFor();
    await page.getByText('Item mapping · mapping.json', { exact: true }).waitFor();
    const response = await session.ctx.request.get(origin + datasets + '/' + snapshotId + '/storage', { headers: headers(session.token) });
    const storage = envelope(response, await response.json(), 200);
    assert.equal(storage.binding, 'CURRENT_SNAPSHOT_PUBLICATION'); assert.equal(storage.files.length, 3);
    for (const file of storage.files) { assert.ok(file.reference.startsWith(snapshotId + '/')); assert.ok(path.resolve(file.absolutePath).startsWith(path.resolve(temp, 'snapshots') + path.sep)); }
    await page.screenshot({ path: path.join(logDirectory, 'workspace-storage-desktop.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.screenshot({ path: path.join(logDirectory, 'workspace-storage-mobile.png'), fullPage: true });
    const anonymous = await context();
    for (const endpoint of [datasets, base, '/api/v1/admin/mining-execution/capacity', datasets + '/' + snapshotId + '/storage'])
      assert.equal((await anonymous.request.get(origin + endpoint)).status(), 401);
    evidence.workspace = { snapshotRediscovered: true, storageFiles: 3, anonymousReadDenials: 4 };
  });

  for (const [batch, mode, value] of [['positive-absolute', 'ABSOLUTE', 0.1], ['positive-percent', 'PERCENT', 14], ['empty-success', 'PERCENT', 100]]) {
    await test(batch + ': both browser-created original results = both direct engines = independent oracle; DB/API/UI metrics', async () => {
      if (batch !== 'positive-absolute') directory = await startBatch(batch);
      const session = await login(), first = await create(session.page, data('WSPM_PRETREE', mode, value));
      const second = await create(await session.ctx.newPage(), data('WSPM_BITPRUNE', mode, value));
      inbox([first.id, second.id]);
      const exported = await success(session, first); await success(session, second);
      assert.equal(readFileSync(path.join(directory, 'CALLS'), 'utf8'), '2');
      assert.equal((await observed(second.id)).props.retainedCount, '2');
      const before = rows(), busy = await session.ctx.request.post(origin + base, { headers: headers(session.token), data: data() });
      envelope(busy, await busy.json(), 503); assert.equal(rows(), before); assert.equal(readFileSync(path.join(directory, 'CALLS'), 'utf8'), '2');
      if (batch === 'positive-absolute') {
        const original = readFileSync(path.join(directory, first.id + '.map.tsv'), 'utf8');
        assert.match(original, /<\(\d+ \d+\)>\t/); assert.match(original, /<\(\d+\)\(\d+\)>\t/);
        const lines = original.trimEnd().split('\n');
        const controls = {
          'support-same-count': [lines[0].replace(/\t\d+$/, '\t99'), ...lines.slice(1)].join('\n') + '\n',
          'structural-same-count': ['<(999999)>\t1', ...lines.slice(1)].join('\n') + '\n',
          'deleted-pattern': lines.slice(1).join('\n') + '\n',
        };
        for (const [name, content] of Object.entries(controls)) {
          const map = path.join(directory, 'negative-' + name + '.tsv'), props = path.join(directory, 'negative-' + name + '.properties');
          writeFileSync(map, content); writeFileSync(props, readFileSync(exported.file, 'utf8').replace(/^map.sha256=.*$/m, 'map.sha256=' + sha(content)));
          const result = verify(first, exported, { map, props }); assert.notEqual(result.status, 0);
          assert.match(result.stderr, /First pattern\/support mismatch|Pattern count mismatch/);
          writeFileSync(path.join(directory, 'negative-' + name + '.log'), result.stderr); evidence.negativeControls.push({ name, status: 'PASS_REJECTED' });
        }
        const originalBits = BigInt('0x' + exported.props['resolved.bits']);
        const props = path.join(directory, 'negative-threshold-ulp.properties');
        writeFileSync(props, readFileSync(exported.file, 'utf8').replace(/^resolved.bits=.*$/m, 'resolved.bits=' + (originalBits + 1n).toString(16)));
        const result = verify(first, exported, { props }); assert.notEqual(result.status, 0); assert.match(result.stderr, /raw-bit mismatch/);
        evidence.negativeControls.push({ name: 'threshold-one-ULP', status: 'PASS_REJECTED' });
        const capacityResponse = await session.ctx.request.get(origin + '/api/v1/admin/mining-execution/capacity', { headers: headers(session.token) });
        const capacity = envelope(capacityResponse, await capacityResponse.json(), 200);
        assert.equal(capacity.snapshot.used, 2); assert.equal(capacity.snapshot.available, 0);
        assert.deepEqual(capacity.snapshot.entries.map(e => e.runId).sort(), [first.id, second.id].sort());
        const historyPage = await session.ctx.newPage(); await historyPage.goto(origin + '/admin/mining-runs?tab=history&snapshotId=' + snapshotId);
        const historyRegion = historyPage.getByRole('region', { name: 'Lịch sử run', exact: true }); await historyRegion.waitFor();
        const rows = historyRegion.locator('tbody tr'); assert.equal(await rows.count(), 2);
        assert.ok((await rows.first().innerText()).includes(second.id.slice(0, 8)));
        await historyPage.screenshot({ path: path.join(logDirectory, 'workspace-history-desktop.png'), fullPage: true });
        await historyPage.setViewportSize({ width: 390, height: 844 });
        assert.equal(await historyPage.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
        await historyPage.screenshot({ path: path.join(logDirectory, 'workspace-history-mobile.png'), fullPage: true });
        await historyPage.goto(origin + '/admin/mining-runs?snapshotId=' + snapshotId);
        await historyPage.getByText('Còn 0 slot', { exact: true }).waitFor();
        await historyPage.screenshot({ path: path.join(logDirectory, 'workspace-capacity-full.png'), fullPage: true });
        evidence.workspace.historyNewestFirst = true; evidence.workspace.capacityUnchangedAfterReads = true;
      } else if (batch === 'empty-success') assert.equal(exported.props['map.count'], '0');
    });
  }

  await test('queued artifact tamper is rejected before second engine call; original first result remains exact', async () => {
    directory = await startBatch('tamper-queued', true); const session = await login();
    const first = await create(session.page, data()); await until(() => existsSync(path.join(directory, 'ENTERED')), 'Engine latch entered');
    assert.equal((await status(session.ctx, session.token, first.id)).status, 'RUNNING');
    const second = await create(await session.ctx.newPage(), data('WSPM_BITPRUNE'));
    assert.equal((await status(session.ctx, session.token, second.id)).status, 'QUEUED');
    inbox([first.id, second.id]);
    const stored = sql(`SELECT sequence_file_path FROM DATASET_SNAPSHOT WHERE snapshot_id='${snapshotId}';`);
    const file = realpathSync(path.isAbsolute(stored) ? stored : path.join(temp, 'snapshots', stored));
    assert.ok(file.startsWith(realpathSync(path.join(temp, 'snapshots')) + path.sep));
    const original = readFileSync(file);
    try {
      writeFileSync(file, Buffer.concat([original, Buffer.from('\n999 -1 -2\n')]));
      writeFileSync(path.join(directory, 'RELEASE'), 'tampered-after-admission');
      await success(session, first);
      const failed = await terminal(session, second); assert.equal(failed.status, 'FAILED'); assert.equal(failed.failure.code, 'INPUT_REVERIFICATION_FAILED');
      const exported = await observed(second.id); assert.equal(exported.props.hasResult, 'false');
      assert.equal(exported.props.failureCode, 'INPUT_REVERIFICATION_FAILED'); assert.equal(readFileSync(path.join(directory, 'CALLS'), 'utf8'), '1');
      assert.equal(existsSync(path.join(directory, 'input-WSPM_BITPRUNE.spmf')), false);
      await second.page.bringToFront();
      await second.page.getByRole('alert').filter({ hasText: 'Không thể xác minh lại dữ liệu đầu vào.' }).waitFor();
      for (const key of ['patternCount', 'candidateCount', 'datasetIoMs', 'preprocessingMs', 'dfsMiningMs', 'completeAlgorithmMs', 'totalExecutionMs', 'peakSampledJvmHeapBytes'])
        assert.equal(key.endsWith('Count') ? failed[key] : failed.metrics[key], null);
      evidence.tamper = { failedRunId: second.id, code: failed.failure.code, engineCalls: 1, originalResultVerified: true };
    } finally { writeFileSync(file, original); assert.equal(sha(readFileSync(file)), evidence.snapshot.sequenceSha256); }
  });

  await test('drop actual 202 after commit: uncertain UI never replays; original committed execution still verifies', async () => {
    directory = await startBatch('lost-response'); const session = await login(), request = data('WSPM_BITPRUNE', 'PERCENT', 14);
    await prepare(session.page, request); let id, posts = 0;
    session.page.on('request', r => { if (r.url() === origin + base && r.method() === 'POST') posts++; });
    await session.page.route('**' + base, async route => {
      const response = await route.fetch(); assert.equal(response.status(), 202);
      const accepted = envelope(response, await response.json(), 202); id = uuid(accepted.runId);
      await route.abort('failed');
    }, { times: 1 });
    const before = Number(rows()); await session.page.getByRole('button', { name: 'Tạo lần chạy', exact: true }).click();
    await session.page.getByText('Chưa xác định kết quả tạo run', { exact: true }).waitFor();
    await until(() => id, 'Privately captured committed run ID'); assert.equal(posts, 1); assert.equal(Number(rows()), before + 1);
    assert.equal(await session.page.getByRole('button', { name: 'Tạo lần chạy', exact: true }).isDisabled(), true);
    inbox([id]); await success(session, { id, request }, { checkUi: false });
    await delay(2500); assert.equal(posts, 1); assert.equal(Number(rows()), before + 1);
    assert.equal((await session.page.locator('body').innerText()).includes(id), false, 'Private captured ID must not be injected into UI');
    await session.page.screenshot({ path: path.join(directory, 'uncertain.png'), fullPage: true });
    evidence.lostResponse = { privatelyObservedRunId: id, committedRows: 1, creationPosts: posts, actual202Dropped: true };
  });

  await test('zero, signed zero, JSON underflow and percentage underflow: 422, no rows, no engine entry', async () => {
    directory = await startBatch('zero-operational-gate'); const session = await login(), before = rows();
    for (const algorithm of ['WSPM_PRETREE', 'WSPM_BITPRUNE']) for (const [mode, text] of [['ABSOLUTE', '0'], ['ABSOLUTE', '-0.0'], ['ABSOLUTE', '1e-999'], ['PERCENT', '4.9e-324']]) {
      const wire = `{"snapshotId":"${snapshotId}","algorithm":"${algorithm}","minsupMode":"${mode}","minsupValue":${text}}`;
      const response = await session.ctx.request.post(origin + base, { headers: { ...headers(session.token), 'Content-Type': 'application/json' }, data: wire });
      const error = envelope(response, await response.json(), 422); assert.equal(error.code, 'UNSUPPORTED_MINING_CONFIGURATION');
      assert.equal(error.details[0].code, 'ZERO_EFFECTIVE_THRESHOLD');
    }
    assert.equal(rows(), before); assert.equal(existsSync(path.join(directory, 'CALLS')), false);
    await session.page.goto(origin + '/admin/mining-runs?snapshotId=' + snapshotId);
    await session.page.getByLabel('Giá trị minsup', { exact: false }).fill('4.9e-324');
    const arrived = session.page.waitForResponse(r => r.url() === origin + base + '/configuration-validation' && r.request().method() === 'POST');
    await session.page.getByRole('button', { name: 'Kiểm tra cấu hình', exact: true }).click();
    const response = await arrived; envelope(response, await response.json(), 422);
    await session.page.getByText('Cấu hình ngoài phạm vi hỗ trợ', { exact: true }).waitFor();
    assert.equal(await session.page.getByRole('button', { name: 'Tạo lần chạy', exact: true }).isDisabled(), true);
    assert.equal(rows(), before); assert.equal(existsSync(path.join(directory, 'CALLS')), false);
    evidence.zeroGate = { rejected: 8, insertedRows: 0, engineCalls: 0, r6MiningPerformed: false };
  });

  if (process.env.WDM_ENGINE_REGRESSION === '1') await test('full core/backend regressions serially on the owned MySQL schema', async () => {
    const configuration = { ...backendConfiguration, 'wdm.execution.enabled': false, 'spring.flyway.enabled': true };
    const logfile = path.join(logDirectory, 'REGRESSION.log');
    const { createWriteStream } = await import('node:fs'); const stream = createWriteStream(logfile);
    const child = spawn(process.platform === 'win32' ? 'cmd.exe' : 'mvn', process.platform === 'win32' ? ['/d', '/c', 'mvn -B -pl backend -am test'] : ['-B', '-pl', 'backend', '-am', 'test'],
      { cwd: root, windowsHide: true, env: { ...process.env, SPRING_APPLICATION_JSON: JSON.stringify(configuration) }, stdio: ['ignore', 'pipe', 'pipe'] });
    child.stdout.pipe(stream); child.stderr.pipe(stream);
    const exit = await new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); });
    await new Promise(resolve => stream.end(resolve)); assert.equal(exit, 0, 'Regression failed: inspect REGRESSION.log');
    evidence.regression = { status: 'PASS', log: 'REGRESSION.log' };
  });
  evidence.status = 'PASS'; save();
}
