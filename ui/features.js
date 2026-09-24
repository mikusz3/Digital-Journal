let preferences = {};
const credits = 'Digital Journal is an independent, fan-made application developed for personal use and offered without profit. The Bullet Journal method was created by Ryder Carroll. Bullet Journal® and BuJo® are trademarks of Lightcage, LLC. This project is not affiliated with, sponsored by, or endorsed by Ryder Carroll or Lightcage, LLC. Learn about the original method at bulletjournal.com. Theme names describe visual inspiration; no third-party artwork is included.';
async function call(action, input) { const r = await window.bujo[action](input); if (!r.ok) throw new Error(r.error); return r; }
function applyAppearance() {
  document.documentElement.lang = preferences.language || 'en';
  if (typeof localize === 'function') localize();
  if (preferences.reduceMotion) document.querySelectorAll('.confetti').forEach(e => e.remove());
  const colors = preferences.theme === 'Custom' ? preferences.custom : preferences.themes?.[preferences.theme || 'Light'];
  if (!colors) return;
  for (const [name, value] of Object.entries(colors)) document.documentElement.style.setProperty(`--${name}`, value);
  document.documentElement.dataset.theme = preferences.theme || 'Light';
  document.body.style.backgroundImage = preferences.wallpaper ? `linear-gradient(#000000${Math.round((preferences.dim || 0) * 2.55).toString(16).padStart(2,'0')},#000000${Math.round((preferences.dim || 0) * 2.55).toString(16).padStart(2,'0')}), url("${preferences.wallpaper}")` : preferences.gradient ? `linear-gradient(135deg,${colors.background},${preferences.gradient})` : 'none';
}
async function settings() {
  try { preferences = (await call('preferences')).preferences; } catch (e) { notify(e.message); return; }
  const colors = preferences.custom || preferences.themes.Custom;
  showForm('Settings', 'Appearance stays on this device.', `<label>Language<select name="language" aria-label="Language">${Object.entries(preferences.locales.languages).map(([code,label]) => `<option value="${code}">${label}</option>`).join('')}</select></label><label>Theme<select name="theme" aria-label="Theme">${Object.keys(preferences.themes).map(n => `<option>${escapeHtml(n)}</option>`).join('')}</select></label><fieldset><legend>Custom palette</legend><div class="colors">${Object.entries(colors).map(([k,v]) => `<label>${k}<input type="color" name="${k}" value="${v}"></label>`).join('')}</div></fieldset><label>Gradient end color (optional)<input name="gradient" placeholder="#ffffff" pattern="#[0-9a-fA-F]{6}" value="${preferences.gradient || ''}"></label><label>Wallpaper dimming (%)<input name="dim" type="number" min="0" max="90" value="${preferences.dim || 0}"></label><div class="form-row"><button type="button" id="wallpaper">Choose wallpaper</button><button type="button" id="clear-wallpaper">Remove wallpaper</button></div><label class="check"><input type="checkbox" name="reduceMotion" ${preferences.reduceMotion ? 'checked' : ''}>Reduce motion</label>${formFooter('Save appearance')}`, async values => {
    preferences = (await call('savePreferences', { ...values, custom: Object.fromEntries(Object.keys(colors).map(k => [k,values[k]])), reduceMotion: !!values.reduceMotion })).preferences;
    applyAppearance(); dialog.close(); notify('Appearance saved.');
  });
  dialog.querySelector('[name=language]').value = preferences.language || 'en';
  dialog.querySelector('[name=theme]').value = preferences.theme || 'Light';
  for (const [id,value] of [['wallpaper',null],['clear-wallpaper','clear']]) dialog.querySelector('#'+id).onclick = async () => { try { preferences = (await call('wallpaper', value)).preferences; applyAppearance(); } catch (e) { dialog.querySelector('.error').textContent = e.message; } };
}
function about() { showForm('About Digital Journal', credits, '<p class="field-help">Version 0.5.0 · Local journals, your own ideas. Source: github.com/mikusz3/Digital-Journal</p><p class="field-help">Compatibility: Android can generate and scan QR links. Reading-app integration and printer support are documented investigations; collections and printing are scheduled for later.</p>' + formFooter('Close'), async () => dialog.close()); }
function celebrate() {
  if (preferences.reduceMotion || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const burst = document.createElement('div'); burst.className = 'confetti'; burst.setAttribute('aria-hidden','true');
  for (let i=0;i<24;i++) { const piece = document.createElement('i'); piece.style.left = `${Math.random()*100}%`; piece.style.background = ['#dfb44c','#4faa83','#818cff'][i%3]; piece.style.animationDelay = `${Math.random()*.2}s`; burst.append(piece); }
  document.body.append(burst); setTimeout(() => burst.remove(),1700);
}
function tasks() {
  const content = root.querySelector('#journal-content'); if (!content) return;
  const all = currentProfile().tasks || [];
  content.innerHTML = `<div class="intro"><h1>Tasks & plans</h1><button id="add-task" class="primary">Add task</button></div><p class="muted">A small next step counts. Dates and subtasks are optional.</p><label>Show<select id="task-filter"><option value="all">All tasks</option><option value="open">Open</option><option value="done">Completed</option></select></label><div id="task-list"></div>`;
  function list() {
    const filter = content.querySelector('#task-filter').value;
    content.querySelector('#task-list').innerHTML = all.filter(t => filter === 'all' || t.done === (filter === 'done')).map(t => `<article class="card task"><div class="task-heading"><button data-toggle-task="${t.id}" aria-label="${t.done ? 'Reopen' : 'Complete'} ${escapeHtml(t.title)}">${t.done ? '✓' : '○'}</button><h2 data-user-content>${escapeHtml(t.title)}</h2></div><p class="muted">${escapeHtml(t.due || 'No date')}</p><p class="task-notes">${escapeHtml(t.notes)}</p>${t.subtasks.map(s => `<label class="check" data-user-content><input type="checkbox" data-task="${t.id}" data-step="${s.id}" ${s.done ? 'checked' : ''}>${escapeHtml(s.title)}</label>`).join('')}<div class="journal-actions"><button data-timer-task="${t.id}">🍅 Focus timer</button><button data-edit-task="${t.id}">Edit task</button><button class="danger-text" data-delete-task="${t.id}">Delete task</button></div></article>`).join('') || '<p class="gentle-note">No tasks here. Add only what is useful to you.</p>';
    content.querySelectorAll('[data-toggle-task],[data-step]').forEach(b => b.onclick = async () => { try { const task = all.find(t => t.id === (b.dataset.toggleTask || b.dataset.task)); const done = b.dataset.step ? task.subtasks.find(s => s.id === b.dataset.step).done : task.done; await save('toggleTask',{profileId, taskId:task.id, subtaskId:b.dataset.step}); tasks(); if (!done) celebrate(); } catch(e) { notify(e.message); } });
    content.querySelectorAll('[data-timer-task]').forEach(b => b.onclick = () => timerForm(all.find(t => t.id === b.dataset.timerTask)));
    content.querySelectorAll('[data-edit-task]').forEach(b => b.onclick = () => taskForm(all.find(t => t.id === b.dataset.editTask)));
    content.querySelectorAll('[data-delete-task]').forEach(b => b.onclick = () => showForm('Delete task?', 'This removes the task and its subtasks from this profile.', formFooter('Delete task'), async () => { await save('deleteTask',{profileId,taskId:b.dataset.deleteTask}); dialog.close(); tasks(); }));
  }
  content.querySelector('#add-task').onclick = () => taskForm(); content.querySelector('#task-filter').onchange = list; list();
}
function taskForm(existing, added = []) {
  const steps = [...(existing?.subtasks || []), ...added.map(title => ({title}))];
  showForm(existing ? 'Edit task' : 'Add task', 'Subtasks: one small step per line. Completing a task does not change its subtasks.', `<label>Task title<input name="title" maxlength="120" required value="${escapeHtml(existing?.title || '')}"></label><label>Planned date<input name="due" type="date" value="${existing?.due || ''}"></label><label>Notes<textarea name="notes" maxlength="4000">${escapeHtml(existing?.notes || '')}</textarea></label><label>Subtasks<textarea name="steps" rows="6">${escapeHtml(steps.map(s=>s.title).join('\n'))}</textarea></label>${formFooter('Save task')}`, async v => {
    const lines = v.steps.split('\n').map(s=>s.trim()).filter(Boolean); const used = new Set();
    const subtasks = lines.map((title,i) => { const old = steps.find(s => s.title === title && !used.has(s.id)) || (steps[i] && !lines.includes(steps[i].title) && !used.has(steps[i].id) ? steps[i] : null); if (old?.id) used.add(old.id); return {id:old?.id,title}; });
    await save('saveTask',{profileId, taskId:existing?.id, title:v.title, notes:v.notes, due:v.due, subtasks}); dialog.close(); tasks();
  });
}

function viewLayout(spread) {
  const kind = spread.layout?.kind || 'blank';
  const cells = (labels, count) => `<div class="layout-grid columns-${count}">${labels.map(x=>`<div>${escapeHtml(x)}</div>`).join('')}</div>`;
  const content = kind === 'calendar' ? '<p>Month / Year · Start the grid on the weekday that fits your month.</p>'+cells(['Mon','Tue','Wed','Thu','Fri','Sat','Sun',...Array(35).fill('')],7)+'<h3>Notes and reminders</h3>' : kind === 'tracker' ? '<h3>What I want to track</h3>'+cells(Array.from({length:31},(_,i)=>String(i+1)),7)+'<h3>Observations</h3>' : kind === 'log' ? cells(['Date','Entry','Reflection',...Array(15).fill('')],3) : kind === 'wishlist' ? cells(['Item or experience','Why it matters','Notes',...Array(15).fill('')],3) : '<p>A blank page for your own layout.</p>';
  showForm('Spread template', 'A guide to drawing this spread on paper.', `<h3 data-user-content>${escapeHtml(spread.title)}</h3>${content}<p class="task-notes">${escapeHtml(spread.layout?.notes || '')}</p>${formFooter('Close')}`, async()=>dialog.close());
}
let focusTimer = null;
let timerFinished = '';
function timerForm(task) {
  showForm('🍅 Tomato focus timer', 'Choose 5–30 minutes for this task. Starting replaces your current timer. No streaks and no penalty for stopping.', `<p data-user-content>${escapeHtml(task.title)}</p><label>Minutes<input type="number" name="minutes" min="5" max="30" step="1" value="25" required></label>${formFooter('Start focus')}`,async v=>{
    focusTimer=(await call('timer',{action:'start',profileId,taskId:task.id,minutes:Number(v.minutes)})).timer;timerFinished='';dialog.close();renderTimer();
  });
}
function renderTimer() {
  let panel=document.querySelector('#focus-timer');
  if(!focusTimer){panel?.remove();return;}
  if(!panel){panel=document.createElement('aside');panel.id='focus-timer';panel.setAttribute('aria-label','Focus timer');document.body.append(panel);}
  const left=focusTimer.running?Math.max(0,focusTimer.deadline-Date.now()):focusTimer.remaining;
  const seconds=Math.ceil(left/1000);const clock=`${Math.floor(seconds/60).toString().padStart(2,'0')}:${(seconds%60).toString().padStart(2,'0')}`;
  if(!panel.querySelector('strong')) {
    panel.innerHTML='<span aria-hidden="true">🍅</span><span data-timer-title data-user-content></span><strong></strong><button data-pause-timer></button><button data-reset-timer>Stop timer</button>';
    panel.querySelector('[data-pause-timer]').onclick=async()=>{focusTimer=(await call('timer',{action:focusTimer.running?'pause':'resume'})).timer;renderTimer()};
    panel.querySelector('[data-reset-timer]').onclick=async()=>{focusTimer=(await call('timer',{action:'reset'})).timer;renderTimer()};
  }
  panel.querySelector('[data-timer-title]').textContent=focusTimer.title;
  panel.querySelector('strong').textContent=clock;
  const pause=panel.querySelector('[data-pause-timer]');pause.textContent=focusTimer.running?'Pause':'Resume';pause.disabled=!left;
  if(!left && timerFinished!==String(focusTimer.deadline)){timerFinished=String(focusTimer.deadline);notify('Focus session finished. Take a gentle break.');}
}
setInterval(()=>{if(focusTimer)renderTimer()},1000);
call('timer').then(r=>{focusTimer=r.timer;renderTimer()}).catch(()=>{});
