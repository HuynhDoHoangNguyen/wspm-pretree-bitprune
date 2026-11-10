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
      // Choose semantic cases explicitly; random generated IDs must not decide which
      // kind is rejected (the sole sequential fixture must remain publishable).
      const approveId=listing.items.find(x=>x.data.comboKind==='SEQUENTIAL_CROSS_SELL').data.comboId;
      const rejectId=listing.items.find(x=>x.data.comboKind==='MIXED_SEQUENCE').data.comboId;
      const combos=[approveId,rejectId,...listing.items.map(x=>x.data.comboId).filter(id=>id!==approveId&&id!==rejectId)];
      evidence.runs.push({run,algorithm,batch:result.batchId,groups:result.storedGroups,policyVersion:preview.policyVersion,requiredQuantities:{keyboard:2,bag:3},protectedDigest:before});save();
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
    const first=evidence.runs[0];await startBatch('native-restart',false,{'wdm.execution.enabled':false});
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
    // TV3 handover: real publication, public catalog and the separate history contract.
    const current = await get(base+'/combos?batchId='+first.batch+'&size=100&page=0');
    const sameOrder = current.items.find(x => x.data.comboKind === 'SAME_ORDER_BUNDLE'
      && ['DRAFT','APPROVED'].includes(x.data.comboStatus));
    assert.ok(sameOrder, 'A publishable same-order candidate must exist');
    const selected = await get(base+'/combos/'+sameOrder.data.comboId);
    if (selected.data.comboStatus === 'DRAFT') await post(base+'/combos/'+selected.data.comboId+'/review',
      {decision:'APPROVE',reviewNote:'TV3 publication gate',expectedReviewToken:selected.reviewToken});
    for (const entry of current.items.filter(x => x.data.comboStatus === 'DRAFT' && x.data.comboId !== selected.data.comboId)) {
      const detail = await get(base+'/combos/'+entry.data.comboId);
      await post(base+'/combos/'+entry.data.comboId+'/review',
        {decision:'APPROVE',reviewNote:'Client three-kind gate',expectedReviewToken:detail.reviewToken});
    }
    assert.equal((await ctx.request.get(origin+'/api/v1/recommendations/combos/'+selected.data.comboId)).status(),404);
    await page.goto(origin+'/admin/combos?batchId='+first.batch);
    const publication = page.waitForResponse(r => r.url().endsWith('/batches/'+first.batch+'/publication') && r.request().method() === 'POST');
    await page.getByRole('button',{name:'Công bố batch cho khách hàng',exact:true}).click();
    const published = await data(await publication); assert.ok(published.publishedCount > 0);
    assert.equal(sql("SELECT COUNT(*) FROM COMBO_BATCH WHERE status='ACTIVE';"),'1');
    assert.equal(sql(`SELECT COUNT(*) FROM COMBO WHERE batch_id='${first.batch}' AND status='PUBLISHED' AND (reviewed_at IS NULL OR reviewed_by IS NULL);`),'0');
    assert.equal(protectedDigest(first.run),first.protectedDigest);
    const product = selected.itemsets[0].items.find(i => i.anchorEligible).productId;
    const publicContext = await browser.newContext({ignoreHTTPSErrors:true});
    try {
      const top = await data(await publicContext.request.get(origin+'/api/v1/recommendations/combos?productId='+product));
      assert.ok(top.items.some(i => i.combo.comboId === selected.data.comboId));
      const customerPage = await publicContext.newPage();
      await customerPage.goto(origin+'/products/'+product);
      await customerPage.getByRole('heading',{name:'Combo gợi ý cho sản phẩm',exact:true}).waitFor();
      const card = customerPage.getByRole('article').filter({has:customerPage.getByRole('heading',{name:selected.data.name,exact:true})});
      await card.waitFor();
      await customerPage.screenshot({path:path.join(logDirectory,'tv3-product-top-k.png'),fullPage:true});
      const comboResponse = customerPage.waitForResponse(r => r.url().endsWith('/api/v1/recommendations/combos/'+selected.data.comboId));
      await card.getByRole('link',{name:'Xem chi tiết Combo',exact:true}).click();
      await data(await comboResponse);
      await customerPage.getByText(selected.data.name,{exact:true}).first().waitFor();
      await customerPage.screenshot({path:path.join(logDirectory,'tv3-public-combo.png'),fullPage:true});
      assert.equal((await publicContext.request.get(origin+'/api/v1/recommendations/combos/personalized')).status(),401);
      const latest = await data(await publicContext.request.get(origin+'/api/v1/recommendations/combos/latest'));
      assert.equal(latest.length,2);
      assert.equal((await get('/api/v1/recommendations/combos/personalized')).items.length,0);
      // An actual public temporal result supplies membership, never a SQL-seeded combo.
      const temporalId = sql(`SELECT combo_id FROM COMBO WHERE batch_id='${first.batch}' AND status='PUBLISHED' AND combo_kind='SEQUENTIAL_CROSS_SELL' ORDER BY combo_id LIMIT 1;`);
      assert.ok(temporalId);
      const temporal = await data(await publicContext.request.get(origin+'/api/v1/recommendations/combos/'+temporalId));
      assert.ok(temporal.items.some(i=>i.itemsetOrder===2));
      assert.equal(temporal.kind,'SEQUENTIAL_CROSS_SELL');
      const customerId=sql(`SELECT customer_id FROM CUSTOMER WHERE user_id='${users['native.customer']}';`);
      assert.ok(customerId);
      sql(`INSERT INTO ORDERS(order_id,order_number,customer_id,ordered_at,status,source) VALUES('client-history','client-history','${customerId}','2026-01-01','COMPLETED','SYNTHETIC_TEST');`);
      for(const item of temporal.items.filter(i=>i.itemsetOrder===1))
        sql(`INSERT INTO ORDER_ITEM(order_id,product_id,quantity,list_unit_price,line_total,sku_snapshot,product_name_snapshot) VALUES('client-history','${item.product.productId}',1,100,100,'TEST','TEST');`);
      const personalContext=await browser.newContext({ignoreHTTPSErrors:true});
      let releasePersonal = () => {};
      try {
        const personalPage=await personalContext.newPage();
        const personalToken=await login(personalPage,'native.customer');
        const resultResponse=await personalContext.request.get(origin+'/api/v1/recommendations/combos/personalized',{headers:{Authorization:'Bearer '+personalToken}});
        assert.equal(resultResponse.headers()['cache-control'],'no-store');
        const personal=await data(resultResponse);assert.equal(personal.policy,'HISTORY_PREFIX_V1');assert.ok(personal.items.length>0);
        assert.ok(personal.items.every(r=>r.matchedItemsets>=1&&r.suggestedProductIds.length>0));
        assert.equal((await personalContext.request.get(origin+'/api/v1/recommendations/combos/personalized?userId='+users['native.admin1'],{headers:{Authorization:'Bearer '+personalToken}})).status(),400);
        for(const width of [1440,390]) {
          await personalPage.setViewportSize({width,height:900});await personalPage.goto(origin+'/products');
          await personalPage.getByRole('heading',{name:'Gợi ý cho lần mua tiếp theo',exact:true}).waitFor();
          const region=personalPage.getByRole('region',{name:'Gợi ý theo lịch sử mua',exact:true});
          await region.getByRole('checkbox').first().waitFor();
          const check=region.getByRole('checkbox').first();await check.uncheck();assert.equal(await check.isChecked(),false);await check.check();
          await personalPage.getByText('Combo mới nhất',{exact:true}).waitFor();
          assert.equal(await personalPage.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
          await personalPage.screenshot({path:path.join(logDirectory,'client-personal-list-'+width+'.png'),fullPage:true});
          if(width===390)await personalPage.getByText('Bộ lọc sản phẩm',{exact:true}).click();
          await personalPage.getByLabel('Tên sản phẩm',{exact:true}).fill('native-keyboard');
          await until(async()=>new URL(personalPage.url()).searchParams.get('q')==='native-keyboard');
          await personalPage.reload();assert.equal(await personalPage.getByLabel('Tên sản phẩm',{exact:true}).inputValue(),'native-keyboard');
          const listing=personalPage.getByRole('list',{name:'Sản phẩm',exact:true});await listing.getByRole('article').first().waitFor();
          assert.equal(await listing.getByRole('article').count(),1);
        }
        const anchor=temporal.items.find(i=>i.itemsetOrder===1).product.productId;
        const detailPersonal=await data(await personalContext.request.get(origin+'/api/v1/recommendations/combos/personalized?productId='+anchor,{headers:{Authorization:'Bearer '+personalToken}}));
        assert.ok(detailPersonal.items.length>0,'Product-context history read must match before K');
        const detailResponse=await data(await personalContext.request.get(origin+'/api/v1/products/'+anchor));
        writeFileSync(path.join(logDirectory,'client-detail-read.json'),JSON.stringify({product:detailResponse,personal:detailPersonal},null,2));
        const contextRead=personalPage.waitForResponse(r=>r.url().includes('/api/v1/recommendations/combos/personalized?productId='));
        await personalPage.goto(origin+'/products/'+anchor);
        const contextBody=await data(await contextRead);
        assert.ok(contextBody.items.length>0);
        await personalPage.screenshot({path:path.join(logDirectory,'client-detail-before-ready.png'),fullPage:true});
        await personalPage.getByTestId('product-combos').getByRole('checkbox').first().waitFor();
        const detailCards=personalPage.getByTestId('product-combos');
        await personalPage.getByTestId('product-information').getByTestId('product-combos').waitFor();
        assert.ok(await detailCards.getByRole('heading',{level:3}).count()<=2);
        assert.equal(await personalPage.getByRole('heading',{name:'Gợi ý cho lần mua tiếp theo',exact:true}).count(),0);
        await personalPage.setViewportSize({width:1440,height:900});
        const photo=await personalPage.getByRole('group',{name:'Ảnh chính',exact:true}).boundingBox();
        const detailBox=await detailCards.boundingBox();assert.ok(detailBox.x>photo.x+photo.width);
        await personalPage.screenshot({path:path.join(logDirectory,'client-detail-right-1440.png'),fullPage:true});
        await personalPage.setViewportSize({width:390,height:900});
        await personalPage.getByRole('heading',{name:'Sản phẩm liên quan',exact:true}).waitFor();
        await personalPage.screenshot({path:path.join(logDirectory,'client-personal-detail.png'),fullPage:true});
        // Hold a real authorized backend response, log out, then deliver the stale response.
        const held=new Promise(r=>{releasePersonal=r;});let captured;
        const fetched=new Promise(r=>{captured=r;});
        await personalPage.route('**/api/v1/recommendations/combos/personalized*',async route=>{
          const response=await route.fetch();captured();await held;
          try {await route.fulfill({response});} catch { /* Navigation/abort may discard the request. */ }
        });
        await personalPage.reload();await fetched;
        await personalPage.getByRole('button',{name:'Tài khoản',exact:true}).click();
        await personalPage.getByRole('menuitem',{name:'Đăng xuất',exact:true}).click();releasePersonal();
        await until(async()=>!((await personalPage.locator('body').innerText()).includes('Dựa trên lịch sử mua:')));
        await personalPage.unroute('**/api/v1/recommendations/combos/personalized*');
        pass('Client history: real Auth/COMPLETED orders, next-step checkboxes, filters persist, desktop/mobile, held-response logout');
      } catch(error) {
        const failedPage=personalContext.pages()[0];
        if(failedPage) {
          await failedPage.screenshot({path:path.join(logDirectory,'client-failure.png'),fullPage:true});
          writeFileSync(path.join(logDirectory,'client-failure.txt'),await failedPage.locator('body').innerText());
        }
        throw error;
      } finally {releasePersonal();await personalContext.close();}
      assert.equal(sql(`SELECT COUNT(*) FROM COMBO WHERE combo_id='${temporalId}' AND (valid_from IS NOT NULL OR valid_to IS NOT NULL);`),'0');
      try {
        sql(`UPDATE COMBO SET valid_to='2000-01-01' WHERE combo_id='${temporalId}';`);
        assert.equal((await publicContext.request.get(origin+'/api/v1/recommendations/combos/'+temporalId)).status(),404);
        assert.ok(!(await data(await publicContext.request.get(origin+'/api/v1/recommendations/combos/latest'))).some(c=>c.comboId===temporalId));
        sql(`UPDATE COMBO_BATCH SET status='ARCHIVED' WHERE batch_id='${first.batch}';`);
        assert.deepEqual(await data(await publicContext.request.get(origin+'/api/v1/recommendations/combos/latest')),[]);
        assert.equal((await data(await publicContext.request.get(origin+'/api/v1/recommendations/combos?productId='+product))).items.length,0);
        assert.equal((await get('/api/v1/recommendations/combos/personalized')).items.length,0);
      } finally {
        sql(`UPDATE COMBO SET valid_to=NULL WHERE combo_id='${temporalId}'; UPDATE COMBO_BATCH SET status='ACTIVE' WHERE batch_id='${first.batch}';`);
      }
      pass('Expired and archived publication disappears from real public/latest/personal reads; isolated mutations restored');
    } finally { await publicContext.close(); }
    pass('Admin manually publishes verified APPROVED kinds; anonymous Top-K/latest/detail; draft hidden and personal API protected');
    const history = await get(base+'/mining-run-history?page=0&size=20');
    assert.ok(history.items.some(r=>r.runId===first.run));
    const historyDetail = await get(base+'/mining-run-history/'+first.run);
    assert.equal(historyDetail.runId,first.run);assert.equal(historyDetail.snapshotId,snapshot);
    const status = await get(base+'/mining-runs/'+first.run);
    assert.equal(status.status,'SUCCEEDED');assert.ok(status.configuration);
    for (const route of ['mining-run-history','mining-monitoring','mining-comparison']) {
      await page.goto(origin+'/admin/'+route);
      if (route === 'mining-comparison') await page.getByLabel('Biểu đồ số liệu RUN đã lưu').waitFor();
      else await page.getByText(first.run,{exact:true}).first().waitFor();
      await page.screenshot({path:path.join(logDirectory,'tv3-'+route+'.png'),fullPage:true});
    }
    pass('TV3 history monitoring and source charts consume real persisted RUNs; WDM status contract remains separate');
    // Three reproducible three-product examples, generated by the production pipeline.
    await startBatch('three-items',false,{'wdm.execution.enabled':true});
    const threeBytes=readFileSync(path.join(root,'backend/src/test/resources/wdm/csv-v1/combo-three-items.csv'));
    const threeSnapshot=(await data(await ctx.request.post(origin+base+'/dataset-snapshots',{headers:headers(),
      multipart:{file:{name:'combo-three-items.csv',mimeType:'text/csv',buffer:threeBytes}}}),201)).snapshotId;
    await post(base+'/dataset-snapshots/'+threeSnapshot+'/validation');
    await post(base+'/dataset-snapshots/'+threeSnapshot+'/conversion');
    const threeRun=(await data(await ctx.request.post(origin+base+'/mining-runs',{headers:headers(),
      data:{snapshotId:threeSnapshot,algorithm:'WSPM_PRETREE',minsupMode:'ABSOLUTE',minsupValue:.1}}),202)).runId;
    await until(async()=>{const result=await get(base+'/mining-runs/'+threeRun+'/results');return result.publishedReadable;});
    const threePreview=await post(base+'/combo-generation/preview',{runId:threeRun,anchorProductId:null,k:20,requestIdentity:'three-products-demo',
      categorySelection:{requiredCategories:[categories[1][0],categories[2][0],categories[3][0]].map(categoryId=>({categoryId,quantity:1})),minDistinctProducts:3,maxDistinctProducts:3}});
    assert.equal(threePreview.status,'READY');
    assert.ok(threePreview.proposals.every(p=>new Set(p.items.map(i=>i.productId)).size===3));
    const expectedKinds=['MIXED_SEQUENCE','SAME_ORDER_BUNDLE','SEQUENTIAL_CROSS_SELL'];
    assert.deepEqual([...new Set(threePreview.proposals.map(p=>p.comboKind))].sort(),expectedKinds);
    const threeStored=await post(base+'/combo-generation/submit',{contextToken:threePreview.contextToken});
    assert.equal(threeStored.status,'INSERTED');
    const threeCombos=await get(base+'/combos?batchId='+threeStored.batchId+'&page=0&size=100');
    for(const entry of threeCombos.items) {
      const detail=await get(base+'/combos/'+entry.data.comboId);
      await post(base+'/combos/'+entry.data.comboId+'/review',{decision:'APPROVE',reviewNote:'Three-product tutorial',expectedReviewToken:detail.reviewToken});
    }
    await post(base+'/combos/batches/'+threeStored.batchId+'/publication');
    const examples=[];
    for(const kind of expectedKinds) {
      const entry=threeCombos.items.find(c=>c.data.comboKind===kind);assert.ok(entry);
      const detail=await data(await ctx.request.get(origin+'/api/v1/recommendations/combos/'+entry.data.comboId));
      assert.equal(detail.items.length,3);assert.equal(detail.kind,kind);
      examples.push({kind,comboId:detail.comboId,pattern:detail.provenance.canonicalPattern,
        steps:detail.items.map(i=>({step:i.itemsetOrder,productId:i.product.productId}))});
    }
    evidence.threeItemExamples={snapshot:threeSnapshot,run:threeRun,batch:threeStored.batchId,csvSha256:sha(threeBytes),examples};save();
    const demo=await browser.newContext({ignoreHTTPSErrors:true});
    try {
      const demoPage=await demo.newPage();
      for(const width of [1440,390]) {
        await demoPage.setViewportSize({width,height:900});await demoPage.goto(origin+'/products/native-keyboard');
        const cards=demoPage.getByTestId('product-combos');await cards.getByRole('checkbox').first().waitFor();
        assert.equal(await cards.getByRole('heading',{level:3}).count(),2);
        const headingColor=await cards.getByRole('heading',{level:2}).evaluate(el=>getComputedStyle(el).color);
        assert.notEqual(headingColor,'rgb(0, 0, 0)','Combo heading must have a visible color accent');
        const totalColor=await cards.getByText(/Tổng giá đang chọn:/).first().evaluate(el=>getComputedStyle(el).color);
        assert.notEqual(totalColor,'rgb(0, 0, 0)','Selected total must have a visible price color');
        for(const card of await cards.locator(':scope > div > article').all())assert.equal(await card.getByRole('checkbox').count(),3);
        assert.equal(await demoPage.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
        await demoPage.screenshot({path:path.join(logDirectory,'three-item-detail-'+width+'.png'),fullPage:true});
      }
      await demoPage.goto(origin+'/products');
      const latest=demoPage.getByLabel('Combo mới nhất',{exact:true});await latest.getByRole('article').first().waitFor();
      assert.equal(await latest.getByRole('article').count(),2);
      for(const card of await latest.getByRole('article').all())for(const name of ['Current native-keyboard','Current native-mouse','Current native-bag'])
        assert.ok(await card.getByText(name,{exact:true}).count()>0);
      await demoPage.screenshot({path:path.join(logDirectory,'three-item-list.png'),fullPage:true});
      sql(`UPDATE COMBO_BATCH SET status='ARCHIVED' WHERE batch_id='${threeStored.batchId}';`);
      try {
        await demoPage.goto(origin+'/products/native-keyboard');
        await demoPage.getByRole('heading',{name:'Current native-keyboard',exact:true}).waitFor();
        await until(async()=>await demoPage.getByText('Đang tải Combo…',{exact:true}).count()===0);
        assert.equal(await demoPage.getByTestId('product-combos').count(),0);
        await demoPage.goto(origin+'/products');await demoPage.getByRole('heading',{name:'Danh sách sản phẩm',exact:true}).waitFor();
        await until(async()=>await demoPage.getByText('Đang tải combo mới nhất…',{exact:true}).count()===0);
        assert.equal(await demoPage.getByLabel('Combo mới nhất',{exact:true}).count(),0);
      } finally {sql(`UPDATE COMBO_BATCH SET status='ACTIVE' WHERE batch_id='${threeStored.batchId}';`);}
    } finally {await demo.close();}
    pass('Three-product same-order/sequential/mixed: real pipeline/review/publication, compact right-column cards, constituent sidebar rows, empty sections hidden');
    evidence.status='PASS';save();
  }catch(e){evidence.status='FAIL';evidence.failure=String(e.message).slice(0,400);save();throw e;}
  finally{await ctx.close();}
}
