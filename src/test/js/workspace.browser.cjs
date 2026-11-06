/** Offline browser regression using installed Chromium/Brave and native CDP; no npm dependencies. */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {spawn} = require('node:child_process');
const root = path.resolve('src/main/resources/static');
const artifacts = process.env.UI_SCREENSHOTS || '/tmp/soopi-ui-screenshots';
fs.mkdirSync(artifacts,{recursive:true});
const catalog=JSON.parse(fs.readFileSync('src/main/resources/skills/catalog.json','utf8'));
const skills=(Array.isArray(catalog)?catalog:catalog.skills).map(s=>({...s,content:undefined}));
const projects=[{id:1,name:'Order service',path:'/workspace/order-service',language:'Java',description:'API quản lý đơn hàng với Spring Boot',createdAt:'2026-11-06T00:00:00Z'},
{id:2,name:'Web console',path:'/workspace/web',language:'JavaScript',description:'Giao diện quản lý dự án',createdAt:'2026-11-06T00:00:00Z'}];
const tasks=[{id:12,projectId:1,projectName:'Order service',type:'CODE_REVIEW',status:'COMPLETED',createdAt:'2026-11-06T01:00:00Z',completedAt:'2026-11-06T01:01:00Z',output:JSON.stringify({summary:'Đã phân tích mã nguồn.\n\n## Kết quả\nMột vấn đề cần kiểm tra.',issues:[{title:'Thiếu kiểm tra quyền sở hữu đơn hàng',severity:'HIGH',category:'SECURITY',file:'OrderController.java',line:42,description:'Endpoint đọc đơn hàng chưa xác minh người sở hữu.',recommendation:'Kiểm tra quyền truy cập trong service.'}],suggestions:['Tách logic validation để dễ kiểm thử.'],appliedSkills:[{id:'security-review',version:'1.0.0'}]})},
{id:11,projectId:2,projectName:'Web console',type:'CODE_REVIEW',status:'ERROR',createdAt:'2026-11-05T01:00:00Z',completedAt:'2026-11-05T01:01:00Z',errorMessage:'429: Free-tier quota exhausted',output:null}];
// New reports retain the Phase 1 aliases while exposing source evidence and observed scope.
const qualityReport=JSON.parse(tasks[0].output);
Object.assign(qualityReport,{schemaVersion:2,limitations:['Chưa kiểm tra cấu hình deployment'],reviewMetadata:{model:'fixture-model',modelCalls:2,toolCalls:1,maxModelCalls:25,maxToolCalls:24,peakRequestChars:5000,maxContextChars:96000,filesRead:[{filePath:'OrderController.java',lineStart:35,lineEnd:48}]}});
Object.assign(qualityReport.issues[0],{filePath:'OrderController.java',lineStart:42,lineEnd:43,evidence:'return orders.findById(id);',classification:'POTENTIAL_RISK',explanation:'Cần đối chiếu chính sách truy cập endpoint.',suggestion:'Kiểm tra quyền truy cập trong service.'});
tasks[0].output=JSON.stringify(qualityReport);
let posts=0,taskPayload,chatReviewPayload,followPayload; const chatReviews=[];
const adminUsers=[{id:1,username:'reviewer',email:'reviewer@example.test',role:'ADMIN',isLocked:false,projectCount:2,taskCount:2},{id:2,username:'developer',email:'developer@example.test',role:'USER',isLocked:false,projectCount:0,taskCount:0}];
const adminTools=[{toolName:'file_system',displayName:'Đọc mã nguồn',description:'Read source',category:'READ',enabled:true}];
const adminSettings={activeModel:'mock-free-model',allowedModels:['mock-free-model','mock-alt-model'],maskedApiKey:'masked-secret-marker',hasCustomApiKey:true};
let adminReadFailure=false,adminDenied=false;const adminWrites=[];let rejectReview=true,readFailure=false,pollMode=false;let pollGets=0,activePollGets=0,maxPollGets=0;const requests=[];
const server=http.createServer(async(req,res)=>{
 const url=new URL(req.url,'http://localhost');requests.push(`${req.method} ${url.pathname}`);
 const json=(data,status=200)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(data));};
 if(url.pathname.startsWith('/api/')){
  if(req.method==='POST'&&url.pathname==='/api/tasks') {
   posts++;let raw='';for await(const chunk of req)raw+=chunk;taskPayload=JSON.parse(raw);
   const allowedTaskKeys=['projectId','type','input','selectedTools','model','prompt','skills'];
   if(Object.keys(taskPayload).some(key=>!allowedTaskKeys.includes(key)))return json({message:'Dữ liệu yêu cầu không hợp lệ'},400);
   await new Promise(r=>setTimeout(r,250));
   if(rejectReview)return json({message:'429: quota exhausted'},429);
   const saved={...tasks[0],id:13,output:JSON.stringify({summary:'Review đã hoàn tất.',issues:[],suggestions:[],appliedSkills:[{id:'architecture-review',version:'1.0.0'}]})};tasks.unshift(saved);return json(saved);
  }
  if(req.method==='POST'&&url.pathname==='/api/auth/login')return json({message:'Sai tài khoản hoặc mật khẩu'},401);
  if(req.method==='POST'&&url.pathname==='/api/auth/register')return json({message:'Tên đăng nhập đã tồn tại'},400);
  if(url.pathname==='/api/projects')return readFailure?json({message:'Unavailable'},503):json(projects);
  if(url.pathname==='/api/tasks'){
   if(pollMode){pollGets++;activePollGets++;maxPollGets=Math.max(maxPollGets,activePollGets);
    if(pollGets>1){await new Promise(r=>setTimeout(r,3400));tasks[0].status='COMPLETED';}
    activePollGets--;
   }
   return json(tasks);
  }
  if(url.pathname==='/api/skills')return json(skills);
  if(url.pathname==='/api/users/me/preferences')return json({theme:'dark',language:'vi',preferredModel:'mock-free-model'});
  if(url.pathname==='/api/system/models')return json({activeModel:adminSettings.activeModel,allowedModels:adminSettings.allowedModels});
  if(url.pathname==='/api/chat/conversations/7/reviews') {
   if(req.method==='POST') { let raw='';for await(const chunk of req)raw+=chunk;chatReviewPayload=JSON.parse(raw);chatReviews.push({...tasks[0],id:99,status:'COMPLETED',output:JSON.stringify(qualityReport)});return json(chatReviews[0]); }
   return json(chatReviews);
  }
  if(url.pathname==='/api/chat' && req.method==='POST') { let raw='';for await(const chunk of req)raw+=chunk;followPayload=JSON.parse(raw);return json({conversationId:7,content:'Giải thích từ báo cáo đã lưu.'}); }
  if(url.pathname==='/api/chat/conversations/7')return json({id:7,title:'Kiến trúc Order service',projectId:1,model:'mock-free-model',messages:[{sender:'USER',content:'Giải thích service layer'},{sender:'AGENT',content:'## Service layer\nTách logic nghiệp vụ.\n```java\n@Service\nclass OrderService {}\n```'}]});
  if(url.pathname==='/api/chat/conversations')return json([{id:7,title:'Kiến trúc Order service',projectId:1,model:'mock-free-model',updatedAt:'2026-11-06T00:00:00Z'}]);
  if(url.pathname.startsWith('/api/admin/')){
   if(adminDenied)return json({message:'Forbidden'},403);
   if(adminReadFailure&&req.method==='GET')return json({message:'Unavailable'},503);
   if(req.method==='PUT'||req.method==='DELETE'){
    adminWrites.push(`${req.method} ${url.pathname}`);
    if(url.pathname==='/api/admin/settings'){
     let raw='';for await(const chunk of req)raw+=chunk;const body=JSON.parse(raw);
     if(body.model)adminSettings.activeModel=body.model;
     if(body.allowedModels)adminSettings.allowedModels=body.allowedModels;
     return json(adminSettings);
    }
    if(url.pathname==='/api/admin/tools/file_system'){adminTools[0].enabled=url.searchParams.get('enabled')==='true';return json(adminTools[0]);}
    const match=url.pathname.match(/users\/(\d+)\/(status|role)/);
    if(match){const target=adminUsers.find(u=>u.id===Number(match[1]));if(match[2]==='status')target.isLocked=url.searchParams.get('locked')==='true';else target.role=url.searchParams.get('role');return json(target);}
    return json({success:true});
   }
   if(url.pathname==='/api/admin/stats')return json({totalUsers:2,totalProjects:2,totalTasks:tasks.length,completedTasks:tasks.filter(t=>t.status==='COMPLETED').length,failedTasks:1,pendingOrProcessingTasks:0});
   if(url.pathname==='/api/admin/users')return json(adminUsers);
   if(url.pathname==='/api/admin/settings')return json(adminSettings);
   if(url.pathname==='/api/admin/tools')return json(adminTools);
   if(url.pathname==='/api/admin/tasks')return json(tasks.map(t=>({...t,username:'reviewer'})));
   if(url.pathname==='/api/admin/audit-logs')return json([{id:1,username:'reviewer',action:'UPDATE_CONFIG',details:'apiKey=super-secret-fixture',createdAt:'2026-11-06T01:00:00Z'},{id:2,username:'reviewer',action:'LOCK_USER',details:'Đã khóa người dùng #2',createdAt:'2026-11-06T01:00:00Z'}]);
  }
  if(url.pathname.endsWith('/tools'))return json(adminTools.filter(t=>t.enabled));
  return json([]);
 }
 const file=path.resolve(root,'.'+decodeURIComponent(url.pathname==='/'?'/index.html':url.pathname));
 if(!file.startsWith(root+path.sep)){res.writeHead(403);return res.end();}
 try {const content=fs.readFileSync(file);const mime={'.css':'text/css','.js':'text/javascript','.png':'image/png','.svg':'image/svg+xml'};res.writeHead(200,{'Content-Type':mime[path.extname(file)]||'text/html'});res.end(content);}catch{res.writeHead(404);res.end();}
});
class CDP {
 constructor(ws){this.ws=ws;this.id=0;this.pending=new Map();this.errors=[];ws.addEventListener('message',e=>{const m=JSON.parse(e.data);if(m.id){const p=this.pending.get(m.id);this.pending.delete(m.id);m.error?p.reject(new Error(JSON.stringify(m.error))):p.resolve(m.result);}if(m.method==='Runtime.exceptionThrown')this.errors.push(m.params.exceptionDetails);});}
 send(method,params={}){const id=++this.id;return new Promise((resolve,reject)=>{this.pending.set(id,{resolve,reject});this.ws.send(JSON.stringify({id,method,params}));});}
 async evaluate(expression){const r=await this.send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw new Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
 async wait(expression){for(let i=0;i<100;i++){if(await this.evaluate(expression))return;await new Promise(r=>setTimeout(r,50));}throw new Error(`Timeout: ${expression}`);}
 async click(selector){await this.evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);await new Promise(r=>setTimeout(r,30));}
 async shot(name){await this.evaluate(`Promise.all(document.getAnimations().filter(a=>a.effect?.getTiming().iterations!==Infinity).map(a=>a.finished.catch(()=>{})))`);const r=await this.send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(path.join(artifacts,name+'.png'),Buffer.from(r.data,'base64'));}
}
(async()=>{
 let browser,cdp;
 try {
  await new Promise(r=>server.listen(0,'127.0.0.1',r));const port=server.address().port;
  let endpoint=process.env.UI_CDP_URL;
  if(!endpoint){
   const executable=process.env.CHROMIUM_PATH || '/opt/brave.com/brave/brave';
   browser=spawn(executable,['--headless','--no-sandbox','--disable-gpu','--disable-dev-shm-usage','--remote-debugging-port=0',`--user-data-dir=/tmp/soopi-browser-${process.pid}`,'about:blank'],{stdio:['ignore','ignore','pipe']});
   endpoint=await new Promise((resolve,reject)=>{let log='';const timer=setTimeout(()=>reject(new Error('Browser startup timeout')),10000);browser.stderr.on('data',chunk=>{log+=chunk;const match=log.match(/DevTools listening on (ws:\/\/[^\s]+)/);if(match){clearTimeout(timer);resolve(match[1].replace(/^ws:/,'http:').replace(/\/devtools\/browser\/.*/,''));}});browser.on('exit',code=>{clearTimeout(timer);reject(new Error(`Browser exited ${code}: ${log}`));});});
  }
  const list=await (await fetch(endpoint+'/json/list')).json();
  const ws=new WebSocket(list.find(x=>x.type==='page').webSocketDebuggerUrl);await new Promise(r=>ws.addEventListener('open',r,{once:true}));cdp=new CDP(ws);
  await cdp.send('Page.enable');await cdp.send('Runtime.enable');await cdp.send('Network.enable');await cdp.send('Network.setBlockedURLs',{urls:['*fonts.googleapis.com*','*fonts.gstatic.com*']});
  await cdp.send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
  await cdp.send('Page.navigate',{url:`http://127.0.0.1:${port}/`});
  await cdp.wait(`typeof WorkspaceView!=='undefined' && document.readyState==='complete'`);
  assert.equal(await cdp.evaluate(`document.title`),'HUS AI Agent');
  await cdp.wait(`Array.from(document.querySelectorAll('img.hus-logo,img.vietnam-flag')).every(img=>img.complete && img.naturalWidth>0)`);
  assert.equal(await cdp.evaluate(`document.querySelector('link[rel="icon"]').getAttribute('href')`),'assets/branding/vietnam-flag.svg');
  await cdp.shot('01-login');
  await cdp.click('#btnTopLogin');await cdp.wait(`document.querySelector('#modalAuth.active') && document.activeElement.closest('#modalAuth')`);await cdp.shot('02-auth');
  await cdp.evaluate(`document.querySelector('#loginPassword').value='test-pass';document.querySelector('#btnToggleLoginPassword').click()`);
  assert.equal(await cdp.evaluate(`document.querySelector('#loginPassword').type`),'text');
  await cdp.evaluate(`document.querySelector('#loginUsername').value='wrong';document.querySelector('#formLogin').requestSubmit()`);
  await cdp.wait(`document.querySelector('#formLogin .form-error')`);
  await cdp.click('#tabAuthRegister');
  await cdp.evaluate(`document.querySelector('#regUsername').value='taken';document.querySelector('#regEmail').value='test@example.test';document.querySelector('#regPassword').value='test-pass';document.querySelector('#formRegister').requestSubmit()`);
  await cdp.wait(`document.querySelector('#formRegister .form-error')`);await cdp.shot('02b-register-error');
  await cdp.evaluate(`document.querySelector('#formRegister button[type=submit]').focus()`);
  await cdp.send('Input.dispatchKeyEvent',{type:'keyDown',key:'Tab',code:'Tab',windowsVirtualKeyCode:9});
  assert.equal(await cdp.evaluate(`!!document.activeElement.closest('#modalAuth')`),true,'Tab must remain in dialog');
  await cdp.evaluate(`document.querySelector('[data-close-modal]').click()`);
  await cdp.evaluate(`localStorage.setItem('ai_agent_jwt_token','mock-session');localStorage.setItem('ai_agent_user',JSON.stringify({id:1,username:'reviewer',email:'reviewer@example.test',role:'ADMIN'}));location.reload()`);
  await cdp.wait(`window.AppState?.state.tasks.length===2 && document.querySelector('#taskSkillsContainer input')`);
  await cdp.shot('03-dashboard-dark');
  await cdp.click('#btnToggleSidebar');
  assert.equal(await cdp.evaluate(`document.querySelector('#appSidebar').getBoundingClientRect().width`),68);
  await cdp.click('[data-nav-page="history"]');
  assert.equal(await cdp.evaluate(`document.body.classList.contains('sidebar-mini')`),true);
  await cdp.click('#btnToggleSidebar');await cdp.click('[data-nav-page="dashboard"]');
  assert.equal(await cdp.evaluate(`document.querySelectorAll('#workspaceDashboard .overview-item')[0].textContent.includes('2')`),true);
  for(const [page,name] of [['projects','04-projects'],['chat','05-chat'],['review','06-review'],['history','07-history'],['settings','08-settings'],['fix','09-future']]){
   await cdp.click(`[data-nav-page="${page}"]`);await cdp.shot(name);
   if(page==='projects')assert.equal(await cdp.evaluate(`document.querySelectorAll('[data-welcome-proj-id]:not([hidden])').length`),2,'Projects must render loaded records');
   assert.equal(await cdp.evaluate(`document.documentElement.scrollWidth <= innerWidth`),true,`${page} horizontal overflow`);
  }
  await cdp.click('[data-nav-page="projects"]');
  await cdp.evaluate(`document.querySelector('#projectSearch').value='order';document.querySelector('#projectSearch').dispatchEvent(new Event('input'))`);
  assert.equal(await cdp.evaluate(`document.querySelectorAll('[data-welcome-proj-id]:not([hidden])').length`),1);
  await cdp.evaluate(`document.querySelector('#projectSearch').value='';document.querySelector('#projectSearch').dispatchEvent(new Event('input'))`);
  await cdp.click('[data-project-review="1"]');assert.equal(await cdp.evaluate(`document.querySelector('#selectTaskProject').value`),'1');
  await cdp.evaluate(`document.querySelector('#modalCreateTask [data-close-modal]').click()`);
  await cdp.click('[data-nav-page="chat"]');await cdp.click('[data-conv-id="7"]');await cdp.wait(`document.querySelector('#chatMessagesList').textContent.includes('OrderService')`);await cdp.shot('10-chat-messages');
  assert.equal(requests.filter(r=>r.startsWith('POST /api/chat')).length,0,'navigation must not generate chat');
  await cdp.wait(`AppState.state.activeConversationId===7 && document.querySelector('#chatReviewSkills input') && document.querySelector('#chatReviewTools input')`);
  await cdp.evaluate(`document.querySelector('#chatReviewMode').value='review';document.querySelector('#chatReviewSkills input').checked=true;document.querySelector('#centralChatTextarea').value='security kiểm tra quyền';document.querySelector('#btnSendCentralChat').click()`);
  await cdp.wait(`document.querySelector('#chatReviewCards').textContent.includes('Code review #99')`);
  assert.equal(chatReviewPayload.message,'/review security kiểm tra quyền');
  assert.equal(chatReviewPayload.skills.length,1);
  assert.deepEqual(chatReviewPayload.selectedTools,['file_system']);
  assert.ok(chatReviewPayload.requestId);
  assert.equal(requests.filter(r=>r==='POST /api/chat/conversations/7/reviews').length,1);
  assert.ok(await cdp.evaluate(`document.querySelector('#chatReviewCards').textContent.includes('POTENTIAL_RISK')`));
  await cdp.click('[data-review-action="open"]');await cdp.wait(`document.querySelector('#modalTaskReport.active')`);
  await cdp.evaluate(`document.querySelector('#modalTaskReport [data-close-modal]').click()`);
  await cdp.click('[data-review-action="follow"]');
  await cdp.evaluate(`document.querySelector('#centralChatTextarea').value='Giải thích finding đầu tiên';document.querySelector('#btnSendCentralChat').click()`);
  await cdp.wait(`document.querySelector('#chatMessagesList').textContent.includes('Giải thích từ báo cáo đã lưu.')`);
  assert.equal(followPayload.reviewTaskId,99);
  const before=posts;
  await cdp.click('[data-nav-page="history"]');
  await cdp.evaluate(`document.querySelector('#historyStatus').value='ERROR';document.querySelector('#historyStatus').dispatchEvent(new Event('input'))`);
  assert.equal(await cdp.evaluate(`document.querySelectorAll('#historyResults tbody tr').length`),1);
  await cdp.click('[data-open-report="11"]');await cdp.shot('11-error-report');await cdp.evaluate(`document.querySelector('#modalTaskReport [data-close-modal]').click()`);
  await cdp.evaluate(`document.querySelector('#historyStatus').value='';document.querySelector('#historyStatus').dispatchEvent(new Event('input'))`);
  await cdp.click('[data-open-report="12"]');
  for(const text of ['OrderController.java:42-43','return orders.findById(id);','POTENTIAL_RISK','Chưa kiểm tra cấu hình deployment'])
    assert.ok(await cdp.evaluate(`document.querySelector('#modalTaskReport').textContent.includes(${JSON.stringify(text)})`),text);
  await cdp.click('#modalTaskReport details summary');
  assert.ok(await cdp.evaluate(`document.querySelector('#modalTaskReport').textContent.includes('OrderController.java:35-48')`));
  await cdp.shot('11b-evidence-report');await cdp.evaluate(`document.querySelector('#modalTaskReport [data-close-modal]').click()`);
  await cdp.click('[data-nav-page="review"]');await cdp.click('[data-action="review"]');
  await cdp.wait(`document.querySelector('#modalCreateTask.active')`);
  assert.equal(await cdp.evaluate(`document.querySelectorAll('#taskSkillsContainer input[name="taskSkills"]:checked').length`),0);
  await cdp.evaluate(`document.querySelector('#selectTaskProject').value='1';document.querySelector('#taskSkillsContainer input[name="taskSkills"]').checked=true`);
  await cdp.shot('12-review-form');
  await cdp.evaluate(`document.querySelector('#formCreateTask').dispatchEvent(new Event('submit',{cancelable:true}));document.querySelector('#formCreateTask').dispatchEvent(new Event('submit',{cancelable:true}))`);
  await cdp.wait(`document.querySelector('#formCreateTask .form-error')?.textContent.includes('429')`);
  assert.equal(posts,before+1);assert.deepEqual(taskPayload.selectedTools,['file_system']);assert.equal(taskPayload.skills.length,1);assert.equal(taskPayload.skills[0].version,'1.0.0');
  await new Promise(r=>setTimeout(r,400));assert.equal(posts,before+1,'429 must not auto retry');
  await cdp.shot('13-quota');
  rejectReview=false;
  await cdp.evaluate(`document.querySelector('#formCreateTask').requestSubmit()`);
  await cdp.wait(`document.querySelector('#taskReportContent').textContent.includes('architecture-review@1.0.0')`);
  assert.equal(posts,2);
  assert.equal(await cdp.evaluate(`document.querySelector('#taskReportContent').textContent.includes('security-review@1.0.0')`),false,'only server-applied skill metadata is evidence');
  await cdp.shot('13b-server-report');await cdp.evaluate(`document.querySelector('#modalTaskReport [data-close-modal]').click()`);
  await cdp.click('[data-nav-page="settings"]');await cdp.click('[data-theme-choice="light"]');await cdp.click('[data-nav-page="dashboard"]');await cdp.shot('14-dashboard-light');
  assert.equal(await cdp.evaluate(`document.documentElement.dataset.theme`),'light');
  await cdp.click('#btnTopAdmin');await cdp.wait(`document.querySelector('#adminControlStatus').textContent==='Đã tải dữ liệu'`);
  await cdp.shot('15-admin-light');
  assert.equal(await cdp.evaluate(`document.querySelector('#adminControlContent').textContent.includes('super-secret-fixture')`),false);
  const adminPostsBefore=posts;
  for(const section of ['users','models','budget','skills','tools','tasks','audit','system','overview']){
   await cdp.click(`[data-admin-nav="${section}"]`);await cdp.wait(`document.querySelector('#adminControlStatus').textContent==='Đã tải dữ liệu'`);
   await cdp.shot('admin-'+section+'-light');
   assert.equal(await cdp.evaluate(`document.querySelector('.admin-control-workspace').scrollWidth<=document.querySelector('.admin-control-workspace').clientWidth`),true);
   if(section==='budget')assert.equal(await cdp.evaluate(`document.querySelectorAll('#adminControlContent form,#adminControlContent button[type=submit]').length`),0);
  }
  assert.equal(posts,adminPostsBefore,'Admin navigation may not generate content');
  await cdp.click('[data-admin-nav="users"]');await cdp.wait(`document.querySelector('#adminUserSearch')`);
  await cdp.evaluate(`document.querySelector('#adminUserSearch').value='developer';document.querySelector('#adminUserSearch').dispatchEvent(new Event('input',{bubbles:true}))`);
  assert.equal(await cdp.evaluate(`document.querySelectorAll('#adminUserResults tbody tr').length`),1);
  await cdp.click('[data-admin-inspect="2"]');await cdp.wait(`document.querySelector('#modalAdminInspectUser.active')`);
  await cdp.evaluate(`document.querySelector('#modalAdminInspectUser [data-close-modal]').click()`);
  assert.equal(await cdp.evaluate(`document.querySelector('#modalAdminPanel').classList.contains('active')`),true,'closing inspection must return to admin');
  await cdp.click('[data-admin-user-lock="2"]');await cdp.wait(`document.querySelector('#modalAdminConfirm.active')`);
  assert.equal(adminWrites.length,0);await cdp.click('#adminConfirmCancel');assert.equal(adminWrites.length,0);
  await cdp.click('[data-admin-user-lock="2"]');await cdp.click('#adminConfirmAccept');
  await cdp.wait(`document.querySelector('#adminControlStatus').textContent==='Đã tải dữ liệu' && document.querySelector('[data-admin-user-lock="2"]').textContent==='Mở khóa'`);
  assert.equal(adminWrites.length,1);
  await cdp.shot('admin-confirm-result');
  await cdp.click('[data-admin-user-role="2"]');await cdp.click('#adminConfirmAccept');
  await cdp.wait(`document.querySelector('[data-admin-user-role="2"]')?.textContent==='Giáng USER'`);
  await cdp.click('[data-admin-nav="tools"]');await cdp.wait(`document.querySelector('[data-admin-tool]')`);
  await cdp.click('[data-admin-tool]');await cdp.click('#adminConfirmAccept');
  await cdp.wait(`document.querySelector('[data-admin-tool]')?.getAttribute('aria-pressed')==='false'`);
  await cdp.click('[data-admin-nav="models"]');await cdp.wait(`document.querySelector('#adminCenterKeyForm')`);
  assert.equal(await cdp.evaluate(`document.querySelector('#adminControlContent').textContent.includes('masked-secret-marker')`),false);
  await cdp.evaluate(`document.querySelector('#adminModelInput').value='mock-alt-model';document.querySelector('#adminCenterModelForm').requestSubmit()`);
  await cdp.click('#adminConfirmAccept');await cdp.wait(`document.querySelector('#adminModelInput')?.value==='mock-alt-model'`);
  await cdp.evaluate(`document.querySelector('#adminKeyInput').value='test-key-do-not-echo';document.querySelector('#adminCenterKeyForm').requestSubmit()`);
  await cdp.wait(`document.querySelector('#modalAdminConfirm.active')`);
  assert.equal(await cdp.evaluate(`document.querySelector('#adminKeyInput').value`),'');
  assert.equal(await cdp.evaluate(`document.body.textContent.includes('test-key-do-not-echo')`),false);
  await cdp.send('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});
  await cdp.wait(`!document.querySelector('#modalAdminConfirm.active')`);
  assert.equal(adminWrites.filter(r=>r==='PUT /api/admin/settings').length,1,'cancelled key update must not write');
  adminReadFailure=true;await cdp.click('[data-admin-nav="users"]');await cdp.wait(`document.querySelector('#adminControlStatus').textContent==='Lỗi tải dữ liệu'`);await cdp.shot('admin-error');
  adminReadFailure=false;await cdp.click('[data-admin-refresh]');await cdp.wait(`document.querySelector('#adminUserSearch')`);
  await cdp.send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  await cdp.shot('admin-mobile-light');
  for(const section of ['overview','models','budget','skills','tools','tasks','audit']){
   await cdp.click(`[data-admin-nav="${section}"]`);await cdp.wait(`document.querySelector('#adminControlStatus').textContent==='Đã tải dữ liệu'`);
   assert.equal(await cdp.evaluate(`document.querySelector('.admin-control-workspace').scrollWidth<=document.querySelector('.admin-control-workspace').clientWidth`),true,`mobile admin ${section}`);
  }
  await cdp.click('#adminCenterTheme');await cdp.shot('admin-mobile-dark');
  await cdp.send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
  await cdp.click('[data-admin-nav="overview"]');await cdp.wait(`document.querySelector('#adminControlStatus').textContent==='Đã tải dữ liệu'`);await cdp.shot('admin-overview-dark');
  await cdp.click('#adminCenterTheme');
  adminDenied=true;await cdp.click('[data-admin-nav="users"]');await cdp.wait(`document.querySelector('#adminControlStatus').textContent==='Truy cập bị từ chối'`);
  assert.equal(await cdp.evaluate(`document.querySelectorAll('[data-admin-user-lock]').length`),0);
  adminDenied=false;
  assert.equal(posts,adminPostsBefore,'Admin mutations must not generate content');
  await cdp.evaluate(`document.querySelector('#modalAdminPanel [data-close-modal]').click()`);
  await cdp.send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});await cdp.shot('16-mobile-dashboard');
  await cdp.click('#btnToggleSidebar');await cdp.shot('17-mobile-navigation');
  await cdp.click('[data-nav-page="chat"]');await cdp.shot('18-mobile-chat');
  assert.equal(await cdp.evaluate(`document.documentElement.scrollWidth <= innerWidth`),true,'mobile horizontal overflow');
  assert.equal(cdp.errors.length,0,JSON.stringify(cdp.errors));
  assert.equal(requests.filter(r=>r.startsWith('POST /api/chat')).length,2,'navigation must not generate additional chat');
  for(const width of [390,768,1024,1440]){
   await cdp.send('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:width<800});
   for(const page of ['dashboard','projects','chat','review','history','settings']){
    await cdp.evaluate(`WorkspaceView.navigate('${page}')`);
    assert.equal(await cdp.evaluate(`document.querySelector('#appMain').scrollWidth<=document.querySelector('#appMain').clientWidth`),true,`${page} overflow at ${width}`);
   }
  }
  await cdp.send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  await cdp.evaluate(`WorkspaceView.navigate('review');document.querySelector('#btnTopNewTask').click()`);await cdp.shot('19-mobile-review');
  assert.equal(await cdp.evaluate(`document.querySelector('#modalCreateTask .modal-panel').scrollWidth<=document.querySelector('#modalCreateTask .modal-panel').clientWidth`),true);
  await cdp.evaluate(`document.querySelector('#modalCreateTask [data-close-modal]').click()`);
  await cdp.send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
  await cdp.evaluate(`WorkspaceView.navigate('dashboard')`);readFailure=true;await cdp.evaluate(`document.querySelector('#btnRefreshTasks').click()`);
  await cdp.wait(`document.querySelector('#workspaceDataBanner.error')`);await cdp.shot('20-read-error');
  assert.equal(await cdp.evaluate(`AppState.state.tasks.length`),3,'read error must preserve last persisted data');
  readFailure=false;await cdp.click('#workspaceDataBanner button');await cdp.wait(`document.querySelector('#workspaceDataBanner').hidden`);
  await cdp.evaluate(`localStorage.setItem('ai_agent_user',JSON.stringify({id:2,username:'developer',role:'USER'}));location.reload()`);
  await cdp.wait(`window.AppState?.state.tasks.length===3 && document.querySelector('#taskSkillsContainer input')`);
  assert.equal(await cdp.evaluate(`getComputedStyle(document.querySelector('#btnTopAdmin')).display`),'none');
  await cdp.evaluate(`WorkspaceView.navigate('settings')`);
  assert.equal(await cdp.evaluate(`!!document.querySelector('#workspaceSettings [data-action=admin]')`),false);
  assert.equal(await cdp.evaluate(`document.documentElement.dataset.theme`),'light','theme persists after reload');
  await cdp.evaluate(`WorkspaceView.navigate('settings');document.querySelector('[data-action=preferences]').click()`);
  await cdp.wait(`document.querySelector('#modalUserPreferences.active')`);
  await cdp.evaluate(`document.querySelector('#prefTheme').value='dark';document.querySelector('#formUserPreferences').requestSubmit()`);
  await cdp.wait(`!document.querySelector('#modalUserPreferences.active')`);
  assert.equal(await cdp.evaluate(`document.documentElement.dataset.theme`),'dark');
  pollMode=true;tasks[0].status='PENDING';
  await cdp.evaluate(`document.querySelector('#btnRefreshTasks').click()`);
  await cdp.wait(`AppState.state.tasks[0].status==='PENDING'`);
  await cdp.evaluate(`WorkspaceView.navigate('history');document.querySelector('[data-open-report="13"]').click()`);await cdp.shot('21-progress');
  await new Promise(r=>setTimeout(r,6800));
  await cdp.wait(`AppState.state.tasks[0].status==='COMPLETED'`);
  assert.equal(maxPollGets,1,'slow GET polling must not overlap');
  assert.equal(pollGets,2,'stop polling after all tasks finish');
  assert.equal(posts,2,'all navigation/filtering/reloading/polling must leave generation count unchanged');
  await cdp.evaluate(`document.querySelector('#modalTaskReport [data-close-modal]').click()`);
  assert.equal(cdp.errors.length,0,JSON.stringify(cdp.errors));
  console.log(JSON.stringify({result:'PASS',screenshots:artifacts,taskPosts:posts,uncaughtErrors:cdp.errors.length,requests:requests.length}));
 }catch(error){console.error('Browser exceptions:',JSON.stringify(cdp?.errors));throw error;}finally{cdp?.ws.close();browser?.kill();server.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
