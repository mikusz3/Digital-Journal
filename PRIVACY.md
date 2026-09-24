# Privacy

Digital Journal 0.4.0 is a local journal companion with no AI services, provider accounts, API calls, telemetry, advertising or automatic cloud synchronization. Profiles are not password-protected accounts.

Profiles, journals, custom layout notes and tasks stay on your device. Backups are plain-text JSON files containing every local profile, journal, spread and task. Settings and wallpapers are excluded. Imports create separate copies. Deleting a profile does not remove exported backups elsewhere.

Upgrading deletes the application's old provider credential file and temporary file on desktop, and its old provider preferences plus dedicated encryption alias on Android. Cleanup does not read or decrypt credentials. Already saved journal content is preserved. Local removal does not revoke keys at provider accounts or delete information previously sent using older versions.

Android no longer requests Internet permission. Its camera is used only by the offline QR scanner. A decoded web link opens in an external browser only after confirmation; the About screen can open explicitly selected websites or installed apps. Those external apps have their own privacy policies.

Android OS backup and device-transfer backup remain disabled; use explicit JSON export/import. Keep exported files somewhere you trust.
