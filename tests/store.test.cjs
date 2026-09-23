const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { JournalStore } = require('../core/store.cjs');

function setup(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'digital-bujo-test-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  return { dir, store: new JournalStore(dir) };
}
function profile(store, name = 'Alex') { return store.change('createProfile', { name }).id; }
function journal(store, profileId, overrides = {}) {
  return store.change('createJournal', { profileId, title: 'Everyday 2029', pages: 96, format: 'A6', template: 'blank', ...overrides }).id;
}
test('profile, journal and spread persist through a new store instance', t => {
  const { dir, store } = setup(t);
  const profileId = profile(store);
  const journalId = journal(store, profileId);
  store.change('addSpread', { profileId, journalId, title: 'A small win', start: 96, end: 96 });
  const restored = new JournalStore(dir).read();
  assert.deepEqual(restored, store.read());
  assert.equal(restored.profiles[0].journals[0].spreads[0].title, 'A small win');
  if (process.platform !== 'win32') assert.equal(fs.statSync(store.file).mode & 0o777, 0o600); // POSIX permission bits are not supported on Windows.
});
test('one profile cannot add spreads to another profile’s journal', t => {
  const { store } = setup(t);
  const alex = profile(store);
  const sam = profile(store, 'Sam');
  const journalId = journal(store, alex);
  const before = store.read();
  assert.throws(() => store.change('addSpread', { profileId: sam, journalId, title: 'Private', start: 1, end: 1 }), /does not belong/);
  assert.deepEqual(store.read(), before);
  assert.equal(store.read().profiles[1].journals.length, 0);
});
test('invalid and overlapping ranges do not change memory or saved data', t => {
  const { store } = setup(t);
  const profileId = profile(store);
  const journalId = journal(store, profileId);
  store.change('addSpread', { profileId, journalId, title: 'Mood', start: 4, end: 5 });
  const before = fs.readFileSync(store.file, 'utf8');
  for (const [start, end] of [[0, 1], [97, 97], [2, 1], [4, 4], [1, 6], [1.5, 2], [1, NaN]]) {
    assert.throws(() => store.change('addSpread', { profileId, journalId, title: 'Other', start, end }));
  }
  assert.throws(() => store.change('addSpread', { profileId, journalId, title: ' ', start: 1, end: 1 }));
  assert.equal(fs.readFileSync(store.file, 'utf8'), before);
  assert.equal(store.read().profiles[0].journals[0].spreads.length, 1);
});
test('templates preserve supplied ranges and leave unknown pages open', t => {
  const { store } = setup(t);
  const profileId = profile(store);
  const mainId = journal(store, profileId, { pages: 200, template: 'main' });
  const dlcId = journal(store, profileId, { pages: 200, template: 'dlc' });
  const [main, dlc] = store.read().profiles[0].journals;
  assert.equal(main.id, mainId); assert.equal(dlc.id, dlcId);
  assert.equal(main.spreads.length, 52); assert.equal(dlc.spreads.length, 12);
  assert.deepEqual(main.spreads.filter(s => s.title === 'Wrzesień 2026').map(s => [s.start, s.end]), [[2, 3]]);
  assert.deepEqual(main.spreads.filter(s => s.title === 'Grudzień 2028').map(s => [s.start, s.end]), [[60, 61]]);
  assert.ok(!main.spreads.some(s => s.start <= 10 && s.end >= 10));
  assert.ok(!dlc.spreads.some(s => s.start <= 36 && s.end >= 36));
  assert.ok(!dlc.spreads.some(s => s.title.includes('?')));
  assert.throws(() => journal(store, profileId, { template: 'main' }), /at least 200/);
});
test('failed save rolls back the proposed change', t => {
  const { store } = setup(t);
  profile(store);
  const before = store.read();
  const disk = fs.readFileSync(store.file, 'utf8');
  fs.mkdirSync(`${store.file}.tmp`);
  assert.throws(() => profile(store, 'Sam'), /Could not save/);
  assert.deepEqual(store.read(), before);
  assert.equal(fs.readFileSync(store.file, 'utf8'), disk);
});
test('damaged data and unsupported versions are preserved rather than reset', t => {
  const { dir, store } = setup(t);
  for (const content of ['{broken', '{"version":99,"profiles":[]}']) {
    fs.writeFileSync(store.file, content);
    assert.throws(() => new JournalStore(dir), /preserved/);
    assert.equal(fs.readFileSync(store.file, 'utf8'), content);
  }
});
test('profile names and custom notebook formats are validated', t => {
  const { store } = setup(t);
  assert.throws(() => profile(store, '  '), /required/);
  const id = profile(store);
  assert.throws(() => profile(store, ' alex '), /already exists/);
  assert.throws(() => journal(store, id, { format: 'Custom' }), /Custom size/);
  journal(store, id, { format: 'Custom', formatDetail: '15 × 21 cm' });
  assert.equal(store.read().profiles[0].journals[0].formatDetail, '15 × 21 cm');
});

