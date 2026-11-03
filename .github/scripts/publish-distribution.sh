#!/usr/bin/env bash
set -euo pipefail
: "${GH_TOKEN:?ANDROID_DISTRIBUTION_TOKEN secret is required}"
: "${VERSION:?VERSION is required}"
: "${VERSION_CODE:?VERSION_CODE is required}"
: "${GITHUB_RUN_NUMBER:?GITHUB_RUN_NUMBER is required}"
APP="SDCleaner"
TARGET="N2K02/n2k02.github.io"
SLUG="sdcleaner"
APK="$APP-$VERSION.apk"
test -s "$APK"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]$ ]] || { echo "::error::Invalid version"; exit 1; }
CHANNEL=stable
TAG="$SLUG-v$VERSION"
VERSION_NAME="$VERSION"
MANIFEST="$SLUG/updates/stable.json"
if [[ "$GITHUB_EVENT_NAME" == "pull_request" ]]; then
  : "${PR_NUMBER:?PR_NUMBER is required for experimental publishing}"
  CHANNEL=experimental
  TAG="$SLUG-experimental-pr-$PR_NUMBER-run-$GITHUB_RUN_NUMBER"
  VERSION_NAME="$VERSION-experimental.$GITHUB_RUN_NUMBER"
  MANIFEST="$SLUG/updates/experimental.json"
fi
SHA=$(sha256sum "$APK" | cut -d' ' -f1)
SIZE=$(stat -c '%s' "$APK")
if gh release view "$TAG" --repo "$TARGET" >/dev/null 2>&1; then
  echo "::error::Release tag already exists; refusing to overwrite an APK"
  exit 1
fi
gh release create "$TAG" "$APK" --repo "$TARGET" --target main --title "$APP $VERSION ($CHANNEL)" --notes-file release-notes.md --latest=false
URL="https://github.com/$TARGET/releases/download/$TAG/$APK"
NOTES=$( { grep '^- ' release-notes.md || true; } | head -10 | sed 's/^- //' | jq -R -s 'split("\n") | map(select(length > 0))')
jq -n --arg channel "$CHANNEL" --arg tag "$TAG" --arg versionName "$VERSION_NAME" --arg apkName "$APK" --arg apkUrl "$URL" --arg pageUrl "https://github.com/$TARGET/releases/tag/$TAG" --arg sha256 "$SHA" --argjson versionCode "$VERSION_CODE" --argjson apkSize "$SIZE" --argjson notes "$NOTES" '{channel:$channel,tag:$tag,versionName:$versionName,versionCode:$versionCode,apkName:$apkName,apkUrl:$apkUrl,apkSize:$apkSize,sha256:$sha256,pageUrl:$pageUrl,notes:$notes}' > manifest.json
CURRENT=$(gh api "repos/$TARGET/contents/$MANIFEST" --jq .content 2>/dev/null | tr -d '\n' | base64 -d 2>/dev/null || true)
OLD=$(jq -r '.versionCode // 0' <<<"$CURRENT" 2>/dev/null || echo 0)
if [[ "$OLD" =~ ^[0-9]+$ ]] && (( OLD >= VERSION_CODE )); then
  echo "A newer $CHANNEL version is already published; preserving its manifest."
  exit 0
fi
ENC=$(base64 -w0 manifest.json)
for attempt in 1 2 3 4 5; do
  CURRENT_SHA=$(gh api "repos/$TARGET/contents/$MANIFEST" --jq .sha 2>/dev/null || true)
  ARGS=(--method PUT "repos/$TARGET/contents/$MANIFEST" -f "message=Publish SDCleaner $VERSION ($CHANNEL)" -f "content=$ENC" -f "branch=main")
  [[ -z "$CURRENT_SHA" ]] || ARGS+=(-f "sha=$CURRENT_SHA")
  if gh api "${ARGS[@]}" >/dev/null; then exit 0; fi
  [[ "$attempt" -lt 5 ]] || { echo "::error::Could not update $MANIFEST after five attempts" >&2; exit 1; }
  sleep "$((attempt * 2))"
done
