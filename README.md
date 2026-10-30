<img src="docs/img/icon-512.png" alt="BdayBoom" width="128">

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

## Contributing

Bug reports and pull requests are welcome. If a reminder didn't arrive, please open an issue with your phone model, Android version and what the in-app **Reminder check-up** shows.

## Licence

Copyright (C) 2026 BdayBoom contributors.

BdayBoom is free software: you can redistribute it and/or modify it under the terms of the [GNU General Public License v3.0](LICENSE) as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but **without any warranty**; see the licence for details.

Bundled third-party parts keep their own licences:
- **Fonts:** Plus Jakarta Sans and Manrope, under the SIL Open Font License 1.1. The texts are in `app/src/main/assets/licenses`.
- **Icons:** Material Symbols, under Apache 2.0.
- **Libraries:** AndroidX, Jetpack Compose, Glance and WorkManager, and SQLDelight, all under Apache 2.0.
