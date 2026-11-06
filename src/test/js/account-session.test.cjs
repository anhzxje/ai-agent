const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
test('preferences saved by a previous account cannot apply its theme to the current session',async()=>{
 let token='alice',finish,handler,applied=0;
 const form={addEventListener:(event,fn)=>{handler=fn;}};
 const fields={formUserPreferences:form,prefTheme:{value:'light'},prefLanguage:{value:'vi'},prefModel:{value:'approved'}};
 const context=vm.createContext({window:{WorkspaceView:{notify(){},formError(){}}},document:{getElementById:id=>fields[id]||null}});
 vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/accountView.js','utf8'),context);
 context.window.AccountView.mount({api:{getToken:()=>token,updateMyPreferences:()=>new Promise(resolve=>{finish=resolve;})},closeModals(){},applyTheme:()=>applied++});
 const pending=handler({preventDefault(){}});token='bob';finish({});await pending;
 assert.equal(applied,0);
});
