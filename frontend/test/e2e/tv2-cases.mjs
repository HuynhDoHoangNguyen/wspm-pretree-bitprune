import assert from 'node:assert/strict';
import { randomBytes, createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

/** TEST_FIXTURE acceptance only; every business response comes from the real HTTP/MySQL path. */
export async function runTv2Cases({ browser, httpsOrigin: origin, sql, temp, logDirectory }) {
  const fixture = JSON.parse(readFileSync(path.join(temp, 'tv2-metadata.json'), 'utf8'));
  assert.equal(fixture.label, 'TEST_FIXTURE_NOT_MINING_OUTPUT');
  assert.equal(fixture.comboCreation, 'COMBO_GENERATION_SERVICE_AND_EXECUTABLE_WRITER');
  const password = process.env.TV2_INT14_BROWSER_PASSWORD || randomBytes(18).toString('base64url');
  const users = {}, contexts = [], cases = [];
  const evidence = { label: fixture.label, browser: browser.version(), status: 'RUNNING', cases, realMining: 'NOT VERIFIED', productionAuthority: 'NOT VERIFIED' };
  function envelope(response, body) {
    assert.equal(body.code, 'SUCCESS');assert.equal(body.requestId, response.headers()['x-request-id']);
    assert.ok(response.headers()['cache-control'].includes('no-store'));return body.data;
  }
  async function context() {
    const c = await browser.newContext({ ignoreHTTPSErrors: true });c.setDefaultTimeout(20000);c.setDefaultNavigationTimeout(25000);contexts.push(c);return c;
  }
  async function test(name, work) {
    try { await work();cases.push({ name, status: 'PASS' });console.log('PASS TV2 ' + cases.length + ': ' + name); }
    catch (error) { cases.push({ name, status: 'FAIL', errorType: error.name });evidence.status = 'FAIL';console.error('FAILED CASE: ' + name);
      console.error((error.stack || '').split('\n').filter(s => s.trim().startsWith('at ')).slice(0, 3).join('\n'));throw error;
    } finally { writeFileSync(path.join(logDirectory, 'RESULT.json'), JSON.stringify(evidence, null, 2));for (const c of contexts.splice(0)) await c.close(); }
  }
  async function authPost(c, op, data) {
    const csrf = await c.request.get(origin + '/api/v1/auth/csrf');assert.equal(csrf.status(), 200);
    return c.request.post(origin + '/api/v1/auth/' + op, { headers: { Origin: origin, 'X-XSRF-TOKEN': envelope(csrf, await csrf.json()).token }, data });
  }
  async function login(username = 'tv2.admin1') {
    const c = await context(), page = await c.newPage();await page.goto(origin + '/admin/signin');
    await page.locator('input[name=username]').fill(username);await page.locator('input[name=password]').fill(password);
    const reply = page.waitForResponse(r => r.url().endsWith('/api/v1/auth/login') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();const response = await reply;assert.equal(response.status(), 200);
    const grant = envelope(response, await response.json());await page.locator('input[name=username]').waitFor({ state: 'detached' });
    return { c, page, token: grant.accessToken };
  }
  function responseFor(p, predicate) {
    const reply = p.waitForResponse(predicate);
    // A click can fail while its response waiter is pending; outer case still observes the rejection.
    reply.catch(() => {});return reply;
  }
  const adminApi = (p, pathname, method = 'GET') => responseFor(p, r => new URL(r.url()).pathname === pathname && r.request().method() === method);
  async function selectRun(page, id) {
    const reply = adminApi(page, `/api/v1/admin/pattern-runs/${id}/patterns`);await page.getByRole('combobox', { name: 'Mining run', exact: true }).selectOption(id);
    const response = await reply;assert.equal(response.status(), 200);return envelope(response, await response.json());
  }
  async function comboList(page, batch = '', status = '', kind = '') {
    assert.ok(batch,'Combo list must be scoped to a saved batch');
    await page.goto(origin+'/admin/combos?batchId='+encodeURIComponent(batch));
    await page.getByLabel('Trạng thái Combo', { exact: true }).selectOption(status);
    await page.getByLabel('Loại Combo', { exact: true }).selectOption(kind);
    const reply = responseFor(page, r => {
      const url = new URL(r.url());return url.pathname === '/api/v1/admin/combos/batches/'+encodeURIComponent(batch)+'/combos' && r.request().method() === 'GET'
        && (url.searchParams.get('status') || '') === status
        && (url.searchParams.get('comboKind') || '') === kind;
    });await page.getByRole('button', { name: 'Áp dụng bộ lọc', exact: true }).click();
    const response = await reply;assert.equal(response.status(), 200);return envelope(response, await response.json());
  }
  async function openCombo(page, row) {
    const reply = adminApi(page, '/api/v1/admin/combos/' + row.comboId);await page.getByRole('button', { name: `${row.name} (${row.comboId})`, exact: true }).click();
    const response = await reply;assert.equal(response.status(), 200);const data = envelope(response, await response.json());
    await page.getByRole('heading', { name: 'Chi tiết Combo ' + row.comboId, exact: true }).waitFor();return data;
  }
  async function review(page, row, decision, note, status = 200) {
    await page.getByRole('button', { name: decision === 'APPROVE' ? 'Duyệt' : 'Từ chối', exact: true }).click();
    if (note !== undefined) await page.getByLabel('Ghi chú review', { exact: true }).fill(note);
    const reply = adminApi(page, '/api/v1/admin/combos/' + row.comboId + '/review', 'POST');
    await page.getByRole('button', { name: 'Xác nhận quyết định', exact: true }).click();const response = await reply;
    assert.equal(response.status(), status);const body = await response.json();return status === 200 ? envelope(response, body) : body;
  }
  function safeId(id) { assert.match(id, /^[A-Za-z0-9_-]{1,40}$/);return id; }
  function stored(id) { return JSON.parse(sql(`SELECT JSON_OBJECT('status',status,'reviewer',reviewed_by,'time',CAST(reviewed_at AS CHAR),'note',review_note) FROM COMBO WHERE combo_id='${safeId(id)}';`)); }
  function protectedState(id) {
    const value = sql(`SET SESSION group_concat_max_len=1048576;
      SELECT combo_id,batch_id,name,combo_kind,currency_code,reference_original_price,reference_sale_price,CAST(support_abs AS CHAR),support_ratio,pattern_weight,explanation,discount_type,stacking_policy FROM COMBO WHERE combo_id='${safeId(id)}';
      SELECT itemset_order,item_order,product_id,quantity,unit_price_snapshot,is_anchor_eligible,is_discount_eligible FROM COMBO_ITEM WHERE combo_id='${safeId(id)}' ORDER BY itemset_order,item_order;
      SELECT s.pattern_id,s.is_primary,p.canonical_pattern,CAST(p.support_abs AS CHAR),p.support_ratio,p.weighted_support,p.pattern_weight,p.pattern_weight_policy FROM COMBO_SOURCE s JOIN PATTERN p ON p.pattern_id=s.pattern_id WHERE s.combo_id='${safeId(id)}' ORDER BY s.pattern_id;
      SELECT pi.pattern_id,pi.itemset_order,pi.item_order,pi.item_id FROM PATTERN_ITEM pi JOIN COMBO_SOURCE s ON s.pattern_id=pi.pattern_id WHERE s.combo_id='${safeId(id)}' ORDER BY pi.pattern_id,pi.itemset_order,pi.item_order;
      SELECT d.run_id,d.item_id,d.product_id,d.product_name_snapshot,d.sku_snapshot,d.occurrence_count,d.sequence_support,d.item_weight FROM RUN_ITEM_STATISTIC d JOIN COMBO_BATCH b ON b.run_id=d.run_id JOIN COMBO c ON c.batch_id=b.batch_id WHERE c.combo_id='${safeId(id)}' ORDER BY d.item_id;
      SELECT HEX(m.receipt_digest),HEX(m.input_digest),m.pattern_count,m.pattern_item_count,m.dictionary_count FROM MINING_RUN_COMPLETION m JOIN COMBO_BATCH b ON b.run_id=m.run_id JOIN COMBO c ON c.batch_id=b.batch_id WHERE c.combo_id='${safeId(id)}';
      SELECT HEX(e.content_digest),HEX(e.evidence_digest),HEX(e.representation_digest) FROM COMBO_BATCH_WRITE_EVIDENCE e JOIN COMBO c ON c.batch_id=e.batch_id WHERE c.combo_id='${safeId(id)}';`);
    return createHash('sha256').update(value).digest('hex');
  }
  await test('owned fixture metadata, real Auth registration and exact DB baseline', async () => {
    const c = await context();
    for (const username of ['tv2.admin1', 'tv2.admin2', 'tv2.customer']) {
      const response = await authPost(c, 'register', { username, password, email: username + '@example.test', displayName: 'TEST_FIXTURE ' + username });assert.equal(response.status(), 201);
      const id = envelope(response, await response.json()).userId;assert.match(id, /^[a-f0-9-]{36}$/);users[username] = id;
      if (username !== 'tv2.customer') sql(`INSERT INTO USER_ROLE(user_id,role_id) VALUES('${id}','admin-role');`);
    }
    assert.equal(sql('SELECT COUNT(*) FROM COMBO_BATCH_WRITE_EVIDENCE;'), '8');assert.equal(sql('SELECT COUNT(*) FROM COMBO;'), '24');
    assert.equal(sql(`SELECT CAST(support_abs AS CHAR) FROM PATTERN WHERE pattern_id=18446744073709551615;`), '9007199254740993');
    evidence.fixture = { batches: 8, combos: 24, comboCreation: fixture.comboCreation, patternRun: fixture.patternRun, patternCount: 23, migrations: sql('SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM flyway_schema_history WHERE success=1;') };
  });
  await test('anonymous and Customer UI gates plus real server 401/403', async () => {
    const anon = await context(), p = await anon.newPage();await p.goto(origin + '/admin/pattern-explorer');await p.locator('input[name=username]').waitFor();
    for (const route of ['/api/v1/admin/combos', '/api/v1/admin/pattern-runs']) assert.equal((await anon.request.get(origin + route)).status(), 401);
    assert.equal((await anon.request.post(origin + '/api/v1/admin/combos/missing/review', { data: {} })).status(), 401);
    const customer = await login('tv2.customer');await customer.page.goto(origin + '/admin/combos');await customer.page.getByText(/không có quyền ADMIN/i).waitFor();
    for (const route of ['/api/v1/admin/combos', '/api/v1/admin/pattern-runs']) assert.equal((await customer.c.request.get(origin + route, { headers: { Authorization: 'Bearer ' + customer.token } })).status(), 403);
    assert.equal((await customer.c.request.post(origin + '/api/v1/admin/combos/missing/review', { headers: { Authorization: 'Bearer ' + customer.token }, data: {} })).status(), 403);
    await customer.page.goto(origin + '/admin/pattern-explorer');await customer.page.getByText(/không có quyền ADMIN/i).waitFor();
  });
  await test('Pattern exact IDs/itemsets/dictionary ALL filter/support bounds/paging/capability', async () => {
    const { page } = await login();await page.goto(origin + '/admin/pattern-explorer');await page.getByRole('combobox', { name: 'Mining run', exact: true }).waitFor();
    const initial = await selectRun(page, fixture.patternRun);assert.equal(initial.runPatternTotal, '23');assert.equal(initial.items.length, 20);assert.equal(initial.weightFilter.enabled, false);
    assert.equal(await page.getByLabel('Trọng số mẫu tối thiểu', { exact: true }).isDisabled(), true);
    const next = adminApi(page, `/api/v1/admin/pattern-runs/${fixture.patternRun}/patterns`);await page.getByRole('button', { name: 'Trang sau', exact: true }).click();assert.equal(envelope(await next, await (await next).json()).items.length, 3);
    await page.getByRole('button', { name: 'Trang trước', exact: true }).click();await page.getByRole('button', { name: '18446744073709551615', exact: true }).waitFor();
    await page.getByRole('button', { name: '18446744073709551615', exact: true }).click();await page.getByRole('group', { name: 'Itemset 1', exact: true }).getByText(/Laptop snapshot/).waitFor();
    assert.equal(await page.getByRole('group', { name: 'Itemset 2', exact: true }).count(), 0);await page.getByText('Support (sequence): 9007199254740993', { exact: true }).waitFor();
    const search = page.getByLabel('Tìm sản phẩm theo tên hoặc SKU snapshot', { exact: true });await search.fill('SKU-T14-L');await page.getByRole('checkbox', { name: /Laptop snapshot/ }).check();
    await search.fill('SKU-T14-M');await page.getByRole('checkbox', { name: /Mouse snapshot/ }).check();
    const all = adminApi(page, `/api/v1/admin/pattern-runs/${fixture.patternRun}/patterns`);await page.getByRole('button', { name: 'Áp dụng', exact: true }).click();const allResponse = await all;
    assert.deepEqual(new URL(allResponse.url()).searchParams.getAll('itemId').sort(), ['1', '2']);assert.equal(envelope(allResponse, await allResponse.json()).filteredTotal, '22');
    await page.getByLabel('Support tối thiểu (sequence)', { exact: true }).fill('8');await page.getByLabel('Support tối đa (sequence)', { exact: true }).fill('8');
    const bounded = adminApi(page, `/api/v1/admin/pattern-runs/${fixture.patternRun}/patterns`);await page.getByRole('button', { name: 'Áp dụng', exact: true }).click();const boundResponse = await bounded;const data = envelope(boundResponse, await boundResponse.json());assert.equal(data.filteredTotal, '1');assert.equal(data.items[0].canonicalPattern, '<(1)(2)>');
    await page.getByRole('button', { name: '9007199254740993', exact: true }).click();await page.getByRole('group', { name: 'Itemset 2', exact: true }).getByText(/Mouse snapshot/).waitFor();
    assert.equal(sql(`SELECT COUNT(DISTINCT pattern_id) FROM PATTERN_ITEM WHERE item_id IN (1,2) AND pattern_id IN (SELECT pattern_id FROM PATTERN WHERE run_id='${fixture.patternRun}' AND support_abs=8);`), '1');
  });
  await test('Pattern run source paging/reset, empty/no-match and real backend error/retry', async () => {
    const { page } = await login();await page.goto(origin + '/admin/pattern-explorer');await page.getByRole('combobox', { name: 'Mining run', exact: true }).waitFor();await selectRun(page, fixture.patternRun);
    const source = adminApi(page, '/api/v1/admin/pattern-runs');await page.getByRole('button', { name: 'Run sau', exact: true }).click();const response = await source;assert.equal(envelope(response, await response.json()).page, 1);
    assert.equal(await page.getByRole('combobox', { name: 'Mining run', exact: true }).inputValue(), '');await page.getByText('Trang run 2', { exact: true }).waitFor();
    const back = adminApi(page, '/api/v1/admin/pattern-runs');await page.getByRole('button', { name: 'Run trước', exact: true }).click();await back;
    assert.equal((await selectRun(page, fixture.emptyRun)).runPatternTotal, '0');await page.getByText('Run thành công, không có Pattern.', { exact: true }).waitFor();
    const notReady = await selectRun(page, fixture.notReadyRun);assert.equal(notReady.resultState, 'NOT_READY');assert.equal(notReady.runPatternTotal, null);
    await page.getByText('Run đang chờ hoặc đang chạy, chưa có kết quả sử dụng được.', { exact: true }).waitFor();
    await selectRun(page, fixture.patternRun);await page.getByLabel('Support tối thiểu (sequence)', { exact: true }).fill('999999999999999999');
    const none = adminApi(page, `/api/v1/admin/pattern-runs/${fixture.patternRun}/patterns`);await page.getByRole('button', { name: 'Áp dụng', exact: true }).click();const empty = await none;assert.equal(envelope(empty, await empty.json()).filteredTotal, '0');await page.getByText('Không có Pattern khớp bộ lọc.', { exact: true }).waitFor();
    await selectRun(page, fixture.emptyRun);sql(`UPDATE MINING_RUN SET pattern_count=24 WHERE run_id='${fixture.patternRun}';`);
    try {
      const failed = adminApi(page, `/api/v1/admin/pattern-runs/${fixture.patternRun}/patterns`);await page.getByRole('combobox', { name: 'Mining run', exact: true }).selectOption(fixture.patternRun);assert.equal((await failed).status(), 409);await page.getByRole('button', { name: 'Tải lại danh sách', exact: true }).waitFor();
    } finally { sql(`UPDATE MINING_RUN SET pattern_count=23 WHERE run_id='${fixture.patternRun}';`); }
    const retry = adminApi(page, `/api/v1/admin/pattern-runs/${fixture.patternRun}/patterns`);await page.getByRole('button', { name: 'Tải lại danh sách', exact: true }).click();assert.equal((await retry).status(), 200);
  });
  await test('Combo blocked DRAFT has explicit missing authority and no enabled actions', async () => {
    const { page } = await login();await page.goto(origin + '/admin/combos');await page.getByRole('heading', { name: 'Quản lý Combo', exact: true }).waitFor();
    const list = await comboList(page, fixture.batches[0], 'DRAFT');assert.equal(list.scopeTotal, '3');assert.equal(list.filteredTotal, '3');const detail = await openCombo(page, list.items[0].data);
    assert.deepEqual(detail.assessment.allowedActions, []);assert.equal(detail.reviewToken, null);await page.getByText('Chặn: Chưa có chính sách duyệt được xác nhận.', { exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: 'Duyệt', exact: true }).isDisabled(), true);assert.equal(await page.getByRole('button', { name: 'Từ chối', exact: true }).isDisabled(), true);
  });
  await test('Combo approve, principal audit, row leaves DRAFT, reload and independent SQL', async () => {
    const { page } = await login();await page.goto(origin + '/admin/combos');const list = await comboList(page, fixture.batches[1], 'DRAFT', 'SAME_ORDER_BUNDLE');assert.equal(list.scopeTotal, '3');assert.equal(list.filteredTotal, '1');
    const row = list.items[0].data, before = protectedState(row.comboId), detail = await openCombo(page, row);assert.equal(detail.data.batchStatus, 'DRAFT');assert.equal(detail.sources.filter(s => s.primary).length, 1);
    const applied = await review(page, row, 'APPROVE');assert.equal(applied.outcome, 'APPLIED');assert.equal(applied.storedAudit.reviewedBy, users['tv2.admin1']);
    await page.getByText('Không có Combo khớp bộ lọc.', { exact: true }).waitFor();assert.equal(await page.getByRole('heading', { name: 'Chi tiết Combo ' + row.comboId, exact: true }).count(), 0);
    assert.equal(stored(row.comboId).status, 'APPROVED');assert.equal(stored(row.comboId).reviewer, users['tv2.admin1']);assert.equal(protectedState(row.comboId), before);
    await page.reload();const saved = await comboList(page, fixture.batches[1], 'APPROVED');await openCombo(page, saved.items[0].data);await page.getByText('Người review: ' + users['tv2.admin1'], { exact: true }).last().waitFor();
    evidence.approval = { comboId: row.comboId, reviewerId: users['tv2.admin1'], protectedDigest: before, storedStatus: stored(row.comboId).status };
  });
  await test('Combo reject validates note and persists server audit without changing sources/items', async () => {
    const { page } = await login();await page.goto(origin + '/admin/combos');const list = await comboList(page, fixture.batches[2], 'DRAFT', 'SEQUENTIAL_CROSS_SELL');const row = list.items[0].data,before = protectedState(row.comboId);await openCombo(page, row);
    let posts = 0;page.on('request', r => { if (r.method() === 'POST' && r.url().endsWith('/review')) posts++; });
    await page.getByRole('button', { name: 'Từ chối', exact: true }).click();await page.getByRole('button', { name: 'Xác nhận quyết định', exact: true }).click();await page.getByText('Cần ghi chú từ chối.', { exact: true }).waitFor();assert.equal(posts, 0);
    await page.getByLabel('Ghi chú review', { exact: true }).fill('TEST_FIXTURE rejection reason');const reply = adminApi(page, '/api/v1/admin/combos/' + row.comboId + '/review', 'POST');await page.getByRole('button', { name: 'Xác nhận quyết định', exact: true }).click();assert.equal((await reply).status(), 200);
    assert.equal(stored(row.comboId).status, 'REJECTED');assert.equal(stored(row.comboId).note, 'TEST_FIXTURE rejection reason');assert.equal(protectedState(row.comboId), before);
  });
  await test('two Admin contexts preserve winner audit on opposite conflict and same-decision retry', async () => {
    const a = await login('tv2.admin1'), b = await login('tv2.admin2');await a.page.goto(origin + '/admin/combos');await b.page.goto(origin + '/admin/combos');
    const list = await comboList(a.page, fixture.batches[3], 'DRAFT', 'MIXED_SEQUENCE');const row = list.items[0].data,before = protectedState(row.comboId);await comboList(b.page, fixture.batches[3], 'DRAFT', 'MIXED_SEQUENCE');
    const da = await openCombo(a.page, row),db = await openCombo(b.page, row);assert.equal(da.reviewToken, db.reviewToken);
    await review(a.page, row, 'APPROVE');const winner = stored(row.comboId);const conflict = await review(b.page, row, 'REJECT', 'TEST_FIXTURE opposite', 409);assert.equal(conflict.code, 'REVIEW_DECISION_CONFLICT');
    await b.page.getByText('Quyết định hoặc dữ liệu đã thay đổi. Đang tải lại.', { exact: true }).waitFor();await b.page.getByText('Không có Combo khớp bộ lọc.', { exact: true }).waitFor();assert.deepEqual(stored(row.comboId), winner);assert.equal(winner.reviewer, users['tv2.admin1']);assert.equal(protectedState(row.comboId), before);
    const retry = await b.c.request.post(origin + '/api/v1/admin/combos/' + row.comboId + '/review', {
      headers: { Authorization: 'Bearer ' + b.token }, data: { decision: 'APPROVE', reviewNote: 'TEST_FIXTURE retry must not replace audit', expectedReviewToken: db.reviewToken },
    });assert.equal(retry.status(), 200);const repeated = envelope(retry, await retry.json());assert.equal(repeated.outcome, 'ALREADY_DECIDED');
    assert.deepEqual(stored(row.comboId), winner);assert.equal(protectedState(row.comboId), before);
    evidence.concurrent = { comboId: row.comboId, conflictStatus: 409, sameDecision: 'ALREADY_DECIDED', winnerId: winner.reviewer, protectedDigest: before };
  });
  await test('current catalog freshness blocks stale submit and refreshed allowedActions consistently', async () => {
    const { page } = await login();await page.goto(origin + '/admin/combos');const list = await comboList(page, fixture.batches[4], 'DRAFT', 'MIXED_SEQUENCE');
    const row = list.items[0].data, before = protectedState(row.comboId), detail = await openCombo(page, row);
    const product = safeId(detail.itemsets[0].items[0].productId);
    const original = JSON.parse(sql(`SELECT JSON_OBJECT('version',CAST(version AS CHAR),'updated',CAST(updated_at AS CHAR)) FROM PRODUCT WHERE product_id='${product}';`));
    assert.match(original.version, /^[0-9]+$/);assert.match(original.updated, /^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:.]+$/);
    sql(`UPDATE PRODUCT SET version=version+1 WHERE product_id='${product}';`);
    try {
      const blocked = await review(page, row, 'APPROVE', undefined, 409);assert.equal(blocked.code, 'REVIEW_BLOCKED');
      await page.getByText('Quyết định hoặc dữ liệu đã thay đổi. Đang tải lại.', { exact: true }).waitFor();
      await comboList(page, fixture.batches[4], 'DRAFT', 'MIXED_SEQUENCE');const refreshed = await openCombo(page, row);
      assert.deepEqual(refreshed.assessment.allowedActions, []);assert.ok(refreshed.assessment.blockingIssues.some(i => i.code === 'REVIEW_EVIDENCE_UNVERIFIED'));
      assert.equal(await page.getByRole('button', { name: 'Duyệt', exact: true }).isDisabled(), true);
      assert.equal(stored(row.comboId).status, 'DRAFT');assert.equal(stored(row.comboId).reviewer, null);assert.equal(protectedState(row.comboId), before);
      await page.screenshot({ path: path.join(logDirectory, 'review-freshness-blocked.png'), fullPage: true });
      evidence.freshness = { comboId: row.comboId, staleSubmit: 409, refreshedActions: [], protectedDigest: before };
    } finally {sql(`UPDATE PRODUCT SET version=${original.version},updated_at='${original.updated}' WHERE product_id='${product}';`);}
  });
  await test('batch history loads parents only and scoped review clears removed detail', async () => {
    const { page } = await login();const requests=[];const observe=r=>requests.push(new URL(r.url()).pathname);page.on('request',observe);
    const history=adminApi(page,'/api/v1/admin/combos/batches');await page.goto(origin+'/admin/combos');
    const reply=await history;const batches=envelope(reply,await reply.json());assert.equal(batches.items.length,8);page.off('request',observe);
    assert.ok(!requests.includes('/api/v1/admin/combos'));
    const list=await comboList(page,fixture.batches[5],'DRAFT');assert.equal(list.filteredTotal,'3');
    const row=list.items[0].data;await openCombo(page,row);await review(page,row,'APPROVE');
    await page.getByText('Trong phạm vi: 3; Khớp bộ lọc: 2',{exact:true}).waitFor();
    assert.equal(await page.getByRole('heading',{name:'Chi tiết Combo '+row.comboId,exact:true}).count(),0);assert.equal(stored(row.comboId).status,'APPROVED');
  });
  if (process.argv.includes('--ui')) {
    evidence.viewports = [];
    async function fits(page) {
      const size = await page.evaluate(() => ({ width: innerWidth, documentWidth: document.documentElement.scrollWidth }));
      assert.ok(size.documentWidth <= size.width + 1, 'No horizontal page overflow');
    }
    for (const width of [1440, 1024, 390]) await test('UI populated layout, keyboard and screenshots at ' + width + 'px', async () => {
      const { page } = await login();await page.setViewportSize({ width, height: 1000 });await page.goto(origin + '/admin/pattern-explorer');
      await selectRun(page, fixture.patternRun);await page.getByRole('button', { name: '18446744073709551615', exact: true }).click();
      await page.getByRole('group', { name: 'Itemset 1', exact: true }).waitFor();await fits(page);
      const controls = await page.getByRole('combobox', { name: 'Mining run', exact: true }).boundingBox();assert.ok(controls.height >= 35 && controls.height <= 40);
      await page.getByRole('combobox', { name: 'Mining run', exact: true }).focus();await page.keyboard.press('Tab');
      assert.ok(await page.evaluate(() => document.activeElement !== document.body));
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.screenshot({ path: path.join(logDirectory, 'patterns-data-' + width + '.png'), fullPage: true });
      await page.goto(origin + '/admin/combos');const list = await comboList(page, fixture.batches[4], 'DRAFT', 'MIXED_SEQUENCE');
      await openCombo(page, list.items[0].data);await page.getByRole('button', { name: 'Duyệt', exact: true }).click();
      await page.getByLabel('Ghi chú review', { exact: true }).waitFor();await fits(page);
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.screenshot({ path: path.join(logDirectory, 'combos-review-' + width + '.png'), fullPage: true });
      evidence.viewports.push({ width, pageOverflow: false, controlHeight: controls.height, screenshots: ['patterns-data-' + width + '.png', 'combos-review-' + width + '.png'] });
    });
    await test('UI real empty datasource layouts at all three viewports', async () => {
      assert.ok(!process.argv.includes('--manual'), 'Use UI empty gate separately from manual mode');
      // End-of-gate reader-state fixture only. sql() verifies the exact owned container before every call.
      // No application hook, mocked response, constraint disabling or shared/demo datasource.
      for (const table of ['COMBO_BATCH_WRITE_EVIDENCE', 'COMBO_SOURCE', 'COMBO_ITEM', 'COMBO', 'COMBO_BATCH', 'PATTERN_ITEM', 'PATTERN', 'RUN_ITEM_STATISTIC',
        'MINING_RUN_COMPLETION', 'MINING_RUN_OWNERSHIP', 'MINING_RUN_INPUT_BINDING', 'MINING_RUN', 'SNAPSHOT_CONVERSION_SEAL', 'DATASET_SNAPSHOT']) sql('DELETE FROM ' + table + ';');
      const { page } = await login();
      for (const width of [1440, 1024, 390]) {
        await page.setViewportSize({ width, height: 1000 });await page.goto(origin + '/admin/pattern-explorer');await page.getByText('Chưa có mining run.', { exact: true }).waitFor();await fits(page);
        assert.equal(await page.getByRole('combobox', { name: 'Mining run', exact: true }).isDisabled(), true);
        await page.screenshot({ path: path.join(logDirectory, 'patterns-empty-' + width + '.png'), fullPage: true });
        await page.goto(origin + '/admin/combos');await page.getByText('Chưa có lần lưu Combo nào', { exact: true }).waitFor();await fits(page);
        await page.screenshot({ path: path.join(logDirectory, 'combos-empty-' + width + '.png'), fullPage: true });
      }
    });
  }
  evidence.status = 'PASS';writeFileSync(path.join(logDirectory, 'RESULT.json'), JSON.stringify(evidence, null, 2));
}
