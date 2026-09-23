# Privacy

Profiles, journals and tasks are stored on your device. There is no project-operated server, telemetry, advertising or automatic cloud synchronization. The application does not provide password-protected profiles.

Backups are user-selected, plain-text JSON files. Export includes every profile, journal, spread and task. API credentials, settings and wallpapers are never exported. Imports create separate copies. Deleting a profile removes its locally stored content, but cannot delete backup files you exported elsewhere.

Optional AI sends exactly the context shown in the form to the selected OpenAI or DeepSeek API, along with your chosen model and a request for journal suggestions. It sends your API key only to that provider as an HTTPS authentication header. Keys are never written to application logs. Provider handling and billing are governed by their own policies:

- [OpenAI API data controls](https://developers.openai.com/api/docs/guides/your-data)
- [DeepSeek privacy policy](https://cdn.deepseek.com/policies/en-US/deepseek-privacy-policy.html)

OpenAI requests disable response storage (`store: false`). This does not promise zero retention by the provider. AI suggestions are never applied without your review.

Android camera access is used by the offline QR scanner. Camera images are not sent to AI or project servers. A decoded web link opens externally only after confirmation. Other apps and websites have their own privacy policies.

Credentials are encrypted using Android Keystore or the desktop OS secret store. On desktop systems without a suitable secure store, credentials can be retained in memory for the current session only. Android OS backup and device-transfer backup are disabled for the app; use the explicit JSON backup feature.
