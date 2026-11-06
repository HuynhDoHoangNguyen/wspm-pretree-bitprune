import assert from 'node:assert/strict';

// Credentials are process inputs, never written to stdout, files, or command arguments.
const adminPassword = process.env.DEMO_ADMIN_PASSWORD;
const customerPassword = process.env.DEMO_CUSTOMER_PASSWORD;
if (!adminPassword || !customerPassword) throw new Error('Supply demo password environment variables.');
const base = 'http://127.0.0.1:8080';
const origin = 'http://localhost:3000';
async function json(path, options = {}) {
  const response = await fetch(base + path, { ...options, signal: AbortSignal.timeout(15000) });
  let body = null;
  if (response.status !== 204) body = await response.json();
  if (body) assert.equal(body.requestId, response.headers.get('x-request-id'));
  return { response, body };
}
async function csrf() {
  const result = await json('/api/v1/auth/csrf');
  assert.equal(result.response.status, 200);
  const cookie = result.response.headers.getSetCookie().find(value => value.startsWith('XSRF-TOKEN='));
  assert.ok(cookie);
  return { cookie: cookie.split(';', 1)[0], token: result.body.data.token };
}
async function post(operation, body, refresh) {
  const protection = await csrf();
  return json('/api/v1/auth/' + operation, {
    method: 'POST',
    headers: { Origin: origin, 'X-XSRF-TOKEN': protection.token,
      Cookie: protection.cookie + (refresh ? '; ' + refresh : ''),
      ...(body ? { 'Content-Type': 'application/json' } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {})
  });
}
try {
  const anonymous = await json('/api/v1/admin/mining-runs');
  assert.equal(anonymous.response.status, 401);
  console.log('Anonymous Admin API: 401 VERIFIED');
  for (const username of ['admin.test', 'customer_001', 'customer_100']) {
    const login = await post('login', { username, password: username === 'admin.test' ? adminPassword : customerPassword });
    assert.equal(login.response.status, 200);
    const access = login.body.data.accessToken;
    const refresh = login.response.headers.getSetCookie().find(value => value.startsWith('wspm_refresh='))?.split(';', 1)[0];
    assert.ok(refresh);
    try {
      const me = await json('/api/v1/auth/me', { headers: { Authorization: 'Bearer ' + access } });
      assert.equal(me.response.status, 200);
      assert.equal(me.body.data.username, username);
      assert.deepEqual([...me.body.data.roles].sort(), username === 'admin.test' ? ['ADMIN', 'CUSTOMER'] : ['CUSTOMER']);
      const admin = await json('/api/v1/admin/mining-runs', { headers: { Authorization: 'Bearer ' + access } });
      assert.equal(admin.response.status, username === 'admin.test' ? 200 : 403);
      console.log(username + ': login=200 me=200 exactRoles=VERIFIED AdminAPI=' + admin.response.status);
    } finally {
      const logout = await post('logout', null, refresh);
      assert.equal(logout.response.status, 204);
    }
  }
  console.log('Shared-auth verification: PASS; verification sessions logged out.');
} catch {
  // Assertion objects or HTTP bodies can contain secrets. Emit only an operation-level failure.
  console.error('Shared-auth verification: FAIL (no credential or response dump).');
  process.exitCode = 1;
}
