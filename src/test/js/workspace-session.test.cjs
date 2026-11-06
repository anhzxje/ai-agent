const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function deferred() { let resolve, reject; const promise = new Promise((a,b) => {resolve=a;reject=b;}); return {promise,resolve,reject}; }
function setup(api, hooks = {}) {
 const context=vm.createContext({window:{},setTimeout,clearTimeout,AbortController});
 for(const file of ['core/taskPolling.js','modules/workspaceSession.js']) vm.runInContext(fs.readFileSync('src/main/resources/static/js/'+file,'utf8'),context);
 let poll; const createPoller=context.window.TaskPolling.create;
 context.window.TaskPolling.create=options=>{poll=options.poll;return createPoller(options);};
 const state={tasks:[],projects:[],activeTask:null};
 const session=context.window.WorkspaceSession.create({api,state,isVisible:()=>false,...hooks});
 return {session,state,poll:()=>poll()};
}
test('workspace refresh is single flight through commit and dependent view loading',async()=>{
 const pending=deferred(), followup=deferred();let reads=0,commits=0;
 const {session,state}=setup({getToken:()=> 'alice',getCurrentUser:()=>({username:'alice'}),getProjects:()=>{reads++;return pending.promise;},getTasks:async()=>[{id:1,isPinned:true}]},{onCommit:()=>commits++,afterLoad:()=>followup.promise});
 const first=session.refresh();assert.equal(session.refresh(),first);
 pending.resolve([{id:2}]);await new Promise(setImmediate);
 assert.equal(session.refresh(),first);assert.equal(commits,1);
 followup.resolve();await first;assert.equal(state.projects[0].id,2);assert.equal(reads,1);
 session.stop();
});
test('account switch rejects stale success and stale errors without clearing current pending load',async()=>{
 let token='alice';const alice=deferred(),bob=deferred();let errors=0;
 const {session,state}=setup({getToken:()=>token,getCurrentUser:()=>({username:token}),getProjects:()=>token==='alice'?alice.promise:bob.promise,getTasks:async()=>[]},{onError:()=>errors++});
 const first=session.refresh();await Promise.resolve();token='bob';const second=session.refresh();
 alice.reject(new Error('old account offline'));await first;
 assert.equal(errors,0);assert.equal(session.refresh(),second);
 bob.resolve([{id:2}]);await second;assert.equal(state.projects[0].id,2);session.stop();
});
test('invalidating a session prevents late commits even when the token is reused',async()=>{
 const pending=deferred();let commits=0;
 const {session,state}=setup({getToken:()=> 'alice',getCurrentUser:()=>({username:'alice'}),getProjects:()=>pending.promise,getTasks:async()=>[{id:1}]},{onCommit:()=>commits++});
 const first=session.refresh();session.invalidate();pending.resolve([{id:2}]);await first;
 assert.equal(commits,0);assert.equal(state.tasks.length,0);session.stop();
});

test('a slow poll cannot overwrite tasks committed by a newer refresh',async()=>{
 const slow=deferred();let calls=0;
 const {session,state,poll}=setup({getToken:()=> 'alice',getCurrentUser:()=>({username:'alice'}),getProjects:async()=>[],getTasks:()=>++calls===1?slow.promise:Promise.resolve([{id:1,status:'COMPLETED'}])});
 const pending=poll();await session.refresh();slow.resolve([{id:1,status:'PROCESSING'}]);await pending;
 assert.equal(state.tasks[0].status,'COMPLETED');session.stop();
});
