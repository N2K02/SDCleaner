#!/usr/bin/env bash
set -euo pipefail
slug="${1:?Usage: latest-public-version.sh app-slug}"
[[ "$slug" =~ ^[a-z0-9]+$ ]] || { echo "::error::Invalid app slug: $slug" >&2; exit 1; }
refs=$(gh api --paginate "repos/N2K02/n2k02.github.io/git/matching-refs/tags/$slug-v" --jq '.[].ref') || {
  echo "::error::Unable to read public $slug release tags" >&2
  exit 1
}
printf '%s\n' "$refs" | sed -nE "s|^refs/tags/$slug-v([0-9]+[.][0-9])(-build-[0-9]+)?$|\1|p" | sort -V | tail -1
