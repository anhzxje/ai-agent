const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function load() {
  const context = vm.createContext({window: {}, console, setTimeout, clearTimeout});
  vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/markdownUtils.js', 'utf8'), context);
  vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/workspaceView.js', 'utf8'), context);
  return context.window.WorkspaceView;
}
test('dashboard counts only persisted completed reports, ignoring requested skills and malformed output', () => {
  const view = load();
  const data = view.summarize([{id:1}], [
    {id:1,status:'COMPLETED',output:JSON.stringify({issues:[{severity:'HIGH'}],appliedSkills:[{id:'security-review',version:'1.0.0'}]})},
    {id:2,status:'FAILED',input:{skills:[{id:'fake'}]},output:'bad json'},
    {id:3,status:'PROCESSING',output:{issues:[{}]}}
  ]);
  assert.equal(data.projects,1); assert.equal(data.tasks,3); assert.equal(data.running,1);
  assert.equal(data.completed,1); assert.equal(data.failed,1); assert.equal(data.issues,1);
  assert.equal(data.recentIssues.length,1);
});
test('task history intersects status, type, project and search without mutating data', () => {
  const v=load();
  const tasks=[{id:1,projectId:4,projectName:'Orders',status:'FAILED',type:'CODE_REVIEW'},
    {id:2,projectId:5,projectName:'Billing',status:'COMPLETED',type:'TEST_GENERATION'}];
  assert.equal(v.filterTasks(tasks,{status:'FAILED',type:'CODE_REVIEW',project:'4',query:'orders'}).length,1);
  assert.equal(v.filterTasks(tasks,{status:'COMPLETED',project:'4'}).length,0);
  assert.equal(tasks[0].id,1);
});
test('task rows escape untrusted content and show persisted timestamps and errors', () => {
  const html=load().taskRows([{id:4,projectName:'<img src=x>',status:'FAILED',type:'CODE_REVIEW',errorMessage:'<script>oops</script>',createdAt:'2026-11-06T00:00:00Z',completedAt:'2026-11-06T01:00:00Z'}],[]);
  assert.ok(!html.includes('<img'));assert.ok(!html.includes('<script>'));
  assert.ok(html.includes('&lt;script&gt;'));assert.ok(html.includes('datetime="2026-11-06T01:00:00Z"'));
});
test('single-flight submit ignores duplicates while pending and unlocks after failure without retry', async () => {
  const gate=load().singleFlight(); let calls=0; let reject;
  const first=gate(()=>{calls++;return new Promise((_,r)=>{reject=r;});});
  const duplicate=await gate(()=>{calls++;}); assert.equal(duplicate,undefined);assert.equal(calls,1);
  reject(new Error('429 quota'));await assert.rejects(first,/429/);
  await gate(()=>{calls++;});assert.equal(calls,2);
});
test('history offers cancel only while running and manual retry only for finished code reviews', () => {
  const view=load();
  const html=view.taskRows([{id:1,status:'PROCESSING',type:'CODE_REVIEW'},{id:2,status:'FAILED',type:'CODE_REVIEW'},{id:3,status:'FAILED',type:'TEST_GENERATION'}],[]);
  assert.ok(html.includes('data-cancel-task="1"'));
  assert.ok(html.includes('data-retry-task="2"'));
  assert.ok(!html.includes('data-retry-task="3"'));
  assert.ok(!html.includes('data-retry-task="1"'));
});
test('backend ERROR status contributes to failures and exposes manual retry', () => {
  const view=load();
  assert.equal(view.summarize([],[{id:5,status:'ERROR',type:'CODE_REVIEW'}]).failed,1);
  assert.ok(view.taskRows([{id:5,status:'ERROR',type:'CODE_REVIEW'}],[]).includes('data-retry-task="5"'));
});
test('review form uses only server models and tools, escaping labels and preserving explicit choices', async () => {
  const context=vm.createContext({window:{},console});
  vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/markdownUtils.js','utf8'),context);
  vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/reviewForm.js','utf8'),context);
  const model={value:'approved',innerHTML:''};
  const tools={innerHTML:'',querySelectorAll:()=>[{value:'grep',checked:true}],dataset:{loaded:'true'}};
  await context.window.ReviewForm.loadModels({getSystemModels:async()=>({activeModel:'approved',allowedModels:['approved','<img>']})},[model]);
  assert.ok(model.innerHTML.includes('&lt;img&gt;'));assert.ok(!model.innerHTML.includes('<img>'));
  assert.equal(model.value,'approved');
  await context.window.ReviewForm.loadTools({getSystemTools:async()=>[{toolName:'grep',displayName:'<img>',description:'Search',category:'READ',enabled:true},{toolName:'shell',displayName:'Shell',enabled:false}]},tools);
  assert.ok(tools.innerHTML.includes('value="grep"'));assert.ok(tools.innerHTML.includes('checked'));assert.ok(!tools.innerHTML.includes('shell'));assert.ok(!tools.innerHTML.includes('<img>'));
});
