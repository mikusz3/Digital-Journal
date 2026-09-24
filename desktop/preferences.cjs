const fs = require('node:fs');
const path = require('node:path');
const themes = require('../core/themes.json');
const locales = require('../core/locales.json');
class Preferences {
  constructor(directory) {
    this.file = path.join(directory, 'preferences.json'); this.data = {};
    // Remove obsolete provider credentials without reading or decrypting them.
    for (const name of ['credentials.json', 'credentials.json.tmp']) fs.rmSync(path.join(directory, name), { force: true });
    if (fs.existsSync(this.file)) {
      try { this.data = JSON.parse(fs.readFileSync(this.file, 'utf8')); }
      catch { throw new Error('Settings could not be read. Restore preferences.json before continuing.'); }
    }
  }
  write(file, data) { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file + '.tmp', JSON.stringify(data), { mode: 0o600 }); fs.renameSync(file + '.tmp', file); }
  read() { return { ...this.data, themes, locales }; }
  save(input) {
    if (input.language && !locales.languages[input.language]) throw new Error('Unknown language.');
    if (!themes[input.theme]) throw new Error('Unknown theme.');
    const custom = {}; for (const k of ['background','surface','text','accent','bar']) { const v = input.custom?.[k] || themes.Custom[k]; if (!/^#[0-9a-f]{6}$/i.test(v)) throw new Error('Use six-digit color values.'); custom[k] = v; }
    const dim = Number(input.dim); if (!Number.isFinite(dim) || dim < 0 || dim > 90) throw new Error('Choose dimming from 0–90%.');
    const gradient = input.gradient || ''; if (gradient && !/^#[0-9a-f]{6}$/i.test(gradient)) throw new Error('Invalid gradient color.');
    this.data = { ...this.data, theme: input.theme, language: input.language || this.data.language || 'en', custom, dim, gradient, reduceMotion: !!input.reduceMotion };
    this.write(this.file, this.data); return this.read();
  }
  wallpaper(data) { this.data.wallpaper = data; this.write(this.file, this.data); return this.read(); }
}
module.exports = { Preferences };
