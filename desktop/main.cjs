const { app, BrowserWindow, ipcMain, dialog, safeStorage } = require('electron');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { JournalStore } = require('../core/store.cjs');
const fs = require('node:fs');
const { randomUUID } = require('node:crypto');
const { MAX_BACKUP_BYTES, createBackup, parseBackup, summarize } = require('../core/backups.cjs');
let pendingImport;
let fileDialogBusy = false;

app.setName('Digital Journal');
// Keep the original storage location so existing profiles survive the rename.
app.setPath('userData', path.join(app.getPath('appData'), 'Digital BuJo'));
if (process.env.BUJO_DATA_DIR) app.setPath('userData', path.resolve(process.env.BUJO_DATA_DIR));
const page = path.join(__dirname, '../ui/index.html');
const pageUrl = pathToFileURL(page).href;
let window;
let store;
let loadError;
let preferences;
let aiRequest;
const { Preferences } = require('./preferences.cjs');
const singleInstance = app.requestSingleInstanceLock();
if (!singleInstance) app.quit();
else {
  app.on('second-instance', () => { if (window) { if (window.isMinimized()) window.restore(); window.focus(); } });
  app.whenReady().then(() => {
    try { store = new JournalStore(app.getPath('userData')); preferences = new Preferences(app.getPath('userData'), safeStorage); }
    catch (error) { loadError = error.message; }
    ipcMain.handle('bujo', async (event, action, input) => {
      if (event.senderFrame !== window?.webContents.mainFrame || event.senderFrame.url !== pageUrl) return { ok: false, error: 'This window cannot access journal data.' };
      try {
        if (loadError) throw new Error(loadError);
        if (action === 'preferences') return { ok: true, preferences: preferences.read() };
        if (action === 'savePreferences') return { ok: true, preferences: preferences.save(input) };
        if (action === 'setKey') return { ok: true, preferences: preferences.setKey(input) };
        if (action === 'cancelAI') { aiRequest?.abort(); return { ok: true }; }
        if (action === 'generateAI') {
          if (aiRequest) throw new Error('An AI request is already running.');
          const controller = new AbortController(); aiRequest = controller;
          const timer = setTimeout(() => controller.abort(), 60000);
          try { return { ok: true, items: await require('../core/ai.cjs').generate(input, preferences.key(input.provider), controller.signal) }; }
          finally { clearTimeout(timer); aiRequest = null; }
        }
        if (action === 'wallpaper') {
          if (input === 'clear') return { ok: true, preferences: preferences.wallpaper('') };
          const selected = await dialog.showOpenDialog(window, { title: 'Choose wallpaper', properties: ['openFile'], filters: [{ name: 'Image', extensions: ['png','jpg','jpeg'] }] });
          if (selected.canceled) return { ok: true, preferences: preferences.read() };
          const file = selected.filePaths[0]; if (fs.statSync(file).size > 5 * 1024 * 1024) throw new Error('Choose an image smaller than 5 MB.');
          const bytes = fs.readFileSync(file);
          const type = bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])) ? 'png' : bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255 ? 'jpeg' : null;
          if (!type) throw new Error('Choose a PNG or JPEG image.');
          return { ok: true, preferences: preferences.wallpaper(`data:image/${type};base64,${bytes.toString('base64')}`) };
        }
        if (action === 'read') return { ok: true, state: store.read() };
        if (action === 'exportBackup' || action === 'previewImport') {
          if (fileDialogBusy) throw new Error('Close the current file chooser first.');
          fileDialogBusy = true;
          try { return await backupDialog(action); } finally { fileDialogBusy = false; }
        }
        if (action === 'cancelImport') { pendingImport = null; return { ok: true }; }
        if (action === 'confirmImport') {
          if (!pendingImport || input !== pendingImport.token) throw new Error('Select a backup again before importing.');
          const result = store.import(pendingImport.state);
          pendingImport = null;
          return { ok: true, ...result };
        }
        return { ok: true, ...store.change(action, input) };
      } catch (error) { return { ok: false, error: error.message }; }
    });
    createWindow();
    app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
  }).catch(error => { dialog.showErrorBox('Digital Journal could not start', error.message); app.quit(); });
}
async function backupDialog(action) {
  const filters = [{ name: 'Digital Journal backup', extensions: ['json'] }];
  if (action === 'exportBackup') {
    const result = await dialog.showSaveDialog(window, { title: 'Export all profiles', defaultPath: `Digital-Journal-${new Date().toISOString().slice(0, 10)}.json`, filters });
    if (result.canceled || !result.filePath) return { ok: true, canceled: true };
    const target = fs.existsSync(result.filePath) ? fs.realpathSync(result.filePath) : path.resolve(result.filePath);
    if ([store.file, `${store.file}.tmp`].includes(target)) throw new Error('Choose a backup location outside the live journal file.');
    const temp = `${result.filePath}.${randomUUID()}.tmp`;
    try {
      fs.writeFileSync(temp, createBackup(store.read()), { mode: 0o600, flag: 'wx' });
      fs.renameSync(temp, result.filePath);
    } finally { try { fs.unlinkSync(temp); } catch {} }
    return { ok: true };
  }
  pendingImport = null;
  const result = await dialog.showOpenDialog(window, { title: 'Import a Digital Journal backup', filters, properties: ['openFile'] });
  if (result.canceled || !result.filePaths.length) return { ok: true, canceled: true };
  const file = result.filePaths[0];
  if (fs.statSync(file).size > MAX_BACKUP_BYTES) throw new Error('Backup is larger than the 10 MB limit.');
  const imported = parseBackup(fs.readFileSync(file, 'utf8'));
  pendingImport = { token: randomUUID(), state: imported };
  return { ok: true, token: pendingImport.token, profiles: summarize(imported) };
}
function createWindow() {
  window = new BrowserWindow({
    title: 'Digital Journal', width: 1180, height: 800, minWidth: 760, minHeight: 580,
    backgroundColor: '#f7f6f1', icon: path.join(__dirname, '../ui/icon.svg'),
    webPreferences: { preload: path.join(__dirname, 'preload.cjs'), nodeIntegration: false, contextIsolation: true, sandbox: true },
  });
  window.setMenuBarVisibility(false);
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  window.webContents.on('will-navigate', event => event.preventDefault());
  window.webContents.session.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
  window.loadFile(page);
}
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
