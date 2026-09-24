const fs = require('node:fs');
const path = require('node:path');
const themes = require('../core/themes.json');
const locales = require('../core/locales.json');
class Preferences {
  constructor(directory, safeStorage) { this.file = path.join(directory, 'preferences.json'); this.vault = path.join(directory, 'credentials.json'); this.safe = safeStorage; this.sessionKeys = {}; this.data = {}; this.encrypted = {}; for (const [file, name] of [[this.file,'data'],[this.vault,'encrypted']]) { if (fs.existsSync(file)) { try { this[name] = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { throw new Error('Settings could not be read. Restore preferences.json or credentials.json before continuing.'); } } } }
  secure() { return this.safe.isEncryptionAvailable() && (process.platform !== 'linux' || this.safe.getSelectedStorageBackend() !== 'basic_text'); }
  write(file, data) { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file + '.tmp', JSON.stringify(data), { mode: 0o600 }); fs.renameSync(file + '.tmp', file); }
  read() { return { ...this.data, themes, locales, secure: this.secure(), keys: Object.fromEntries(['openai','deepseek','gemini'].map(p => [p, !!this.sessionKeys[p] || !!this.encrypted[p]])) }; }
  save(input) {
    if (input.language && !locales.languages[input.language]) throw new Error('Unknown language.');
    if (!themes[input.theme]) throw new Error('Unknown theme.');
    const custom = {}; for (const k of ['background','surface','text','accent','bar']) { const v = input.custom?.[k] || themes.Custom[k]; if (!/^#[0-9a-f]{6}$/i.test(v)) throw new Error('Use six-digit color values.'); custom[k] = v; }
    const dim = Number(input.dim); if (!Number.isFinite(dim) || dim < 0 || dim > 90) throw new Error('Choose dimming from 0–90%.');
    const gradient = input.gradient || ''; if (gradient && !/^#[0-9a-f]{6}$/i.test(gradient)) throw new Error('Invalid gradient color.');
    this.data = { ...this.data, theme: input.theme, language: input.language || this.data.language || 'en', custom, dim, gradient, reduceMotion: !!input.reduceMotion };
    this.write(this.file, this.data); return this.read();
  }
  key(provider) { if (this.sessionKeys[provider]) return this.sessionKeys[provider]; if (!this.encrypted[provider]) return ''; try { return this.safe.decryptString(Buffer.from(this.encrypted[provider], 'base64')); } catch { throw new Error('Saved key is unavailable. Enter it again in Settings.'); } }
  setKey({provider,key,remember}) {
    if (!['openai','deepseek','gemini'].includes(provider) || typeof key !== 'string' || key.length > 512 || /[\s\x00-\x1f]/.test(key)) throw new Error('Invalid API key.');
    if (remember && key && !this.secure()) throw new Error('Secure key storage is unavailable. Use this session only.');
    delete this.encrypted[provider]; delete this.sessionKeys[provider];
    if (key) { if (remember) this.encrypted[provider] = this.safe.encryptString(key).toString('base64'); else this.sessionKeys[provider] = key; }
    this.write(this.vault, this.encrypted); return this.read();
  }
  wallpaper(data) { this.data.wallpaper = data; this.write(this.file, this.data); return this.read(); }
}
module.exports = { Preferences };
