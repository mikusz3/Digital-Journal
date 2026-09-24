const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { JournalStore } = require('../core/store.cjs');
const { createBackup, parseBackup } = require('../core/backups.cjs');
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
test('upgrade removes obsolete credentials without touching journals or appearance',t=>{
  const s=store(t);const dir=path.dirname(s.file);const {Preferences}=require('../desktop/preferences.cjs');
  const profile=s.change('createProfile',{name:'Keep me'});const before=fs.readFileSync(s.file,'utf8');
  fs.writeFileSync(path.join(dir,'preferences.json'),JSON.stringify({theme:'Dark',language:'pl',timer:{minutes:5}}));
  for(const name of ['credentials.json','credentials.json.tmp'])fs.writeFileSync(path.join(dir,name),'old encrypted or damaged key data');
  const p=new Preferences(dir);assert.equal(p.read().theme,'Dark');assert.equal(p.read().language,'pl');assert.equal(p.read().timer.minutes,5);
  for(const name of ['credentials.json','credentials.json.tmp'])assert.equal(fs.existsSync(path.join(dir,name)),false);
  assert.equal(fs.readFileSync(s.file,'utf8'),before);assert.equal('keys' in p.read(),false);new Preferences(dir);
});
test('renaming persists only the selected profile name and rejects duplicates or invalid input',t=>{
  const s=store(t);const id=s.change('createProfile',{name:'Old name'}).id;
  const other=s.change('createProfile',{name:'Other'}).id;
  s.change('createJournal',{profileId:id,title:'Keep this',pages:100,format:'A5'});
  s.change('saveTask',{profileId:id,title:'Keep task',notes:'',due:'',subtasks:[]});
  const before=s.read();s.change('renameProfile',{profileId:id,name:'  New name  '});
  const after=s.read();const expected=structuredClone(before);expected.profiles[0].name='New name';assert.deepEqual(after,expected);
  assert.equal(new JournalStore(path.dirname(s.file)).read().profiles[0].name,'New name');
  for(const name of ['', 'other','x'.repeat(81)])assert.throws(()=>s.change('renameProfile',{profileId:id,name}));
  assert.throws(()=>s.change('renameProfile',{profileId:'missing',name:'No'}));assert.deepEqual(s.read(),after);
  s.change('renameProfile',{profileId:id,name:'NEW NAME'});assert.equal(s.read().profiles[1].id,other);
});
