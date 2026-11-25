#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
out="$(mkdir -p "${1:?output directory required}" && cd "$1" && pwd)"
java -cp "${PILOT_CLOJURE_JAR:?absolute Clojure jar required}:src" clojure.main \
  -m conformance.native-regions >"$out/jvm.edn"
jolt -m conformance.native-regions >"$out/source.edn"
diff -u "$out/jvm.edn" "$out/source.edn"
jolt build -m conformance.native-regions -o "$out/native-regions" 2>&1 | tee "$out/build.log"
grep -Eq 'compiled [1-9][0-9]* native region\(s\)' "$out/build.log"
"$out/native-regions" >"$out/compiled.edn"
diff -u "$out/jvm.edn" "$out/compiled.edn"
