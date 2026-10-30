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

git push origin HEAD --tags
REPO=$(grep '^UPDATE_REPO=' local.properties | cut -d= -f2 | tr -d '[:space:]')

if command -v gh >/dev/null 2>&1; then
  gh release create "v$VERSION" "$APK" --title "v$VERSION" --notes "${NOTES:-Version $VERSION}"
else
  # No GitHub CLI: use the GitHub API with the login git already stores (Git Credential Manager).
  # The token stays in this process's memory; it is never printed or written to disk.
  TOKEN=$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null | sed -n 's/^password=//p')
  [[ -n "$TOKEN" ]] || { echo "no GitHub login found: install the GitHub CLI (gh auth login) and run: gh release create v$VERSION $APK"; exit 1; }
  BODY=$(python -I -c 'import json,sys; print(json.dumps({"tag_name": sys.argv[1], "name": sys.argv[1], "body": sys.argv[2]}))' "v$VERSION" "${NOTES:-Version $VERSION}")
  UPLOAD=$(curl -sf -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
    -d "$BODY" "https://api.github.com/repos/$REPO/releases" | python -I -c 'import json,sys; print(json.load(sys.stdin)["upload_url"].split("{")[0])')
  curl -sf -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary @"$APK" "$UPLOAD?name=$(basename "$APK")" >/dev/null
  unset TOKEN
fi
echo "published v$VERSION: https://github.com/$REPO/releases/tag/v$VERSION"
echo "installed apps offer it within 6 hours of opening, or at once via Settings > Version"
