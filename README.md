# Digital Journal

A local companion for paper journals, with separate profiles, a page index, gentle task planning and optional AI assistance. Android, Linux and Windows share a portable JSON backup format. No application account or subscription is required.

Digital Journal is an **independent, fan-made application developed for personal use and offered without profit**. The **Bullet Journal method was created by Ryder Carroll**. **Bullet Journal® and BuJo® are trademarks of Lightcage, LLC**. This project is not affiliated with, sponsored by, or endorsed by Ryder Carroll or Lightcage, LLC. Visit the original creators at [bulletjournal.com](https://bulletjournal.com). Full attribution: [CREDITS.md](CREDITS.md).

Original code is [MIT licensed](LICENSE). The project's non-profit purpose is not a restriction on the permissions granted by MIT.

## Download and run

Get builds from [GitHub Releases](https://github.com/mikusz3/Digital-Journal/releases). These early releases are unsigned on Windows and use a project release certificate on Android; no store certification is claimed.

- **Linux x64:** extract the archive and double-click `Digital Journal`, or run `./"Digital Journal"` inside the extracted folder. Keep the folder's files together. The Electron sandbox remains enabled; a host that disables user namespaces may require its administrator to configure sandbox support.
- **Windows x64:** extract the entire ZIP, then open `Digital Journal.exe`. Keep the accompanying files. Windows may show an unrecognized-publisher prompt.
- **Android 8+ (API 26+):** install the APK through Android's normal installer. Camera permission is requested only for scanning QR codes.
- **Earlier development Android installations:** their debug certificate differs from the public release certificate. Export a backup before switching editions. Do not uninstall an existing edition until its backup is verified. Routine updates signed by the same certificate retain data.

## Features

- Spread guides: calendar with notes, tracker, log, wishlist, blank and AI-assisted/custom layouts. AI drafts include practical layout instructions for review.
- A task-linked tomato timer: 5–30 minutes, pause/resume/stop, persistent countdown. Android does not schedule a background alarm; it reports completion on the timer screen.
- Selectable Polish, English, German, Spanish (Spain and Latin America), Japanese, Russian, Ukrainian and French interfaces. User content is preserved in its original language; legal notices and some technical/system messages retain their source language.
- Local profiles, journals, page ranges, search and edits; exact-name-confirmed profile deletion.
- Backup export/import with preview; imports become separate copies and never overwrite existing profiles.
- Tasks, optional dates, notes and subtasks, completion/reopening, and optional confetti respecting reduced motion.
- Optional OpenAI or DeepSeek spread suggestions and task breakdowns. Review and edit before saving.
- Android QR link generation, PNG export and camera scanning with confirmation before opening links.
- Light, Dark, Matrix, XP Luna-inspired, Frutiger Aero-inspired, Win9x-inspired, Discord-inspired and custom palettes. Custom gradients, local wallpaper and dimming.
- In-app credits and supported Android launch shortcuts for Cover to Cover Club and Xiaomi Home. See [compatibility findings](docs/COMPATIBILITY.md).

Collections (task 8), print layout (11), printable installation QR (14), and journal image attachments (4) remain future work. QR images are ordinary link codes, not an implementation of task 14.

## Optional AI

Open **Settings → AI provider keys** and enter your own provider key there, never in a GitHub issue or chat. API billing is separate from ChatGPT/Codex subscriptions. Configure a model your provider account can access; the editable defaults are `gpt-4.1-mini` and `deepseek-flash`.

The app sends only the text displayed in the AI form, when you press Generate, directly to the selected provider over HTTPS. It does not upload your whole profile or backup. Results cannot run commands or silently write to journals. Generated content can be mistaken; review it. Manual planning works without a key or internet.

Android stores keys using Android Keystore encryption. Desktop uses Electron safeStorage backed by the OS credential service; if secure storage is unavailable, only session keys are allowed. Keys, theme preferences and wallpapers are excluded from journal backups. OpenAI requests set `store: false`; this does not override a provider's other retention policies. See [PRIVACY.md](PRIVACY.md).

## Build from source

Desktop requires Node.js 22.12+ and npm:

```sh
npm ci
npm start
npm test
npm run check
npm run package:linux
npm run package:windows
```

Windows packaging is best run on Windows; Linux packaging on Linux. Packaged apps include their runtime and do not require Node.js. `launch.sh` also runs a source checkout on Linux after installing dependencies.

Android uses the included Gradle wrapper, Android SDK 36, and JDK 25 (or another JDK supported by Gradle 9.3.1):

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Set `ANDROID_HOME` or create an untracked `android/local.properties` with `sdk.dir=...`. See [Android build and signing](android/README.md) and [release process](docs/RELEASING.md).

## Data and compatibility

Both apps store profiles locally. Profiles separate content, not OS users or passwords. Keep backups in a trusted location: they contain names, journal indexes and tasks in plain text.

The display name changed from Digital BuJo to Digital Journal. Desktop deliberately retains the legacy `Digital BuJo` application-data folder, and Android retains `pl.digitalbujo.app`, so existing installations can find their journals. Version 1 journals and backups migrate to version 2. Version 2 backups are not intended for older app versions. Details: [BACKUP_FORMAT.md](docs/BACKUP_FORMAT.md).

Private development prompts, device screenshots, signing keys, API keys and personal journal data are not part of this repository.
