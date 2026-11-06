const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
function load(){
 const context=vm.createContext({window:{},console});
 vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/markdownUtils.js','utf8'),context);
 vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/adminControlCenter.js','utf8'),context);
 return context.window.AdminControlCenter;
}
test('admin success rate uses completed and failed only, preserving unknown and zero denominator',()=>{
 const v=load();
 assert.equal(v.successRate({completedTasks:6,failedTasks:2,totalTasks:20}),'75.0%');
 assert.equal(v.successRate({completedTasks:0,failedTasks:0}),'Chưa có dữ liệu');
 assert.equal(v.successRate({completedTasks:6}),'Chưa có dữ liệu');
 assert.equal(v.successRate(null),'Chưa có dữ liệu');
});
test('admin users filter intersects role, lock status and query without losing records',()=>{
 const v=load(),users=[{id:1,username:'ann',role:'ADMIN',isLocked:false},{id:2,username:'bob',email:'b@example.test',role:'USER',isLocked:true}];
 assert.equal(v.filterUsers(users,{query:'b@example',role:'USER',status:'locked'})[0].id,2);
 assert.equal(v.filterUsers(users,{role:'ADMIN',status:'locked'}).length,0);
 assert.equal(users.length,2);
});
test('skills show version and conflict metadata as escaped read-only data',()=>{
 const html=load().skillRows([{id:'security-review',version:'1.0.0',description:'<img>',languages:['Java'],conflictsWith:['other@2.0.0']}]);
 assert.ok(html.includes('other@2.0.0'));assert.ok(html.includes('1.0.0'));assert.ok(html.includes('&lt;img&gt;'));
 assert.ok(!html.includes('<input'));assert.ok(!html.includes('<img>'));
});
test('task and audit messages containing secrets are withheld from presentation',()=>{
 const v=load();
 for(const secret of ['Authorization: Bearer token-value','apiKey=private-value','password: private-value','AIzaSyFakeGoogleSecret01234567890','eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature']){
  assert.ok(!v.safeText(secret).includes(secret));
 }
 assert.equal(v.safeText('429 quota exhausted'),'429 quota exhausted');
 const html=v.taskRows([{id:1,status:'ERROR',username:'<script>',errorMessage:'api_key=super-secret-value'}]);
 assert.ok(!html.includes('super-secret-value'));assert.ok(!html.includes('<script>'));
});
test('overview treats missing totals as unavailable and labels task-page quota counts',()=>{
 const html=load().overview({stats:{totalUsers:0,completedTasks:0,failedTasks:0},tasks:[{id:1,status:'ERROR',errorMessage:'429 quota exhausted'}],settings:{activeModel:'free-model'},logs:[]});
 assert.ok(html.includes('Chưa có dữ liệu'));assert.ok(html.includes('free-model'));
 assert.ok(html.includes('trang tác vụ đã tải'));assert.ok(!html.includes('100%'));
});
