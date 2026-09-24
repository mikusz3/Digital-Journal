const { randomUUID } = require('node:crypto');
const templates = require('./templates.json');

const FORMATS = ['A6', 'B6', 'A5', 'B5', 'Custom'];
function label(value, field, max = 120) {
  if (typeof value !== 'string' || !value.trim()) throw new Error(`${field} is required.`);
  const result = value.trim();
  if (result.length > max) throw new Error(`${field} must be ${max} characters or fewer.`);
  return result;
}
function integer(value, field, min, max) {
  if (!Number.isInteger(value) || value < min || value > max) throw new Error(`${field} must be a whole number from ${min} to ${max}.`);
  return value;
}
function freshState() { return { version: 2, profiles: [] }; }
function getProfile(state, id) {
  const profile = state.profiles.find(p => p.id === id);
  if (!profile) throw new Error('That profile could not be found. Please select a profile again.');
  return profile;
}
function getJournal(state, profileId, journalId) {
  const journal = getProfile(state, profileId).journals.find(j => j.id === journalId);
  if (!journal) throw new Error('That journal does not belong to this profile.');
  return journal;
}
function layout(input) {
  if (input == null) return { kind: 'blank', notes: '' };
  if (!['blank','calendar','tracker','log','wishlist','custom'].includes(input.kind) || typeof input.notes !== 'string' || input.notes.length > 2000) throw new Error('Choose a spread template and keep layout notes under 2000 characters.');
  return { kind: input.kind, notes: input.notes };
}
function validateSpread(input, pages) {
  return {
    title: label(input.title, 'Spread title'),
    layout: layout(input.layout),
    start: integer(input.start, 'First page', 1, pages),
    end: integer(input.end, 'Last page', 1, pages),
  };
}
function mutate(state, action, input = {}) {
  if (!input || typeof input !== 'object') throw new Error('Invalid input.');
  if (action === 'saveTask' || action === 'toggleTask' || action === 'deleteTask') return require('./tasks.cjs').changeTask(getProfile(state, input.profileId), action, input);
  if (action === 'deleteProfile') {
    const profile = getProfile(state, input.profileId);
    if (input.confirmName !== profile.name) throw new Error('Type the profile name exactly to confirm deletion.');
    state.profiles = state.profiles.filter(p => p.id !== profile.id);
    return profile.id;
  }
  if (action === 'renameProfile') {
    const profile = getProfile(state, input.profileId);
    const name = label(input.name, 'Name', 80);
    if (state.profiles.some(p => p.id !== profile.id && p.name.toLocaleLowerCase() === name.toLocaleLowerCase())) throw new Error('A profile with that name already exists. Choose a different name.');
    profile.name = name; return profile.id;
  }
  if (action === 'createProfile') {
    const name = label(input.name, 'Name', 80);
    if (state.profiles.some(p => p.name.toLocaleLowerCase() === name.toLocaleLowerCase())) throw new Error('A profile with that name already exists. Choose a different name.');
    const profile = { id: randomUUID(), name, journals: [] };
    state.profiles.push(profile);
    return profile.id;
  }
  if (action === 'createJournal') {
    const profile = getProfile(state, input.profileId);
    const title = label(input.title, 'Journal title');
    const pages = integer(input.pages, 'Page count', 1, 10000);
    if (!FORMATS.includes(input.format)) throw new Error('Choose a supported journal format.');
    const formatDetail = input.format === 'Custom' ? label(input.formatDetail, 'Custom size', 60) : '';
    const journal = { id: randomUUID(), title, pages, format: input.format, formatDetail, spreads: [] };
    if (input.template && input.template !== 'blank') {
      const template = templates.find(t => t.id === input.template);
      if (!template) throw new Error('Unknown journal template.');
      if (pages < 200) throw new Error('The starter indexes need a journal with at least 200 pages.');
      journal.spreads = template.spreads.map(s => ({ ...s, id: randomUUID() }));
    }
    profile.journals.push(journal);
    return journal.id;
  }
  if (action === 'editJournal') {
    const journal = getJournal(state, input.profileId, input.journalId);
    const pages = integer(input.pages, 'Page count', 1, 10000);
    if (journal.spreads.some(s => s.end > pages)) throw new Error('Page count cannot exclude an existing spread. Move that spread first.');
    if (!FORMATS.includes(input.format)) throw new Error('Choose a supported journal format.');
    const title = label(input.title, 'Journal title');
    const formatDetail = input.format === 'Custom' ? label(input.formatDetail, 'Custom size', 60) : '';
    Object.assign(journal, { title, pages, format: input.format, formatDetail });
    return journal.id;
  }
  if (action === 'addSpread' || action === 'editSpread') {
    const journal = getJournal(state, input.profileId, input.journalId);
    const existing = action === 'editSpread' ? journal.spreads.find(s => s.id === input.spreadId) : null;
    if (action === 'editSpread' && !existing) throw new Error('That spread does not belong to this journal.');
    const spread = validateSpread(input, journal.pages);
    if (spread.end < spread.start) throw new Error('Last page must be the same as or after the first page.');
    const overlap = journal.spreads.find(s => s.id !== existing?.id && spread.start <= s.end && spread.end >= s.start);
    if (overlap) throw new Error(`These pages already belong to “${overlap.title}” (${overlap.start}–${overlap.end}). Choose unused pages.`);
    spread.id = existing?.id || randomUUID();
    if (existing) Object.assign(existing, spread);
    else journal.spreads.push(spread);
    journal.spreads.sort((a, b) => a.start - b.start);
    return spread.id;
  }
  throw new Error('That action is not supported.');
}

function validateState(state) {
  if (!state || ![1, 2].includes(state.version) || !Array.isArray(state.profiles)) throw new Error('Unsupported or damaged journal data.');
  const ids = new Set();
  function checkId(id) {
    if (typeof id !== 'string' || !id || ids.has(id)) throw new Error('Damaged journal identifiers.');
    ids.add(id);
  }
  for (const p of state.profiles) {
    checkId(p.id); label(p.name, 'Name', 80);
    if (!Object.hasOwn(p, 'tasks')) p.tasks = [];
    require('./tasks.cjs').validateTasks(p.tasks, checkId);
    if (!Array.isArray(p.journals)) throw new Error('Damaged profile data.');
    for (const j of p.journals) {
      checkId(j.id); label(j.title, 'Journal title'); integer(j.pages, 'Page count', 1, 10000);
      if (!FORMATS.includes(j.format) || !Array.isArray(j.spreads)) throw new Error('Damaged journal data.');
      if (j.format === 'Custom') label(j.formatDetail, 'Custom size', 60);
      const sorted = [...j.spreads].sort((a, b) => a.start - b.start);
      let last = 0;
      for (const s of sorted) {
        checkId(s.id); validateSpread(s, j.pages);
        if (s.end < s.start || s.start <= last) throw new Error('Damaged spread ranges.');
        last = s.end;
      }
    }
  }
  state.version = 2;
  return state;
}
module.exports = { freshState, mutate, validateState, layout };
