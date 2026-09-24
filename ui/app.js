const root = document.querySelector('#app');
const dialog = document.querySelector('#form-dialog');
let state;
let profileId = null;
let journalId = null;
let noticeTimer;
const escapeHtml = value => String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const brand = '<div class="brand"><img src="icon.svg" alt="">Digital Journal</div>';
const local = '<span class="local-pill">Saved on this device</span><button class="quiet backup-button" data-backups>Backups</button>';
const currentProfile = () => state.profiles.find(p => p.id === profileId);
const currentJournal = () => currentProfile()?.journals.find(j => j.id === journalId);

function notify(message) {
  clearTimeout(noticeTimer);
  document.querySelector('#notice').textContent = message;
  noticeTimer = setTimeout(() => { document.querySelector('#notice').textContent = ''; }, 4500);
}
function headingFocus() {
  const heading = root.querySelector('h1');
  if (heading) { heading.tabIndex = -1; heading.focus({ preventScroll: true }); }
}
function profileFields() {
  return '<label>Full name<input name="name" autocomplete="name" maxlength="80" required placeholder="Your name"><span class="field-help">Only stored here. You can also use a name you prefer.</span></label>';
}
function formFooter(button) {
  return `<p class="error" role="alert"></p><div class="form-actions"><button type="button" data-close>Cancel</button><button class="primary" type="submit">${button}</button></div>`;
}
function showForm(title, description, html, onSave) {
  document.querySelector('#dialog-body').innerHTML = `<h2 id="dialog-title">${title}</h2><p>${description}</p><form class="form-stack">${html}</form>`;
  const form = dialog.querySelector('form');
  form.querySelector('[data-close]').addEventListener('click', () => dialog.close());
  bindForm(form, onSave);
  dialog.showModal();
}
function bindForm(form, onSave) {
  let busy = false;
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (busy) return;
    busy = true;
    const submit = form.querySelector('[type=submit]');
    submit.disabled = true;
    form.querySelector('.error').textContent = '';
    try { await onSave(Object.fromEntries(new FormData(form))); }
    catch (error) { form.querySelector('.error').textContent = error.message; }
    finally { submit.disabled = false; busy = false; }
  });
}
async function save(action, input) {
  const result = await window.bujo[action](input);
  if (!result.ok) throw new Error(result.error);
  state = result.state;
  if (action === 'deleteTask' || action === 'deleteProfile') { focusTimer = (await call('timer')).timer; renderTimer(); }
  return result.id;
}
async function addProfile(input) {
  profileId = await save('createProfile', input);
  journalId = null;
  if (dialog.open) dialog.close();
  render(); headingFocus(); notify('Your space is ready.');
}
function newProfile() {
  showForm('A space of your own', 'No email, password or online account needed.', profileFields() + formFooter('Create profile'), addProfile);
}
function deleteProfile(id) {
  const profile = state.profiles.find(p => p.id === id);
  if (!profile) return;
  const count = profile.journals.reduce((n, j) => n + j.spreads.length, 0);
  showForm('Delete this profile?', `This permanently removes ${escapeHtml(profile.name)} and its ${profile.journals.length} journals (${count} spreads) from this device. Export a backup first if you want to keep them. Existing backup files are not deleted.`, `<label>Type the profile name to confirm<input name="confirmName" maxlength="80" autocomplete="off" required placeholder="${escapeHtml(profile.name)}"></label>${formFooter('Delete profile')}`, async input => {
    await save('deleteProfile', { profileId: id, confirmName: input.confirmName });
    profileId = null; journalId = null;
    dialog.close(); render(); headingFocus(); notify('Profile deleted from this device.');
  });
  dialog.querySelector('[type=submit]').classList.add('danger');
}
function wireBackups() {
  const header = root.querySelector('header');
  for (const [label,action] of [['Settings',settings],['About',about],...(currentProfile() ? [['Tasks & plans',tasks]] : [])]) { const b = document.createElement('button'); b.textContent=label; b.className='quiet'; b.onclick=action; header?.append(b); }
  root.querySelectorAll('[data-backups]').forEach(b => b.addEventListener('click', backups)); }
