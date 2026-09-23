# Digital Journal — incremental delivery

Tasks 1–3, 5–7, 9–10 and the supported part of 12 are implemented. Task 13 is covered by cross-platform build/test workflows and release packaging. Additional requested features are spread layouts, a 5–30 minute tomato timer and nine interface languages. See release notes for actual device coverage and live AI limitations. Tasks 4, 8, 11 and 14 are deferred.

## Product intent

A shame-free, local companion to physical Bullet Journals, not a replacement for writing in them. Target Android (the user's Moto G54 5G), Linux Mint, and Windows. No online login; local profiles own separate journals. AI is optional and uses a provider key supplied by the user.

Requirements were derived from a private product brief. The private brief is not distributed with source code.

## Small tasks, in order

Each task ends with a runnable result and a short handoff. Selected tasks may be grouped when explicitly requested.

| Task | Deliverable | Completion check |
| --- | --- | --- |
| 1 | Desktop foundation: local profiles and journal index | Create a profile, journal and spread; close and reopen; data survives and belongs only to that profile. |
| 2 | Journal editing and backup | Edit metadata and page ranges; export and restore a versioned backup without silently overwriting another profile. |
| 3 | Android foundation | Same profile/journal workflow in an Android app; build APK and separately test on the Moto G54 when available. |
| 4 | Journal images | Optional profile picture and spread scans, with persistent local copies and graceful handling of missing files. |
| 5 | Tasks and planning | Add tasks, optionally split into smaller tasks, complete/reopen them; show optional confetti with reduced-motion support. No overdue shaming or streak punishment. |
| 6 | AI spread suggestions | User configures OpenAI or DeepSeek; intentionally send selected context; handle missing key, offline state and provider errors. |
| 7 | AI planning assistance | Suggest task breakdowns which the user can review before adding. Keep manual planning usable without AI. |
| 8 | Collection shelf | Add owned items, categories, title and optional release date; sort ascending/descending and preserve unknown dates. |
| 9 | Android QR tools | Generate QR codes for links and scan via camera; handle permission denial and let the user confirm opening a decoded link. |
| 10 | Themes | Dark, Light, Matrix, XP Luna inspired, Frutiger Aero inspired, Win9x inspired, Discord inspired, and custom colors/gradients; image wallpaper with adjustable dimming. |
| 11 | Image print layout | Image selection, paper size, scaling/cropping, margins and preview; verify output dimensions before printer integration. |
| 12 | Compatibility investigations | Determine supported import/export or link interfaces for Klub Czytelnika and actual printing paths for Xiaomi Photo Printer Pro-D0F1; implement only supported paths. |
| 13 | Distribution | Clickable Linux build and terminal launch, Windows `.exe`, Android release packaging; test each on its target platform. |
| 14 | Printable app QR | Once stable installation/download destinations exist, generate printable codes pointing to them. |

## Task 1 acceptance criteria

- Start offline without requesting email, passwords, API keys, or a remote account.
- Create a local profile with a name. Profile pictures are optional and scheduled for Task 4.
- Open the sole existing profile automatically; show a profile chooser when several exist.
- Create journals with a title, page count and format (A6, B6, A5, B5 or custom). Allow journals covering multiple years and future journals from 2029 onward.
- Add and display spread titles with an inclusive page range. Reject empty titles and ranges outside the journal's page count; allow one-page entries.
- Support separate journals under each profile, including the main 2026–2028 journal and the DLC journal.
- Offer the document's journal indexes as an optional starter template, not as every user's default data. Preserve Polish month names and exact page numbers. Leave unspecified pages unassigned; never invent their content.
- Persist changes outside disposable build output. Report save failures without claiming success.
- Verify creation, invalid input, profile isolation and reopen persistence. Inspect the actual interface.

## Architecture decisions

The source lists C++, Pascal, Kotlin, JavaScript/TypeScript, Rust and Electron without selecting a mandatory combination. Selected: Electron/JavaScript desktop and Kotlin native Android with platform Views for the foundation. Both use version-1 journal JSON and a shared backup contract. Android builds its starter assets from the desktop source. Do not introduce every listed language.

Keep profile/journal data separate from UI and platform services. Use stable IDs and a versioned storage format so later tasks can add attachments, tasks and collections without resetting data. Keep API credentials out of ordinary journal exports and logs.

Desktop and Android are separate deliverables. A browser preview is not a Windows executable or an Android APK. Cross-device synchronization is not specified; begin with local storage and explicit backup/import, and define synchronization only if requested.

## Requirements needing later clarification or evidence

- Collection sorting “alphabetically” and “by title” appear equivalent; implement title sorting unless a distinct author/series field is requested.
- Identify the exact Klub Czytelnika app and its documented interoperability options. Installation on a phone alone does not imply an integration API.
- Printer compatibility depends on available OS support, connection method and manufacturer interfaces. Preview/export can be delivered independently.
- Decide interface language before broad UI polish; preserve supplied journal titles in their original language.
- Scans are image attachments unless OCR is explicitly added; automatic handwriting recognition is not part of the current requirements.
- A shame-free experience means neutral empty states, optional task breakdowns, no punitive missed-day messaging, and user-controlled celebration effects.

## Verification and usage discipline

Read this roadmap and the release notes before later work. Inspect only the current task's files. Run focused checks and keep successful output concise. Update the status record after each task. Do not claim Windows, phone, printer or external-app compatibility based solely on a local build.
