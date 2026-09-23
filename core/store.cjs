const fs = require('node:fs');
const path = require('node:path');
const { freshState, mutate, validateState } = require('./journals.cjs');
const { importCopies } = require('./backups.cjs');

class JournalStore {
  constructor(directory) {
    this.file = path.join(directory, 'journals.json');
    this.state = freshState();
    try {
      this.state = validateState(JSON.parse(fs.readFileSync(this.file, 'utf8')));
    } catch (error) {
      if (error.code !== 'ENOENT') throw new Error(`Your saved journals could not be opened. The original file has been preserved at ${this.file}. ${error.message}`);
    }
  }
  read() { return structuredClone(this.state); }
  change(action, input) {
    const next = this.read();
    const id = mutate(next, action, input);
    this.persist(next);
    return { state: this.read(), id };
  }
  import(imported) {
    this.persist(importCopies(this.state, imported));
    return { state: this.read() };
  }
  persist(next) {
    validateState(next);
    const temporary = `${this.file}.tmp`;
    try {
      fs.mkdirSync(path.dirname(this.file), { recursive: true });
      const fd = fs.openSync(temporary, 'w', 0o600);
      try { fs.writeFileSync(fd, JSON.stringify(next, null, 2) + '\n'); fs.fsyncSync(fd); }
      finally { fs.closeSync(fd); }
      fs.renameSync(temporary, this.file);
    } catch (error) {
      try { fs.unlinkSync(temporary); } catch { /* Preserve the last successful save. */ }
      throw new Error(`Could not save your change. Your previous journal data is unchanged. Check disk space and folder permissions. ${error.message}`);
    }
    this.state = next;
  }
}
module.exports = { JournalStore };
