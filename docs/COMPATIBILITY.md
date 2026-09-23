# Compatibility investigations — task 12

Investigated 2026-09-23. This records evidence and supported launch paths, not a claim of full integration.

## Klub Czytelnika / Cover to Cover Club

The installed application was identified as `com.quillguild.covertocoverclub`, published as **Cover to Cover Club** by Radosław Ziemniewicz. Its [official Google Play listing](https://play.google.com/store/apps/details?id=com.quillguild.covertocoverclub) documents reading tracking, ISBN scanning, shareable book cards/calendars, and website book links that open in the app.

No documented bulk library import/export API was identified in the reviewed public listing and website. This is a research limitation, not proof that no interface exists. Digital Journal therefore offers a normal Android launch shortcut in About. It does not access the other app's private database, scrape an account or invent an import format. Collection import remains deferred with task 8. A future integration needs a supported export sample or published API/deep-link contract.

## Xiaomi Photo Printer Pro-D0F1

`D0F1` is not enough to prove the exact commercial hardware model. The installed Xiaomi Home package is `com.xiaomi.smarthome`; a normal launch shortcut is available in About.

For the **Xiaomi Portable Photo Printer Pro**, [Xiaomi's official specifications](https://www.mi.com/sg/product/xiaomi-portable-photo-printer-pro/specs/) list Bluetooth 5.2, Xiaomi Home, JPEG/PNG inputs, 50 × 76 mm paper and 313 × 313 dpi. These specifications should only be applied after confirming that this is the user's exact model. The USB-C port listing does not establish support for USB desktop printing.

The supported manufacturer workflow for that model is Xiaomi Home on a compatible phone/tablet. No documented public direct-print API, Linux CUPS driver or Windows printer driver was established in this investigation. Digital Journal does not claim direct printing. Image export and print-layout work remain deferred with task 11. No physical test print has been performed and no paper/ink consumed.

## Platform verification

See release notes for tests performed on each build. Building a Windows executable on Linux is not equivalent to running it on Windows. QR scanner permission denial must return to the journal without changing data; supported links are HTTP/HTTPS and are shown before opening.
