const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function load() {
  const context = vm.createContext({window:{MarkdownUtils:{escapeHtml: s => String(s ?? '').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;')}},setTimeout,clearTimeout,AbortController});
  for (const file of ['core/taskPolling.js','modules/chatReview.js']) vm.runInContext(fs.readFileSync(`src/main/resources/static/js/${file}`,'utf8'),context);
  return context.window.ChatReview;
}
test('review routing recognizes only explicit slash command and preserves focus', () => {
  const review=load();
  assert.equal(review.isReview('/review security inspect'),true);
  assert.equal(review.isReview('/reviewer'),false);
  assert.equal(review.isReview('please review this'),false);
});
test('conversation change prevents stale review list from committing', async () => {
  const review=load(); let resolve;
  const state={activeConversationId:1,activeProject:{id:3},currentUser:{id:1}};
  const api={getToken:()=> 'token',getConversationReviews:()=>new Promise(r=>resolve=r)};
  let rendered=0;
  const controller=review.create({api,state,render:()=>rendered++});
  const pending=controller.load(); await Promise.resolve(); state.activeConversationId=2; resolve([{id:4,status:'COMPLETED'}]); await pending;
  assert.equal(rendered,0);
});
test('rendered review report escapes server text and retains verified details', () => {
  const review=load();
  const html=review.renderCard({id:7,status:'COMPLETED',output:{summary:'<script>',issues:[{title:'<img>',severity:'HIGH',category:'SECURITY',classification:'POTENTIAL_RISK',filePath:'a.java',lineStart:2,lineEnd:3,evidence:'<code>',explanation:'why',suggestion:'fix'}],limitations:['limited'],appliedSkills:[{id:'security-review',version:'1'}],reviewMetadata:{model:'m',modelCalls:1,toolCalls:2,filesRead:[{filePath:'a.java',lineStart:1,lineEnd:3}]}}});
  for(const s of ['&lt;script&gt;','&lt;img&gt;','&lt;code&gt;','POTENTIAL_RISK','security-review@1','a.java:1-3','limited']) assert.ok(html.includes(s),s);
  assert.ok(!html.includes('<script>'));
});


test('project or account changes discard stale responses and concurrent reads coalesce', async () => {
  for (const change of ['project','account']) {
    const review=load(); let resolve, calls=0, token='one';
    const state={view:'chat',activeConversationId:1,activeProject:{id:3},currentUser:{id:1}};
    const api={getToken:()=>token,getConversationReviews:()=>{calls++; return new Promise(r=>resolve=r)}};
    let rendered=0; const controller=review.create({api,state,render:()=>rendered++});
    const first=controller.load(), second=controller.load(); await Promise.resolve(); assert.equal(calls,1);
    if(change==='project') state.activeProject={id:4}; else token='two';
    resolve([{id:5,status:'COMPLETED'}]); await Promise.all([first,second]); assert.equal(rendered,0);
  }
});
test('legacy findings and suggestions render distinctly', () => {
  const html=load().renderCard({id:1,status:'COMPLETED',output:{summary:'old',findings:[{file:'old.java',description:'legacy',recommendation:'change'}],suggestions:['consider separation']}});
  for(const value of ['old.java','legacy','change','consider separation']) assert.ok(html.includes(value));
});

test('an old submission cannot unlock a new conversation submission', async () => {
  let resolveFirst, resolveSecond, count=0;
  const window={MarkdownUtils:{escapeHtml:s=>String(s??'')},SkillView:{readSelection:()=>[]},WorkspaceView:{notify(){}},
    ChatReview:{isReview:s=>s.startsWith('/review'),create:()=>({load:async()=>{},invalidate(){}})}};
  const elements={chatReviewSkills:{},chatReviewTools:{querySelectorAll:()=>[]}};
  const context=vm.createContext({window,document:{getElementById:id=>elements[id]||null},crypto:{randomUUID:()=> 'id'},console});
  vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/chatView.js','utf8'),context);
  const api={getToken:()=> 'token',createConversationReview:()=>new Promise(r=>{if(++count===1)resolveFirst=r;else resolveSecond=r}),getChatConversations:async()=>[]};
  const state={activeConversationId:1,activeConversation:{projectId:2},activeProject:{id:2},currentUser:{id:1},isChatSending:false};
  const el={btnSendCentralChat:{disabled:false}};
  const first=window.ChatView.handleSendCentralChatMessage('/review security',state,el,api);
  window.ChatView.resetReviews(state); state.activeConversationId=3;
  const second=window.ChatView.handleSendCentralChatMessage('/review performance',state,el,api);
  resolveFirst({id:1}); await first;
  assert.equal(state.isChatSending,true); assert.equal(el.btnSendCentralChat.disabled,true);
  resolveSecond({id:2}); await second;
  assert.equal(state.isChatSending,false); assert.equal(el.btnSendCentralChat.disabled,false);
});

test('retrying the same uncertain review submission reuses its idempotency key', async () => {
  const requests=[];
  const window={MarkdownUtils:{escapeHtml:s=>String(s??'')},SkillView:{readSelection:()=>[]},WorkspaceView:{notify(){}},
    ChatReview:{isReview:s=>s.startsWith('/review'),create:()=>({load:async()=>{},invalidate(){}})}};
  const elements={chatReviewSkills:{},chatReviewTools:{querySelectorAll:()=>[]}};
  let next=0; const context=vm.createContext({window,document:{getElementById:id=>elements[id]||null},crypto:{randomUUID:()=> 'id-'+(++next)},console});
  vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/chatView.js','utf8'),context);
  const api={getToken:()=> 'token',createConversationReview:async(id,request)=>{requests.push(request); if(requests.length===1) throw Error('network'); return {id:5}},getChatConversations:async()=>[]};
  const state={activeConversationId:1,activeConversation:{projectId:2},activeProject:{id:2},currentUser:{id:1}};
  await window.ChatView.handleSendCentralChatMessage('/review security',state,{},api);
  await window.ChatView.handleSendCentralChatMessage('/review security',state,{},api);
  assert.equal(requests[0].requestId,requests[1].requestId);
});

test('skill limit is enforced by the server rather than a hardcoded client limit', async () => {
  const selected = Array.from({length: 4}, (_, i) => ({id: `skill-${i}`, version: '1'}));
  let request, notice;
  const window={MarkdownUtils:{escapeHtml:s=>String(s??'')},SkillView:{readSelection:()=>selected},WorkspaceView:{notify:message=>notice=message},
    ChatReview:{isReview:s=>s.startsWith('/review')}};
  const elements={chatReviewSkills:{},chatReviewTools:{querySelectorAll:()=>[]}};
  const context=vm.createContext({window,document:{getElementById:id=>elements[id]||null},crypto:{randomUUID:()=> 'id'},console});
  vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/chatView.js','utf8'),context);
  const api={getToken:()=> 'token',createConversationReview:async(id,value)=>{request=value; throw Error('Skill vượt budget')}};
  const state={activeConversationId:1,activeConversation:{projectId:2},activeProject:{id:2},currentUser:{id:1}};
  await window.ChatView.handleSendCentralChatMessage('/review security',state,{},api);
  assert.ok(request, 'selection should reach the server for its configured limit');
  assert.equal(request.skills.length, 4);
  assert.match(notice, /Skill vượt budget/);
});

test('invalidating a conversation aborts the in-flight review read', async () => {
  const review=load(); let signal;
  const state={view:'chat',activeConversationId:1,activeProject:{id:1},currentUser:{id:1}};
  const api={getToken:()=> 'token',getConversationReviews:(id,options)=>{signal=options.signal;return new Promise(()=>{})}};
  const controller=review.create({api,state,render(){}}); controller.load(); await Promise.resolve();
  controller.invalidate(); assert.equal(signal.aborted,true);
});
