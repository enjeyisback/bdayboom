#!/usr/bin/env bash
# Publish a new version that installed apps pick up through the in-app updater.
#
#   ./scripts/release.sh 0.2.0 "What's new, one line or more"
#
# Bumps versionName/versionCode, runs the tests, builds the signed release APK, commits, tags v0.2.0,
# pushes, and creates the GitHub release with the APK attached (needs the GitHub CLI: gh auth login).
# The app reads UPDATE_REPO's latest release: tag = version, first .apk asset = the update.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:?usage: release.sh <version e.g. 0.2.0> \"notes\"}"
NOTES="${2:-}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version must look like 1.2.3"; exit 1; }
[[ -f keystore.properties ]] || { echo "keystore.properties missing: release builds must be signed with the same key every time"; exit 1; }
grep -q '^UPDATE_REPO=.*/' local.properties || { echo "set UPDATE_REPO=owner/name in local.properties first"; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "commit or stash your changes first"; exit 1; }
git rev-parse "v$VERSION" >/dev/null 2>&1 && { echo "tag v$VERSION already exists"; exit 1; }

GRADLE=app/build.gradle.kts
CODE=$(grep -oE 'versionCode = [0-9]+' "$GRADLE" | grep -oE '[0-9]+')
sed -i -E "s/versionCode = [0-9]+/versionCode = $((CODE + 1))/; s/versionName = \"[^\"]+\"/versionName = \"$VERSION\"/" "$GRADLE"
echo "version $VERSION (code $((CODE + 1)))"

./gradlew --console=plain -q testDebugUnitTest assembleRelease
APK="dist/BdayBoom-$VERSION.apk"
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "$APK"

# Refuse to publish an APK that isn't signed with the release key (it would fail to install as an update).
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
APKSIGNER=$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)apksigner.bat
[[ -f "$APKSIGNER" ]] || APKSIGNER="${APKSIGNER%.bat}"
"$APKSIGNER" verify --print-certs "$APK" | grep -m1 "SHA-256"

git add "$GRADLE"
git commit -m "release: v$VERSION"
git tag "v$VERSION"

if command -v gh >/dev/null 2>&1; then
  git push origin HEAD --tags
  gh release create "v$VERSION" "$APK" --title "v$VERSION" --notes "${NOTES:-Version $VERSION}"
  echo "published: installed apps will offer v$VERSION within 6 hours, or at once via Settings > Version"
else
  echo "GitHub CLI not found. Push, then upload $APK to a new release tagged v$VERSION on GitHub:"
  echo "  git push origin HEAD --tags"
fi
