#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -r "$out"' EXIT
if [[ -n ${PILOT_CLOJURE_JAR:-} ]]; then
  timeout 60 java -cp "$PILOT_CLOJURE_JAR" clojure.main -e \
    '(assert (= "1.12.3" (clojure-version))) (load-file "pilot/numeric.clj")' > "$out/jvm"
else
  timeout 60 "${CLOJURE:-clojure}" -M pilot/numeric.clj > "$out/jvm"
fi
# The worker uses only clojure.core. Bypass JVM dependency resolution on Jolt.
timeout 60 "${JOLT:-jolt}" -Scp pilot pilot/numeric.clj > "$out/jolt"
diff -u "$out/jvm" "$out/jolt"
cat "$out/jvm"
