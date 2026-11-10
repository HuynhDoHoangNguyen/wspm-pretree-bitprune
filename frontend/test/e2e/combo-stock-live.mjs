import assert from 'node:assert/strict';
import {chromium} from 'playwright-core';
import {readFileSync,mkdirSync,mkdtempSync,writeFileSync} from 'node:fs';
import path from 'node:path';

// Read-only UI/API gate against an explicitly supplied owned demo runtime. No cart/order/stock writes.
assert.ok(process.env.COMBO_DEMO_RUNTIME_PATH,'Set COMBO_DEMO_RUNTIME_PATH to the demo runtime.private.json');
const runtime=JSON.parse(readFileSync(process.env.COMBO_DEMO_RUNTIME_PATH,'utf8'));
const origin=new URL(runtime.origin);assert.ok(['127.0.0.1','localhost'].includes(origin.hostname));
const base=path.resolve('.agent-workspace/combo-demo-20261111');mkdirSync(base,{recursive:true});
const evidence=mkdtempSync(path.join(base,'stock-ui-'));
const browser=await chromium.launch({channel:'msedge',headless:true});
const writes=[],results=[];
function total(price,quantity){
  const [whole,fraction='']=price.split('.'),scale=Math.max(2,fraction.length);
  const cents=(BigInt(whole+fraction.padEnd(scale,'0'))*BigInt(quantity)).toString().padStart(scale+1,'0');
  const integer=cents.slice(0,-scale).replace(/\B(?=(\d{3})+(?!\d))/g,'.'),tail=cents.slice(-scale).replace(/0+$/,'');
  return integer+(tail?','+tail:'')+' VND';
}
try{
  for(const width of [1440,390]){
    const context=await browser.newContext({ignoreHTTPSErrors:true,viewport:{width,height:950}});
    const page=await context.newPage();page.setDefaultTimeout(15000);
    context.on('request',request=>{if(!['GET','HEAD'].includes(request.method())&&!new URL(request.url()).pathname.startsWith('/api/v1/auth/'))writes.push(request.url());});
    const response=await context.request.get(runtime.origin+'/api/v1/recommendations/combos/latest');assert.equal(response.status(),200);
    const latestData=(await response.json()).data;assert.equal(latestData.length,5);
    assert.ok(latestData.every(c=>c.kind==='SAME_ORDER_BUNDLE'));
    assert.ok(latestData.flatMap(c=>c.items).every(i=>i.product.availableQuantity===null||/^(0|[1-9][0-9]*)$/.test(i.product.availableQuantity)));
    await page.goto(runtime.origin+'/products?page=1');
    await page.waitForLoadState('networkidle');
    const latest=page.getByLabel('Top Combo mới nhất',{exact:true});await latest.getByRole('article').nth(4).waitFor();
    assert.equal(await latest.getByRole('article').count(),5);
    for(let n=1;n<=5;n++)assert.equal(await latest.getByText('#'+n,{exact:false}).count(),1);
    assert.equal(await latest.getByRole('link',{name:'Xem chi tiết Combo',exact:true}).count(),5);
    const original=page.url();await latest.getByRole('link',{name:'Xem chi tiết Combo',exact:true}).first().click();
    let dialog=page.getByRole('dialog');await dialog.getByRole('checkbox').first().waitFor();
    assert.equal(page.url(),original);assert.equal(await dialog.getByRole('checkbox',{checked:true}).count(),0);
    await dialog.getByText('Tổng giá đang chọn: 0 VND',{exact:true}).waitFor();
    const chosen=latestData[0].items.find(i=>i.product.availableQuantity!==null&&BigInt(i.product.availableQuantity)>0);assert.ok(chosen);
    const checkbox=dialog.getByRole('checkbox',{name:'Chọn '+chosen.product.name,exact:true});
    const quantity=dialog.getByLabel('Số lượng '+chosen.product.name,{exact:true});
    assert.equal(await quantity.inputValue(),'1');assert.equal(await quantity.isDisabled(),true);
    await checkbox.check();assert.equal(await quantity.isEnabled(),true);
    assert.equal(await quantity.getAttribute('min'),'1');assert.equal(await quantity.getAttribute('max'),chosen.product.availableQuantity);
    await quantity.fill(chosen.product.availableQuantity);
    await dialog.getByText('Tổng giá đang chọn: '+total(chosen.product.currentPrice,chosen.product.availableQuantity),{exact:true}).waitFor();
    await quantity.fill((BigInt(chosen.product.availableQuantity)+BigInt(1)).toString());assert.equal(await quantity.inputValue(),chosen.product.availableQuantity);
    await quantity.fill('0');assert.equal(await quantity.inputValue(),chosen.product.availableQuantity);
    assert.equal(await dialog.getByRole('button',{name:'Thêm vào giỏ hàng',exact:true}).isDisabled(),true);
    assert.equal(await dialog.getByText('Sắp hỗ trợ',{exact:true}).count(),0);
    await page.screenshot({path:path.join(evidence,'popup-'+width+'.png'),fullPage:true});
    await dialog.getByRole('button',{name:'Đóng',exact:true}).click();
    await latest.getByRole('link',{name:'Xem chi tiết Combo',exact:true}).first().click();dialog=page.getByRole('dialog');
    await dialog.getByRole('checkbox').first().waitFor();assert.equal(await dialog.getByRole('checkbox',{checked:true}).count(),0);
    assert.equal(await dialog.getByLabel('Số lượng '+chosen.product.name,{exact:true}).inputValue(),'1');
    await Promise.all([page.waitForURL(runtime.origin+'/products/'+encodeURIComponent(chosen.product.productId)),dialog.getByRole('link',{name:chosen.product.name,exact:true}).click()]);
    const section=page.getByTestId('product-combos');await section.getByRole('article').first().waitFor();
    assert.equal(await section.getByRole('checkbox',{checked:true}).count(),0);
    const current=section.locator('[data-current-product="true"]').first();await current.waitFor();
    assert.equal(await current.getAttribute('aria-current'),'true');
    assert.equal(await current.locator('a').evaluate(el=>getComputedStyle(el.closest('.mantine-Group-root')).opacity),'1');
    assert.equal(await section.getByText(/🔥/).count(),0);
    const otherRows=await section.locator('[data-current-product="false"]').all();
    let changed=false;
    for(const row of otherRows){
      const check=row.getByRole('checkbox');
      assert.equal(await row.locator('a').evaluate(el=>getComputedStyle(el.closest('.mantine-Group-root')).opacity),'0.45');
      if(!changed&&await check.isEnabled()){
        await check.check();assert.equal(await row.getAttribute('data-selected'),'true');
        assert.equal(await row.locator('a').evaluate(el=>getComputedStyle(el.closest('.mantine-Group-root')).opacity),'1');
        await check.uncheck();assert.equal(await row.locator('a').evaluate(el=>getComputedStyle(el.closest('.mantine-Group-root')).opacity),'0.45');changed=true;
      }
    }
    assert.ok(changed,'Need one available suggested product to test brightness');
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    await page.screenshot({path:path.join(evidence,'context-'+width+'.png'),fullPage:true});
    const outOfStock=latestData.find(c=>c.items.some(i=>i.product.availableQuantity==='0'));
    if(outOfStock){
      await page.goto(runtime.origin+'/combos/'+encodeURIComponent(outOfStock.comboId));dialog=page.getByRole('dialog');
      const item=outOfStock.items.find(i=>i.product.availableQuantity==='0');await dialog.getByRole('checkbox',{name:'Chọn '+item.product.name,exact:true}).waitFor();
      assert.equal(await dialog.getByRole('checkbox',{name:'Chọn '+item.product.name,exact:true}).isDisabled(),true);
    }
    results.push({width,latest:5,unchecked:true,stockLimits:true,exactTotal:true,popupProductLink:true,contextAndBrightness:true,cartDisabled:true});
    await context.close();
  }
  assert.deepEqual(writes,[],'UI verification must not write business data');
  writeFileSync(path.join(evidence,'result.json'),JSON.stringify({status:'PASS',results,writes},null,2));
  console.log(JSON.stringify({status:'PASS',evidence,results}));
}finally{await browser.close();}
