const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { JournalStore } = require('../core/store.cjs');
const { createBackup, parseBackup } = require('../core/backups.cjs');
const { generate, parseSuggestions, request } = require('../core/ai.cjs');
function store(t) { const dir=fs.mkdtempSync(path.join(os.tmpdir(),'journal-tasks-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));return new JournalStore(dir); }
test('legacy migration, tasks, profile isolation, persistence and copied identifiers', t => {
  const s=store(t);fs.writeFileSync(s.file,JSON.stringify({version:1,profiles:[{id:'legacy',name:'Legacy',journals:[]}]}));
  const loaded=new JournalStore(path.dirname(s.file));assert.equal(loaded.read().version,2);
  const id=loaded.change('saveTask',{profileId:'legacy',title:'Write',notes:'A page',due:'2028-02-29',subtasks:[{title:'Find pen'}]}).id;
  const step=loaded.read().profiles[0].tasks[0].subtasks[0].id;
  loaded.change('toggleTask',{profileId:'legacy',taskId:id,subtaskId:step});loaded.change('toggleTask',{profileId:'legacy',taskId:id});
  const other=loaded.change('createProfile',{name:'Other'}).id;
  assert.throws(()=>loaded.change('toggleTask',{profileId:other,taskId:id}));
  const before=loaded.read();assert.throws(()=>loaded.change('saveTask',{profileId:'legacy',taskId:id,title:'Write',due:'2027-02-29'}));assert.deepEqual(loaded.read(),before);
  const restored=new JournalStore(path.dirname(s.file));assert.equal(restored.read().profiles[0].tasks[0].done,true);
  const backup=parseBackup(createBackup(restored.read()));loaded.import(backup);
  const copy=loaded.read().profiles[2].tasks[0];assert.notEqual(copy.id,id);assert.notEqual(copy.subtasks[0].id,step);assert.equal(copy.subtasks[0].done,true);
  loaded.change('deleteTask',{profileId:'legacy',taskId:id});assert.equal(loaded.read().profiles[2].tasks.length,1);
});
test('AI supports both fixed HTTPS endpoints and rejects incomplete, malformed and oversized suggestions', async () => {
  const input={provider:'openai',kind:'steps',model:'gpt-4.1-mini',context:'A small project'};
  assert.equal(request(input).body.store,false);assert.throws(()=>request({...input,provider:'https://attacker.invalid'}));
  const items=[{title:'Plan',detail:'Write a short outline.'}];
  const data={status:'completed',output:[{content:[{type:'output_text',text:JSON.stringify({items})}]}]};
  let sent;assert.deepEqual(await generate(input,'test-placeholder',undefined,async(url,options)=>{sent={url,options};return new Response(JSON.stringify(data));}),items);
  assert.equal(sent.url,'https://api.openai.com/v1/responses');assert.equal(sent.options.redirect,'error');
  assert.deepEqual(parseSuggestions('deepseek',{choices:[{finish_reason:'stop',message:{content:JSON.stringify({items})}}]}),items);
  assert.throws(()=>parseSuggestions('openai',{...data,status:'incomplete'}));
  assert.throws(()=>parseSuggestions('deepseek',{choices:[{finish_reason:'stop',message:{content:'not JSON'}}]}));
  await assert.rejects(generate(input,'',undefined),/key/);
  await assert.rejects(generate(input,'test',undefined,async()=>new Response('',{status:401})),/key/);
});
test('key vault refuses plaintext persistence and never returns credentials', t => {
  const s=store(t);const { Preferences }=require('../desktop/preferences.cjs');
  const p=new Preferences(path.dirname(s.file),{isEncryptionAvailable:()=>false});
  assert.throws(()=>p.setKey({provider:'openai',key:'not-a-real-key',remember:true}),/Secure/);
  p.setKey({provider:'openai',key:'not-a-real-key',remember:false});assert.equal(p.key('openai'),'not-a-real-key');assert.equal(JSON.stringify(p.read()).includes('not-a-real-key'),false);assert.equal(fs.readFileSync(p.vault,'utf8'),'{}');
  p.setKey({provider:'openai',key:'',remember:false});assert.equal(p.key('openai'),'');
});
test('tomato timer bounds, pause, resume and expiry use deadlines',()=>{
  const {change,remaining}=require('../core/pomodoro.cjs');
  assert.throws(()=>change(null,{action:'start',minutes:4}));assert.throws(()=>change(null,{action:'start',minutes:31}));
  let timer=change(null,{action:'start',minutes:5,title:'Task'},1000);assert.equal(remaining(timer,61000),240000);
  timer=change(timer,{action:'pause'},61000);assert.equal(remaining(timer,100000),240000);
  timer=change(timer,{action:'resume'},100000);assert.equal(remaining(timer,340000),0);assert.equal(change(timer,{action:'reset'}),null);
});
test('spread templates survive transfer and malformed templates are refused',t=>{
  const s=store(t);const pid=s.change('createProfile',{name:'Templates'}).id;const jid=s.change('createJournal',{profileId:pid,title:'Book',format:'A5',pages:100}).id;
  s.change('addSpread',{profileId:pid,journalId:jid,title:'Month',start:1,end:2,layout:{kind:'calendar',notes:'Notes on the right'}});
  assert.deepEqual(parseBackup(createBackup(s.read())).profiles[0].journals[0].spreads[0].layout,{kind:'calendar',notes:'Notes on the right'});
  assert.throws(()=>s.change('addSpread',{profileId:pid,journalId:jid,title:'Wrong',start:3,end:4,layout:{kind:'execute-code',notes:''}}));
});
test('all requested locales contain the same supported interface keys',()=>{
  const {languages,messages}=require('../core/locales.json');assert.deepEqual(Object.keys(languages).sort(),['en','pl','de','es','es-419','ja','ru','uk','fr'].sort());
  for(const code of Object.keys(languages)){assert.deepEqual(Object.keys(messages[code]).sort(),Object.keys(messages.en).sort());for(const value of Object.values(messages[code]))assert.ok(value.length);}
});
test('AI errors distinguish billing from rate limits without leaking provider echoes', async () => {
  const {providerError}=require('../core/ai.cjs');
  assert.match(providerError('deepseek',402),/credits/);
  assert.match(providerError('openai',429,JSON.stringify({error:{type:'insufficient_quota',message:'secret-key-and-private-context'}})),/credits/);
  assert.match(providerError('openai',429,'{}'),/Too many requests/);
  assert.match(providerError('openai',404),/Model unavailable/);
  assert.match(providerError('deepseek',503),/temporarily unavailable/);
  assert.match(providerError('deepseek',420,'<html>secret-key</html>'),/HTTP 420/);
  assert.doesNotMatch(providerError('openai',401,JSON.stringify({error:{message:'secret-key'}})),/secret-key/);
  await assert.rejects(generate({provider:'deepseek',kind:'steps',context:'Plan'},'fake-key',undefined,async()=>new Response(JSON.stringify({error:{type:'insufficient_quota'}}),{status:429})),/credits/);
});
