import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, createWriteStream, existsSync } from 'node:fs';
import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import path from 'node:path';

const base='/api/v1/admin/mining-runs', datasets='/api/v1/admin/dataset-snapshots';
async function until(check,message) {
  const deadline=Date.now()+60000;
  do {if(await check()) return; await new Promise(r=>setTimeout(r,100));} while(Date.now()<deadline);
  throw new Error(message);
}
export async function runPublicationCases({browser,origin,sql,root,logDirectory,startBatch,initialDirectory,backendConfiguration}) {
  const ctx=await browser.newContext({ignoreHTTPSErrors:true,acceptDownloads:true});ctx.setDefaultTimeout(20000);
  const evidence={gate:'WDM-06.11',status:'RUNNING',cases:[],runs:[]};
  const save=()=>writeFileSync(path.join(logDirectory,'RESULT.json'),JSON.stringify(evidence,null,2));
  const password=randomBytes(18).toString('base64url'),username='publication.admin';
  let token;
  let activePage;
  const auth=()=>({Authorization:'Bearer '+token});
  async function body(response,status=200) {
    assert.equal(response.status(),status);const v=await response.json();assert.equal(v.requestId,response.headers()['x-request-id']);
    assert.ok(response.headers()['cache-control'].includes('no-store'));return v.data;
  }
  async function login(page) {
    await page.goto(origin+'/admin/mining-runs');
    await page.getByLabel('Tên tài khoản',{exact:false}).fill(username);await page.getByLabel('Mật khẩu',{exact:false}).fill(password);
    const arrived=page.waitForResponse(r=>r.url().endsWith('/api/v1/auth/login')&&r.request().method()==='POST');
    await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();token=(await body(await arrived)).accessToken;
    await page.getByRole('heading',{name:'Từ dữ liệu đơn hàng đến combo sản phẩm',exact:true}).waitFor();
  }
  async function published(id) {
    await until(async()=> (await body(await ctx.request.get(origin+base+'/'+id+'/results',{headers:auth()}))).publishedReadable,'publication timeout');
    await until(async()=> (await body(await ctx.request.get(origin+'/api/v1/admin/mining-execution/capacity',{headers:auth()}))).snapshot.used===0,'capacity release timeout');
  }
  try {
    const csrf=await body(await ctx.request.get(origin+'/api/v1/auth/csrf'));
    const user=await body(await ctx.request.post(origin+'/api/v1/auth/register',{headers:{Origin:origin,'X-XSRF-TOKEN':csrf.token},
      data:{username,email:'publication.admin@example.test',password,displayName:'Synthetic Publication Gate'}}),201);
    assert.match(user.userId,/^[0-9a-f-]{36}$/);sql(`INSERT INTO USER_ROLE(user_id,role_id) VALUES('${user.userId}','admin-role');`);
    sql("INSERT INTO CATEGORY(category_id,name,slug) VALUES('publication-category','Synthetic','publication-category');");
    const resources=path.join(root,'backend/src/test/resources/wdm/csv-v1');
    for(const product of JSON.parse(readFileSync(path.join(resources,'round-trip-expected.json'),'utf8')).catalogProducts) {
      assert.match(product,/^rt-[a-z0-9]+$/);
      sql(`INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES('${product}','${product}','Synthetic','${product}','publication-category',999);`);
    }
    const page=await ctx.newPage();activePage=page;await login(page);
    const upload=await body(await ctx.request.post(origin+datasets,{headers:auth(),multipart:{file:{name:'publication.csv',mimeType:'text/csv',buffer:readFileSync(path.join(resources,'round-trip.csv'))}}}),201);
    const snapshotId=upload.snapshotId;
    await body(await ctx.request.post(origin+datasets+'/'+snapshotId+'/validation',{headers:auth()}));
    await body(await ctx.request.post(origin+datasets+'/'+snapshotId+'/conversion',{headers:auth()}));
    let first, referenceMap;
    for(const [index,algorithm] of ['WSPM_PRETREE','WSPM_BITPRUNE'].entries()) {
      const run=await body(await ctx.request.post(origin+base,{headers:auth(),data:{snapshotId,algorithm,minsupMode:'ABSOLUTE',minsupValue:.1}}),202);
      assert.match(run.runId,/^[0-9a-f-]{36}$/);await published(run.runId);first??=run.runId;
      await page.goto(origin+'/admin/mining-runs/'+run.runId);
      await page.getByText('Đã công bố và kiểm chứng',{exact:true}).waitFor();
      const download=page.waitForEvent('download');await page.getByRole('button',{name:'Tải JSON đã công bố',exact:true}).click();
      const file=await download,bytes=readFileSync(await file.path());writeFileSync(path.join(logDirectory,run.runId+'.json'),bytes);
      const doc=JSON.parse(bytes);assert.equal(doc.formatVersion,'WDM_RESULT_EXPORT_V2');assert.equal(doc.purpose,'COMBO_HANDOFF');
      assert.equal(doc.result.eligibleForCombo,true);assert.equal(doc.runItems.length,3);
      const actual=doc.patterns.map(p=>p.pattern.canonicalPattern+'\t'+p.pattern.supportAbs+'\n').sort().join('');
      if(referenceMap===undefined) referenceMap=actual; else assert.equal(actual,referenceMap,'Both engine full pattern/support maps must agree');
      assert.equal(actual,readFileSync(path.join(initialDirectory,'original-'+(index+1)+'.map.tsv'),'utf8'));
      const stored=sql(`SELECT canonical_pattern,support_abs FROM PATTERN WHERE run_id='${run.runId}' ORDER BY BINARY canonical_pattern;`);
      assert.equal(actual.trimEnd(),stored);
      await page.getByRole('link',{name:'Xem pattern đã công bố',exact:true}).click();await page.getByRole('table').waitFor();
      assert.ok(new URL(page.url()).searchParams.get('view')==='published');
      await page.getByRole('link',{name:/Xem chi tiết pattern/}).first().click();await page.getByRole('region',{name:'Tổng quan pattern'}).waitFor();
      assert.ok(new URL(page.url()).searchParams.get('view')==='published');
      evidence.runs.push({runId:run.runId,algorithm,patterns:doc.patterns.length,digest:doc.result.resultSha256});save();
    }
    evidence.cases.push('both original engine maps agree and equal DB and real browser downloads; published navigation; capacity released');
    // The third successful mining admission proves capacity recycling without restarting the executor.
    const third=await body(await ctx.request.post(origin+base,{headers:auth(),data:{snapshotId,algorithm:'WSPM_PRETREE',minsupMode:'ABSOLUTE',minsupValue:100000}}),202);
    await published(third.runId);
    const empty=await body(await ctx.request.get(origin+base+'/'+third.runId+'/results',{headers:auth()}));
    assert.equal(empty.storedPatternCount,'0');assert.equal(empty.eligibleForCombo,false);assert.equal(empty.publishedReadable,true);
    await page.goto(origin+'/admin/mining-runs/'+third.runId);await page.getByText('Đã kiểm chứng đầy đủ · Không có pattern để sinh Combo.',{exact:true}).waitFor();
    await page.screenshot({path:path.join(logDirectory,'publication-desktop.png'),fullPage:true});
    await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(logDirectory,'publication-mobile.png'),fullPage:true});
    evidence.cases.push('third admission and empty-result publication');
    const restarted=await startBatch('publication-restart');
    const after=await body(await ctx.request.get(origin+base+'/'+first+'/results',{headers:auth()}));
    assert.equal(after.publishedReadable,true);assert.equal(after.resultSha256,evidence.runs[0].digest);
    const saved=await ctx.request.get(origin+base+'/'+first+'/patterns/export?view=published',{headers:auth()});assert.equal(saved.status(),200);
    assert.deepEqual(await saved.json(),JSON.parse(readFileSync(path.join(logDirectory,first+'.json'),'utf8')));
    await page.reload();await page.getByText('Đã công bố và kiểm chứng',{exact:true}).waitFor();
    assert.equal(existsSync(path.join(restarted,'CALLS')),false,'Published reads must not enter an engine');
    evidence.cases.push('actual JVM restart: metadata, export and browser reads independent of Outcome');
    const anonymous=await browser.newContext({ignoreHTTPSErrors:true});
    try {assert.equal((await anonymous.request.get(origin+base+'/'+first+'/patterns/export?view=published')).status(),401);} finally {await anonymous.close();}
    sql(`UPDATE RUN_ITEM_STATISTIC SET product_name_snapshot='Corrupt' WHERE run_id='${first}';`);
    for(const suffix of ['/results','/patterns?view=published','/patterns/export?view=published'])
      assert.equal((await ctx.request.get(origin+base+'/'+first+suffix,{headers:auth()})).status(),409);
    evidence.cases.push('anonymous export denied and post-publication corruption rejected');
    if(process.env.WDM_ENGINE_REGRESSION==='1') {
      const configuration={...backendConfiguration,'wdm.execution.enabled':false,'wdm.result-publication.enabled':false,'spring.flyway.enabled':true};
      const stream=createWriteStream(path.join(logDirectory,'REGRESSION.log'));
      const child=spawn(process.platform==='win32'?'cmd.exe':'mvn',process.platform==='win32'?['/d','/c','mvn -B -pl backend -am test']:['-B','-pl','backend','-am','test'],
        {cwd:root,windowsHide:true,env:{...process.env,SPRING_APPLICATION_JSON:JSON.stringify(configuration)},stdio:['ignore','pipe','pipe']});
      child.stdout.pipe(stream);child.stderr.pipe(stream);
      const exit=await new Promise((resolve,reject)=>{child.once('exit',resolve);child.once('error',reject);});
      await new Promise(resolve=>stream.end(resolve));assert.equal(exit,0,'Full reactor regression failed; inspect REGRESSION.log');
      evidence.regression={status:'PASS',log:'REGRESSION.log'};
    }
    evidence.status='PASS';save();console.log('PASS WDM-06.11 publication browser cases');
  } catch(error) {
    evidence.status='FAIL';evidence.error=error.name;
    evidence.failureLocation=(error.stack||'').split('\n').filter(line=>line.trim().startsWith('at ')).slice(0,4);
    if(activePage) await activePage.screenshot({path:path.join(logDirectory,'failure.png'),fullPage:true}).catch(()=>{});
    save();throw error;
  }
  finally {await ctx.close();}
}
