import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Local acceptance only: existing backend/demo accounts, no fixtures or intercepted API responses.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const build = path.join(root, 'frontend/build');
assert.ok(fs.existsSync(path.join(build, 'index.html')), 'Build frontend first.');
assert.ok(process.env.DEMO_ADMIN_PASSWORD && process.env.DEMO_CUSTOMER_PASSWORD, 'Supply demo credentials through process environment.');
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'tv2-pattern-browser-'));
const server = http.createServer((request, response) => {
  if (request.url.startsWith('/api/')) {
    const proxy = http.request({ host: '127.0.0.1', port: 8080, path: request.url, method: request.method, headers: request.headers }, remote => {
      response.writeHead(remote.statusCode, remote.headers);remote.pipe(response);
    });proxy.on('error', () => { response.writeHead(502);response.end(); });request.pipe(proxy);return;
  }
  let target = path.resolve(build, '.' + new URL(request.url, 'http://localhost:3000').pathname);
  if (!target.startsWith(build + path.sep) || !fs.existsSync(target) || fs.statSync(target).isDirectory()) target = path.join(build, 'index.html');
  response.setHeader('Content-Type', ({ '.js': 'application/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json' })[path.extname(target)] || 'application/octet-stream');
  fs.createReadStream(target).pipe(response);
});
const channels = new Set();let edge, browser, activeTab, stage = 'start';
const waitFor = async check => {
  const deadline = Date.now() + 25000;
  while (Date.now() < deadline) { try { const value = await check();if (value) return value; } catch {} await new Promise(resolve => setTimeout(resolve, 150)); }
  throw new Error('Browser condition timed out.');
};
async function connect(url) {
  const ws = new WebSocket(url);channels.add(ws);
  await new Promise((resolve, reject) => { ws.onopen = resolve;ws.onerror = reject; });
  const pending = new Map(), listeners = new Set();let id = 0;
  ws.onmessage = event => {
    const message = JSON.parse(event.data), request = pending.get(message.id);
    if (request) { pending.delete(message.id);message.error ? request.reject(new Error('CDP failure.')) : request.resolve(message.result); }
    else for (const listener of listeners) listener(message);
  };
  return { onEvent(listener) { listeners.add(listener); }, send(method, params = {}) {
    return new Promise((resolve, reject) => { const next = ++id;pending.set(next, { resolve, reject });ws.send(JSON.stringify({ id: next, method, params })); });
  } };
}
async function evaluate(tab, expression) {
  const result = await tab.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error('Page evaluation failed.');return result.result.value;
}
async function logout(tab) {
  return evaluate(tab, `(async()=>{const r=await fetch('/api/v1/auth/csrf');const b=await r.json();return (await fetch('/api/v1/auth/logout',{method:'POST',headers:{'X-XSRF-TOKEN':b.data.token}})).status})()`);
}
try {
  await new Promise((resolve, reject) => { server.once('error', reject);server.listen(3000, '127.0.0.1', resolve); });
  const childEnv = { ...process.env };delete childEnv.DEMO_ADMIN_PASSWORD;delete childEnv.DEMO_CUSTOMER_PASSWORD;
  edge = spawn(process.env.DEMO_EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    ['--headless=new', '--disable-gpu', '--no-first-run', '--remote-debugging-port=0', '--user-data-dir=' + profile, 'about:blank'],
    { env: childEnv, stdio: 'ignore', windowsHide: true });
  const debug = await waitFor(() => fs.existsSync(path.join(profile, 'DevToolsActivePort')) && fs.readFileSync(path.join(profile, 'DevToolsActivePort'), 'utf8').trim().split(/\r?\n/));
  browser = await connect('ws://127.0.0.1:' + debug[0] + debug[1]);
  for (const username of ['anonymous', 'admin.test', 'customer_001', 'customer_100']) {
    stage = username + ':open';
    const { browserContextId } = await browser.send('Target.createBrowserContext');
    const { targetId } = await browser.send('Target.createTarget', { browserContextId, url: 'about:blank' });
    const tab = await connect('ws://127.0.0.1:' + debug[0] + '/devtools/page/' + targetId);activeTab = tab;
    const records = [];
    tab.onEvent(message => {
      if (message.method === 'Network.responseReceived') {
        const { response, requestId } = message.params;
        const pathname = new URL(response.url).pathname;
        if (pathname.startsWith('/api/v1/admin/mining-runs')) records.push({ pathname, status: response.status, requestId });
      }
    });await tab.send('Network.enable');
    await tab.send('Page.navigate', { url: 'http://localhost:3000' + (username === 'anonymous' ? '/admin/patterns' : username === 'admin.test' ? '/admin/signin' : '/signin') });
    await waitFor(() => evaluate(tab, '!!document.querySelector("input[name=username]:not(:disabled)")'));
    if (username !== 'anonymous') {
      stage = username + ':login';
      for (const [name, value] of [['username', username], ['password', username === 'admin.test' ? process.env.DEMO_ADMIN_PASSWORD : process.env.DEMO_CUSTOMER_PASSWORD]]) {
        await evaluate(tab, 'document.querySelector(' + JSON.stringify('input[name=' + name + ']') + ').focus()');await tab.send('Input.insertText', { text: value });
      }
      await evaluate(tab, 'document.querySelector("form button[type=submit]").click()');
      await waitFor(() => evaluate(tab, '!document.querySelector("input[name=username]")'));
      console.log(username + ': observed login landing=' + await evaluate(tab, 'location.pathname'));
      await tab.send('Page.navigate', { url: 'http://localhost:3000/admin/patterns' });
    }
    stage = username + ':pattern-state';
    if (username === 'admin.test') {
      await waitFor(() => evaluate(tab, 'document.body.innerText.includes("Chưa có mining run.")'));
      assert.equal(await evaluate(tab, 'document.querySelector(\'select[aria-label="Mining run"]\').disabled'), true);
      assert.equal(await evaluate(tab, '!!document.querySelector(\'input[aria-label="Tìm sản phẩm theo tên hoặc SKU snapshot"]\')'), false);
      const source = records.find(record => record.pathname === '/api/v1/admin/mining-runs');assert.ok(source);assert.equal(source.status, 200);
      const body = JSON.parse((await tab.send('Network.getResponseBody', { requestId: source.requestId })).body);
      assert.equal(body.code, 'SUCCESS');assert.equal(body.data.total, '0');assert.deepEqual(body.data.items, []);
      assert.equal(body.data.page, 0);assert.equal(body.data.size, 20);
      assert.equal(records.filter(record => record.pathname !== '/api/v1/admin/mining-runs').length, 0);
      console.log('Admin /admin/patterns: real source HTTP200 total="0", empty message, disabled selector, no run-dependent request: PASS');
    } else if (username === 'anonymous') {
      assert.equal(await evaluate(tab, 'document.body.innerText.includes("Đăng nhập Admin")'), true);
      assert.equal(records.length, 0);console.log('Anonymous /admin/patterns: requires shared-auth sign-in, no protected data request: PASS');
    } else {
      await waitFor(() => evaluate(tab, 'document.body.innerText.includes("không có quyền ADMIN")'));
      assert.equal(records.length, 0);console.log(username + ': Pattern route denied by shared-auth role gate: PASS');
    }
    assert.equal(await logout(tab), 204);activeTab = null;
    await browser.send('Target.disposeBrowserContext', { browserContextId });
  }
  console.log('INT-06 empty-DB browser acceptance: 4/4 PASS. Real Pattern data remains NOT VERIFIED.');
} catch {
  console.error('INT-06 browser acceptance: FAIL at ' + stage + ' (no credential or protocol dump).');process.exitCode = 1;
} finally {
  if (activeTab) try { await logout(activeTab); } catch {}
  if (browser) try { await browser.send('Browser.close'); } catch {}
  for (const channel of channels) channel.close();
  if (edge && edge.exitCode === null) { edge.kill();await new Promise(resolve => { edge.once('exit', resolve);setTimeout(resolve, 3000); }); }
  await new Promise(resolve => { server.closeAllConnections();server.close(resolve); });
  if (path.dirname(profile) === path.resolve(os.tmpdir()) && path.basename(profile).startsWith('tv2-pattern-browser-')) fs.rmSync(profile, { recursive: true, force: true, maxRetries: 10, retryDelay: 150 });
}
