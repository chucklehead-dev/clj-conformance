#!/usr/bin/env bash
set -euo pipefail
channel=$1
out=$2
mkdir -p "$out"
case "$channel" in
  release)
    repo=babashka/babashka
    endpoint=releases/latest
    ;;
  dev)
    repo=babashka/babashka-dev-builds
    # Use the same development version pointer as the upstream installer.
    version=$(curl -fsSL --retry 3 https://raw.githubusercontent.com/babashka/babashka/master/resources/BABASHKA_VERSION)
    if [[ "$version" != *-SNAPSHOT ]]; then repo=babashka/babashka; fi
    endpoint="releases/tags/v$version"
    ;;
  *) exit 2 ;;
esac
gh api "repos/$repo/$endpoint" > "$out/release.json"
jq -e --arg repo "$repo" '
  .tag_name as $tag | ("babashka-" + ($tag|ltrimstr("v")) + "-linux-amd64-static.tar.gz") as $name |
  {repo:$repo,tag:$tag,release_id:.id,asset:(.assets[]|select(.name==$name)|{id,name,digest,updated_at})}
' "$out/release.json" > "$out/build.json"
id=$(jq -er .asset.id "$out/build.json")
digest=$(jq -er '.asset.digest | select(test("^sha256:[0-9a-f]{64}$"))' "$out/build.json")
archive=$(mktemp)
trap 'rm -f "$archive"' EXIT
gh api -H 'Accept: application/octet-stream' "repos/$repo/releases/assets/$id" > "$archive"
echo "${digest#sha256:}  $archive" | sha256sum -c -
tar -xzf "$archive" -C "$out" bb
"$out/bb" --version | tee "$out/version.txt"
"$out/bb" describe | tee "$out/describe.edn"
