import assert from 'node:assert/strict';
import {execFileSync,spawn,spawnSync} from 'node:child_process';
import {randomBytes,createHash} from 'node:crypto';
import {readFileSync,writeFileSync,mkdirSync,mkdtempSync,existsSync,createWriteStream} from 'node:fs';
import {createServer} from 'node:net';
import {createRequire} from 'node:module';
import path from 'node:path';

// Verifies the standalone SQL on a NEW labelled MySQL, never the owner's datasource.
const root=process.cwd(),require=createRequire(path.join(root,'frontend/test/e2e/package.json'));
const {request}=require('playwright-core');
const base=path.join(root,'.agent-workspace/seeder2-tests');mkdirSync(base,{recursive:true});
const work=mkdtempSync(path.join(base,'run-')),name='wspm-seeder2-'+randomBytes(6).toString('hex');
const password=randomBytes(24).toString('hex'),db='wspm_combo';
const jar=path.resolve(process.env.SEEDER2_BACKEND_JAR||process.env.COMBO_DEMO_JAR||'backend/target/backend-1.0-SNAPSHOT.jar');
assert.ok(existsSync(jar),'Package a backend JAR or set SEEDER2_BACKEND_JAR');
const seed=readFileSync('seeder2.sql','utf8'),catalog=JSON.parse(readFileSync('docs/demo/combo-demo/data/catalog-20.json','utf8'));
const q=value=>`CONVERT(0x${Buffer.from(String(value)).toString('hex')} USING utf8mb4)`;
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const evidence={status:'RUNNING',cases:[],fixture:'Standalone seeder2.sql on isolated MySQL 8.4'};
const save=()=>writeFileSync(path.join(work,'result.json'),JSON.stringify(evidence,null,2));
const pass=value=>{evidence.cases.push(value);save();console.log('PASS '+value);};
function command(executable,args,options={}){
  try{return execFileSync(executable,args,{cwd:root,encoding:'utf8',windowsHide:true,timeout:60000,maxBuffer:16*1024*1024,stdio:['pipe','pipe','pipe'],...options});}
  catch(error){throw Error(executable+' exit '+error.status+': '+String(error.stderr||'').slice(-2000));}
}
function owned(){const c=JSON.parse(command('docker',['inspect',name]))[0];assert.equal(c.Config.Labels['wspm-seeder2-owner'],name);assert.equal(c.Mounts.some(m=>m.Type==='bind'),false);return c;}
function sql(input,force=false){owned();return command('docker',['exec','-i','-e','MYSQL_PWD='+password,name,'mysql','-uroot','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names',...(force?['--force']:[]),db],{input}).trim();}
function rejectedSql(input,expected){
  owned();const result=spawnSync('docker',['exec','-i','-e','MYSQL_PWD='+password,name,'mysql','-uroot','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names','--force',db],{input,encoding:'utf8',windowsHide:true,timeout:60000,maxBuffer:16*1024*1024});
  assert.ok(result.stderr.includes(expected),'Expected SQL rejection: '+expected+'; received '+result.stderr.slice(-1800));
}
async function until(check,label,seconds=90){for(let n=0;n<seconds*4;n++){if(await check())return;await delay(250);}throw Error('Timeout '+label);}
async function port(){const server=createServer();await new Promise((yes,no)=>server.once('error',no).listen(0,'127.0.0.1',yes));const value=server.address().port;await new Promise(resolve=>server.close(resolve));return value;}
let backend,log,context,started=false;
try{
  console.log('Seeder2 evidence: '+work);
  command('docker',['run','--detach','--name',name,'--label','wspm-seeder2-owner='+name,'--env','MYSQL_ROOT_PASSWORD='+password,'--env','MYSQL_DATABASE='+db,'--publish','127.0.0.1::3306','mysql:8.4']);started=true;
  await until(()=>{try{return sql('SELECT 1;')==='1';}catch{return false;}},'MySQL',120);
  const mysqlPort=owned().NetworkSettings.Ports['3306/tcp'][0].HostPort,backendPort=await port(),origin='http://127.0.0.1:'+backendPort;
  const settings=spawnSync('java',['-XshowSettings:properties','-version'],{encoding:'utf8',windowsHide:true});assert.equal(settings.status,0);
  const javaHome=settings.stderr.match(/java.home\s*=\s*(.+)/)?.[1]?.trim();assert.ok(javaHome);
  const config={'debug':false,'spring.config.import':'','server.address':'127.0.0.1','server.port':backendPort,
    'spring.datasource.url':`jdbc:mysql://127.0.0.1:${mysqlPort}/${db}?allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`,
    'spring.datasource.username':'root','spring.datasource.password':password,'spring.flyway.baseline-on-migrate':false,
    'auth.jwt.secret':randomBytes(32).toString('base64'),'auth.jwt.issuer':origin,'auth.jwt.key-id':'seeder2-test','auth.cookie-secure':false,'auth.allowed-origins':origin,
    'wdm.execution.enabled':true,'wdm.result-publication.enabled':true};
  const {tmpdir}=await import('node:os');config['wdm.snapshot-storage.root']=mkdtempSync(path.join(tmpdir(),'wspm-seeder2-'));
  writeFileSync(path.join(work,'runtime.private.json'),JSON.stringify({name,password,db,origin,jar,storage:config['wdm.snapshot-storage.root']},null,2));
  log=createWriteStream(path.join(work,'backend.log'));
  backend=spawn(path.join(javaHome,'bin',process.platform==='win32'?'java.exe':'java'),['-jar',jar],{cwd:root,windowsHide:true,env:{...process.env,SPRING_APPLICATION_JSON:JSON.stringify(config)},stdio:['ignore','pipe','pipe']});backend.stdout.pipe(log);backend.stderr.pipe(log);
  await until(async()=>{assert.equal(backend.exitCode,null,'Backend exited');try{return(await fetch(origin+'/api/health')).ok;}catch{return false;}},'backend',120);
  assert.equal(sql('SELECT COUNT(*) FROM PRODUCT;'),'0','First seed must also work on a fresh migrated schema');
  const baseline=sql('CHECKSUM TABLE PRODUCT,PRODUCT_IMAGE,CATEGORY,USER_ACCOUNT,CUSTOMER,ORDERS,ORDER_ITEM,INVENTORY_BALANCE,ROLE,USER_ROLE;');
  const rollback=seed.replace('    DROP TEMPORARY TABLE s2_products,s2_users,s2_customers,s2_orders,s2_items,s2_stock;',"    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Seeder2 forced rollback test';\n    DROP TEMPORARY TABLE s2_products,s2_users,s2_customers,s2_orders,s2_items,s2_stock;");
  assert.notEqual(rollback,seed);rejectedSql(rollback,'Seeder2 forced rollback test');assert.equal(sql('CHECKSUM TABLE PRODUCT,PRODUCT_IMAGE,CATEGORY,USER_ACCOUNT,CUSTOMER,ORDERS,ORDER_ITEM,INVENTORY_BALANCE,ROLE,USER_ROLE;'),baseline);
  pass('Forced failure after inserts rolls back all application data; catalog unchanged');
  sql(seed);assert.equal(sql('SELECT COUNT(*) FROM PRODUCT; SELECT COUNT(*) FROM USER_ACCOUNT; SELECT COUNT(*) FROM CUSTOMER; SELECT COUNT(*) FROM ORDERS; SELECT COUNT(*) FROM ORDER_ITEM; SELECT COUNT(*) FROM INVENTORY_BALANCE;'),'20\n5\n46\n65\n94\n20');
  assert.equal(sql('SELECT (SELECT COUNT(*) FROM MINING_RUN)+(SELECT COUNT(*) FROM PATTERN)+(SELECT COUNT(*) FROM COMBO);'),'0');
  assert.equal(sql('SELECT product_id,sku FROM PRODUCT ORDER BY product_id;'),catalog.map(p=>p.productId+'\t'+p.sku).sort().join('\n'));
  const after=sql('CHECKSUM TABLE PRODUCT,PRODUCT_IMAGE,CATEGORY,USER_ACCOUNT,CUSTOMER,ORDERS,ORDER_ITEM,INVENTORY_BALANCE,ROLE,USER_ROLE;');sql(seed);assert.equal(sql('CHECKSUM TABLE PRODUCT,PRODUCT_IMAGE,CATEGORY,USER_ACCOUNT,CUSTOMER,ORDERS,ORDER_ITEM,INVENTORY_BALANCE,ROLE,USER_ROLE;'),after);
  pass('SQL seeds exact counts, fabricates no mined output, and a second run is a no-op');
  const wrongCatalog=seed.replace(q(catalog[0].sku),q('WRONG-SEEDER2-SKU'));assert.notEqual(wrongCatalog,seed);
  rejectedSql(wrongCatalog,'Seeder2 requires exactly the 20 existing catalog IDs/SKUs');
  assert.equal(sql('CHECKSUM TABLE PRODUCT,PRODUCT_IMAGE,CATEGORY,USER_ACCOUNT,CUSTOMER,ORDERS,ORDER_ITEM,INVENTORY_BALANCE,ROLE,USER_ROLE;'),after);
  pass('Different catalog identity/SKU is rejected with no change to the 20 products or application data');
  const invalid=seed.replace("DEFAULT '{bcrypt}","DEFAULT 'changed-{bcrypt}");assert.notEqual(invalid,seed);rejectedSql(invalid,'Seeder2 account collision/changed password');assert.equal(sql('CHECKSUM TABLE PRODUCT,PRODUCT_IMAGE,CATEGORY,USER_ACCOUNT,CUSTOMER,ORDERS,ORDER_ITEM,INVENTORY_BALANCE,ROLE,USER_ROLE;'),after);
  assert.equal(sql("SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema=DATABASE() AND routine_name='wspm_seeder2_20261111';"),'0');
  pass('Conflicting account/hash rejected without overwriting; helper routine cleaned');
  context=await request.newContext();
  async function data(response,status=200){assert.equal(response.status(),status,response.url()+': '+await response.text());return(await response.json()).data;}
  const tokens={};
  for(const name of ['admin','sequence','mixed','cold','wrong']){
    const csrf=await data(await context.get(origin+'/api/v1/auth/csrf'));
    const auth=await data(await context.post(origin+'/api/v1/auth/login',{headers:{Origin:origin,'X-XSRF-TOKEN':csrf.token},data:{username:'demo2.'+name,password:'DemoCombo@2026'}}));tokens[name]=auth.accessToken;
    assert.ok(auth.user.roles.includes('CUSTOMER'));assert.equal(auth.user.roles.includes('ADMIN'),name==='admin');
  }
  pass('All five fixed credentials log in through real Auth with exact roles');
  const headers=(token=tokens.admin)=>({Origin:origin,Authorization:'Bearer '+token}),base='/api/v1/admin';
  const get=url=>context.get(origin+url,{headers:headers()}).then(response=>data(response));
  const post=(url,body)=>context.post(origin+url,{headers:headers(),...(body===undefined?{}:{data:body})}).then(response=>data(response));
  assert.equal((await context.get(origin+base+'/combos',{headers:headers(tokens.sequence)})).status(),403);
  const bytes=readFileSync('docs/demo/combo-demo/data/orders.csv');const snapshot=(await data(await context.post(origin+base+'/dataset-snapshots',{headers:headers(),multipart:{file:{name:'orders.csv',mimeType:'text/csv',buffer:bytes}}}),201)).snapshotId;
  await post(base+'/dataset-snapshots/'+snapshot+'/validation');const converted=await post(base+'/dataset-snapshots/'+snapshot+'/conversion');assert.equal(converted.statistics.sequenceCount,'41');
  const runs=[];
  for(const algorithm of ['WSPM_PRETREE','WSPM_BITPRUNE']){
    const run=(await data(await context.post(origin+base+'/mining-runs',{headers:headers(),data:{snapshotId:snapshot,algorithm,minsupMode:'ABSOLUTE',minsupValue:.001}}),202)).runId;
    await until(async()=>{const status=await get(base+'/mining-runs/'+run);assert.notEqual(status.status,'FAILED');return(await get(base+'/mining-runs/'+run+'/results')).publishedReadable;},algorithm,120);
    runs.push({run,algorithm,map:sql(`SELECT canonical_pattern,support_abs FROM PATTERN WHERE run_id=${q(run)} ORDER BY canonical_pattern;`)});
  }
  assert.equal(runs[0].map,runs[1].map);pass('Manual-guide CSV gives equal complete pattern/support maps for both real engines');
  const mouse=catalog.find(p=>p.sku==='SV3V2-P12'),run=runs[1].run;
  const preview=await post(base+'/combo-generation/preview',{runId:run,anchorProductId:null,k:20,recommendationK:20,draftLimit:20,requestIdentity:'seeder2-verified',categorySelection:{requiredCategories:[{categoryId:mouse.categoryId,quantity:1}],minDistinctProducts:2,maxDistinctProducts:3}});
  assert.equal(preview.status,'READY');const stored=await post(base+'/combo-generation/submit',{contextToken:preview.contextToken});assert.equal(stored.status,'INSERTED');
  const list=await get(base+'/combos?batchId='+stored.batchId+'&page=0&size=20');
  for(const item of list.items){const detail=await get(base+'/combos/'+item.data.comboId);assert.equal((await post(base+'/combos/'+item.data.comboId+'/review',{decision:'APPROVE',reviewNote:'Seeder2 acceptance',expectedReviewToken:detail.reviewToken})).outcome,'APPLIED');}
  await post(base+'/combos/batches/'+stored.batchId+'/publication');
  const kinds=Object.fromEntries(sql(`SELECT combo_kind,COUNT(*) FROM COMBO WHERE batch_id=${q(stored.batchId)} GROUP BY combo_kind;`).split(/\r?\n/).map(line=>{const[k,n]=line.split('\t');return[k,Number(n)];}));
  assert.ok(kinds.SAME_ORDER_BUNDLE>=5);assert.ok(kinds.SEQUENTIAL_CROSS_SELL>=5);assert.ok(kinds.MIXED_SEQUENCE>=3);
  assert.equal((await data(await context.get(origin+'/api/v1/recommendations/combos/latest'))).length,5);
  for(const name of ['sequence','mixed','cold','wrong']){
    const personal=await data(await context.get(origin+'/api/v1/recommendations/combos/personalized',{headers:headers(tokens[name])}));
    if(name==='sequence')assert.ok(personal.items.filter(r=>r.combo.kind==='SEQUENTIAL_CROSS_SELL').length>=5);
    if(name==='mixed')assert.ok(personal.items.filter(r=>r.combo.kind==='MIXED_SEQUENCE').length>=3);
    if(name==='cold'||name==='wrong')assert.equal(personal.items.length,0);
  }
  evidence.status='PASS';evidence.kinds=kinds;evidence.csvSha256=createHash('sha256').update(bytes).digest('hex');save();pass('Real review/publication and all seeded buyer histories produce expected public/personal combos');
}catch(error){evidence.status='FAIL';evidence.failure=String(error.message).slice(0,2000);save();console.error(evidence.failure);process.exitCode=1;}
finally{
  if(context)await context.dispose();
  if(backend&&backend.exitCode===null){backend.kill();await Promise.race([new Promise(resolve=>backend.once('exit',resolve)),delay(5000)]);}
  if(log)log.end();if(started){owned();command('docker',['stop','--time','5',name]);}
}
