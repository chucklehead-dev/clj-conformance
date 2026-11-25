#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=target/jvm-versions
mkdir -p "$out"
repo=https://maven-central.storage-download.googleapis.com/maven2
curl -fsSL --retry 3 "$repo/org/clojure/clojure/maven-metadata.xml" -o "$out/metadata.xml"
resolved=$(python3 - "$out/metadata.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
stable = []
versions = []
for v in ET.parse(sys.argv[1]).findall('.//versions/version'):
    if re.fullmatch(r'1\.12\.\d+', v.text or ''):
        stable.append((int(v.text.split('.')[-1]), v.text))
    m = re.fullmatch(r'1\.13\.(\d+)(?:-(alpha|beta|rc)(\d+))?', v.text or '')
    if m:
        versions.append(((int(m[1]), {'alpha':0,'beta':1,'rc':2,None:3}[m[2]], int(m[3] or 0)), v.text))
print(max(stable)[1], max(versions)[1])
PY
)
read -r stable candidate <<< "$resolved"
echo "Comparing pinned 1.12.3, stable $stable and candidate $candidate"
failed=0
for version in 1.12.3 "$stable" "$candidate"; do
  deps="{:deps {org.clojure/clojure {:mvn/version \"$version\"}} :mvn/repos {\"central\" {:url \"$repo/\"}}}"
  run=(clojure -Srepro -Sdeps "$deps" -M)
  bash packs/vector/run.sh "${run[@]}" || failed=1
  actual=$("${run[@]}" -e '(print (clojure-version))')
  test "$actual" = "$version"
  runtime_cp=$(clojure -Srepro -Sdeps "$deps" -Spath)
  bash packs/clojure-1.13/aot/run.sh "$runtime_cp" "$out/$version.aot" 2>&1 | tee "$out/$version.aot.log" || failed=1
  "${run[@]}" packs/clojure-1.13/interactions.clj "${GITHUB_RUN_ID:-13011}" 2>&1 | tee "$out/$version.interactions.log" || failed=1
  if [[ -f target/vector/shared.edn ]]; then
    mkdir -p target/shared
    case "$version" in
      1.12.3) label=jvm-baseline ;;
      "$stable") label=jvm-stable ;;
      *) label=jvm-candidate ;;
    esac
    "${run[@]}" packs/shared/worker.clj target/vector/shared.edn "$label" "$actual" "target/shared/$label.edn" || failed=1
  fi
  "${run[@]}" pilot/numeric.clj > "$out/$version.numeric" || failed=1
  if [[ "$version" = "$candidate" ]]; then
    "${run[@]}" packs/clojure-1.13/check.clj 2>&1 | tee "$out/$version.properties.log" || failed=1
    seed=${DESTRUCTURING_SEED:-${GITHUB_RUN_ID:-13011}}
    "${run[@]}" packs/clojure-1.13/swarm.clj "$seed" 2>&1 | tee "$out/$version.swarm.log" || failed=1
    "${run[@]}" packs/clojure-1.13/mixed.clj "$seed" 2>&1 | tee "$out/$version.mixed.log" || failed=1
    "${run[@]}" packs/clojure-1.13/siblings.clj "$seed" 2>&1 | tee "$out/$version.siblings.log" || failed=1
  fi
  unset CLJ_CONFORMANCE_REVIEW
  if [[ "$version" != 1.12.3 && -f "pilot/reviewed-$version.edn" ]]; then
    export CLJ_CONFORMANCE_REVIEW="pilot/reviewed-$version.edn"
  fi
  "${run[@]}" test/conformance/certify.clj --edn "$out/$version.edn" \
    --outcomes "$out/$version.rows" || failed=1
done
for result in numeric rows; do
  diff -u "$out/1.12.3.$result" "$out/$stable.$result" > "$out/pinned-vs-stable.$result.diff" || failed=1
  head -100 "$out/pinned-vs-stable.$result.diff"
  diff -u "$out/$stable.$result" "$out/$candidate.$result" > "$out/$result.diff" || failed=1
  head -100 "$out/$result.diff"
done
exit "$failed"
