import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHmac, randomUUID, createHash } from 'node:crypto';

const password = 'Test1234';
const exportUrl = '/api/v1/admin/order-exports?fromTime=2026-11-01T00:00:00Z&toTime=2026-12-01T00:00:00Z';
export async function runCases({ browser, httpsOrigin: origin, sql, jwtSecret }) {
  const contexts = [];
  const context = async (fallback = false) => {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, acceptDownloads: true }); contexts.push(ctx);
    if (fallback) await ctx.addInitScript(() => Object.defineProperty(navigator, 'locks', { value: undefined, configurable: true }));
    return ctx;
  };
  const call = (page, method, ...args) => page.evaluate(async ({ method, args }) => {
    try { return { ok: true, value: await window.auth06[method](...args) }; }
    catch (error) { return { ok: false, status: error.status || 0 }; }
  }, { method, args });
  const state = page => page.evaluate(() => window.auth06.snapshot());
  async function until(work, predicate, name = 'state transition') {
    for (let attempt = 0; attempt < 100; attempt++) {
      const value = await work(); if (predicate(value)) return value;
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    throw new Error('Timed out: ' + name);
  }
  async function harness(ctx) {
    const page = await ctx.newPage(); await page.goto(origin + '/__auth06');
    await until(() => page.evaluate(() => !!window.auth06), Boolean, 'harness startup');
    await until(() => state(page), value => value.status !== 'bootstrapping', 'bootstrap'); return page;
  }
  async function login(page, username = 'e2e.admin') { assert.equal((await call(page, 'login', username, password)).ok, true); }
  let count = 0;
  async function test(name, work) {
    const started = Date.now();
    try { await work(); count++; console.log('PASS browser ' + count + ': ' + name + ' (' + (Date.now() - started) + 'ms)'); }
    catch (error) {
      console.error('FAILED CASE: ' + name);
      console.error((error.stack || '').split('\n').filter(line => line.trim().startsWith('at ')).slice(0, 3).join('\n'));
      throw error;
    } finally { for (const ctx of contexts.splice(0)) await ctx.close(); }
  }
  // Registration and role provisioning are fixture-only; this never runs against development.
  const setup = await context(), fixture = await harness(setup);
  const customer = await call(fixture, 'register', { username: 'e2e.customer', password, email: 'e2e.customer@example.test', displayName: 'E2E customer' });
  const admin = await call(fixture, 'register', { username: 'e2e.admin', password, email: 'e2e.admin@example.test', displayName: 'E2E admin' });
  assert.equal(customer.ok, true); assert.equal(admin.ok, true);
  const customerId = customer.value.userId, adminId = admin.value.userId;
  assert.match(customerId, /^[a-f0-9-]{36}$/); assert.match(adminId, /^[a-f0-9-]{36}$/);
  sql(`INSERT INTO USER_ROLE(user_id,role_id) VALUES('${adminId}','admin-role');
    UPDATE CUSTOMER SET anonymous_code='e2e-shopper' WHERE user_id='${customerId}';
    INSERT INTO CATEGORY(category_id,name,slug) VALUES('e2e-category','Fixture','e2e-category');
    INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES('e2e-product','CURRENT','Current','e2e-product','e2e-category',999);
    INSERT INTO ORDERS(order_id,order_number,customer_id,ordered_at,status,source,currency_code)
      SELECT 'e2e-order','e2e-order',customer_id,'2026-11-02 03:04:05','COMPLETED','IMPORT','VND' FROM CUSTOMER WHERE user_id='${customerId}';
    INSERT INTO ORDER_ITEM(order_item_id,order_id,product_id,quantity,list_unit_price,allocated_discount_amount,line_total,sku_snapshot,product_name_snapshot)
      VALUES(1,'e2e-order','e2e-product',2,100,0,200,'HISTORY-SKU','Historical name');`);
  await setup.close(); contexts.splice(0);

  await test('real UI registration, duplicate, bad login, identity and reload without cart dependency', async () => {
    const ctx = await context(), page = await ctx.newPage(), authPaths = [];
    page.on('request', request => { if (request.headers().authorization || request.url().includes('/api/v1/auth/')) authPaths.push(new URL(request.url()).pathname); });
    async function fill() {
      await page.getByLabel('Tên tài khoản', { exact: false }).fill('e2e.signup');
      await page.getByLabel('Email', { exact: false }).fill('e2e.signup@example.test');
      await page.getByLabel('Tên hiển thị', { exact: false }).fill('E2E signup');
      await page.getByLabel(/^Mật khẩu/).fill(password);
      await page.getByLabel('Xác nhận mật khẩu', { exact: false }).fill(password);
    }
    await page.goto(origin + '/signup'); await fill(); await page.getByRole('button', { name: 'Đăng ký', exact: true }).click();
    await page.getByText('Đã tạo tài khoản.', { exact: false }).waitFor();
    await page.goto(origin + '/signup'); await fill(); await page.getByRole('button', { name: 'Đăng ký', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: 'Tên tài khoản hoặc email đã được sử dụng.' }).waitFor();
    await page.goto(origin + '/signin');
    await page.getByLabel('Tên tài khoản', { exact: false }).fill('e2e.signup');
    await page.getByLabel(/^Mật khẩu/).fill('Wrong1234');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: 'không hợp lệ' }).waitFor();
    await page.getByLabel(/^Mật khẩu/).fill(password);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.waitForURL(origin + '/');
    await page.goto(origin + '/user'); await page.getByText('E2E signup', { exact: true }).waitFor();
    await page.reload(); await page.getByText('E2E signup', { exact: true }).waitFor();
    const storage = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]));
    assert.equal(/accessToken|jwtToken|wspm_refresh|eyJ/.test(storage), false);
    assert.equal(authPaths.some(value => /cart|client-api|\/api\/auth\//.test(value)), false);
    assert.equal(page.url().includes('token'), false);
  });

  await test('real form field errors, confirmation match and mobile layout', async () => {
    const ctx = await context(), page = await ctx.newPage();
    await page.setViewportSize({ width: 375, height: 812 });
    let registrations = 0;
    page.on('request', r => { if (r.url().endsWith('/api/v1/auth/register') && r.method() === 'POST') registrations++; });
    await page.goto(origin + '/signup');
    await page.getByRole('button', { name: 'Đăng ký', exact: true }).click();
    for (const name of ['username', 'email', 'displayName', 'password', 'passwordConfirmation']) {
      assert.equal(await page.locator('input[name="' + name + '"]').getAttribute('aria-invalid'), 'true');
    }
    assert.equal(await page.evaluate(() => document.activeElement.name), 'username');
    assert.equal(registrations, 0);
    const box = await page.getByRole('form', { name: 'Đăng ký', exact: true }).boundingBox();
    assert.ok(box.x >= 0 && box.x + box.width <= 375);
    await page.getByLabel('Tên tài khoản', { exact: false }).fill('e2e.customer');
    await page.getByLabel('Email', { exact: false }).fill('e2e.customer@example.test');
    await page.getByLabel('Tên hiển thị', { exact: false }).fill('Fixture');
    await page.getByLabel(/^Mật khẩu/).fill(password);
    await page.getByLabel('Xác nhận mật khẩu', { exact: false }).fill('mismatch');
    await page.getByRole('button', { name: 'Đăng ký', exact: true }).click();
    await page.getByText('Mật khẩu xác nhận không khớp.', { exact: true }).waitFor();
    assert.equal(registrations, 0);
    await page.getByLabel('Xác nhận mật khẩu', { exact: false }).fill(password);
    await page.getByRole('button', { name: 'Đăng ký', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: 'đã được sử dụng' }).waitFor();
    assert.equal(registrations, 1);
    assert.equal(await page.locator('input[name="username"]').getAttribute('aria-invalid'), 'true');
    assert.equal(await page.locator('input[name="email"]').getAttribute('aria-invalid'), 'true');
    await page.goto(origin + '/signin');
    assert.equal(await page.getByText('Khôi phục phiên', { exact: true }).count(), 0);
    assert.equal(await page.getByText('Đăng ký CUSTOMER', { exact: true }).count(), 0);
  });

  await test('ADMIN login on Client redirects to Admin and switches sites; CUSTOMER logout returns home', async () => {
    const ctx = await context(), page = await ctx.newPage();
    async function signIn(username) {
      await page.goto(origin + '/signin');
      await page.getByLabel('Tên tài khoản', { exact: false }).fill(username);
      await page.getByLabel(/^Mật khẩu/).fill(password);
      await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    }
    await signIn('e2e.admin');
    await page.waitForURL(origin + '/admin/datasets');
    await page.getByRole('link', { name: 'Về cửa hàng', exact: true }).click();
    await page.waitForURL(origin + '/');
    await page.getByRole('link', { name: 'Trang quản trị', exact: true }).click();
    await page.getByRole('heading', { name: 'Dữ liệu khai phá', exact: true }).waitFor();
    await page.getByTitle('Đăng xuất', { exact: true }).click(); await page.waitForURL(origin + '/');
    await signIn('e2e.customer'); await page.waitForURL(origin + '/');
    assert.equal(await page.getByRole('link', { name: 'Trang quản trị', exact: true }).count(), 0);
    await page.goto(origin + '/user');
    await page.getByRole('button', { name: 'Tài khoản', exact: true }).click();
    await page.getByRole('menuitem', { name: 'Đăng xuất', exact: true }).click();
    await page.waitForURL(origin + '/');
    assert.equal(await page.getByText('Vui lòng đăng nhập để truy cập.', { exact: true }).count(), 0);
  });

  await test('anonymous 401, CUSTOMER 403, forged storage denies Admin UI before data', async () => {
    const ctx = await context(), page = await harness(ctx);
    const absent = await page.evaluate(async path => { const r = await fetch(path); return { status: r.status, cache: r.headers.get('cache-control'), id: r.headers.get('x-request-id'), type: r.headers.get('content-type') }; }, exportUrl);
    assert.equal(absent.status, 401); assert.ok(absent.cache.includes('no-store')); assert.ok(absent.id); assert.ok(absent.type.includes('json'));
    await login(page, 'e2e.customer'); assert.equal((await call(page, 'read')).value.status, 403);
    assert.equal((await state(page)).forbidden, true); await call(page, 'logout');
    await page.evaluate(() => localStorage.setItem('electro-admin-auth-store', JSON.stringify({ state: { jwtToken: 'forged', user: { roles: [{ code: 'ADMIN' }] } } })));
    await page.goto(origin + '/admin/datasets'); await page.getByRole('heading', { name: 'Đăng nhập Admin' }).waitFor();
    assert.equal(await page.evaluate(() => localStorage.getItem('electro-admin-auth-store')), null);
  });

  await test('HTTPS HttpOnly/Secure/Path/SameSite cookie, CSRF and foreign Origin rejection', async () => {
    const ctx = await context(), page = await harness(ctx); await login(page);
    const cookie = (await ctx.cookies()).find(value => value.name === 'wspm_refresh');
    assert.ok(cookie); assert.equal(cookie.httpOnly, true); assert.equal(cookie.secure, true);
    assert.equal(cookie.sameSite, 'Lax'); assert.equal(cookie.path, '/api/v1/auth');
    assert.ok(cookie.expires - Date.now() / 1000 > 604780);
    const storedHash = sql(`SELECT refresh_token_hash FROM USER_SESSION WHERE user_id='${adminId}' AND revoked_at IS NULL ORDER BY created_at DESC LIMIT 1;`);
    assert.equal(storedHash === createHash('sha256').update(cookie.value).digest('hex'), true);
    const authPath = await ctx.newPage(); await authPath.goto(origin + '/api/v1/auth/csrf');
    assert.equal((await authPath.evaluate(() => document.cookie)).includes('wspm_refresh'), false);
    const response = await page.evaluate(async () => (await fetch('/api/v1/auth/refresh', { method: 'POST' })).status);
    assert.equal(response, 403);
    const foreign = await ctx.request.post(origin + '/api/v1/auth/refresh', { headers: { Origin: 'https://untrusted.example.test' } });
    assert.equal(foreign.status(), 403);
    const cookiesAtExport = await ctx.cookies(origin + exportUrl);
    assert.equal(cookiesAtExport.some(value => value.name === 'wspm_refresh'), false);
    assert.equal((await state(page)).tokenPresent, true);
  });

  await test('real Admin shell and actual CSV download file with 13 columns and historical ORDER_ITEM', async () => {
    const ctx = await context(), page = await ctx.newPage(); await page.goto(origin + '/admin/datasets');
    await page.getByLabel('Tên tài khoản', { exact: false }).fill('e2e.admin');
    await page.getByLabel(/^Mật khẩu/).fill(password);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.getByRole('heading', { name: 'Dữ liệu khai phá', exact: true }).waitFor();
    const client = await harness(ctx);
    const promise = client.waitForEvent('download'); await client.getByRole('button', { name: 'Download fixture CSV' }).click();
    const download = await promise; assert.equal(await download.failure(), null);
    const csv = readFileSync(await download.path(), 'utf8'), lines = csv.trimEnd().split('\r\n');
    assert.equal(lines[0].split(',').length, 13); assert.equal(lines.length, 2);
    assert.ok(lines[1].includes('e2e-order,1,e2e-shopper,2026-11-02T03:04:05.000000Z,COMPLETED,e2e-product,HISTORY-SKU,Historical name,2,100.00,0.00,200.00,VND'));
    await client.close(); // Its stop event is local; product header logout still calls the server.
    await page.getByTitle('Đăng xuất', { exact: true }).click();
    await page.waitForURL(origin + '/');
    assert.equal((await ctx.cookies()).some(value => value.name === 'wspm_refresh'), false);
  });

  await test('browser single-flight rotation, old refresh rejection, reload/bootstrap, revoked JWT', async () => {
    const ctx = await context(), page = await harness(ctx); await login(page);
    const oldCookie = (await ctx.cookies()).find(value => value.name === 'wspm_refresh');
    const expiry = sql(`SELECT expires_at FROM USER_SESSION WHERE user_id='${adminId}' AND revoked_at IS NULL ORDER BY created_at DESC LIMIT 1;`);
    let refreshCount = 0, accessToken;
    page.on('request', request => { if (new URL(request.url()).pathname.endsWith('/refresh') && request.method() === 'POST') refreshCount++; });
    page.on('response', async response => {
      if (!response.url().endsWith('/refresh') || !response.ok()) return;
      const envelope = await response.json();
      assert.equal(envelope.code, 'SUCCESS');
      assert.equal(envelope.requestId, response.headers()['x-request-id']);
      accessToken = envelope.data.accessToken;
    });
    await page.evaluate(() => Promise.all([window.auth06.refresh(), window.auth06.refresh(), window.auth06.refresh()]));
    assert.equal(refreshCount, 1);
    const newCookie = (await ctx.cookies()).find(value => value.name === 'wspm_refresh');
    assert.equal(oldCookie.value === newCookie.value, false);
    assert.equal(sql(`SELECT expires_at FROM USER_SESSION WHERE user_id='${adminId}' AND revoked_at IS NULL ORDER BY created_at DESC LIMIT 1;`), expiry);
    const stale = await context(); await stale.addCookies([oldCookie]); const stalePage = await harness(stale);
    assert.equal((await state(stalePage)).status, 'anonymous');
    await page.reload(); await until(() => state(page), value => value.status === 'authenticated');
    await until(async () => !!accessToken, Boolean, 'captured grant');
    await call(page, 'cache'); assert.equal((await state(page)).cacheCount, 1);
    await page.getByRole('button', { name: 'Logout fixture' }).click();
    await until(() => state(page), value => value.status === 'anonymous'); assert.equal((await state(page)).cacheCount, 0);
    assert.equal((await state(page)).tokenPresent, false);
    const denied = await ctx.request.get(origin + '/api/v1/auth/me', { headers: { Authorization: 'Bearer ' + accessToken } });
    assert.equal(denied.status(), 401);
  });

  await test('proactive timer and 401/403 do not create refresh/download retry loops', async () => {
    const ctx = await context(), page = await harness(ctx); await page.clock.install(); await login(page);
    let refreshes = 0, reads = 0;
    page.on('request', req => { if (req.url().endsWith('/refresh') && req.method() === 'POST') refreshes++; if (req.url().includes('/order-exports')) reads++; });
    await page.clock.fastForward(870000); await until(async () => refreshes, value => value === 1, 'proactive refresh');
    await until(() => state(page), value => value.status === 'authenticated');
    sql(`DELETE FROM USER_ROLE WHERE user_id='${adminId}' AND role_id='admin-role';`);
    assert.equal((await call(page, 'read')).value.status, 403); assert.equal(reads, 1);
    await page.clock.fastForward(1000); assert.equal(refreshes, 1); assert.equal(reads, 1);
    await call(page, 'check'); assert.equal((await state(page)).user.roles.includes('ADMIN'), false);
    sql(`INSERT INTO USER_ROLE(user_id,role_id) VALUES('${adminId}','admin-role'); UPDATE USER_ACCOUNT SET status='LOCKED' WHERE user_id='${adminId}';`);
    assert.equal((await call(page, 'read')).value.status, 401);
    await page.clock.fastForward(3600000); assert.equal(refreshes, 1); assert.equal((await state(page)).status, 'anonymous');
    sql(`UPDATE USER_ACCOUNT SET status='ACTIVE' WHERE user_id='${adminId}';`);
  });

  await test('two tabs serialize refresh and logout; stale response cannot resurrect session', async () => {
    const ctx = await context(), first = await harness(ctx); await login(first);
    const second = await harness(ctx);
    assert.equal((await state(second)).status, 'authenticated');
    assert.equal(await first.evaluate(() => typeof navigator.locks?.request), 'function');
    await Promise.all([call(first, 'refresh'), call(second, 'refresh')]);
    assert.equal((await state(first)).status, 'authenticated'); assert.equal((await state(second)).status, 'authenticated');
    let release; const blocked = new Promise(resolve => { release = resolve; });
    let arrived; const started = new Promise(resolve => { arrived = resolve; });
    await first.route('**/api/v1/auth/refresh', async route => { const response = await route.fetch(); arrived(); await blocked; await route.fulfill({ response }); });
    const refresh = call(first, 'refresh'); await started;
    const logout = call(second, 'logout');
    await until(() => state(first), value => value.status === 'anonymous', 'cross-tab invalidation');
    release(); await Promise.all([refresh, logout]);
    assert.equal((await state(first)).tokenPresent, false); assert.equal((await state(second)).tokenPresent, false);
    await first.unrouteAll({ behavior: 'wait' }); await first.reload();
    await until(() => state(first), value => value.status === 'anonymous', 'revoked refresh after reload');
  });

  await test('without Web Locks, local queue handles refresh/logout and aborts a pending download', async () => {
    const ctx = await context(true), page = await harness(ctx); await login(page);
    assert.equal(await page.evaluate(() => navigator.locks), undefined);
    let release; const blocked = new Promise(resolve => { release = resolve; });
    let arrived; const started = new Promise(resolve => { arrived = resolve; });
    await page.route('**/api/v1/admin/order-exports?*', async route => { arrived(); await blocked; try { await route.continue(); } catch { /* canceled */ } });
    const download = call(page, 'read'); await started;
    const results = await Promise.all([call(page, 'refresh'), call(page, 'logout')]);
    assert.equal(results[1].ok, true); assert.equal((await state(page)).tokenPresent, false);
    release(); assert.notEqual((await download).value.status, 200); await page.unrouteAll({ behavior: 'wait' });
    await page.reload(); await until(() => state(page), value => value.status === 'anonymous');
  });

  await test('expired but correctly signed access JWT is rejected before WDM query validation', async () => {
    const ctx = await context(), page = await harness(ctx); await login(page);
    const now = Math.floor(Date.now() / 1000), b64 = value => Buffer.from(JSON.stringify(value)).toString('base64url');
    const head = b64({ alg: 'HS256', typ: 'at+jwt', kid: 'auth06-test-key' });
    const sid = sql(`SELECT session_id FROM USER_SESSION WHERE user_id='${adminId}' AND revoked_at IS NULL ORDER BY created_at DESC LIMIT 1;`);
    assert.match(sid, /^[a-f0-9-]{36}$/);
    const claims = b64({ iss: 'https://auth06.example.test', aud: 'wspm-api', sub: adminId, sid, jti: randomUUID(), iat: now - 1000, nbf: now - 1000, exp: now - 100 });
    const input = head + '.' + claims, jwt = input + '.' + createHmac('sha256', jwtSecret).update(input).digest('base64url');
    const validClaims = b64({ iss: 'https://auth06.example.test', aud: 'wspm-api', sub: adminId, sid, jti: randomUUID(), iat: now, nbf: now, exp: now + 900 });
    const validInput = head + '.' + validClaims, validJwt = validInput + '.' + createHmac('sha256', jwtSecret).update(validInput).digest('base64url');
    assert.equal((await ctx.request.get(origin + '/api/v1/auth/me', { headers: { Authorization: 'Bearer ' + validJwt } })).status(), 200);
    const response = await ctx.request.get(origin + '/api/v1/admin/order-exports?fromTime=invalid', { headers: { Authorization: 'Bearer ' + jwt } });
    assert.equal(response.status(), 401); assert.ok(response.headers()['cache-control'].includes('no-store')); assert.ok(response.headers()['x-request-id']);
  });
  await test('transport failures deny protected UI and report unconfirmed server logout', async () => {
    const ctx = await context(), page = await harness(ctx); await login(page);
    await call(page, 'cache');
    await page.route('**/api/v1/auth/logout', route => route.abort('failed'));
    assert.equal((await call(page, 'logout')).ok, false);
    const local = await state(page); assert.equal(local.status, 'unavailable'); assert.equal(local.tokenPresent, false); assert.equal(local.cacheCount, 0);
    assert.equal((await ctx.cookies()).some(value => value.name === 'wspm_refresh'), true);
    await page.unrouteAll({ behavior: 'wait' });
    await page.route('**/api/v1/auth/refresh', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'SERVICE_UNAVAILABLE' }) }));
    await page.goto(origin + '/admin/datasets');
    await page.getByRole('alert').filter({ hasText: 'Chưa xác minh được phiên' }).waitFor();
    assert.equal(await page.getByRole('heading', { name: 'Dữ liệu khai phá', exact: true }).count(), 0);
    await page.unrouteAll({ behavior: 'wait' });
    await page.getByRole('link', { name: 'Đăng nhập lại', exact: true }).click();
    await page.getByLabel('Tên tài khoản', { exact: false }).fill('e2e.admin');
    await page.getByLabel(/^Mật khẩu/).fill(password);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.getByRole('heading', { name: 'Dữ liệu khai phá', exact: true }).waitFor();
    await page.getByTitle('Đăng xuất', { exact: true }).click();
    await page.waitForURL(origin + '/');
  });
  console.log('Browser cases: ' + count + '/' + count + ' PASS. Fixture logins: e2e.customer, e2e.admin, e2e.signup / Test1234 (disposable DB only).');
}
