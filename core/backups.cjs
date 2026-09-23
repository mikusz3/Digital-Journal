const { randomUUID } = require('node:crypto');
const { validateState } = require('./journals.cjs');
const MAX_BACKUP_BYTES = 10 * 1024 * 1024;
function createBackup(state) {
  validateState(state);
  const text = JSON.stringify({ format: 'digital-journal', backupVersion: 2, exportedAt: new Date().toISOString(), state }, null, 2) + '\n';
  if (Buffer.byteLength(text, 'utf8') > MAX_BACKUP_BYTES) throw new Error('Backup exceeds the supported 10 MB limit. No backup was written.');
  return text;
}
function parseBackup(text) {
  if (Buffer.byteLength(text, 'utf8') > MAX_BACKUP_BYTES) throw new Error('Backup is larger than the 10 MB limit.');
  let backup;
  try { backup = JSON.parse(text); } catch { throw new Error('This file is not valid JSON. Select a Digital Journal backup.'); }
  if (!((backup?.format === 'digital-bujo' && backup.backupVersion === 1) || (backup?.format === 'digital-journal' && backup.backupVersion === 2))) throw new Error('Unsupported backup format or version.');
  validateState(backup.state);
  if (!backup.state.profiles.length) throw new Error('This backup contains no profiles to import.');
  // Only retain supported fields; imports cannot inject arbitrary settings or paths.
  return { version: 2, profiles: backup.state.profiles.map(p => ({ id: p.id, name: p.name, tasks: (p.tasks || []).map(require('./tasks.cjs').cleanTask), journals: p.journals.map(j => ({
    id: j.id, title: j.title, pages: j.pages, format: j.format, formatDetail: j.formatDetail || '',
    spreads: j.spreads.map(s => ({ id: s.id, title: s.title, start: s.start, end: s.end, ...(s.layout ? { layout: require('./journals.cjs').layout(s.layout) } : {}) })),
  })) })) };
}
function summarize(state) {
  return state.profiles.map(p => ({ name: p.name, tasks: (p.tasks || []).map(require('./tasks.cjs').cleanTask), journals: p.journals.length, spreads: p.journals.reduce((n, j) => n + j.spreads.length, 0) }));
}
function importCopies(current, imported) {
  validateState(imported);
  const next = structuredClone(current);
  for (const profile of imported.profiles) {
    const copy = structuredClone(profile);
    let name = copy.name;
    let count = 1;
    while (next.profiles.some(p => p.name.toLocaleLowerCase() === name.toLocaleLowerCase())) {
      const suffix = ` (import ${count++})`;
      name = copy.name.slice(0, 80 - suffix.length) + suffix;
    }
    copy.name = name;
    copy.id = randomUUID();
    for (const task of copy.tasks || []) { task.id = randomUUID(); for (const step of task.subtasks) step.id = randomUUID(); }
    for (const j of copy.journals) { j.id = randomUUID(); for (const s of j.spreads) s.id = randomUUID(); }
    next.profiles.push(copy);
  }
  return validateState(next);
}
module.exports = { MAX_BACKUP_BYTES, createBackup, parseBackup, summarize, importCopies };
