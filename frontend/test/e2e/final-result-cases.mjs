import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, existsSync, renameSync, createWriteStream } from 'node:fs';
import { createHash, randomBytes } from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import path from 'node:path';

const base = '/api/v1/admin/mining-runs', datasets = '/api/v1/admin/dataset-snapshots';
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const properties = file => Object.fromEntries(readFileSync(file, 'utf8').split(/\r?\n/).filter(l => l && !l.startsWith('#')).map(l => { const i = l.indexOf('='); return [l.slice(0, i), l.slice(i + 1)]; }));
async function until(check, message, timeout = 60000) {
  const deadline = Date.now() + timeout;
  do { const result = await check(); if (result) return result; await delay(100); } while (Date.now() < deadline);
  throw new Error(message);
}
function sameMap(expected, actual, layer) {
  for (const key of [...new Set([...Object.keys(expected), ...Object.keys(actual)])].sort())
    assert.equal(actual[key], expected[key], `${layer}: first pattern ${key}; expected=${expected[key]} actual=${actual[key]}`);
  assert.equal(Object.keys(actual).length, Object.keys(expected).length, layer + ' count');
}
function canonical(sets) { return '<' + sets.map(s => '(' + s.items.map(i => i.itemId).join(' ') + ')').join('') + '>'; }
function patternMap(patterns) {
  const map = {};
  for (const { pattern: p, itemsets } of patterns) {
    assert.equal(canonical(itemsets), p.canonicalPattern, 'Temporal coordinates must preserve identity');
    itemsets.forEach((s, n) => { assert.equal(s.itemsetOrder, n + 1); s.items.forEach((i, j) => assert.equal(i.itemOrder, j + 1)); });
    assert.equal(p.patternWeightPolicy, 'EQ2_EQ3_ARITHMETIC_MEAN_V1');
    assert.equal(typeof p.patternId, 'string'); assert.equal(typeof p.supportAbs, 'string');
    assert.equal(map[p.canonicalPattern], undefined, 'Duplicate identity'); map[p.canonicalPattern] = Number(p.supportAbs);
  }
  return map;
}

