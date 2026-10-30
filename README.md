# BdayBoom 🎉

Birthday reminders that actually arrive. Offline, no ads, no account: your list stays on your phone.

## Install

Get the latest APK from the [Releases page](https://github.com/enjeyisback/bdayboom/releases/latest).

1. Open the link on your Android phone (Android 8.0 or newer) and download `BdayBoom-<version>.apk`.
2. Tap the file and allow "install unknown apps" when Android asks.
3. Open BdayBoom and run the **Reminder check-up**. On Xiaomi, Redmi and POCO phones it walks you through turning on Autostart, so reminders survive a restart.

Once installed, BdayBoom tells you when a new version is out, and you can also check under **Settings → Version**.

## What it does

- Countdown list of birthdays, anniversaries and events, with today's people up top
- Reminders on the day and up to 2 weeks before, on the minute; today's reminder stays until you tap **Wished**
- Imports birthdays from your contacts and picks up dates you change later
- Calendar view, groups, search, photos, gift notes, star signs
- Backup and restore to a file
- Home-screen widgets
- Light and dark themes

## Build

Requires JDK 17 and the Android SDK (compileSdk 35).

```
./gradlew assembleDebug
```

Optional `local.properties` entry, for the in-app updater:

```
UPDATE_REPO=enjeyisback/bdayboom
```

Publishing a release is covered in [RELEASING.md](RELEASING.md).

## Licences

The fonts (Plus Jakarta Sans, Playfair Display, Manrope) are under the SIL Open Font License 1.1; their licence texts are in `app/src/main/assets/licenses`. The icons are Material Symbols, under Apache 2.0.
