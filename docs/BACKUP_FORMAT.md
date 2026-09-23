# Portable backup format

Version 0.3 exports `{ "format": "digital-journal", "backupVersion": 2, "exportedAt": "ISO timestamp", "state": {...} }`.

State version 2 contains profiles with `id`, `name`, `journals`, and `tasks`. Journal and spread fields retain version 1 semantics. A task contains `id`, `title` (1–120 characters), `notes` (up to 4000), optional `due` (empty string or YYYY-MM-DD), boolean `done`, and `subtasks` (up to 100). Each subtask contains its own `id`, `title` and boolean `done`.

Both platforms accept legacy `digital-bujo` backupVersion 1/state version 1 backups and initialize missing task arrays. Future versions and malformed input are rejected. Only supported fields are imported. Backups are limited to 10 MiB. Keys, wallpapers and appearance preferences are not included.

Import creates new IDs for every profile, journal, spread, task and subtask. Existing profiles remain unchanged. Colliding names gain ` (import N)`, with length bounded to 80 characters. Import is atomic. These are copies, not synchronized profiles.

Range validation rejects journal pages outside 1–10000, reversed/out-of-range spreads and overlaps. Task dates are validated as actual calendar dates. IDs are unique across the entire state. Corrupt local data is preserved and reported; it is never silently reset.

An exported file can contain personal information. Profile deletion does not remove previously exported files. Older app versions cannot import the new format; keep an old backup before downgrading.
