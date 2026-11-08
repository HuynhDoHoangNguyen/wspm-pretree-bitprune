import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { randomBytes, createHash } from 'node:crypto';
import path from 'node:path';
const base='/api/v1/admin', sha=b=>createHash('sha256').update(b).digest('hex');
export async function runNativeComboCases({browser,origin,sql,root,logDirectory,startBatch,initialDirectory}) {
  const evidence={gate:'TV2_NATIVE_COMBO',status:'RUNNING',fixture:'SYNTHETIC_CSV_REAL_ENGINES_NATIVE_PUBLICATION_PRODUCTION_BUSINESS_POLICY',cases:[],runs:[]};
  const save=()=>writeFileSync(path.join(logDirectory,'RESULT.json'),JSON.stringify(evidence,null,2));
  const pass=name=>{evidence.cases.push({name,status:'PASS'});save();console.log('PASS Native Combo: '+name);};
  const password=randomBytes(18).toString('base64url');
  const ctx=await browser.newContext({ignoreHTTPSErrors:true});ctx.setDefaultTimeout(30000);
  const page=await ctx.newPage();let token;
  const headers=()=>({Authorization:'Bearer '+token,Origin:origin});
  async function data(response,status=200){assert.equal(response.status(),status,response.url()+': '+await response.text());return (await response.json()).data;}
  async function get(url){return data(await ctx.request.get(origin+url,{headers:headers()}));}
  async function post(url,body){return data(await ctx.request.post(origin+url,{headers:headers(),data:body}));}
  async function until(check){for(let n=0;n<300;n++){const x=await check();if(x)return x;await new Promise(r=>setTimeout(r,100));}throw Error('Readiness timeout');}
  const protectedDigest=run=>sql(`SELECT SHA2(CONCAT((SELECT GROUP_CONCAT(CONCAT_WS(':',pattern_id,canonical_pattern,support_abs,pattern_weight) ORDER BY pattern_id SEPARATOR '|') FROM PATTERN WHERE run_id='${run}'),(SELECT GROUP_CONCAT(CONCAT_WS(':',item_id,product_id,product_name_snapshot,sku_snapshot) ORDER BY item_id SEPARATOR '|') FROM RUN_ITEM_STATISTIC WHERE run_id='${run}'),(SELECT result_sha256 FROM RUN_RESULT_PUBLICATION WHERE run_id='${run}')),256);`);
  try {
    writeFileSync(path.join(initialDirectory,'RELEASE'),'native business gate does not delay mining');
    // Explicit approved category IDs, synthetic catalog; no mined Pattern/Combo rows are seeded.
    const categories=[['e94fd8e6-4e01-54d7-9872-73b19997722f','Laptop'],['8b877527-9862-571f-bf47-61cb515da304','Keyboard'],['56f90ed8-3291-5285-9e08-91b94aa88ecc','Mouse'],['9a453e78-cdaa-50b9-adf3-d6293ce7d1b4','Bag'],['5bd6a957-c0d1-5c73-a237-4fe41d161727','Speaker']];
    for(const [id,name]of categories)sql(`INSERT INTO CATEGORY(category_id,name,slug,status) VALUES('${id}','${name}','native-${name}','ACTIVE');`);
    for(const [i,p]of ['native-laptop','native-keyboard','native-mouse','native-bag'].entries())sql(`INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price,status) VALUES('${p}','${p}','Current ${p}','${p}','${categories[i][0]}',100,'ACTIVE');`);
    const csrf=await data(await ctx.request.get(origin+'/api/v1/auth/csrf'));
    const users={};for(const username of ['native.admin1','native.admin2','native.customer']){
      const r=await ctx.request.post(origin+'/api/v1/auth/register',{headers:{Origin:origin,'X-XSRF-TOKEN':csrf.token},data:{username,email:username+'@example.test',password,displayName:'Synthetic '+username}});
      const user=await data(r,201);users[username]=user.userId;
      if(username!=='native.customer')sql(`INSERT INTO USER_ROLE(user_id,role_id) SELECT '${user.userId}',role_id FROM ROLE WHERE code='ADMIN';`);
    }
    async function login(p,username){await p.goto(origin+'/admin/combos');await p.getByLabel('Tên tài khoản',{exact:false}).fill(username);await p.getByLabel('Mật khẩu',{exact:false}).fill(password);
      const response=p.waitForResponse(r=>r.url().endsWith('/api/v1/auth/login')&&r.request().method()==='POST');await p.getByRole('button',{name:'Đăng nhập',exact:true}).click();
      return (await data(await response)).accessToken;}
    token=await login(page,'native.admin1');pass('Shared Auth real Admin login; required-category catalog policy');
    const anonymous=await browser.newContext({ignoreHTTPSErrors:true});
    assert.equal((await anonymous.request.post(origin+base+'/combo-generation/preview',{data:{}})).status(),401);
    assert.equal((await anonymous.request.get(origin+base+'/combo-generation/category-options/10000000-0000-4000-8000-000000000001')).status(),401);await anonymous.close();
    const customerContext=await browser.newContext({ignoreHTTPSErrors:true});const customerCsrf=await data(await customerContext.request.get(origin+'/api/v1/auth/csrf'));
    const customer=await data(await customerContext.request.post(origin+'/api/v1/auth/login',{headers:{Origin:origin,'X-XSRF-TOKEN':customerCsrf.token},data:{username:'native.customer',password}}));
    assert.equal((await customerContext.request.post(origin+base+'/combo-generation/preview',{headers:{Authorization:'Bearer '+customer.accessToken,Origin:origin},data:{}})).status(),403);
    assert.equal((await customerContext.request.get(origin+base+'/combo-generation/category-options/10000000-0000-4000-8000-000000000001',{headers:{Authorization:'Bearer '+customer.accessToken}})).status(),403);
    await customerContext.close();
    pass('Server generation command 401/403 before producer/approval parsing');
    const bytes=readFileSync(path.join(root,'backend/src/test/resources/wdm/csv-v1/native-combo.csv'));
    const snapshot=(await data(await ctx.request.post(origin+base+'/dataset-snapshots',{headers:headers(),multipart:{file:{name:'native-combo.csv',mimeType:'text/csv',buffer:bytes}}}),201)).snapshotId;
    await post(base+'/dataset-snapshots/'+snapshot+'/validation',undefined);await post(base+'/dataset-snapshots/'+snapshot+'/conversion',undefined);
    evidence.csvSha256=sha(bytes);pass('Real CSV acquisition validation conversion; no Pattern fixture inserts');
    for(const algorithm of ['WSPM_PRETREE','WSPM_BITPRUNE']){
      const accepted=await data(await ctx.request.post(origin+base+'/mining-runs',{headers:headers(),data:{snapshotId:snapshot,algorithm,minsupMode:'ABSOLUTE',minsupValue:.1}}),202);
      const run=accepted.runId;await until(async()=>{const m=await get(base+'/mining-runs/'+run+'/results');return m.publishedReadable&&m;});
      const before=protectedDigest(run);const count=sql('SELECT COUNT(*) FROM COMBO_BATCH;');
      const runOptions=await get(base+'/combo-generation/category-options/'+run);
      const keyboard=runOptions.categories.find(c=>c.categoryId===categories[1][0]);
      const bag=runOptions.categories.find(c=>c.categoryId===categories[3][0]);assert.ok(keyboard&&bag);
      await page.goto(origin+'/admin/mining-runs/'+run);await page.getByRole('link',{name:'Kiểm tra đầu vào Combo'}).click();
      await page.getByRole('heading',{name:'Xem đề xuất và lưu Combo',exact:true}).waitFor();
      const selectionInput=page.getByRole('searchbox',{name:'Danh mục bắt buộc trong Combo',exact:true});
      await selectionInput.fill(keyboard.name);await page.getByRole('option',{name:`${keyboard.name} (${keyboard.productCount} sản phẩm)`,exact:true}).click();
      await page.getByLabel('Số sản phẩm tối đa trong Combo',{exact:true}).fill('2');
      const firstPreview=page.waitForResponse(r=>r.url().endsWith('/combo-generation/preview'));
      await page.getByRole('button',{name:'Xem trước Combo',exact:true}).click();await data(await firstPreview);
      const anchorField=page.getByLabel('Sản phẩm neo (chỉ lọc xem trước)',{exact:true});
      await anchorField.waitFor();await anchorField.selectOption('');assert.equal(await anchorField.isDisabled(),false);
      await page.getByText(`${keyboard.name} (${keyboard.productCount} sản phẩm)`,{exact:true}).click();
      await selectionInput.fill(bag.name);await page.getByRole('option',{name:`${bag.name} (${bag.productCount} sản phẩm)`,exact:true}).click();
      assert.equal(await anchorField.isVisible(),true);assert.equal(await anchorField.isDisabled(),true);assert.equal(await anchorField.inputValue(),'');
      const revisedPreview=page.waitForResponse(r=>r.url().endsWith('/combo-generation/preview'));
      await page.getByRole('button',{name:'Xem trước Combo',exact:true}).click();await data(await revisedPreview);
      await anchorField.selectOption('');assert.equal(await anchorField.isDisabled(),false);
      await page.getByLabel('Số sản phẩm tối đa trong Combo',{exact:true}).fill('4');
      assert.equal(await anchorField.isVisible(),true);assert.equal(await anchorField.isDisabled(),true);
      await page.getByLabel('Số lượng mỗi sản phẩm · '+keyboard.name,{exact:true}).fill('2');
      await page.getByLabel('Số lượng mỗi sản phẩm · '+bag.name,{exact:true}).fill('3');
      await page.getByRole('button',{name:'Xem trước Combo',exact:true}).click();
      await page.getByLabel('Số đề xuất xem trước',{exact:true}).fill('1');
      const response=page.waitForResponse(r=>r.url().endsWith('/combo-generation/preview'));
      await page.getByRole('button',{name:'Xem trước Combo',exact:true}).click();const preview=await data(await response);
      assert.equal(preview.status,'READY');assert.equal(preview.topPatternIds.length,1);assert.ok(preview.groups>1);assert.equal(sql('SELECT COUNT(*) FROM COMBO_BATCH;'),count);
      assert.equal(preview.policyVersion,'TV2_SELECTED_CATEGORIES_V3');
      assert.equal(preview.proposals[0].items.find(i=>i.productId==='native-keyboard').quantity,2);
      assert.equal(preview.proposals[0].items.find(i=>i.productId==='native-bag').quantity,3);
      await page.setViewportSize({width:390,height:844});
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
      await page.screenshot({path:path.join(logDirectory,algorithm+'-selection-mobile.png'),fullPage:true});
      await page.setViewportSize({width:1440,height:900});
      const written=page.waitForResponse(r=>r.url().endsWith('/combo-generation/submit'));
      await page.getByRole('button',{name:`Lưu ${preview.groups} Combo DRAFT`,exact:true}).click();const result=await data(await written);
      assert.equal(result.status,'INSERTED');assert.equal(result.storedGroups,preview.groups);
      assert.equal((await post(base+'/combo-generation/submit',{contextToken:preview.contextToken})).status,'NO_OP');
      assert.equal((await post(base+'/combo-generation/reconcile',{contextToken:preview.contextToken})).status,'NO_OP');
      assert.equal(sql(`SELECT COUNT(*) FROM COMBO c WHERE c.batch_id='${result.batchId}' AND c.reference_original_price<>(SELECT SUM(i.quantity*i.unit_price_snapshot) FROM COMBO_ITEM i WHERE i.combo_id=c.combo_id);`),'0');
      assert.equal(sql(`SELECT COUNT(*) FROM COMBO_ITEM i JOIN COMBO c ON c.combo_id=i.combo_id WHERE c.batch_id='${result.batchId}' AND ((i.product_id='native-keyboard' AND i.quantity<>2) OR (i.product_id='native-bag' AND i.quantity<>3));`),'0');
      assert.equal(sql(`SELECT COUNT(*) FROM COMBO c WHERE c.batch_id='${result.batchId}' AND ((SELECT COUNT(*) FROM COMBO_ITEM i WHERE i.combo_id=c.combo_id AND i.product_id IN ('native-keyboard','native-bag'))<>2);`),'0');
      assert.ok(Number(sql(`SELECT COUNT(*) FROM COMBO c WHERE c.batch_id='${result.batchId}' AND NOT EXISTS(SELECT 1 FROM COMBO_ITEM i WHERE i.combo_id=c.combo_id AND i.product_id='native-laptop');`))>0);
      const kinds=sql(`SELECT DISTINCT combo_kind FROM COMBO WHERE batch_id='${result.batchId}' ORDER BY combo_kind;`).split(/\r?\n/);
      assert.deepEqual(kinds,['MIXED_SEQUENCE','SAME_ORDER_BUNDLE','SEQUENTIAL_CROSS_SELL']);assert.equal(protectedDigest(run),before);
      await page.getByRole('link',{name:'Mở batch đã lưu',exact:true}).click();await page.getByRole('heading',{name:'Quản lý Combo',exact:true}).waitFor();
      await page.getByText('Batch '+result.batchId,{exact:true}).waitFor();
      await page.screenshot({path:path.join(logDirectory,algorithm+'-generated.png'),fullPage:true});
      const listing=await get(base+'/combos?batchId='+result.batchId+'&size=20&page=0');assert.equal(Number(listing.filteredTotal),preview.groups);
      const combos=listing.items.map(x=>x.data.comboId);evidence.runs.push({run,algorithm,batch:result.batchId,groups:result.storedGroups,policyVersion:preview.policyVersion,requiredQuantities:{keyboard:2,bag:3},protectedDigest:before});save();
      pass(algorithm+': real engine/publication -> UI preview (no writes) -> all three kinds stored beyond K -> stable retry/reconcile');
      const detail=page.waitForResponse(r=>r.url().endsWith('/combos/'+combos[0]));await page.getByRole('button',{name:new RegExp('\\('+combos[0]+'\\)')}).click();await data(await detail);
      const approved=page.waitForResponse(r=>r.url().endsWith('/combos/'+combos[0]+'/review'));await page.getByRole('button',{name:'Duyệt',exact:true}).click();await page.getByRole('button',{name:'Xác nhận quyết định',exact:true}).click();
      assert.equal((await data(await approved)).outcome,'APPLIED');assert.equal(sql(`SELECT reviewed_by FROM COMBO WHERE combo_id='${combos[0]}';`),users['native.admin1']);assert.equal(protectedDigest(run),before);
      await page.reload();await page.getByRole('button',{name:new RegExp('\\('+combos[1]+'\\)')}).click();
      await page.getByRole('button',{name:'Từ chối',exact:true}).click();await page.getByLabel('Ghi chú review',{exact:true}).fill('Synthetic owner rejection');
      const rejected=page.waitForResponse(r=>r.url().endsWith('/combos/'+combos[1]+'/review'));await page.getByRole('button',{name:'Xác nhận quyết định',exact:true}).click();assert.equal((await data(await rejected)).outcome,'APPLIED');
      assert.equal(sql(`SELECT status FROM COMBO_BATCH WHERE batch_id='${result.batchId}';`),'DRAFT');assert.equal(protectedDigest(run),before);
      pass(algorithm+': UI approve/reject persists principal audit; batch stays DRAFT; protected mining unchanged');
      if(algorithm==='WSPM_PRETREE'){
        const c2=await browser.newContext({ignoreHTTPSErrors:true});const p2=await c2.newPage();const t2=await login(p2,'native.admin2');
        const d1=await get(base+'/combos/'+combos[2]);const d2=await data(await c2.request.get(origin+base+'/combos/'+combos[2],{headers:{Authorization:'Bearer '+t2}}));
        await post(base+'/combos/'+combos[2]+'/review',{decision:'APPROVE',reviewNote:null,expectedReviewToken:d1.reviewToken});
        const audit=sql(`SELECT CONCAT_WS(':',status,reviewed_by,reviewed_at) FROM COMBO WHERE combo_id='${combos[2]}';`);
        assert.equal((await c2.request.post(origin+base+'/combos/'+combos[2]+'/review',{headers:{Authorization:'Bearer '+t2,Origin:origin},data:{decision:'REJECT',reviewNote:'opposite',expectedReviewToken:d2.reviewToken}})).status(),409);
        const retry=await data(await c2.request.post(origin+base+'/combos/'+combos[2]+'/review',{headers:{Authorization:'Bearer '+t2,Origin:origin},data:{decision:'APPROVE',reviewNote:null,expectedReviewToken:d2.reviewToken}}));assert.equal(retry.outcome,'ALREADY_DECIDED');
        assert.equal(sql(`SELECT CONCAT_WS(':',status,reviewed_by,reviewed_at) FROM COMBO WHERE combo_id='${combos[2]}';`),audit);await c2.close();
        pass('Two independent Admin sessions: opposite conflict and same-decision retry preserve first audit');
      }
    }
    const first=evidence.runs[0];await startBatch('native-restart',{'wdm.execution.enabled':false});
    const metadata=await get(base+'/mining-runs/'+first.run+'/results');assert.equal(metadata.publishedReadable,true);
    const pageData=await get(base+'/combos?batchId='+first.batch+'&size=20&page=0');assert.equal(Number(pageData.filteredTotal),first.groups);
    assert.equal(protectedDigest(first.run),first.protectedDigest);pass('Fresh execution-disabled JVM preserves published results and generated/reviewed Combos');
    const draft=pageData.items.find(x=>x.data.comboStatus==='DRAFT');assert.ok(draft);
    const replay=await get(base+'/combos/'+draft.data.comboId);assert.deepEqual(replay.assessment.allowedActions,['APPROVE','REJECT']);
    assert.equal((await post(base+'/combos/'+draft.data.comboId+'/review',{decision:'APPROVE',reviewNote:'After restart',expectedReviewToken:replay.reviewToken})).outcome,'APPLIED');
    assert.equal(protectedDigest(first.run),first.protectedDigest);pass('Native signed policy/context/evidence reproduce across JVM restart before review');
    await page.setViewportSize({width:390,height:844});await page.goto(origin+'/admin/combos?batchId='+first.batch);
    await page.getByRole('heading',{name:'Quản lý Combo',exact:true}).waitFor();
    await page.getByRole('button',{name:new RegExp('\\('+pageData.items[0].data.comboId+'\\)')}).waitFor();
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    await page.screenshot({path:path.join(logDirectory,'native-combo-mobile.png'),fullPage:true});pass('Mobile 390px layout retains batch filtering with local table scrolling');
    for(const width of [1440,390]){
      await page.setViewportSize({width,height:900});
      const reads=[];const observe=r=>{if(r.url().includes('/api/v1/admin/combos'))reads.push(new URL(r.url()).pathname);};
      page.on('request',observe);await page.goto(origin+'/admin/combos');
      await page.getByRole('button',{name:'Mở batch '+first.batch,exact:true}).waitFor();page.off('request',observe);
      assert.ok(reads.includes('/api/v1/admin/combos/batches'));assert.ok(!reads.includes('/api/v1/admin/combos'));
      assert.ok(!reads.some(p=>p.endsWith('/combos')&&p!=='/api/v1/admin/combos'));
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
      await page.screenshot({path:path.join(logDirectory,'batch-history-'+width+'.png'),fullPage:true});
      await page.getByRole('button',{name:'Mở batch '+first.batch,exact:true}).click();
      await page.getByRole('button',{name:new RegExp('\\('+pageData.items[0].data.comboId+'\\)')}).waitFor();
    }
    pass('Batch-first desktop/mobile history loads summaries only; opens scoped Combo page on demand');
    evidence.status='PASS';save();
  }catch(e){evidence.status='FAIL';evidence.failure=String(e.message).slice(0,400);save();throw e;}
  finally{await ctx.close();}
}
