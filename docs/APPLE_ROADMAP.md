# macOS and iOS roadmap

Planning only, 24 September 2026. Neither Apple build is released or device-tested yet. Finish the Linux/Windows/Android 0.3 release first. No iPhone connection or Apple account purchase is needed now.

## Shared contract

Keep Digital Journal's MIT license, credits, local profiles, manual planning and nine languages. Preserve the existing versioned backup contract (including legacy imports), ID rekeying, validation and profile deletion. Exchange data through explicit export/import; cloud synchronization is outside this plan. AI integration is retired and must not be reintroduced. Reuse locale/template catalogs and cross-platform data fixtures instead of rewriting the existing apps.

## macOS: reuse the Electron desktop app

| Milestone | Work | Acceptance gate |
| --- | --- | --- |
| M1 — build foundation | Extend the existing packaging script and GitHub Actions with macOS runners. Produce separate Apple silicon and Intel artifacts where the chosen Electron release supports them. Pin the runner, Electron version and minimum macOS version at implementation time. | Both supported architectures build; core tests pass; app assets contain no credentials or personal data. |
| M2 — platform behavior | Test application menus, keyboard shortcuts, file dialogs, writable data paths, themes/languages and restart persistence. Preserve the current data-folder identity. | Automated macOS UI tests and backup round trips pass. |
| M3 — distribution | Configure Developer ID signing, hardened runtime, notarization and stapling using protected CI secrets; package a downloadable archive or DMG. | Verify signatures and notarization on a clean macOS environment. Publish checksums and exact supported OS/architectures. |
| M4 — release validation | Obtain a trusted Mac tester or access to a hosted Mac desktop for interactive testing. Check installation, first launch, upgrade, offline use, backup dialogs and accessibility. | Label any CI-only preview honestly; do not call it physically tested without that evidence. |

Not owning a Mac does not prevent a macOS CI build. It does limit hands-on validation. A hosted Mac or volunteer tester can close that gap later; no purchase is assumed. Apple signing credentials and membership are a separate distribution prerequisite, not a requirement for preparing source.

## iOS: native companion, iPhone XR first

Recommended foundation: Swift/SwiftUI with small data, storage and UI modules. Electron and the Android Kotlin Views UI cannot be shipped directly as an iOS app. Share JSON catalogs, schemas and test fixtures; keep the existing desktop and Android implementations intact.

Provisional minimum deployment target: iOS 18, to retain the intended iPhone XR test device. Confirm its actual installed version when iOS work starts. Do not raise the minimum to iOS 26: Apple's compatibility list excludes XR. Build SDK and minimum deployment target are different; select an Xcode version that supports both the required distribution SDK and the intended minimum OS, checking Apple's current table before implementation.

| Milestone | Work | Acceptance gate |
| --- | --- | --- |
| I1 — runnable foundation | Create Xcode project and macOS CI build/test job. Implement local profiles, journals and spreads with atomic storage, editing and deletion. | Offline simulator workflow survives restart; invalid ranges and cross-profile access rejected. |
| I2 — portable data | Implement Files/share-sheet backup export/import, legacy migration, size limits and copy-on-import ID rekeying. | Desktop → iOS → Android round trips preserve supported content. Invalid imports leave existing data intact; keys excluded. |
| I3 — feature parity | Tasks/subtasks, page templates, themes, nine languages, accessibility and 5–30 minute tomato timer. Use persisted deadlines; explicitly define foreground completion and optional notification behavior. | Small-screen layout, rotation, large text, background/resume and time changes tested. No promise of continuous background execution. |
| I4 — QR tools | Add camera permission handling, offline QR scanning/generation and share export. | Denied permissions remain usable; links open externally only after confirmation. |
| I5 — iPhone XR validation | Connect the XR when a signed build is ready and a suitable installation route exists. Test startup, memory, keyboard, camera, Files and suspension on the actual device. | Record real device/iOS version and results. Delete only isolated test profiles and exports; preserve personal data. |
| I6 — distribution | Choose personal Xcode installation initially or Developer Program/TestFlight for repeatable remote testing; prepare privacy disclosures and review materials before App Store submission. | Upgrade and backup restore verified on XR; account/signing requirements met. Publish source and supported installation instructions on GitHub. |

A USB-connected iPhone on this Linux computer alone does not provide Xcode, an iOS SDK or signing. We will need macOS build access (hosted/borrowed Mac or CI), signing, and an installation route. A free Personal Team supports limited personal device testing with short-lived provisioning; TestFlight requires the Developer Program and its builds expire. A GitHub IPA download is not a universally installable equivalent of an Android APK. No membership enrollment, spending, signing setup or iOS build is authorized by this planning document alone.


## Decisions to make when Apple implementation starts

1. Available macOS build/interactive-test access and budget, if any.
2. XR's installed iOS version and preferred installation route.
3. Apple Developer membership/signing availability; no credentials in chat or source.
4. Exact supported macOS versions/architectures based on the then-current Electron/Xcode toolchains.

## Primary references

- [Apple: iOS 26 compatibility](https://support.apple.com/en-ie/123705) — XR is absent from the supported models.
- [Apple: Xcode system requirements](https://developer.apple.com/xcode/system-requirements) — select SDK and deployment support separately.
- [Apple: membership comparison](https://developer.apple.com/support/compare-memberships/) — Personal Team limitations and distribution membership.
- [Apple: TestFlight overview](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/) — beta distribution and build expiration.
- [Electron: code signing](https://www.electronjs.org/docs/latest/tutorial/code-signing) — macOS signing and notarization.
