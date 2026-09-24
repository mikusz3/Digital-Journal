const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('bujo', {
  ...Object.fromEntries(['timer','saveTask','toggleTask','deleteTask','preferences','savePreferences','wallpaper'].map(action => [action, input => ipcRenderer.invoke('bujo', action, input)])),
  read: () => ipcRenderer.invoke('bujo', 'read'),
  createProfile: input => ipcRenderer.invoke('bujo', 'createProfile', input),
  deleteProfile: input => ipcRenderer.invoke('bujo', 'deleteProfile', input),
  createJournal: input => ipcRenderer.invoke('bujo', 'createJournal', input),
  addSpread: input => ipcRenderer.invoke('bujo', 'addSpread', input),
  editJournal: input => ipcRenderer.invoke('bujo', 'editJournal', input),
  editSpread: input => ipcRenderer.invoke('bujo', 'editSpread', input),
  exportBackup: () => ipcRenderer.invoke('bujo', 'exportBackup'),
  previewImport: () => ipcRenderer.invoke('bujo', 'previewImport'),
  confirmImport: token => ipcRenderer.invoke('bujo', 'confirmImport', token),
  cancelImport: () => ipcRenderer.invoke('bujo', 'cancelImport'),
});