const { createBackup, parseBackup } = require('../core/backups.cjs');
test('edits preserve identifiers and reject shrinking or overlapping occupied pages', t => {
  const { dir, store } = setup(t);
  const profileId = profile(store);
  const journalId = journal(store, profileId);
  const spreadId = store.change('addSpread', { profileId, journalId, title: 'Old', start: 90, end: 96 }).id;
  store.change('editSpread', { profileId, journalId, spreadId, title: 'Revised', start: 80, end: 85 });
  store.change('editJournal', { profileId, journalId, title: 'Revised journal', pages: 85, format: 'B5' });
  const before = store.read();
  assert.throws(() => store.change('editJournal', { profileId, journalId, title: 'Too short', pages: 84, format: 'A5' }), /exclude/);
  assert.throws(() => store.change('editSpread', { profileId, journalId, spreadId: 'missing', title: 'Missing', start: 1, end: 1 }), /does not belong/);
  assert.deepEqual(new JournalStore(dir).read(), before);
  assert.equal(before.profiles[0].journals[0].spreads[0].id, spreadId);
});
test('backups round-trip and duplicate imports create new profiles without overwriting', t => {
  const { dir, store } = setup(t);
  const profileId = profile(store);
  journal(store, profileId, { pages: 200, template: 'main' });
  const original = store.read();
  const imported = parseBackup(createBackup(original));
  assert.deepEqual(imported, original);
  store.import(imported); store.import(imported);
  const restored = new JournalStore(dir).read();
  assert.deepEqual(restored.profiles[0], original.profiles[0]);
  assert.deepEqual(restored.profiles.map(p => p.name), ['Alex', 'Alex (import 1)', 'Alex (import 2)']);
  assert.notEqual(restored.profiles[1].journals[0].id, original.profiles[0].journals[0].id);
  assert.notEqual(restored.profiles[1].journals[0].spreads[0].id, original.profiles[0].journals[0].spreads[0].id);
});
test('malformed and future backups are rejected; import save failure preserves current data', t => {
  const { store } = setup(t);
  profile(store);
  assert.throws(() => parseBackup('{'), /valid JSON/);
  assert.throws(() => parseBackup('{"format":"digital-bujo","backupVersion":2}'), /version/);
  assert.throws(() => parseBackup(' '.repeat(10 * 1024 * 1024 + 1)), /10 MB/);
  const bad = JSON.parse(createBackup(store.read()));
  bad.state.profiles.push(bad.state.profiles[0]);
  assert.throws(() => parseBackup(JSON.stringify(bad)), /identifiers/);
  const before = store.read();
  fs.mkdirSync(`${store.file}.tmp`);
  assert.throws(() => store.import(before), /Could not save/);
  assert.deepEqual(store.read(), before);
});
test('profile deletion needs exact confirmation, persists and preserves every other profile', t => {
  const { dir, store } = setup(t);
  const alex = profile(store); journal(store, alex);
  const sam = profile(store, 'Sam');
  const other = store.read().profiles[1];
  const before = store.read();
  assert.throws(() => store.change('deleteProfile', { profileId: alex, confirmName: 'alex' }), /exactly/);
  assert.deepEqual(store.read(), before);
  store.change('deleteProfile', { profileId: alex, confirmName: 'Alex' });
  assert.deepEqual(new JournalStore(dir).read().profiles, [other]);
  store.change('deleteProfile', { profileId: sam, confirmName: 'Sam' });
  assert.deepEqual(new JournalStore(dir).read(), { version: 2, profiles: [] });
});
test('failed deletion save retains the profile and its journals', t => {
  const { store } = setup(t);
  const profileId = profile(store); journal(store, profileId);
  const before = store.read();
  fs.mkdirSync(`${store.file}.tmp`);
  assert.throws(() => store.change('deleteProfile', { profileId, confirmName: 'Alex' }), /Could not save/);
  assert.deepEqual(store.read(), before);
});
