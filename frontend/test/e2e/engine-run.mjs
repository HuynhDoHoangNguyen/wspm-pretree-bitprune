import { chromium } from 'playwright-core';
import { spawn, execFileSync } from 'node:child_process';
import { randomBytes, createHash } from 'node:crypto';
import { request as httpRequest } from 'node:http';
import { createServer as httpsServer } from 'node:https';
import { createServer as netServer } from 'node:net';
import { mkdtempSync, readFileSync, writeFileSync, existsSync, realpathSync, createWriteStream, mkdirSync, cpSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import { runEngineCases } from './engine-cases.mjs';

// Dedicated opt-in gate: actual packaged application + test-only same-JVM observer.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const label = 'WDM-05.10', prefix = 'wspm-wdm0510-', name = prefix + randomBytes(6).toString('hex');
const temp = mkdtempSync(path.join(tmpdir(), prefix));
const logBase = path.join(root, '.agent-workspace/phase-wdm'); mkdirSync(logBase, { recursive: true });
const logDirectory = mkdtempSync(path.join(logBase, label + '-'));
const password = randomBytes(24).toString('hex'), jwtSecret = randomBytes(32).toString('base64'), db = 'wdm0510_gate';
const streams = [], processes = [], servers = [];
let containerId, browser, current, backendPort;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const digest = file => hash(readFileSync(file));
function command(exe, args, options = {}) {
  return execFileSync(exe, args, { cwd: root, encoding: 'utf8', timeout: 60000, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'], ...options }).trim();
}
function verifyContainer() {
  assert.match(name, /^wspm-wdm0510-[a-f0-9]+$/);
  const actual = JSON.parse(command('docker', ['inspect', containerId]))[0];
  assert.equal(actual.Id, containerId); assert.equal(actual.Config.Labels['codex-task'], label);
  assert.equal(actual.Config.Labels['codex-owner'], name); assert.equal(actual.Mounts.filter(m => m.Type === 'bind').length, 0);
  return actual;
}
function sql(input) {
  verifyContainer();
  return command('docker', ['exec', '--interactive', '--env', 'MYSQL_PWD=' + password, containerId,
    'mysql', '--default-character-set=utf8mb4', '--user=root', '--batch', '--raw', '--skip-column-names', db], { input });
}
async function freePort() {
  const server = netServer(); await new Promise((resolve, reject) => server.once('error', reject).listen(0, '127.0.0.1', resolve));
  const port = server.address().port; await new Promise(resolve => server.close(resolve)); return port;
}
function launch(args, logfile) {
  const stream = createWriteStream(path.join(logDirectory, logfile)); streams.push(stream);
  const child = spawn('java', args, { cwd: root, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.pipe(stream); child.stderr.pipe(stream); processes.push(child); return child;
}
async function ready(child) {
  for (let n = 0; n < 120; n++) {
    assert.equal(child.exitCode, null, 'Backend exited: inspect batch log');
    try { if ((await fetch('http://127.0.0.1:' + backendPort + '/api/health', { signal: AbortSignal.timeout(3000) })).ok) return; } catch { /* startup */ }
    await delay(1000);
  }
  throw new Error('Backend readiness deadline');
}
async function stopBatch() {
  if (!current) return;
  writeFileSync(path.join(current.directory, 'RELEASE'), 'cleanup');
  writeFileSync(path.join(current.directory, 'STOP'), 'graceful');
  for (let n = 0; n < 400 && current.child.exitCode === null; n++) await delay(100);
  assert.equal(current.child.exitCode, 0, 'Graceful shutdown must complete'); current = undefined;
}
function sourceInventory() {
  const files = command('git', ['ls-files', '--cached', '--others', '--exclude-standard', 'backend/src', 'mining-core/src', 'frontend/src', 'frontend/test/e2e']);
  return Object.fromEntries(files.split(/\r?\n/).filter(Boolean).sort().map(file => [file, digest(path.join(root, file))]));
}
console.log('Evidence directory: ' + path.relative(root, logDirectory));
try {
  const buildRoot = realpathSync(path.resolve(root, process.env.WDM_E2E_BUILD_DIR || 'frontend/build'));
  assert.ok(buildRoot.startsWith(realpathSync(root) + path.sep));
  const backendJar = path.join(root, 'backend/target/backend-1.0-SNAPSHOT.jar');
  const coreJar = path.join(root, 'mining-core/target/mining-core-1.0-SNAPSHOT.jar');
  const jarEntries = command('jar', ['tf', backendJar]);
  assert.equal(/vn\/edu\/wspm\/e2e\//.test(jarEntries), false, 'Test helpers must not enter production JAR');
  assert.equal(/EngineE2EOracleVerifier|BruteForceWeightedPatternOracle/.test(command('jar', ['tf', coreJar])), false);
  const stage = path.join(temp, 'stage'); mkdirSync(stage);
  command('jar', ['xf', backendJar], { cwd: stage });
  const nestedCore = path.join(stage, 'BOOT-INF/lib/mining-core-1.0-SNAPSHOT.jar');
  assert.equal(digest(nestedCore), digest(coreJar), 'Direct verifier must use the exact packaged core bytes');
  const helperSource = path.join(root, 'backend/target/test-classes/vn/edu/wspm/e2e');
  const helperTarget = path.join(stage, 'test-classes/vn/edu/wspm/e2e');
  mkdirSync(helperTarget, { recursive: true });
  // Explicit allowlist excludes JUnit test classes and any unrelated backend test support.
  const helpers = ['EngineE2EApplication', 'EngineE2ETestConfiguration', 'EngineExecutionGate', 'EngineRunEvidenceObserver'];
  for (const file of helpers) cpSync(path.join(helperSource, file + '.class'), path.join(helperTarget, file + '.class'));
  const classpath = [path.join(stage, 'BOOT-INF/classes'), path.join(stage, 'BOOT-INF/lib/*'), path.join(stage, 'test-classes')].join(path.delimiter);
  const verifierClasspath = [nestedCore, path.join(root, 'mining-core/target/test-classes')].join(path.delimiter);
  const preparation = { gate: label, gitHead: command('git', ['rev-parse', 'HEAD']), node: process.version,
    java: command('java', ['--version']).split('\n')[0], buildDirectory: path.relative(root, buildRoot),
    indexSha256: digest(path.join(buildRoot, 'index.html')), manifestSha256: digest(path.join(buildRoot, 'asset-manifest.json')),
    backendJarSha256: digest(backendJar), packagedCoreSha256: digest(nestedCore), directCoreSha256: digest(coreJar),
    helperClasses: Object.fromEntries(readdirSync(helperTarget).map(file => [file, digest(path.join(helperTarget, file))])), sources: sourceInventory() };
  writeFileSync(path.join(logDirectory, 'PREPARATION.json'), JSON.stringify(preparation, null, 2));
  containerId = command('docker', ['run', '--detach', '--rm', '--name', name, '--label', 'codex-task=' + label,
    '--label', 'codex-owner=' + name, '--env', 'MYSQL_ROOT_PASSWORD=' + password, '--env', 'MYSQL_DATABASE=' + db,
    '--publish', '127.0.0.1::3306', 'mysql:8.4']);
  const inspect = verifyContainer(), port = inspect.NetworkSettings.Ports['3306/tcp'][0].HostPort;
  let mysqlReady = false;
  for (let n = 0; n < 100; n++) { try { sql('SELECT 1'); mysqlReady = true; break; } catch { await delay(1000); } }
  assert.ok(mysqlReady); assert.equal(sql('SHOW TABLES'), '', 'Flyway must start from an empty schema');
  const httpsPort = await freePort(), origin = 'https://127.0.0.1:' + httpsPort;
  command('keytool', ['-genkeypair', '-alias', 'gate', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2', '-dname', 'CN=localhost',
    '-ext', 'SAN=IP:127.0.0.1,DNS:localhost', '-storetype', 'PKCS12', '-keystore', path.join(temp, 'tls.p12'), '-storepass', password, '-keypass', password]);
  const proxy = httpsServer({ pfx: readFileSync(path.join(temp, 'tls.p12')), passphrase: password }, (req, res) => {
    if (req.url.startsWith('/api/')) {
      const upstream = httpRequest({ hostname: '127.0.0.1', port: backendPort, path: req.url, method: req.method,
        headers: { ...req.headers, host: '127.0.0.1:' + backendPort } }, result => { res.writeHead(result.statusCode, result.headers); result.pipe(res); });
      upstream.on('error', () => { if (!res.headersSent) res.writeHead(502); res.end(); });
      req.on('aborted', () => upstream.destroy()); req.pipe(upstream); return;
    }
    const asset = path.resolve(buildRoot, '.' + new URL(req.url, origin).pathname);
    if (!asset.startsWith(buildRoot + path.sep) && asset !== buildRoot) { res.writeHead(400); res.end(); return; }
    const file = existsSync(asset) && path.extname(asset) ? asset : path.join(buildRoot, 'index.html');
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Content-Type', file.endsWith('.js') ? 'application/javascript' : file.endsWith('.css') ? 'text/css' : file.endsWith('.html') ? 'text/html' : 'application/octet-stream');
    try { res.end(readFileSync(file)); } catch { res.writeHead(404); res.end(); }
  });
  servers.push(proxy); await new Promise(resolve => proxy.listen(httpsPort, '127.0.0.1', resolve));
  const configuration = {
    'debug': false, 'spring.config.import': '', 'spring.datasource.url': 'jdbc:mysql://127.0.0.1:' + port + '/' + db,
    'spring.datasource.username': 'root', 'spring.datasource.password': password,
    'spring.datasource.hikari.data-source-properties.allowPublicKeyRetrieval': true,
    'spring.datasource.hikari.data-source-properties.connectionTimeZone': 'UTC', 'spring.datasource.hikari.data-source-properties.forceConnectionTimeZoneToSession': true,
    'spring.flyway.enabled': true, 'spring.flyway.baseline-on-migrate': false, 'spring.sql.init.mode': 'never', 'spring.jpa.hibernate.ddl-auto': 'validate',
    'auth.jwt.secret': jwtSecret, 'auth.jwt.issuer': 'https://wdm0510.example.test', 'auth.jwt.key-id': 'wdm0510-test-key',
    'auth.allowed-origins': origin, 'auth.cookie-secure': true, 'wdm.snapshot-storage.root': path.join(temp, 'snapshots'),
    'wdm.execution.enabled': true, 'wdm.execution.parallelism': 1, 'wdm.execution.queue-capacity': 1,
    'wdm.execution.shutdown-grace-millis': 30000, 'wdm.execution.lease-millis': 60000, 'wdm.execution.heartbeat-millis': 10000, 'wdm.execution.recovery-batch': 100,
  };
  async function startBatch(batch, hold = false) {
    await stopBatch(); backendPort = await freePort();
    const directory = path.join(logDirectory, batch); mkdirSync(directory);
    if (hold) writeFileSync(path.join(directory, 'HOLD'), 'test-only');
    const config = { ...configuration, 'server.port': backendPort, 'engine.e2e.directory': directory };
    const child = launch(['-cp', classpath, 'vn.edu.wspm.e2e.EngineE2EApplication', ...Object.entries(config).map(([key, value]) => '--' + key + '=' + value)], batch + '-SERVER.log');
    current = { directory, child }; await ready(child);
    assert.equal(sql("SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM flyway_schema_history WHERE success=1;"), '1,2,3');
    assert.equal(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0 OR type='BASELINE';"), '0');
    return directory;
  }
  await startBatch('positive-absolute');
  const history = sql('SELECT version,description,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank;');
  writeFileSync(path.join(logDirectory, 'FLYWAY.tsv'), history + '\n');
  preparation.mysql = { version: sql('SELECT VERSION();'), image: inspect.Image, containerId, historySha256: hash(history) };
  sql("INSERT INTO ROLE(role_id,code,name) VALUES('customer-role','CUSTOMER','Customer'),('admin-role','ADMIN','Admin');");
  browser = await chromium.launch({ channel: 'msedge', headless: true }); preparation.browser = 'Microsoft Edge ' + browser.version();
  writeFileSync(path.join(logDirectory, 'PREPARATION.json'), JSON.stringify(preparation, null, 2));
  await runEngineCases({ browser, origin, sql, root, temp, logDirectory, verifierClasspath, startBatch, initialDirectory: current.directory,
    backendConfiguration: configuration });
  await stopBatch();
  assert.deepEqual(sourceInventory(), preparation.sources, 'Source changed during gate');
  console.log('PASS WDM-05.10 Engine E2E Gate; owner acceptance remains separate');
} catch (error) {
  console.error('FAIL WDM-05.10: ' + (error.code || error.name || 'error'));
  console.error((error.stack || '').split('\n').filter(line => line.trim().startsWith('at ')).slice(0, 4).join('\n'));
  process.exitCode = 1;
} finally {
  if (current) { try { await stopBatch(); } catch { process.exitCode = 1; } }
  if (browser) await browser.close();
  for (const server of servers) { server.closeAllConnections?.(); await new Promise(resolve => server.close(resolve)); }
  for (const child of processes) if (child.exitCode === null && child.pid) {
    // Own child only, after graceful stop deadline. No owner process is enumerated or killed.
    try { command('taskkill', ['/PID', String(child.pid), '/T', '/F']); } catch { /* already stopped */ }
  }
  for (const stream of streams) await new Promise(resolve => stream.end(resolve));
  if (containerId) { try { verifyContainer(); command('docker', ['stop', '--time', '20', containerId]); } catch { process.exitCode = 1; } }
  // Current invocation's private staging/TLS/storage only. Evidence is retained separately.
  const target = realpathSync(temp), parent = realpathSync(tmpdir());
  assert.equal(path.dirname(target), parent); assert.ok(path.basename(target).startsWith(prefix));
  command('powershell', ['-NoProfile', '-NonInteractive', '-Command', "Remove-Item -LiteralPath '" + target.replaceAll("'", "''") + "' -Recurse -Force"]);
}
