import { chromium } from 'playwright-core';
import { build } from 'esbuild';
import { spawn, execFileSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { createServer as httpServer, request as httpRequest, globalAgent } from 'node:http';
import { createServer as httpsServer } from 'node:https';
import { createServer as netServer } from 'node:net';
import { mkdtempSync, readFileSync, writeFileSync, existsSync, rmSync, realpathSync, createWriteStream, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import { runCases } from './cases.mjs';
import { runDatasetCases } from './dataset-cases.mjs';
import { runTv2Cases } from './tv2-cases.mjs';

const datasetMode = process.argv.includes('--dataset');
const tv2Mode = process.argv.includes('--tv2');
const applicationMode = datasetMode || tv2Mode;
const here = path.dirname(fileURLToPath(import.meta.url)), root = path.resolve(here, '../../..');
const prefix = tv2Mode ? 'wspm-tv2-int14-' : datasetMode ? 'wspm-wdm018-' : 'wspm-auth06-';
const temp = mkdtempSync(path.join(tmpdir(), prefix));
const label = tv2Mode ? 'TV2-INT-14' : datasetMode ? 'WDM-01.8' : 'AUTH-06', name = prefix + randomBytes(5).toString('hex');
const password = randomBytes(24).toString('hex'), db = tv2Mode ? 'tv2_int14_' + randomBytes(5).toString('hex') : 'auth06_browser_test';
const processes = [], servers = [], logStreams = [];
let containerId, browser;
const logBase = path.resolve(root, process.env.AUTH_E2E_LOG_DIR || '.agent-workspace/phase-wdm');
mkdirSync(logBase, { recursive: true });
const logDirectory = mkdtempSync(path.join(logBase, label + '-'));
console.log('Evidence directory: ' + path.relative(root, logDirectory));
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function freePort() {
  const server = netServer(); await new Promise((resolve, reject) => server.once('error', reject).listen(0, '127.0.0.1', resolve));
  const port = server.address().port; await new Promise(resolve => server.close(resolve)); return port;
}
function command(exe, args, options = {}) {
  return execFileSync(exe, args, { cwd: root, encoding: 'utf8', timeout: 60000, stdio: ['pipe', 'pipe', 'pipe'], ...options }).trim();
}
function verifyContainer() {
  assert.ok(new RegExp('^' + prefix + '[a-f0-9]+$').test(name));
  const actual = JSON.parse(command('docker', ['inspect', containerId]))[0];
  assert.equal(actual.Id, containerId); assert.equal(actual.Config.Labels['codex-task'], label);
  assert.equal(actual.Mounts.filter(mount => mount.Type === 'bind').length, 0);
}
function sql(input) {
  verifyContainer();
  return command('docker', ['exec', '--interactive', '--env', 'MYSQL_PWD=' + password, containerId,
    'mysql', '--default-character-set=utf8mb4', '--user=root', '--batch', '--raw', '--skip-column-names', db], { input });
}
function launch(exe, args, logName, env = {}, cwd = root) {
  const stream = createWriteStream(path.join(logDirectory, logName)); logStreams.push(stream);
  const child = spawn(exe, args, { cwd, windowsHide: true, env: { ...process.env, ...env }, stdio: ['ignore', 'pipe', 'pipe'] });
  processes.push(child); child.stdout.pipe(stream); child.stderr.pipe(stream);
  child.on('error', () => console.error('Could not launch test service: ' + logName));
  return child;
}
async function ready(url, child) {
  for (let attempt = 0; attempt < 150; attempt++) {
    if (child.exitCode !== null) throw new Error('Test service exited; inspect service log');
    try { if ((await fetch(url, { signal: AbortSignal.timeout(5000) })).ok) return; } catch { /* startup */ }
    await delay(1000);
  }
  throw new Error('Test service readiness timed out');
}
async function finished(child, name) {
  let deadline;
  try {
    const code = await Promise.race([
      child.exitCode !== null ? Promise.resolve(child.exitCode)
        : new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); }),
      new Promise((_, reject) => { deadline = setTimeout(() => reject(new Error('Regression deadline exceeded')), 600000); }),
    ]);
    assert.equal(code, 0, name + ' failed; inspect its log');
    console.log('PASS ' + name);
  } finally { clearTimeout(deadline); }
}
try {
  if (tv2Mode && process.argv.includes('--manual')) assert.ok(process.env.TV2_INT14_BROWSER_PASSWORD, 'Manual mode requires a password supplied through process environment');
  assert.ok(existsSync(path.join(root, 'frontend/build/index.html')), 'Build frontend first');
  if (!tv2Mode) assert.ok(existsSync(path.join(root, 'backend/target/backend-1.0-SNAPSHOT.jar')), 'Package backend first');
  const backendPort = await freePort(), httpPort = await freePort(), httpsPort = await freePort();
  const httpOrigin = 'http://127.0.0.1:' + httpPort, httpsOrigin = 'https://127.0.0.1:' + httpsPort;
  containerId = command('docker', ['run', '--detach', '--rm', '--name', name, '--label', 'codex-task=' + label,
    '--env', 'MYSQL_ROOT_PASSWORD=' + password, '--env', 'MYSQL_DATABASE=' + db,
    '--publish', '127.0.0.1::3306', 'mysql:8.4']);
  verifyContainer();
  const port = JSON.parse(command('docker', ['inspect', containerId]))[0].NetworkSettings.Ports['3306/tcp'][0].HostPort;
  let mysqlReady = false;
  for (let attempt = 0; attempt < 100; attempt++) {
    try { sql('SELECT 1'); mysqlReady = true; break; } catch { await delay(1000); }
  }
  assert.ok(mysqlReady, 'Isolated MySQL readiness');
  if (!tv2Mode) {
  const v2 = readFileSync(path.join(root, 'backend/src/main/resources/db/migration/V2__complete_laptop_combo_schema.sql'), 'utf8');
  const definitions = v2.match(/^(?:CREATE TABLE|ALTER TABLE) `.*?;/gms);
  assert.equal(definitions.length, 47); sql(definitions.join('\n'));
  sql("INSERT INTO ROLE(role_id,code,name) VALUES('customer-role','CUSTOMER','Customer'),('admin-role','ADMIN','Admin');");
  }
  const jwtSecret = randomBytes(32);
  command('keytool', ['-genkeypair', '-alias', 'auth06', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2',
    '-dname', 'CN=localhost', '-ext', 'SAN=IP:127.0.0.1,DNS:localhost', '-storetype', 'PKCS12',
    '-keystore', path.join(temp, 'tls.p12'), '-storepass', password, '-keypass', password]);
  let server;
  if (tv2Mode) {
    const report = readFileSync(path.join(root, 'backend/target/surefire-reports/TEST-vn.edu.wspm.backend.repositories.ComboManagementMySqlTest.xml'), 'utf8');
    const classpath = report.match(/<property name="java.class.path" value="([^"]+)"/)[1]
      .replaceAll('&quot;', '"').replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&');
    assert.ok(existsSync(path.join(root, 'backend/target/test-classes/vn/edu/wspm/backend/repositories/Tv2BrowserApplication.class')), 'Compile TV2 test launcher first');
    const configuration = { 'debug': false, 'server.address': '127.0.0.1', 'server.port': backendPort,
      'spring.datasource.url': `jdbc:mysql://127.0.0.1:${port}/${db}?allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`,
      'spring.datasource.username': 'root', 'spring.datasource.password': password, 'spring.flyway.enabled': false,
      'spring.sql.init.mode': 'never', 'spring.jpa.hibernate.ddl-auto': 'validate',
      'auth.jwt.secret': jwtSecret.toString('base64'), 'auth.jwt.issuer': 'https://tv2-int14.example.test', 'auth.jwt.key-id': 'int14-test-key',
      'auth.allowed-origins': httpsOrigin, 'auth.cookie-secure': true, 'wdm.snapshot-storage.root': path.join(temp, 'snapshots'),
      'logging.level.org.springframework': 'INFO', 'logging.level.org.hibernate': 'WARN', 'logging.level.vn.edu.wspm': 'INFO' };
    server = launch('java', ['-cp', classpath, 'vn.edu.wspm.backend.repositories.Tv2BrowserApplication'], 'SERVER.log',
      { SPRING_APPLICATION_JSON: JSON.stringify(configuration), TV2_INT14_MODE: 'OWNED_DISPOSABLE_INT14', TV2_INT14_DATABASE: db,
        TV2_INT14_METADATA: path.join(temp, 'tv2-metadata.json') });
  } else server = launch('java', ['-jar', 'backend/target/backend-1.0-SNAPSHOT.jar', '--debug=false', '--server.port=' + backendPort,
    '--spring.datasource.url=jdbc:mysql://127.0.0.1:' + port + '/' + db, '--spring.datasource.username=root',
    '--spring.datasource.password=' + password, '--spring.datasource.hikari.data-source-properties.allowPublicKeyRetrieval=true',
    '--spring.datasource.hikari.data-source-properties.connectionTimeZone=UTC', '--spring.datasource.hikari.data-source-properties.forceConnectionTimeZoneToSession=true',
    '--spring.flyway.enabled=false', '--spring.sql.init.mode=never', '--spring.jpa.hibernate.ddl-auto=validate',
    '--auth.jwt.secret=' + jwtSecret.toString('base64'),
    '--auth.jwt.issuer=https://auth06.example.test', '--auth.jwt.key-id=auth06-test-key',
    '--auth.allowed-origins=' + httpsOrigin, '--auth.cookie-secure=true',
    '--wdm.snapshot-storage.root=' + path.join(temp, 'snapshots'),
    '--spring.servlet.multipart.max-file-size=4096B', '--spring.servlet.multipart.max-request-size=8192B'], 'SERVER.log');
  await ready('http://127.0.0.1:' + backendPort + '/api/health', server);
  if (!applicationMode) await build({ entryPoints: [path.join(here, 'harness.tsx')], bundle: true, outfile: path.join(temp, 'harness.js'),
    nodePaths: [path.join(root, 'frontend/node_modules')], tsconfig: path.join(root, 'frontend/tsconfig.json'),
    define: { 'process.env.NODE_ENV': '"test"', 'process.env.REACT_APP_WDM_API_BASE_URL': '"/api"' } });
  function handler(request, response) {
    if (request.url.startsWith('/api/')) {
      const upstream = httpRequest({ hostname: '127.0.0.1', port: backendPort, path: request.url,
        method: request.method, headers: { ...request.headers, host: '127.0.0.1:' + backendPort } }, result => {
        response.writeHead(result.statusCode, result.headers); result.pipe(response);
      });
      upstream.on('error', () => { if (!response.headersSent) response.writeHead(502); response.end(); });
      request.on('aborted', () => upstream.destroy()); request.pipe(upstream); return;
    }
    response.setHeader('Cache-Control', 'no-store');
    if (!applicationMode && request.url === '/__auth06') { response.setHeader('Content-Type', 'text/html'); response.end('<div id="root"></div><script src="/__auth06.js"></script>'); return; }
    if (!applicationMode && request.url === '/__auth06.js') { response.setHeader('Content-Type', 'application/javascript'); response.end(readFileSync(path.join(temp, 'harness.js'))); return; }
    const asset = path.resolve(root, 'frontend/build', '.' + new URL(request.url, httpOrigin).pathname);
    const buildRoot = path.resolve(root, 'frontend/build');
    if (!asset.startsWith(buildRoot + path.sep) && asset !== buildRoot) { response.writeHead(400); response.end(); return; }
    const file = existsSync(asset) && path.extname(asset) ? asset : path.join(buildRoot, 'index.html');
    response.setHeader('Content-Type', file.endsWith('.js') ? 'application/javascript' : file.endsWith('.css') ? 'text/css' : file.endsWith('.html') ? 'text/html' : 'application/octet-stream');
    try { response.end(readFileSync(file)); } catch { response.writeHead(404); response.end(); }
  }
  for (const [service, portNumber] of [[httpServer(handler), httpPort], [httpsServer({ pfx: readFileSync(path.join(temp, 'tls.p12')), passphrase: password }, handler), httpsPort]]) {
    servers.push(service); await new Promise(resolve => service.listen(portNumber, '127.0.0.1', resolve));
  }
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  console.log('Browser: Microsoft Edge ' + browser.version() + '; isolated MySQL 8.4; HTTPS reverse proxy; Secure cookies');
  let deadline;
  try {
    await Promise.race([
      (tv2Mode ? runTv2Cases : datasetMode ? runDatasetCases : runCases)({ browser, httpOrigin, httpsOrigin, sql, jwtSecret, root, temp, logDirectory }),
      new Promise((_, reject) => { deadline = setTimeout(() => reject(new Error('Browser suite deadline exceeded')), 600000); }),
    ]);
  } finally { clearTimeout(deadline); }
  if (!applicationMode && process.env.AUTH06_REGRESSION === '1') {
    const configuration = {
      'debug': false,
      'spring.datasource.url': 'jdbc:mysql://127.0.0.1:' + port + '/' + db,
      'spring.datasource.username': 'root', 'spring.datasource.password': password,
      'spring.datasource.hikari.data-source-properties.allowPublicKeyRetrieval': true,
      'spring.datasource.hikari.data-source-properties.connectionTimeZone': 'UTC',
      'spring.datasource.hikari.data-source-properties.forceConnectionTimeZoneToSession': true,
      'spring.flyway.enabled': false, 'spring.sql.init.mode': 'never', 'spring.jpa.hibernate.ddl-auto': 'validate',
      'auth.jwt.secret': jwtSecret.toString('base64'),
      'auth.jwt.issuer': 'https://auth06.example.test', 'auth.jwt.key-id': 'auth06-test-key',
      'auth.allowed-origins': httpsOrigin, 'auth.cookie-secure': true,
      'wdm.snapshot-storage.root': path.join(temp, 'regression-snapshots'),
    };
    const maven = process.platform === 'win32' ? 'cmd.exe' : 'mvn';
    const argumentsForMaven = process.platform === 'win32'
      ? ['/d', '/c', 'mvn -B -pl backend -am test'] : ['-B', '-pl', 'backend', '-am', 'test'];
    await finished(launch(maven, argumentsForMaven, 'AUTH-06_BACKEND.log',
      { SPRING_APPLICATION_JSON: JSON.stringify(configuration) }), 'backend regression on isolated datasource');
  }
  // The existing CRA package points to 8080. Verify it only if no user process owns that port.
  if (!applicationMode) {
  const probe = netServer(); let port8080Free = false;
  try { await new Promise((resolve, reject) => probe.once('error', reject).listen(8080, '127.0.0.1', resolve)); port8080Free = true; }
  catch { console.log('NOT VERIFIED: CRA proxy (8080 is occupied; preserved existing service)'); }
  if (port8080Free) {
    await new Promise(resolve => probe.close(resolve));
    const bridge = httpServer((req, res) => {
      const upstream = httpRequest({ hostname: '127.0.0.1', port: backendPort, path: req.url, method: req.method, headers: req.headers }, result => { res.writeHead(result.statusCode, result.headers); result.pipe(res); });
      upstream.on('error', () => { res.writeHead(502); res.end(); }); req.pipe(upstream);
    });
    servers.push(bridge); await new Promise(resolve => bridge.listen(8080, '127.0.0.1', resolve));
    const craPort = await freePort();
    const cra = launch(process.execPath, ['--require', path.join(here, 'cra.cjs'), 'node_modules/react-scripts/scripts/start.js'], 'AUTH-06_CRA.log',
      { PORT: String(craPort), HOST: '127.0.0.1', BROWSER: 'none', CI: 'true' }, path.join(root, 'frontend'));
    await ready('http://127.0.0.1:' + craPort + '/api/health', cra);
    assert.equal((await fetch('http://127.0.0.1:' + craPort + '/api/health')).status, 200);
    console.log('PASS CRA development /api proxy -> owned 8080 bridge -> isolated backend');
  }
  }
  console.log('PASS ' + label + ' browser gate (see case counts; owner acceptance remains separate)');
  if (tv2Mode && process.argv.includes('--manual')) {
    console.log('MANUAL test-only UI: ' + httpsOrigin + '/admin/patterns ; ' + httpsOrigin + '/admin/combos');
    console.log('Test accounts: tv2.admin1, tv2.admin2, tv2.customer. Press Ctrl+C here for owned cleanup.');
    await new Promise(resolve => process.once('SIGINT', resolve));
  }
} catch (error) {
  // Do not print assertion values, response bodies, cookie jars, JWTs or credentials.
  console.error('FAIL ' + label + ': ' + (error.code || error.name || 'test error') + '. See named case/service logs.');
  process.exitCode = 1;
} finally {
  if (browser) await browser.close();
  for (const service of servers) { service.closeAllConnections?.(); await new Promise(resolve => service.close(resolve)); }
  for (const child of processes.reverse()) {
    if (child.exitCode === null) {
      if (process.platform === 'win32') command('taskkill', ['/PID', String(child.pid), '/T', '/F']);
      else child.kill();
      await Promise.race([new Promise(resolve => child.once('exit', resolve)), delay(5000)]);
    }
  }
  for (const stream of logStreams) stream.end();
  if (containerId) { verifyContainer(); command('docker', ['stop', containerId]); }
  assert.equal(path.dirname(realpathSync(temp)), realpathSync(tmpdir()));
  assert.ok(path.basename(temp).startsWith(prefix));
  rmSync(temp, { recursive: true });
  globalAgent.destroy();
  console.log('Cleanup complete: owned browser/server/container/temp keys only.');
}
