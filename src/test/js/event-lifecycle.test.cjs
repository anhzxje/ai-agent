const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
test('password visibility has one handler after its shell bindings are replaced',()=>{
 const context=vm.createContext({window:{}});
 vm.runInContext(fs.readFileSync('src/main/resources/static/js/modules/authView.js','utf8'),context);
 const auth=context.window.AuthView.create({});
 const button=new EventTarget();button.querySelector=()=>null;
 const input={type:'password'};const old=new AbortController(),current=new AbortController();
 auth.setupPasswordToggle(button,input,old.signal);old.abort();
 auth.setupPasswordToggle(button,input,current.signal);
 button.dispatchEvent(new Event('click',{cancelable:true}));assert.equal(input.type,'text');
 current.abort();button.dispatchEvent(new Event('click'));assert.equal(input.type,'text');
});
