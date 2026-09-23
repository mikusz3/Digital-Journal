const { randomUUID } = require('node:crypto');
function cleanTask(t) { return { id: t.id, title: t.title, notes: t.notes, due: t.due, done: t.done, subtasks: t.subtasks.map(s => ({ id: s.id, title: s.title, done: s.done })) }; }
function validateTasks(tasks, checkId = () => {}) {
  if (!Array.isArray(tasks) || tasks.length > 10000) throw new Error('Invalid task list.');
  const title = v => { if (typeof v !== 'string' || !v.trim() || v.length > 120) throw new Error('Task titles need 1–120 characters.'); };
  for (const t of tasks) {
    checkId(t.id); title(t.title);
    if (typeof t.notes !== 'string' || t.notes.length > 4000 || typeof t.done !== 'boolean') throw new Error('Invalid task details.');
    if (typeof t.due !== 'string' || (t.due && (!/^\d{4}-\d{2}-\d{2}$/.test(t.due) || !Number.isFinite(Date.parse(t.due)) || new Date(t.due).toISOString().slice(0,10) !== t.due))) throw new Error('Use a valid date or leave it empty.');
    if (!Array.isArray(t.subtasks) || t.subtasks.length > 100) throw new Error('Use at most 100 subtasks.');
    for (const s of t.subtasks) { checkId(s.id); title(s.title); if (typeof s.done !== 'boolean') throw new Error('Invalid subtask.'); }
  }
}
function changeTask(profile, action, input) {
  const tasks = profile.tasks ||= [];
  const existing = tasks.find(t => t.id === input.taskId);
  if (input.taskId && !existing) throw new Error('Task does not belong to this profile.');
  if (action === 'deleteTask') { if (!existing) throw new Error('Task not found.'); profile.tasks = tasks.filter(t => t !== existing); return existing.id; }
  if (action === 'toggleTask') {
    const item = input.subtaskId ? existing?.subtasks.find(s => s.id === input.subtaskId) : existing;
    if (!item) throw new Error('Task or subtask not found.');
    item.done = !item.done; return item.id;
  }
  const task = { id: existing?.id || randomUUID(), title: String(input.title || '').trim(), notes: String(input.notes || '').trim(), due: input.due || '', done: existing?.done || false,
    subtasks: (input.subtasks || []).map(s => { const old = existing?.subtasks.find(x => x.id === s.id); return { id: old?.id || randomUUID(), title: String(s.title || '').trim(), done: old?.done || false }; }) };
  validateTasks([task]);
  if (existing) Object.assign(existing, task); else tasks.push(task);
  validateTasks(tasks); return task.id;
}
module.exports = { cleanTask, validateTasks, changeTask };
