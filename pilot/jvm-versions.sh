#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=target/jvm-versions
mkdir -p "$out"
repo=https://maven-central.storage-download.googleapis.com/maven2
curl -fsSL --retry 3 "$repo/org/clojure/clojure/maven-metadata.xml" -o "$out/metadata.xml"
candidate=$(python3 - "$out/metadata.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
versions = []
for v in ET.parse(sys.argv[1]).findall('.//versions/version'):
    m = re.fullmatch(r'1\.13\.(\d+)(?:-(alpha|beta|rc)(\d+))?', v.text or '')
    if m:
        versions.append(((int(m[1]), {'alpha':0,'beta':1,'rc':2,None:3}[m[2]], int(m[3] or 0)), v.text))
print(max(versions)[1])
PY
)
echo "Comparing 1.12.3 and $candidate"
failed=0
for version in 1.12.3 "$candidate"; do
  deps="{:deps {org.clojure/clojure {:mvn/version \"$version\"}} :mvn/repos {\"central\" {:url \"$repo/\"}}}"
  run=(clojure -Srepro -Sdeps "$deps" -M)
  bash packs/vector/run.sh "${run[@]}" || failed=1
  actual=$("${run[@]}" -e '(print (clojure-version))')
  test "$actual" = "$version"
  "${run[@]}" pilot/numeric.clj > "$out/$version.numeric" || failed=1
  if [[ "$version" = "$candidate" ]]; then
    "${run[@]}" packs/clojure-1.13/check.clj 2>&1 | tee "$out/$version.properties.log" || failed=1
  fi
  unset CLJ_CONFORMANCE_REVIEW
  if [[ "$version" != 1.12.3 && -f "pilot/reviewed-$version.edn" ]]; then
    export CLJ_CONFORMANCE_REVIEW="pilot/reviewed-$version.edn"
  fi
  "${run[@]}" test/conformance/certify.clj --edn "$out/$version.edn" \
    --outcomes "$out/$version.rows" || failed=1
done
for result in numeric rows; do
  diff -u "$out/1.12.3.$result" "$out/$candidate.$result" > "$out/$result.diff" || failed=1
  head -100 "$out/$result.diff"
done
exit "$failed"
