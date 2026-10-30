# Releasing an update

Installed apps update themselves from GitHub, the same way Playgram does. On every start (at most every 6 hours), and from **Settings → Version**, the app reads the **latest release** of `UPDATE_REPO`. If that release's tag is a newer version, it offers the first `.apk` attached to it and hands it to Android's installer.

## One-time setup

1. **A public GitHub repo for releases.** The app reads releases without a login, so the repo must be public. It can be the code repo, or a separate one that only holds releases.
2. **`local.properties`** (not in git):
   ```
   UPDATE_REPO=owner/name
   ```
3. **GitHub CLI**, so the script can publish:
   ```
   winget install GitHub.cli
   gh auth login
   ```
4. **The signing key.** It's at `~/.android-keys/birthdays-release.jks` (outside the project), and its password is in `keystore.properties` (not in git).
   - **Back up both somewhere safe**, like a password manager or an encrypted drive.
   - If you lose them, no installed copy can ever be updated again: everyone would have to uninstall and reinstall.
   - Release certificate SHA-256: `47:58:70:e0:04:91:2e:02:f8:6f:57:3d:9e:b2:71:a0:bd:b7:93:1a:06:3a:02:8a:34:3d:ca:16:ad:1b:08:98`

## Each release

```
./scripts/release.sh 0.2.0 "What's new in this version"
```

The script:
1. Bumps `versionName` and `versionCode`.
2. Runs the tests.
3. Builds the signed release APK into `dist/`.
4. Checks its signature.
5. Commits and tags `v0.2.0`.
6. Pushes and creates the GitHub release with the APK attached.

## Rules

- **Tag = version:** `v0.2.0` must match `versionName` `0.2.0`. The script does this for you.
- **Same app id and same key, every time.** The app id `app.bdayboom` is permanent: never change it.
- **Not for the Play Store build.** Google Play doesn't allow apps to update themselves outside Play. A Play build must leave `UPDATE_REPO` empty, which turns the updater off, and drop `REQUEST_INSTALL_PACKAGES`.
- **Debug builds** (`.debug`) are a separate app, signed with the debug key, and never update from GitHub releases.