export async function runFinalResultCases({ browser, origin, sql, root, logDirectory, verifierClasspath, startBatch, initialDirectory, backendConfiguration }) {
  const controlsOnly = process.env.WDM_FINAL_RESULT_CONTROLS_ONLY === '1';
  const evidence = { gate: 'WDM-06.12', scope: controlsOnly ? 'FOCUSED_CONTROLS_ONLY' : 'FULL_FINAL_RESULT', status: 'RUNNING', ownerAcceptance: 'PENDING', cases: [], runs: [], negativeControls: [], acceptance: {} };
  const save = () => writeFileSync(path.join(logDirectory, 'RESULT.json'), JSON.stringify(evidence, null, 2));
  const record = (name, data = {}) => { evidence.cases.push({ name, status: 'PASS', ...data }); save(); console.log('PASS Final Result: ' + name); };
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, acceptDownloads: true }); ctx.setDefaultTimeout(20000);
  const page = await ctx.newPage(), password = randomBytes(18).toString('base64url'), username = 'final.admin';
  const resources = path.join(root, 'backend/src/test/resources/wdm/csv-v1');
  let token, directory = initialDirectory, providerIndex = 0;
  const headers = () => ({ Authorization: 'Bearer ' + token });
  async function body(response, status = 200) {
    assert.equal(response.status(), status, 'HTTP ' + response.url());
    const json = await response.json(); assert.equal(json.requestId, response.headers()['x-request-id']);
    assert.ok(response.headers()['cache-control'].includes('no-store'));
    return status < 300 ? json.data : json;
  }
  async function get(url) { return body(await ctx.request.get(origin + url, { headers: headers() })); }
  async function provider(id, expected = 'OK') {
    const index = ++providerIndex, inbox = path.join(directory, 'PROVIDER-REQUEST');
    await until(() => !existsSync(inbox), 'Provider request consumed');
    writeFileSync(inbox + '.tmp', index + ' ' + id); renameSync(inbox + '.tmp', inbox);
    const file = path.join(directory, 'provider-' + index + '.json');
    await until(() => { assert.equal(existsSync(path.join(directory, 'PROVIDER-ERROR')), false); return existsSync(file); }, 'Provider helper deadline');
    const result = JSON.parse(readFileSync(file)); assert.equal(result.runId, id); assert.equal(result.status, expected);
    return result;
  }
  async function published(id) {
    const info = await until(async () => { const m = await get(base + '/' + id + '/results'); return m.publishedReadable ? m : null; }, 'Publication deadline');
    assert.equal(info.publicationStatus, 'COMMITTED'); assert.equal(info.completeness, 'COMPLETE_VERIFIED');
    await until(async () => (await get('/api/v1/admin/mining-execution/capacity')).snapshot.used === 0, 'Acknowledgement/capacity deadline');
    return info;
  }
  async function prepareFixture(name, expectedInput) {
    const bytes = readFileSync(path.join(resources, name + '.csv'));
    const upload = await body(await ctx.request.post(origin + datasets, { headers: headers(), multipart: { file: { name: name + '.csv', mimeType: 'text/csv', buffer: bytes } } }), 201);
    const id = upload.snapshotId;
    await body(await ctx.request.post(origin + datasets + '/' + id + '/validation', { headers: headers() }));
    await body(await ctx.request.post(origin + datasets + '/' + id + '/conversion', { headers: headers() }));
    const sequence = await ctx.request.get(origin + datasets + '/' + id + '/artifacts/sequence', { headers: headers() }); assert.equal(sequence.status(), 200);
    const input = await sequence.body(); assert.equal(input.toString().trim(), expectedInput.trim(), 'Conversion must match hand-authored sequence structure');
    const file = path.join(logDirectory, name + '.spmf'); writeFileSync(file, input);
    const mappingResponse = await ctx.request.get(origin + datasets + '/' + id + '/artifacts/mapping', { headers: headers() }); assert.equal(mappingResponse.status(), 200);
    writeFileSync(path.join(logDirectory, name + '-mapping.json'), await mappingResponse.body());
    record('prepare ' + name + ' via real upload/validation/conversion APIs', { snapshotId: id, fixtureSha256: sha(bytes), inputSha256: sha(input), source: 'synthetic correctness fixture; no mined output seeded' });
    return { id, name, file, hash: sha(input) };
  }
  async function create(fixture, algorithm, mode, value, ui = true) {
    const request = { snapshotId: fixture.id, algorithm, minsupMode: mode, minsupValue: value };
    const before = existsSync(path.join(directory, 'CALLS')) ? Number(readFileSync(path.join(directory, 'CALLS'))) : 0;
    let response;
    if (ui) {
      await page.goto(origin + '/admin/mining-runs?snapshotId=' + fixture.id);
      await page.getByRole('form', { name: 'Cấu hình khai phá', exact: true }).waitFor();
      await page.getByLabel('Thuật toán', { exact: true }).click();
      await page.getByRole('option', { name: algorithm === 'WSPM_PRETREE' ? 'WSPM_PreTree' : 'WSPM_BitPrune', exact: true }).click();
      await page.getByLabel('Mode minsup', { exact: true }).click();
      await page.getByRole('option', { name: mode === 'ABSOLUTE' ? 'Tuyệt đối (ABSOLUTE)' : 'Phần trăm (PERCENT)', exact: true }).click();
      await page.getByLabel('Giá trị minsup', { exact: false }).fill(String(value));
      const checked = page.waitForResponse(r => r.url().endsWith(base + '/configuration-validation') && r.request().method() === 'POST');
      await page.getByRole('button', { name: 'Kiểm tra cấu hình', exact: true }).click();
      assert.equal((await body(await checked)).configurationStatus, 'SUPPORTED');
      await until(() => page.getByRole('button', { name: 'Tạo lần chạy', exact: true }).isEnabled(), 'Create enabled');
      const accepted = page.waitForResponse(r => r.url() === origin + base && r.request().method() === 'POST');
      await page.getByRole('button', { name: 'Tạo lần chạy', exact: true }).click(); response = await accepted;
      assert.deepEqual(response.request().postDataJSON(), request);
    } else response = await ctx.request.post(origin + base, { headers: headers(), data: request });
    const run = await body(response, 202); assert.match(run.runId, /^[0-9a-f-]{36}$/);
    if (ui) await page.waitForURL(origin + '/admin/mining-runs/' + run.runId);
    const capture = path.join(directory, 'original-' + (before + 1));
    await until(() => existsSync(capture + '.properties'), 'Atomic original capture');
    assert.equal(Number(readFileSync(path.join(directory, 'CALLS'))), before + 1, 'One production entry for one UI/202 run');
    return { id: run.runId, request, call: before + 1, capture, fixture, createdVia: ui ? 'UI/HTTP202' : 'API/HTTP202' };
  }
  async function verifyOriginal(run) {
    const status = await get(base + '/' + run.id), binding = { runId: run.id, snapshotId: run.fixture.id, call: run.call,
      request: run.request, sequenceSha256: run.fixture.hash, configuration: { ...status.configuration, runId: status.runId } };
    assert.equal(status.status, 'SUCCEEDED'); assert.equal(status.snapshotId, run.fixture.id);
    const file = path.join(logDirectory, run.id + '-binding.json'); writeFileSync(file, JSON.stringify(binding, null, 2));
    const expected = path.join(logDirectory, run.id + '-expected.json');
    const args = ['--class-path', verifierClasspath, path.join(root, 'frontend/test/e2e/java/FinalResultE2EVerifier.java'),
      run.capture + '.properties', run.capture + '.map.tsv', run.capture + '.spmf', run.fixture.file, file, expected];
    const result = spawnSync('java', args, { encoding: 'utf8', windowsHide: true, timeout: 60000 });
    writeFileSync(path.join(logDirectory, run.id + '-ORACLE.log'), result.stdout + result.stderr);
    assert.equal(result.status, 0, 'STOP: original/direct/oracle mismatch; inspect ORACLE.log');
    run.verifyArgs = args; run.expected = JSON.parse(readFileSync(expected)); run.binding = file;
    return run.expected;
  }
  function dbSnapshot() {
    const tables = ['MINING_RUN', 'RUN_PROVENANCE', 'RUN_RESULT_PUBLICATION', 'PATTERN', 'PATTERN_ITEM', 'RUN_ITEM_STATISTIC', 'COMBO_BATCH', 'COMBO', 'COMBO_ITEM', 'COMBO_SOURCE'];
    return Object.fromEntries(tables.map(t => [t, sha(sql('SELECT * FROM `' + t + '` ORDER BY 1,2,3;'))]));
  }
  async function verifyLayers(run, phase, mobile = false) {
    const expected = run.expected, info = await get(base + '/' + run.id + '/results');
    assert.equal(info.publishedReadable, true); assert.equal(info.storedPatternCount, String(Object.keys(expected.patterns).length));
    assert.equal(info.provenance.sequenceSha256, run.fixture.hash); assert.equal(info.provenance.snapshotId, run.fixture.id);
    assert.equal(info.provenance.mappingSha256, sha(readFileSync(path.join(logDirectory,run.fixture.name+'-mapping.json'))));
    const sqlMap = Object.fromEntries(sql(`SELECT canonical_pattern,support_abs FROM PATTERN WHERE run_id='${run.id}' ORDER BY BINARY canonical_pattern;`).split('\n').filter(Boolean).map(l => { const [p,s] = l.split('\t'); return [p,Number(s)]; }));
    sameMap(expected.patterns, sqlMap, 'DB');
    const all = [], apiPages = [];
    for (let n = 1; ; n++) {
      const p = await get(base + '/' + run.id + '/patterns?view=published&page=' + n + '&size=20'); apiPages.push(p);
      assert.equal(p.total, String(Object.keys(expected.patterns).length));
      for (const summary of p.items) {
        assert.equal(all.some(d => d.pattern.patternId === summary.patternId), false, 'Repeated API page ID');
        const detail = await get(base + '/' + run.id + '/patterns/' + summary.patternId + '?view=published'); assert.deepEqual(detail.pattern, summary); all.push(detail);
      }
      if (n * 20 >= Number(p.total)) break;
    }
    sameMap(expected.patterns, patternMap(all), 'API all pages/details');
    const temporal = sql(`SELECT CAST(i.pattern_id AS CHAR),i.itemset_order,i.item_order,i.item_id FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id WHERE p.run_id='${run.id}' ORDER BY i.pattern_id,i.itemset_order,i.item_order;`);
    const temporalExpected = all.flatMap(d => d.itemsets.flatMap(s => s.items.map(i => [d.pattern.patternId,s.itemsetOrder,i.itemOrder,i.itemId].join('\t')))).sort((a,b) => { const x=a.split('\t').map(BigInt),y=b.split('\t').map(BigInt); for(let n=0;n<4;n++) if(x[n]!==y[n]) return x[n]<y[n]?-1:1; return 0; }).join('\n');
    assert.equal(temporal, temporalExpected, 'DB temporal rows before canonicalization');
    const stats = sql(`SELECT item_id,occurrence_count,sequence_support,item_weight,product_id,product_name_snapshot,sku_snapshot FROM RUN_ITEM_STATISTIC WHERE run_id='${run.id}' ORDER BY item_id;`).split('\n').filter(Boolean).map(l => { const [itemId,occurrenceCount,sequenceSupport,itemWeight,productId,productNameSnapshot,skuSnapshot]=l.split('\t'); return { itemId:Number(itemId),occurrenceCount,sequenceSupport,itemWeight:Number(itemWeight),productId,productNameSnapshot,skuSnapshot }; });
    assert.deepEqual(stats.map(({itemId,occurrenceCount,sequenceSupport,itemWeight})=>({itemId,occurrenceCount,sequenceSupport,itemWeight})), expected.runItems, 'All input statistics from core, including items absent in patterns');
    stats.forEach(s => { assert.equal(s.productNameSnapshot,'Synthetic'); assert.equal(s.skuSnapshot,s.productId); });
    for(const d of all) for(const s of d.itemsets) for(const i of s.items) {
      const {itemOrder,...item}=i; assert.deepEqual(item,stats.find(s=>s.itemId===i.itemId));
    }
    await page.setViewportSize(mobile ? {width:390,height:844} : {width:1280,height:900});
    await page.goto(origin + '/admin/mining-runs/' + run.id);
    await page.getByRole('link',{name:'Xem pattern đã công bố',exact:true}).waitFor();
    const dl = page.waitForEvent('download');
    const http = page.waitForResponse(r=>r.url().includes('/patterns/export?view=published'));
    await page.getByRole('button',{name:'Tải JSON đã công bố',exact:true}).click();
    const download = await dl, response = await http; assert.equal(response.status(),200);
    assert.ok(response.headers()['content-type'].startsWith('application/json')); assert.ok(response.headers()['cache-control'].includes('no-store')); assert.equal(response.headers()['x-content-type-options'],'nosniff');
    assert.equal(download.suggestedFilename(),'mining-result-'+run.id+'.json'); assert.ok(response.headers()['content-disposition'].includes(download.suggestedFilename()));
    const exported = JSON.parse(readFileSync(await download.path()));
    assert.equal(exported.formatVersion,'WDM_RESULT_EXPORT_V2'); assert.equal(exported.purpose,'COMBO_HANDOFF');
    sameMap(expected.patterns,patternMap(exported.patterns),'UI export full map'); assert.deepEqual(exported.patterns,all.map(({pattern,itemsets})=>({pattern,itemsets}))); assert.deepEqual(exported.runItems,stats); assert.deepEqual(exported.result,info);
    const handed = await provider(run.id, all.length ? 'OK' : 'INVALID_RESOURCE_STATE');
    assert.equal(handed.outcomeRetained,false,'Provider after production acknowledgement');
    if(all.length) assert.deepEqual(handed.bundle,{result:info,patterns:exported.patterns,runItems:stats});
    await page.getByRole('link',{name:'Xem pattern đã công bố',exact:true}).click();
    const uiMap={},uiDetails=[];
    for(let n=1;n<=apiPages.length;n++) {
      const summaries=apiPages[n-1].items;
      if(summaries.length) await page.getByRole('table',{name:'Pattern đã công bố'}).waitFor();
      else await page.getByText('Kết quả đã công bố có 0 pattern.',{exact:true}).waitFor();
      const rows=page.locator('table[aria-label="Pattern đã công bố"] tbody tr'); assert.equal(await rows.count(),summaries.length);
      for(let j=0;j<summaries.length;j++) {
        const p=summaries[j],row=rows.nth(j),cells=row.locator('td');
        assert.equal(await row.locator('code').innerText(),p.canonicalPattern); assert.equal(await cells.nth(1).innerText(),p.supportAbs);
        assert.equal(await cells.nth(5).innerText(),p.itemsetCount+' itemset · '+p.patternItemCount+' item');
        assert.equal(uiMap[p.canonicalPattern],undefined); uiMap[p.canonicalPattern]=Number(await cells.nth(1).innerText());
      }
      for(const p of summaries) {
        await page.getByRole('link',{name:'Xem chi tiết pattern '+p.patternId,exact:true}).click();
        const overview=page.getByRole('region',{name:'Tổng quan pattern',exact:true}); await overview.waitFor();
        await overview.getByText(p.canonicalPattern,{exact:true}).waitFor(); await overview.getByText('Support tuyệt đối: '+p.supportAbs+' · Tỷ lệ support: '+p.supportRatio,{exact:true}).waitFor();
        const d=all.find(d=>d.pattern.patternId===p.patternId);
        for(const s of d.itemsets) {
          const table=page.getByRole('table',{name:'Item của itemset '+s.itemsetOrder,exact:true});
          await table.waitFor(); const rows=table.locator('tbody tr'); assert.equal(await rows.count(),s.items.length);
          for(let k=0;k<s.items.length;k++) { const i=s.items[k]; assert.deepEqual(await rows.nth(k).locator('td').allInnerTexts(),[i.itemOrder+' / '+i.itemId,i.productNameSnapshot+'\n'+i.productId,i.skuSnapshot,String(i.itemWeight),i.occurrenceCount,i.sequenceSupport]); }
        }
        uiDetails.push(p.patternId);
        await page.getByRole('link',{name:'Về Pattern Explorer',exact:true}).click();
        assert.equal(new URL(page.url()).searchParams.get('page'),String(n)); assert.equal(new URL(page.url()).searchParams.get('view'),'published');
        await page.getByRole('table',{name:'Pattern đã công bố'}).waitFor();
      }
      if(n<apiPages.length) await page.getByRole('button',{name:'Trang sau',exact:true}).click();
    }
    sameMap(expected.patterns,uiMap,'Rendered UI all pages and details'); assert.equal(uiDetails.length,all.length);
    await page.screenshot({path:path.join(logDirectory,run.id+'-'+phase+'.png'),fullPage:true});
    const artifact={phase,runId:run.id,original:properties(run.capture+'.properties'),metadata:info,apiPages,details:all,dbTemporal:temporal,dbStatistics:stats,export:exported,provider:handed,uiMap,uiDetails,viewport:mobile?'mobile':'desktop'};
    writeFileSync(path.join(logDirectory,run.id+'-'+phase+'.json'),JSON.stringify(artifact,null,2));
    if(run.bundle) assert.deepEqual(exported,run.bundle,'Restart content and manifest unchanged'); else run.bundle=exported;
    record(run.id+' '+phase+': full DB/API/rendered UI/export/provider equality',{patterns:all.length,pages:apiPages.length,uiDetails:uiDetails.length});
  }
  try {
    save();
    const csrf = await body(await ctx.request.get(origin+'/api/v1/auth/csrf'));
    const user = await body(await ctx.request.post(origin+'/api/v1/auth/register',{headers:{Origin:origin,'X-XSRF-TOKEN':csrf.token},data:{username,email:'final.admin@example.test',password,displayName:'Synthetic Final Gate'}}),201);
    assert.match(user.userId,/^[0-9a-f-]{36}$/); sql(`INSERT INTO USER_ROLE(user_id,role_id) VALUES('${user.userId}','admin-role');`);
    sql("INSERT INTO CATEGORY(category_id,name,slug) VALUES('final-category','Synthetic','final-category');");
    for(const product of ['rt-p1','rt-p2','rt-p3','rt-p4','rt-unused','mp-p1','mp-p2','mp-p3',...Array.from({length:10},(_,n)=>'numeric-p'+(n+1))]) sql(`INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES('${product}','${product}','Synthetic','${product}','final-category',999);`);
    await page.goto(origin+'/admin/mining-runs');
    await page.getByLabel('Tên tài khoản',{exact:false}).fill(username); await page.getByLabel('Mật khẩu',{exact:false}).fill(password);
    const logged=page.waitForResponse(r=>r.url().endsWith('/api/v1/auth/login')&&r.request().method()==='POST');
    await page.getByRole('button',{name:'Đăng nhập',exact:true}).click(); token=(await body(await logged)).accessToken;
    await page.getByRole('heading',{name:'Từ dữ liệu đơn hàng đến combo sản phẩm',exact:true}).waitFor(); record('Real shared Auth/JWT and Admin UI');
    const tiny=await prepareFixture('round-trip','1 2 -1 3 -1 1 -1 -2\n1 2 -1 -2\n1 -1 2 -1 -2\n');
    const multipage=await prepareFixture('multipage','4 5 -1 4 6 -1 5 -1 -2\n4 5 -1 4 6 -1 5 -1 -2\n');
    const numeric=await prepareFixture('numeric-item-order',Array.from({length:10},(_,n)=>(n+7)+' -1 -2\n').join(''));
    const runs=[];
    for(const [mode,value] of [['ABSOLUTE',0.1],['PERCENT',14],['PERCENT',100]].slice(0,controlsOnly?1:3)) for(const algorithm of ['WSPM_PRETREE','WSPM_BITPRUNE']) {
      const run=await create(tiny,algorithm,mode,value); await published(run.id); await verifyOriginal(run);
      await verifyLayers(run,'initial-desktop'); runs.push(run); evidence.runs.push({runId:run.id,request:run.request,call:run.call,patterns:Object.keys(run.expected.patterns).length,createdVia:run.createdVia}); save();
    }
    for(const algorithm of controlsOnly ? [] : ['WSPM_PRETREE','WSPM_BITPRUNE']) {
      const run=await create(multipage,algorithm,'ABSOLUTE',0.1); await published(run.id); await verifyOriginal(run);
      assert.ok(Object.keys(run.expected.patterns).length>20,'Real pagination fixture'); await verifyLayers(run,'initial-mobile',true); runs.push(run);
      evidence.runs.push({runId:run.id,request:run.request,call:run.call,patterns:Object.keys(run.expected.patterns).length,createdVia:run.createdVia}); save();
    }
    for(const algorithm of controlsOnly ? [] : ['WSPM_PRETREE','WSPM_BITPRUNE']) {
      const run=await create(numeric,algorithm,'ABSOLUTE',0.1); await published(run.id); await verifyOriginal(run);
      assert.equal(Object.keys(run.expected.patterns).length,10);assert.ok(run.expected.runItems.some(i=>i.itemId>=10));
      await verifyLayers(run,'numeric-item-order-desktop');runs.push(run);
      evidence.runs.push({runId:run.id,request:run.request,call:run.call,patterns:10,createdVia:run.createdVia,regression:'numeric item order crosses 9/10'});save();
    }
    record(controlsOnly?'Focused controls preparation: two UI-created reference runs':'Six tiny configurations, two >20-pattern runs and two multi-digit item-id runs; production capacity recycled without restart');
    // Copied artifacts deliberately preserve shape/digest where needed so the independent oracle must reject.
    const reference=runs[0],map=readFileSync(reference.capture+'.map.tsv','utf8'),lines=map.trimEnd().split('\n');
    const controls={support:lines.map((l,i)=>i?l:l.replace(/\t\d+$/,'\t99')).join('\n')+'\n',boundary:map.replace(/\)\(/, ' '),deleted:lines.slice(1).join('\n')+'\n',duplicate:map+lines[0]+'\n'};
    for(const [name,text] of Object.entries(controls)) {
      assert.notEqual(text,map,'Negative mutation must take effect'); const target=path.join(logDirectory,'negative-'+name),p=properties(reference.capture+'.properties'); p['map.sha256']=sha(text);
      writeFileSync(target+'.tsv',text); writeFileSync(target+'.properties',Object.entries(p).map(([k,v])=>k+'='+v).join('\n'));
      const args=[...reference.verifyArgs]; args[3]=target+'.properties';args[4]=target+'.tsv';args[8]=target+'-unexpected.json';
      const result=spawnSync('java',args,{encoding:'utf8',windowsHide:true,timeout:60000}); writeFileSync(target+'.log',result.stdout+result.stderr); assert.notEqual(result.status,0);
      if(['support','boundary','deleted'].includes(name)) assert.match(result.stderr,/First pattern\/support mismatch/);
      const mismatch=result.stderr.match(/First pattern\/support mismatch: ([^\r\n]+)/)?.[1];
      const altered=Object.fromEntries(text.trim().split('\n').map(l=>{const [p,s]=l.split('\t');return [p,Number(s)];}));
      evidence.negativeControls.push({name,layer:'independent oracle',status:'PASS',...(mismatch?{firstPattern:mismatch,expectedSupport:reference.expected.patterns[mismatch]??null,actualSupport:altered[mismatch]??null}:{})});
    }
    for(const name of ['wrong-run','wrong-snapshot','one-ulp']) {
      const binding=JSON.parse(readFileSync(reference.binding));
      if(name==='wrong-run')binding.runId='00000000-0000-0000-0000-000000000000';
      if(name==='wrong-snapshot')binding.snapshotId='00000000-0000-0000-0000-000000000000';
      if(name==='one-ulp')binding.request.minsupValue=0.10000000000000002;
      const file=path.join(logDirectory,'negative-'+name+'.json');writeFileSync(file,JSON.stringify(binding)); const args=[...reference.verifyArgs];args[7]=file;args[8]=file+'.unexpected';
      const result=spawnSync('java',args,{encoding:'utf8',windowsHide:true,timeout:60000});writeFileSync(file+'.log',result.stdout+result.stderr);assert.notEqual(result.status,0);
      evidence.negativeControls.push({name,layer:'capture binding/raw bits',status:'PASS'});
    }
    record('Copied artifact negative controls: full map, duplicates, run/provenance binding and one ULP');
    const anonymous=await browser.newContext({ignoreHTTPSErrors:true});
    const customer=await browser.newContext({ignoreHTTPSErrors:true});
    try {
      const csrf=await body(await customer.request.get(origin+'/api/v1/auth/csrf'));
      await body(await customer.request.post(origin+'/api/v1/auth/register',{headers:{Origin:origin,'X-XSRF-TOKEN':csrf.token},data:{username:'final.customer',email:'final.customer@example.test',password,displayName:'Synthetic Customer'}}),201);
      const c=await body(await customer.request.get(origin+'/api/v1/auth/csrf'));
      const login=await body(await customer.request.post(origin+'/api/v1/auth/login',{headers:{Origin:origin,'X-XSRF-TOKEN':c.token},data:{username:'final.customer',password}}));
      const detail=reference.bundle.patterns[0].pattern.patternId;
      for(const suffix of ['/results','/patterns?view=published','/patterns/'+detail+'?view=published','/patterns/export?view=published']) {
        await body(await anonymous.request.get(origin+base+'/'+reference.id+suffix),401);
        await body(await customer.request.get(origin+base+'/'+reference.id+suffix,{headers:{Authorization:'Bearer '+login.accessToken}}),403);
      }
    } finally {await anonymous.close();await customer.close();}
    record('Real 401/403 on metadata, page, detail, export; ADMIN success established by full reads');
    const beforeInvalid=sql('SELECT COUNT(*) FROM MINING_RUN;'),beforeCalls=readFileSync(path.join(directory,'CALLS'),'utf8');
    for(const literal of ['0','-0','1e-9999']) {
      await body(await ctx.request.post(origin+base,{headers:{...headers(),'Content-Type':'application/json'},data:`{"snapshotId":"${tiny.id}","algorithm":"WSPM_PRETREE","minsupMode":"ABSOLUTE","minsupValue":${literal}}`}),422);
    }
    await body(await ctx.request.post(origin+base,{headers:headers(),data:{snapshotId:tiny.id,algorithm:'WSPM_BITPRUNE',minsupMode:'PERCENT',minsupValue:5e-324}}),422);
    assert.equal(sql('SELECT COUNT(*) FROM MINING_RUN;'),beforeInvalid);assert.equal(readFileSync(path.join(directory,'CALLS'),'utf8'),beforeCalls);record('Zero/signed zero/JSON and percentage underflow rejected before run insertion/engine');
    const beforeReads=dbSnapshot(); writeFileSync(path.join(logDirectory,'BEFORE-READS.json'),JSON.stringify(beforeReads,null,2));
    sql("UPDATE PRODUCT SET name='Current catalog changed',sku=CONCAT('NEW-',product_id) WHERE category_id='final-category';");
    for(const [phase,overrides] of controlsOnly ? [] : [['restart',{}],['execution-disabled',{'wdm.execution.enabled':false,'wdm.result-publication.enabled':false}]]) {
      directory=await startBatch('final-'+phase,false,overrides);
      for(const run of runs) await verifyLayers(run,phase,phase==='execution-disabled');
      assert.equal(existsSync(path.join(directory,'CALLS')),false,'Reads may not enter production engine');
      assert.deepEqual(dbSnapshot(),beforeReads,'Mining/result/publication/Combo tables unchanged; Auth writes excluded');
      record(phase+': real fresh JVM, all maps/pages/details/UI/export/provider and historical snapshots; no engine entry or result write');
    }
    directory=await startBatch('final-export-budget',false,{'wdm.execution.enabled':false,'wdm.result-publication.enabled':false,'wdm.result-query.max-export-bytes':256});
    const tooLarge=await ctx.request.get(origin+base+'/'+reference.id+'/patterns/export?view=published',{headers:headers()});await body(tooLarge,413);assert.equal(tooLarge.headers()['content-disposition'],undefined);record('Whole export budget fails before attachment; no truncation');
    directory=await startBatch('final-staging',false,{'wdm.result-publication.enabled':false});
    const staged=await create(tiny,'WSPM_PRETREE','ABSOLUTE',0.1,false);
    await until(async()=> (await get(base+'/'+staged.id)).status==='SUCCEEDED','Staging terminal');
    const staging=await get(base+'/'+staged.id+'/results');assert.equal(staging.publishedReadable,false);assert.equal(staging.publicationStatus,'NOT_PUBLISHED');
    await body(await ctx.request.get(origin+base+'/'+staged.id+'/patterns?view=published',{headers:headers()}),409);await provider(staged.id,'INVALID_RESOURCE_STATE');record('SUCCEEDED without publication remains unavailable for published reads/provider');
    directory=await startBatch('final-corruption');
    const mutations={
      support:id=>`UPDATE PATTERN SET support_abs=support_abs+1 WHERE run_id='${id}' LIMIT 1;`,
      temporal:id=>`UPDATE PATTERN_ITEM SET itemset_order=itemset_order+10 WHERE pattern_id=(SELECT pattern_id FROM PATTERN WHERE run_id='${id}' AND itemset_count>1 LIMIT 1) ORDER BY itemset_order DESC,item_order DESC LIMIT 1;`,
      removed:id=>`DELETE i FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id WHERE p.run_id='${id}';`,
      added:id=>`INSERT INTO PATTERN_ITEM(pattern_id,itemset_order,item_order,item_id) SELECT pattern_id,100,1,1 FROM PATTERN WHERE run_id='${id}' LIMIT 1;`,
      snapshot:id=>`UPDATE RUN_ITEM_STATISTIC SET product_name_snapshot='Corrupt snapshot' WHERE run_id='${id}';`,
      provenance:id=>`UPDATE RUN_PROVENANCE SET provenance_version='CORRUPT',provenance_json=JSON_SET(provenance_json,'$.version','CORRUPT') WHERE run_id='${id}';`,
      manifest:id=>`UPDATE RUN_RESULT_PUBLICATION SET manifest_version='UNSUPPORTED' WHERE run_id='${id}';`,
      digest:id=>`UPDATE RUN_RESULT_PUBLICATION SET result_sha256=REPEAT('0',64) WHERE run_id='${id}';`
    };
    for(const [name,mutate] of Object.entries(mutations)) {
      evidence.activeControl=name;save();
      // Fresh real run per mutation: never repair a corrupt publication to make it readable.
      const run=await create(tiny,'WSPM_PRETREE','ABSOLUTE',0.1,false);await published(run.id);
      const detail=sql(`SELECT pattern_id FROM PATTERN WHERE run_id='${run.id}' LIMIT 1;`);sql(mutate(run.id));
      const suffixes=name==='manifest'?['/patterns?view=published','/patterns/'+detail+'?view=published','/patterns/export?view=published']:['/results','/patterns?view=published','/patterns/'+detail+'?view=published','/patterns/export?view=published'];
      for(const suffix of suffixes) await body(await ctx.request.get(origin+base+'/'+run.id+suffix,{headers:headers()}),409);
      if(name==='manifest'){const m=await get(base+'/'+run.id+'/results');assert.equal(m.publishedReadable,false);assert.equal(m.completeness,'UNVERIFIED');}
      await provider(run.id,'INVALID_RESOURCE_STATE');evidence.negativeControls.push({name,runId:run.id,layer:'server manifest verification',status:'PASS',restored:false});save();
    }
    record('DB corruption: support/temporal/remove/add/snapshot/provenance/manifest/digest rejected; no repair');
    // Edge network throttling changes timing only; every HTTP response still comes from production.
    for (const action of ['run-change','logout','role-loss']) {
      const isolated=await browser.newContext({ignoreHTTPSErrors:true,acceptDownloads:true});
      try {
        const p=await isolated.newPage();await p.goto(origin+'/admin/mining-runs');
        await p.getByLabel('Tên tài khoản',{exact:false}).fill(username);await p.getByLabel('Mật khẩu',{exact:false}).fill(password);
        await p.getByRole('button',{name:'Đăng nhập',exact:true}).click();
        await p.getByRole('heading',{name:'Từ dữ liệu đơn hàng đến combo sản phẩm',exact:true}).waitFor();
        await p.goto(origin+'/admin/patterns?runId='+reference.id+'&page=1&view=published');
        await p.getByRole('table',{name:'Pattern đã công bố'}).waitFor();
        let downloads=0;p.on('download',()=>downloads++);
        const cdp=await isolated.newCDPSession(p);await cdp.send('Network.enable');
        await cdp.send('Network.emulateNetworkConditions',{offline:false,latency:2000,downloadThroughput:128,uploadThroughput:1000000});
        const sent=p.waitForRequest(r=>r.url().includes('/patterns/export?view=published'));
        const receiving=p.waitForResponse(r=>r.url().includes('/patterns/export?view=published'));
        const aborted=p.waitForEvent('requestfailed',{predicate:r=>r.url().includes('/patterns/export?view=published')});
        await p.getByRole('button',{name:'Tải JSON đã công bố',exact:true}).click();await sent;
        assert.equal((await receiving).status(),200,'Authorized real export is receiving its body before invalidation');
        if(action==='run-change') {
          await p.getByLabel('Run ID',{exact:true}).fill(runs[1].id);await p.getByRole('button',{name:'Mở kết quả',exact:true}).click();
        } else if(action==='logout') await p.getByTitle('Đăng xuất',{exact:true}).click();
        else {
          sql(`DELETE FROM USER_ROLE WHERE user_id='${user.userId}' AND role_id='admin-role';`);
          // The second real read receives 403 and triggers the shared session's protected-request abort.
          await p.getByRole('button',{name:'Làm mới danh sách',exact:true}).click();
          await p.getByText('Tài khoản đã đăng nhập nhưng không có quyền ADMIN hoặc máy chủ đã từ chối quyền truy cập.',{exact:true}).waitFor();
        }
        await aborted;await cdp.send('Network.emulateNetworkConditions',{offline:false,latency:0,downloadThroughput:-1,uploadThroughput:-1});
        if(action==='run-change') {
          assert.equal(new URL(p.url()).searchParams.get('runId'),runs[1].id);
          await p.getByText('Chưa thể đọc pattern staging',{exact:true}).waitFor();
        }
        else await until(()=>p.getByRole('table',{name:'Pattern đã công bố'}).count().then(n=>n===0),'Protected data cleared');
        await delay(250);assert.equal(downloads,0,'No late protected download');assert.equal(await p.getByText('Đã gửi tệp JSON để trình duyệt tải xuống.',{exact:true}).count(),0);
        await p.screenshot({path:path.join(logDirectory,'security-'+action+'.png'),fullPage:true});record('Real UI '+action+': in-flight export aborted, no late download, protected result cleared');
      } finally {await isolated.close();}
    }
    if(process.env.WDM_ENGINE_REGRESSION==='1') {
      // Do not run the reactor against intentionally corrupted browser fixtures.
      // sql() verifies the current invocation's ownership-labelled container.
      sql('CREATE DATABASE wdm0612_regression CHARACTER SET utf8mb4;');
      const config={...backendConfiguration,'spring.datasource.url':backendConfiguration['spring.datasource.url'].replace(/\/wdm0510_gate$/, '/wdm0612_regression'),
        'wdm.execution.enabled':false,'wdm.result-publication.enabled':false};
      const stream=createWriteStream(path.join(logDirectory,'REGRESSION.log'));
      const child=spawn(process.platform==='win32'?'cmd.exe':'mvn',process.platform==='win32'?['/d','/c','mvn -B -pl backend -am test']:['-B','-pl','backend','-am','test'],{cwd:root,windowsHide:true,env:{...process.env,SPRING_APPLICATION_JSON:JSON.stringify(config)},stdio:['ignore','pipe','pipe']});
      child.stdout.pipe(stream);child.stderr.pipe(stream);const exit=await new Promise((resolve,reject)=>{child.once('exit',resolve);child.once('error',reject);});await new Promise(r=>stream.end(r));assert.equal(exit,0,'Full regression failed');evidence.regression={status:'PASS',exitCode:exit,log:'REGRESSION.log',schema:'wdm0612_regression (fresh schema in invocation-owned container)'};
    }
    evidence.status='BROWSER_PASS_REVIEW_PENDING';save();console.log('PASS WDM-06.12 browser cases; full AC review and owner acceptance separate');
  } catch(error) {
    evidence.status='FAIL';evidence.failure={type:error.name,message:error.name==='AssertionError'?error.message:'Gate operation failed; inspect safe failure location and screenshot',stack:(error.stack||'').split('\n').filter(l=>l.trim().startsWith('at ')).slice(0,5)};
    await page.screenshot({path:path.join(logDirectory,'failure.png'),fullPage:true}).catch(()=>{});save();throw error;
  } finally {await ctx.close();}
}
