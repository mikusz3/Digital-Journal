# Release process

1. Update desktop and Android versions together; increment Android versionCode.
2. Run domain tests, desktop UI tests, Android unit tests and lint. Verify task-bearing backup transfer both ways. Test the isolated QA edition on a physical device, then remove test profiles/files and the QA edition.
3. Use `npm run package:linux` on Linux and `npm run package:windows` on Windows. Archive the entire output directory, not just its executable. CI builds and tests both desktop targets. A CI Windows test is not a substitute for broad Windows hardware testing.
4. Build Android release with the private signing variables described in [android/README.md](../android/README.md). Verify the APK signature. Keep the keystore and passwords outside Git and release assets.
5. Publish the source tag, Linux archive, Windows ZIP and signed Android APK, with SHA-256 checksums and concrete verification/limitations in release notes. GitHub Actions test APKs use a disposable debug certificate and are not stable public updates.
6. Never include private prompts, local app data, API keys, signing files, device identifiers or personal test screenshots. Stage source files explicitly and inspect the staged file list.

Live AI acceptance requires a user-provided funded provider key. Fixture tests verify request formatting, errors, parsing and review-before-save; they do not prove current billing, model access or network success on a user's provider account.
