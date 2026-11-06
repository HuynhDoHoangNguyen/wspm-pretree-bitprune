/** @jest-environment node */
import { runInThisContext } from 'vm';
import * as http from 'http';
import { execFileSync } from 'child_process';
import { createAuthApi } from './api';
import { createWdmApiClient } from 'api/admin/data-mining/client';

Object.assign(globalThis, runInThisContext('({ fetch, Headers, Response, Blob, AbortController })'));
const backend = process.env.AUTH05_LIVE_BACKEND;
const live = backend ? test : test.skip;
function required(name: string): string { const value = process.env[name]; if (!value) throw new Error('Missing explicit live fixture configuration'); return value; }

// Opt-in actual frontend code -> same-origin proxy -> isolated Spring/MySQL.
// Cookie jar is a Node transport harness, not browser E2E or a production client.
live('frontend Auth lifecycle and authorized CSV work with the real isolated backend', async () => {
  const name = required('AUTH05_TEST_CONTAINER'), expectedId = required('AUTH05_TEST_CONTAINER_ID');
  if (!/^wspm-auth05-module-[a-f0-9]+$/.test(name)) throw new Error('Explicit isolated test container required');
  const info = JSON.parse(execFileSync('docker', ['inspect', name], { encoding: 'utf8' }))[0];
  expect(info.Id).toBe(expectedId); expect(info.Config.Labels['codex-task']).toBe('AUTH-05');
  const sql = (input: string) => execFileSync('docker', ['exec', '--interactive', '--env',
    'MYSQL_PWD=' + required('AUTH05_TEST_DB_PASSWORD'), name, 'mysql', '--user=root',
    '--batch', '--skip-column-names', 'auth05_frontend_test'], { input, encoding: 'utf8' }).trim();
  const target = new URL(required('AUTH05_LIVE_BACKEND'));
  const proxy = http.createServer((incoming, outgoing) => {
    const forwarded = http.request(new URL(incoming.url || '/', target), { method: incoming.method,
      headers: { ...incoming.headers, host: target.host } }, response => {
      outgoing.writeHead(response.statusCode || 502, { ...response.headers, connection: 'close' }); response.pipe(outgoing);
    });
    forwarded.on('error', () => { outgoing.writeHead(502); outgoing.end(); }); incoming.pipe(forwarded);
  });
  await new Promise<void>(resolve => proxy.listen(Number(process.env.AUTH05_PROXY_PORT), '127.0.0.1', resolve));
  const origin = 'http://127.0.0.1:' + process.env.AUTH05_PROXY_PORT;
  const cookies = new Map<string, string>();
  const trace: { path: string; method: string }[] = [];
  const transport: typeof fetch = async (path, init) => {
    const url = new URL(String(path), origin); const headers = new Headers(init?.headers);
    if (init?.method === 'POST') headers.set('Origin', origin);
    if (init?.credentials !== 'omit' && cookies.size) headers.set('Cookie', Array.from(cookies.values()).join('; '));
    trace.push({ path: url.pathname, method: init?.method || 'GET' });
    const response = await fetch(url.toString(), { ...init, headers });
    const setter = (response.headers as Headers & { getSetCookie(): string[] }).getSetCookie();
    for (const cookie of setter) {
      const pair = cookie.split(';', 1)[0], index = pair.indexOf('='), key = pair.slice(0, index);
      if (!pair.slice(index + 1) || /max-age=0/i.test(cookie)) cookies.delete(key); else cookies.set(key, pair);
    }
    return response;
  };
  const { createAuthSession } = await import('./session');
  const api = createAuthApi(transport), session = createAuthSession(api, work => work());
  const wdm = createWdmApiClient({ fetchImpl: transport, getAuthAdapter: () => ({
    getAccessToken: session.token, getSessionSignal: session.signal,
    onUnauthorized: session.unauthorized, onForbidden: session.forbidden,
  }) });
  const password = 'Ui123456';
  try {
    const customer = await session.register({ username: 'ui.customer', password, email: 'ui.customer@example.test', displayName: 'UI customer' });
    expect(customer.roles).toEqual(['CUSTOMER']); expect(session.useStore.getState().user).toBeNull();
    await session.login('ui.customer', password);
    await expect(wdm.download('/v1/admin/order-exports')).rejects.toMatchObject({ status: 403, code: 'ACCESS_DENIED' });
    expect(session.useStore.getState().forbidden).toBe(true); await session.logout();
    const admin = await session.register({ username: 'ui.admin', password, email: 'ui.admin@example.test', displayName: 'UI admin' });
    for (const id of [admin.userId, customer.userId]) expect(id).toMatch(/^[a-f0-9-]{36}$/);
    // Fixture-only assignment; no application endpoint/default account grants ADMIN.
    expect(sql(`INSERT INTO USER_ROLE(user_id,role_id) VALUES('${admin.userId}','admin-role'); SELECT ROW_COUNT();`)).toBe('1');
    sql(`UPDATE CUSTOMER SET anonymous_code='ui-shopper' WHERE user_id='${customer.userId}';
      INSERT INTO CATEGORY(category_id,name,slug) VALUES('ui-category','Fixture','ui-category');
      INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES('ui-product','CURRENT','Current','ui-product','ui-category',999);
      INSERT INTO ORDERS(order_id,order_number,customer_id,ordered_at,status,source,currency_code)
        SELECT 'ui-order','ui-order',customer_id,'2026-11-02 03:04:05','COMPLETED','IMPORT','VND' FROM CUSTOMER WHERE user_id='${customer.userId}';
      INSERT INTO ORDER_ITEM(order_item_id,order_id,product_id,quantity,list_unit_price,allocated_discount_amount,line_total,sku_snapshot,product_name_snapshot)
        VALUES(1,'ui-order','ui-product',2,100,0,200,'HISTORY-SKU','Historical name');`);
    await session.login('ui.admin', password); expect(session.useStore.getState().user?.roles).toContain('ADMIN');
    const refreshCount = trace.filter(call => call.path.endsWith('/refresh')).length;
    await Promise.all([session.refresh(), session.refresh(), session.refresh()]);
    expect(trace.filter(call => call.path.endsWith('/refresh')).length).toBe(refreshCount + 1);
    const result = await wdm.download('/v1/admin/order-exports', { query: { fromTime: '2026-11-01T00:00:00Z', toTime: '2026-12-01T00:00:00Z' } });
    const csv = await result.blob.text();
    expect(csv.split('\r\n')[0].split(',')).toHaveLength(13);
    expect(csv).toContain('ui-order,1,ui-shopper,2026-11-02T03:04:05.000000Z,COMPLETED,ui-product,HISTORY-SKU,Historical name,2,100.00,0.00,200.00,VND');
    expect(result.contentDisposition).toContain('attachment');
    const oldToken = session.token() || ''; expect(oldToken).not.toBe(''); await session.logout(); expect(session.token()).toBeNull();
    await expect(api.me(oldToken)).rejects.toMatchObject({ status: 401 });
    expect(trace.every(call => call.path.startsWith('/api/v1/'))).toBe(true);
    expect(trace.some(call => /cart|client-api/.test(call.path))).toBe(false);
  } finally {
    session.stop();
    (proxy as http.Server & { closeAllConnections?: () => void }).closeAllConnections?.();
    await new Promise<void>(resolve => proxy.close(() => resolve()));
  }
}, 60000);
