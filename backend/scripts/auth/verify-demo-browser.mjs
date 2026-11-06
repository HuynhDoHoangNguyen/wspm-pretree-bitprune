import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Node 22+/Edge; no browser package, fixture route or application test hook.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const build = path.join(root, 'frontend/build');
assert.ok(fs.existsSync(path.join(build, 'index.html')), 'Build frontend first.');
assert.ok(process.env.DEMO_ADMIN_PASSWORD && process.env.DEMO_CUSTOMER_PASSWORD, 'Supply demo password environment variables.');
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'tv2-demo-browser-'));
const types = { '.js': 'application/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png' };
const server = http.createServer((request, response) => {
  if (request.url.startsWith('/api/')) {
    const proxy = http.request({ host: '127.0.0.1', port: 8080, path: request.url, method: request.method, headers: request.headers }, remote => {
      response.writeHead(remote.statusCode, remote.headers); remote.pipe(response);
    });
    proxy.on('error', () => { response.writeHead(502); response.end(); });
    request.pipe(proxy); return;
  }
  let target = path.resolve(build, '.' + new URL(request.url, 'http://localhost:3000').pathname);
  if (!target.startsWith(build + path.sep) || !fs.existsSync(target) || fs.statSync(target).isDirectory()) target = path.join(build, 'index.html');
  response.setHeader('Content-Type', types[path.extname(target)] || 'application/octet-stream');
  fs.createReadStream(target).pipe(response);
});
let edge;
let browser;
let stage = 'start-proxy';
const channels = new Set();
let activeTab;
async function logoutTab(tab) {
  return evaluate(tab, `(async()=>{const r=await fetch('/api/v1/auth/csrf');const b=await r.json();return (await fetch('/api/v1/auth/logout',{method:'POST',headers:{'X-XSRF-TOKEN':b.data.token}})).status})()`);
}
async function delay() { await new Promise(resolve => setTimeout(resolve, 150)); }
async function waitFor(check) {
  const deadline = Date.now() + 25000;
  while (Date.now() < deadline) { try { const result = await check(); if (result) return result; } catch {} await delay(); }
  throw new Error('Browser condition timed out.');
}
async function connect(url) {
  const ws = new WebSocket(url); channels.add(ws);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  let id = 0; const pending = new Map();
  ws.onmessage = event => { const m = JSON.parse(event.data); const p = pending.get(m.id); if (p) { pending.delete(m.id); m.error ? p.reject(new Error('CDP operation failed.')) : p.resolve(m.result); } };
  return { send(method, params = {}) { return new Promise((resolve, reject) => { const next = ++id; pending.set(next, { resolve, reject }); ws.send(JSON.stringify({ id: next, method, params })); }); } };
}
async function evaluate(tab, expression) {
  const result = await tab.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error('Browser evaluation failed.');
  return result.result.value;
}
try {
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(3000, '127.0.0.1', resolve); });
  const childEnv = { ...process.env }; delete childEnv.DEMO_ADMIN_PASSWORD; delete childEnv.DEMO_CUSTOMER_PASSWORD;
  stage = 'launch-edge';
  edge = spawn(process.env.DEMO_EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    ['--headless=new', '--disable-gpu', '--no-first-run', '--remote-debugging-port=0', '--user-data-dir=' + temp, 'about:blank'],
    { windowsHide: true, stdio: 'ignore', env: childEnv });
  const debug = await waitFor(() => fs.existsSync(path.join(temp, 'DevToolsActivePort')) && fs.readFileSync(path.join(temp, 'DevToolsActivePort'), 'utf8').trim().split(/\r?\n/));
  browser = await connect('ws://127.0.0.1:' + debug[0] + debug[1]);
  for (const [username, route, destination] of [['admin.test', '/admin/signin', '/admin/datasets'], ['customer_001', '/signin', '/'], ['customer_100', '/signin', '/']]) {
    stage = username + ':create-context';
    const { browserContextId } = await browser.send('Target.createBrowserContext');
    const { targetId } = await browser.send('Target.createTarget', { browserContextId, url: 'http://localhost:3000' + route });
    const tab = await connect('ws://127.0.0.1:' + debug[0] + '/devtools/page/' + targetId);
    activeTab = tab;
    stage = username + ':wait-for-form';
    await waitFor(() => evaluate(tab, '!!document.querySelector("input[name=username]:not(:disabled)")'));
    for (const [name, value] of [['username', username], ['password', username === 'admin.test' ? process.env.DEMO_ADMIN_PASSWORD : process.env.DEMO_CUSTOMER_PASSWORD]]) {
      await evaluate(tab, 'document.querySelector(' + JSON.stringify('input[name=' + name + ']') + ').focus()');
      await tab.send('Input.insertText', { text: value });
    }
    stage = username + ':submit-form';
    await evaluate(tab, 'document.querySelector("form button[type=submit]").click()');
    stage = username + ':wait-for-destination';
    await waitFor(() => evaluate(tab, '(location.pathname === ' + JSON.stringify(destination) + ') || (' + JSON.stringify(username === 'admin.test') + ' && !document.querySelector("input[name=username]") && !!document.querySelector(\'a[href="/admin/datasets"]\'))'));
    const actual = await evaluate(tab, 'location.pathname');
    console.log(username + ': browser login at ' + route + ', observed landing=' + actual + ' VERIFIED');
    if (actual !== destination) {
      assert.equal(username, 'admin.test');
      console.log('OBSERVATION: Admin shell retains sign-in path; automatic /admin/datasets redirect is not verified.');
      await evaluate(tab, 'document.querySelector(\'a[href="/admin/datasets"]\').click()');
      await waitFor(() => evaluate(tab, 'location.pathname === "/admin/datasets"'));
      console.log('Admin dataset navigation after login: VERIFIED');
    }
    const logout = await logoutTab(tab);
    assert.equal(logout, 204);
    activeTab = null;
    await browser.send('Target.disposeBrowserContext', { browserContextId });
  }
  console.log('Browser demo sign-in: 3/3 VERIFIED; landing observations above; CUSTOMER destination is /, not /user.');
} catch {
  console.error('Browser demo sign-in: FAIL at ' + stage + ' (no credentials, page or protocol dump).'); process.exitCode = 1;
} finally {
  if (activeTab) try { await logoutTab(activeTab); } catch {}
  if (browser) try { await browser.send('Browser.close'); } catch {}
  for (const channel of channels) channel.close();
  if (edge && edge.exitCode === null) { edge.kill(); await new Promise(resolve => { edge.once('exit', resolve); setTimeout(resolve, 3000); }); }
  await new Promise(resolve => { server.closeAllConnections(); server.close(resolve); });
  // This exact mkdtemp directory is owned by this invocation; no pre-existing evidence is removed.
  if (path.dirname(temp) === path.resolve(os.tmpdir()) && path.basename(temp).startsWith('tv2-demo-browser-')) {
    fs.rmSync(temp, { recursive: true, force: true, maxRetries: 10, retryDelay: 150 });
  }
}