function render() {
  if (!state.profiles.length) return renderWelcome();
  if (!currentProfile()) return renderProfiles();
  const profile = currentProfile();
  if (!currentJournal()) journalId = profile.journals[0]?.id || null;
  root.innerHTML = `<div class="shell"><aside class="sidebar">${brand}<div><p class="side-label">YOUR PAPER JOURNALS</p><nav class="journal-nav" aria-label="Journals">${profile.journals.map(j => `<button class="journal-button ${j.id === journalId ? 'selected' : ''}" data-journal="${j.id}" ${j.id === journalId ? 'aria-current="page"' : ''}><span class="book-glyph" aria-hidden="true">▤</span><span>${escapeHtml(j.title)}<small>${escapeHtml(j.format === 'Custom' ? j.formatDetail : j.format)} · ${j.pages} pages</small></span></button>`).join('')}</nav><button class="quiet" id="new-journal-side">+ Add journal</button></div><div class="sidebar-bottom"><button class="profile-button quiet" id="profiles"><span class="avatar">${escapeHtml(profile.name.charAt(0).toUpperCase())}</span><span class="profile-name" data-user-content>${escapeHtml(profile.name)}<small>Switch or add profile</small></span></button></div></aside><main class="workspace"><header class="topbar"><span>Your companion to pen & paper</span>${local}</header><div id="journal-content"></div></main></div>`;
  root.querySelector('#new-journal-side').addEventListener('click', () => newJournal());
  root.querySelector('#profiles').addEventListener('click', () => { profileId = null; journalId = null; render(); headingFocus(); });
  root.querySelectorAll('[data-journal]').forEach(button => button.addEventListener('click', () => { journalId = button.dataset.journal; render(); headingFocus(); }));
  wireBackups();
  renderJournal();
}
function renderWelcome() {
  root.innerHTML = `<main class="welcome"><header class="welcome-head">${brand}<span><span class="local-pill">Local & personal</span><button class="quiet" data-backups>Import backup</button></span></header><div class="welcome-grid"><section class="welcome-copy"><p class="eyebrow">A little space, just for you</p><h1>Your journal.<br>Your own pace.</h1><p>A quiet companion for the pages you put pen to. Keep track of your paper journals and leave room for whatever comes next.</p><p class="small">No streaks to keep. No catching up required.</p></section><section class="card"><h2>Make yourself at home</h2><p class="muted small">Start with a local profile. Your journals stay on this device.</p><form class="form-stack">${profileFields()}<p class="error" role="alert"></p><button class="primary" type="submit">Create my space</button></form></section></div></main>`;
  bindForm(root.querySelector('form'), addProfile);
  wireBackups();
}
function renderProfiles() {
  root.innerHTML = `<main class="welcome"><header class="welcome-head">${brand}${local}</header><p class="eyebrow">Make yourself at home</p><h1>Whose journal shelf?</h1><p class="muted">Each profile has its own space and paper journals.</p><div class="profiles">${state.profiles.map(p => `<div class="profile-entry"><button class="profile-tile" data-profile="${p.id}" aria-label="${escapeHtml(p.name)}"><span class="avatar">${escapeHtml(p.name.charAt(0).toUpperCase())}</span><span data-user-content>${escapeHtml(p.name)}</span><small>${p.journals.length} journal${p.journals.length === 1 ? '' : 's'}</small></button><button class="quiet danger-text" data-delete-profile="${p.id}" aria-label="Delete profile ${escapeHtml(p.name)}">Delete profile</button></div>`).join('')}<button class="profile-tile" id="add-profile"><span class="avatar">+</span><span>Create a profile</span><small>A fresh, separate space</small></button></div><p class="gentle-note">Profiles organize journals; they are not password-protected accounts.</p></main>`;
  wireBackups();
  root.querySelector('#add-profile').addEventListener('click', newProfile);
  root.querySelectorAll('[data-delete-profile]').forEach(b => b.addEventListener('click', () => deleteProfile(b.dataset.deleteProfile)));
  root.querySelectorAll('[data-profile]').forEach(button => button.addEventListener('click', () => { profileId = button.dataset.profile; journalId = null; render(); headingFocus(); }));
}
function renderJournal() {
  const content = root.querySelector('#journal-content');
  const journal = currentJournal();
  if (!journal) {
    content.innerHTML = '<p class="eyebrow">Your journal shelf</p><h1>A place for your paper pages.</h1><section class="card empty"><div class="empty-mark" aria-hidden="true">▤</div><h2>Start with a journal</h2><p>Add the notebook beside you. You can bring in your existing page index, or start with a blank one.</p><button class="primary" id="first-journal">+ Add your first journal</button></section><p class="gentle-note">The writing stays on paper. This is just a little help finding your way back.</p>';
    content.querySelector('#first-journal').addEventListener('click', () => newJournal());
    return;
  }
  const used = journal.spreads.reduce((n, s) => n + s.end - s.start + 1, 0);
  content.innerHTML = `<section class="intro"><div><p class="eyebrow">Your paper journal, at a glance</p><h1 data-user-content>${escapeHtml(journal.title)}</h1><p class="muted small">${escapeHtml(journal.format === 'Custom' ? journal.formatDetail : journal.format)} notebook · ${journal.pages} pages · room to make it yours</p></div><div class="journal-actions"><button id="edit-journal">Edit journal</button><button class="primary" id="new-spread">+ Add spread</button></div></section><div class="stats"><div class="stat"><strong>${journal.spreads.length}</strong><span>Spreads in your index</span></div><div class="stat"><strong>${used}</strong><span>Pages with a home</span></div><div class="stat"><strong>${journal.pages - used}</strong><span>Pages still open</span></div></div><section class="index-card"><div class="index-head"><h2>Page index</h2><input type="search" aria-label="Find a spread" placeholder="Find a spread…"></div><div id="spread-list"></div></section><p class="gentle-note">An open page is an invitation, never an obligation.</p>`;
  content.querySelector('#edit-journal').addEventListener('click', () => newJournal(journal));
  content.querySelector('#new-spread').addEventListener('click', () => newSpread());
  content.querySelector('input[type=search]').addEventListener('input', e => renderSpreads(e.target.value));
  renderSpreads('');
}
function renderSpreads(query) {
  const journal = currentJournal();
  const spreads = [...journal.spreads].sort((a, b) => a.start - b.start).filter(s => `${s.title} ${s.start} ${s.end}`.toLocaleLowerCase().includes(query.toLocaleLowerCase().trim()));
  root.querySelector('#spread-list').innerHTML = spreads.length ? `<div role="table" aria-label="Journal spreads"><div class="table-head" role="row"><span role="columnheader">Pages</span><span role="columnheader">Spread</span><span role="columnheader">Length</span></div>${spreads.map(s => `<div class="spread-row" role="row"><span role="cell"><span class="page-tag">${s.start === s.end ? s.start : `${s.start}–${s.end}`}</span></span><span class="spread-title" role="cell" aria-label="${escapeHtml(s.title)}"><span data-user-content>${escapeHtml(s.title)}</span><button class="edit-spread quiet" data-view-layout="${s.id}" aria-label="View template ${escapeHtml(s.title)}">Template</button><button class="edit-spread quiet" data-edit-spread="${s.id}" aria-label="Edit ${escapeHtml(s.title)}">Edit</button></span><span class="spread-count" role="cell">${s.end - s.start + 1} ${s.start === s.end ? 'page' : 'pages'}</span></div>`).join('')}</div>` : `<div class="empty"><h2>${query ? 'No matching spreads' : 'Your index starts here'}</h2><p>${query ? 'Try a different title or page number.' : 'Give a page or a pair of pages a name. Add only what is useful to you.'}</p></div>`;
  root.querySelectorAll('[data-view-layout]').forEach(b => b.onclick = () => viewLayout(journal.spreads.find(s => s.id === b.dataset.viewLayout)));
  root.querySelectorAll('[data-edit-spread]').forEach(b => b.addEventListener('click', () => newSpread(journal.spreads.find(s => s.id === b.dataset.editSpread))));
}
function newJournal(existing = null) {
  showForm(existing ? 'Edit paper journal' : 'Add a paper journal', 'A small map of the notebook you already use.', `<label ${existing ? 'hidden' : ''}>Starting index<select name="template"><option value="blank">Blank — I’ll add my own spreads</option><option value="main">My Bullet Journal 2026/2027/2028</option><option value="dlc">My Bullet Journal DLC</option></select></label><label>Journal title<input name="title" maxlength="120" required placeholder="e.g. Everyday journal 2029"></label><div class="form-row"><label>Page count<input name="pages" type="number" min="1" max="10000" step="1" value="200" required></label><label>Notebook size<select name="format"><option>A5</option><option>A6</option><option>B6</option><option>B5</option><option>Custom</option></select></label></div><label id="custom-size" hidden>Custom size<input name="formatDetail" maxlength="60" placeholder="e.g. 15 × 21 cm"></label><p class="field-help" id="template-note">You can use any year, page count or notebook size.</p>${formFooter(existing ? 'Save journal' : 'Add journal')}`, async input => {
    journalId = await save(existing ? 'editJournal' : 'createJournal', { ...input, pages: Number(input.pages), profileId, journalId: existing?.id });
    dialog.close(); render(); headingFocus(); notify(existing ? 'Journal updated.' : 'Journal added to your shelf.');
  });
  const form = dialog.querySelector('form');
  if (existing) {
    for (const field of ['title', 'pages', 'format', 'formatDetail']) form.elements[field].value = existing[field] || '';
    form.querySelector('#custom-size').hidden = existing.format !== 'Custom';
    form.elements.formatDetail.required = existing.format === 'Custom';
  }
  form.elements.template.addEventListener('change', () => {
    const template = form.elements.template.value;
    if (template !== 'blank') {
      form.elements.title.value = template === 'main' ? 'Bullet Journal 2026/2027/2028' : 'Bullet Journal DLC';
      form.elements.pages.value = Math.max(200, Number(form.elements.pages.value) || 200);
    }
    form.querySelector('#template-note').textContent = template === 'blank' ? 'You can use any year, page count or notebook size.' : 'Uses your document’s exact page ranges. Unnamed pages stay open. Check the notebook size before saving.';
  });
  form.elements.format.addEventListener('change', () => {
    const custom = form.elements.format.value === 'Custom';
    form.querySelector('#custom-size').hidden = !custom;
    form.elements.formatDetail.required = custom;
  });
}
function newSpread(existing = null, preset = {}) {
  const journal = currentJournal();
  let first = 1;
  for (const s of [...journal.spreads].sort((a, b) => a.start - b.start)) { if (s.start > first) break; first = s.end + 1; }
  const suggestion = first <= journal.pages ? first : '';
  showForm(existing ? 'Edit spread' : 'Give your pages a home', 'Add a name and the page numbers from your paper journal.', `<label>Template<select name="layoutKind" aria-label="Template"><option value="blank">Blank</option><option value="calendar">Calendar with notes</option><option value="tracker">Tracker</option><option value="log">Log</option><option value="wishlist">Wishlist</option><option value="custom">Custom</option></select></label><label>Layout notes<textarea name="layoutNotes" maxlength="2000"></textarea></label><label>Spread title<input name="title" maxlength="120" required placeholder="e.g. Small things I’m grateful for"></label><div class="form-row"><label>First page<input name="start" type="number" min="1" max="${journal.pages}" step="1" value="${suggestion}" required></label><label>Last page<input name="end" type="number" min="1" max="${journal.pages}" step="1" value="${suggestion}" required></label></div><p class="field-help">For one page, use the same number twice. This journal has ${journal.pages} pages.</p>${formFooter(existing ? 'Save spread' : 'Add spread')}`, async input => {
    await save(existing ? 'editSpread' : 'addSpread', { ...input, start: Number(input.start), end: Number(input.end), profileId, journalId, spreadId: existing?.id, layout: { kind: input.layoutKind, notes: input.layoutNotes } });
    dialog.close(); render(); headingFocus(); notify('Spread saved. A little easier to find now.');
  });
  dialog.querySelector('[name=layoutKind]').value = existing?.layout?.kind || preset.kind || 'blank';
  dialog.querySelector('[name=layoutNotes]').value = existing?.layout?.notes || preset.notes || '';
  if (existing) {
    const form = dialog.querySelector('form');
    for (const field of ['title', 'start', 'end']) form.elements[field].value = existing[field];
  }
}
function backups() {
  showForm('Keep a copy of your journals', 'Export includes every local profile. Imported profiles are added as separate copies; existing journals are never replaced.', '<button type="button" id="export-backup">Export all profiles</button><button type="button" id="import-backup">Choose backup to import</button><p class="field-help">Backups contain names and journal content in plain text. Keep them somewhere you trust.</p><p class="error" role="alert"></p><div id="import-preview"></div><div class="form-actions"><button type="button" data-close>Close</button><button class="primary" type="submit" hidden>Import as new profiles</button></div>', async () => {
    const result = await window.bujo.confirmImport(dialog.dataset.importToken);
    if (!result.ok) throw new Error(result.error);
    state = result.state; profileId = null; journalId = null;
    dialog.close(); render(); headingFocus(); notify('Backup imported as separate profiles.');
  });
  dialog.addEventListener('close', () => { delete dialog.dataset.importToken; window.bujo.cancelImport(); }, { once: true });
  const error = dialog.querySelector('.error');
  async function run(action) {
    const buttons = [...dialog.querySelectorAll('button')];
    buttons.forEach(b => { b.disabled = true; });
    error.textContent = '';
    try {
      if (action === 'previewImport') {
        dialog.querySelector('[type=submit]').hidden = true;
        dialog.querySelector('#import-preview').textContent = '';
        delete dialog.dataset.importToken;
      }
      const result = await window.bujo[action]();
      if (!result.ok) throw new Error(result.error);
      if (result.canceled) return;
      if (action === 'exportBackup') { notify('Backup exported.'); return; }
      dialog.dataset.importToken = result.token;
      dialog.querySelector('#import-preview').innerHTML = '<h3>Profiles to add</h3><ul>' + result.profiles.map(p => `<li>${escapeHtml(p.name)} — ${p.journals} journals, ${p.spreads} spreads</li>`).join('') + '</ul><p class="field-help">Matching names receive an “import” suffix. This adds copies, not synchronization.</p>';
      dialog.querySelector('[type=submit]').hidden = false;
    } catch (e) { error.textContent = e.message; }
    finally { buttons.forEach(b => { b.disabled = false; }); }
  }
  dialog.querySelector('#export-backup').addEventListener('click', () => run('exportBackup'));
  dialog.querySelector('#import-backup').addEventListener('click', () => run('previewImport'));
}
async function start() {
  try {
    if (!window.bujo) throw new Error('Open Digital Journal using its desktop launcher. This page needs the local app to save your journals.');
    const result = await window.bujo.read();
    if (!result.ok) throw new Error(result.error);
    state = result.state;
    preferences = (await call('preferences')).preferences; applyAppearance();
    if (state.profiles.length === 1) profileId = state.profiles[0].id;
    render();
  } catch (error) {
    root.innerHTML = `<main class="card fatal"><h1>Your journals need attention</h1><p>${escapeHtml(error.message)}</p><p class="muted">No data has been replaced. Close the app and resolve the issue before trying again.</p></main>`;
  }
}
// Wait for all deferred feature/translation scripts before initializing the app.
window.addEventListener('DOMContentLoaded', start, { once: true });
