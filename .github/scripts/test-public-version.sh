#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/gh" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail
[[ "$*" == 'api --paginate repos/N2K02/SDCleaner/git/matching-refs/tags/sdcleaner-v --jq .[].ref' ]] || exit 4
[[ "${FAIL_API:-0}" == "0" ]] || exit 5
cat "$FIXTURE"
FAKE
chmod +x "$tmp/gh"
printf '%s\n' refs/tags/sdcleaner-v1.8 refs/tags/sdcleaner-v2.0 refs/tags/sdcleaner-v1.9 refs/tags/sdcleaner-experimental-pr-10-run-1 refs/tags/v100.0 > "$tmp/refs"
actual=$(PATH="$tmp:$PATH" FIXTURE="$tmp/refs" GITHUB_REPOSITORY=N2K02/SDCleaner bash "$root/.github/scripts/latest-public-version.sh" sdcleaner)
[[ "$actual" == "2.0" ]] || { echo "Expected 2.0, got $actual" >&2; exit 1; }
if PATH="$tmp:$PATH" FIXTURE="$tmp/refs" FAIL_API=1 GITHUB_REPOSITORY=N2K02/SDCleaner bash "$root/.github/scripts/latest-public-version.sh" sdcleaner >/dev/null 2>&1; then exit 1; fi
if PATH="$tmp:$PATH" FIXTURE="$tmp/refs" GITHUB_REPOSITORY=N2K02/SDCleaner bash "$root/.github/scripts/latest-public-version.sh" wrong >/dev/null 2>&1; then exit 1; fi
! grep -Rq 'secrets.ANDROID_DISTRIBUTION_TOKEN' "$root/.github/workflows" || exit 1
! test -e "$root/.github/scripts/publish-distribution.sh" || exit 1
echo "SDCleaner source release policy tests passed"
