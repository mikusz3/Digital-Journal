# Android

Native Kotlin and platform Views, Android 8+ (minSdk 26), compile/target SDK 36. The stable application ID is `pl.digitalbujo.app` to preserve existing installations; the displayed name is Digital Journal.

Use JDK 25, Android SDK 36 and the included Gradle 9.3.1 wrapper. Set `ANDROID_HOME`, or create untracked `local.properties` with `sdk.dir=/path/to/sdk`.

```sh
./gradlew :app:assembleDebug :app:assembleQa :app:testDebugUnitTest :app:lintDebug
```

The QA edition uses `pl.digitalbujo.app.qa`; it cannot read regular app data. Install it only for explicit device testing and remove its test data/installation afterwards. `tests/android_smoke.py` accepts an explicit device serial and never clears the regular application.

## Release signing

Keep the signing keystore and password outside version control. Configure these environment variables before `./gradlew :app:assembleRelease`:

- `JOURNAL_KEYSTORE`: absolute path to the persistent release keystore.
- `JOURNAL_STORE_PASSWORD`: keystore password.
- `JOURNAL_KEY_ALIAS`: alias, default `journal`.
- `JOURNAL_KEY_PASSWORD`: key password, default same as store password.

Without signing variables Gradle produces an unsigned release APK; do not publish that as installable. Public releases are signed locally with the persistent project release key. Do not commit or upload that key to a release. Back it up securely: future updates require the same certificate.

The first public release certificate differs from early debug builds. Android cannot replace a differently signed installation. Export and verify a backup before any user-directed switch; never clear data to work around signing errors.

Optional AI uses HTTPS directly to the chosen provider. Credentials use Android Keystore AES-GCM. The camera is used only by the QR scanner. No storage permission is needed; imports, exports and wallpapers use Android's document picker.
