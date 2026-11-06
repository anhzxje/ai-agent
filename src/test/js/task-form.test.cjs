const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
test('a submitted review from the previous account cannot open its report in the new session',async()=>{
 let token='alice',finish,running,opened=0,closed=0;
 const form={addEventListener:(type,handler)=>{form.submit=handler;},querySelector:()=>null};
 const fields={selectTaskProject:{value:'1'},selectTaskType:{value:'CODE_REVIEW'},btnSubmitNewTask:{disabled:false,textContent:''}};
 const context=vm.createContext({window:{
  apiClient:{getToken:()=>token,createTask:()=>new Promise(resolve=>{finish=resolve;})},
  SkillView:{readSelection:()=>[]},
  WorkspaceView:{singleFlight:()=>fn=>{running=fn();},formError:()=>{}}
 },document:{getElementById:id=>fields[id]||null,querySelectorAll:()=>[]}});
 vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/taskForm.js','utf8'),context);
 context.window.TaskForm.mount({state:{projects:[{id:1,path:'/workspace'}]},el:{formCreateTask:form},closeModals:()=>closed++,loadDatabaseData:async()=>{},selectTask:()=>opened++});
 form.submit({preventDefault(){}});token='bob';finish({id:9});await running;
 assert.equal(opened,0);assert.equal(closed,0);
});


test('task form reads tools only from its own selector', async () => {
 let running,payload;
 const form={addEventListener:(type,handler)=>{form.submit=handler},querySelector:()=>null};
 const fields={selectTaskProject:{value:'1'},selectTaskType:{value:'CODE_REVIEW'},btnSubmitNewTask:{},taskToolsContainer:{querySelectorAll:()=>[{value:'file_system'}]}};
 const context=vm.createContext({window:{apiClient:{getToken:()=> 'token',createTask:async p=>{payload=p;return {id:1}}},SkillView:{readSelection:()=>[]},WorkspaceView:{singleFlight:()=>fn=>{running=fn()},formError:()=>{}}},document:{getElementById:id=>fields[id]||null,querySelectorAll:()=>[{value:'file_system'},{value:'chat-only'}]}});
 vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/taskForm.js','utf8'),context);
 context.window.TaskForm.mount({state:{projects:[{id:1}]},el:{formCreateTask:form},closeModals(){},loadDatabaseData:async()=>{},selectTask(){}});
 form.submit({preventDefault(){}}); await running;
 assert.deepEqual(Array.from(payload.selectedTools),['file_system']);
});
