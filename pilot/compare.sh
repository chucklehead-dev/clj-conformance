#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -r "$out"' EXIT
timeout 60 "${CLOJURE:-clojure}" -M pilot/numeric.clj > "$out/jvm"
# The worker uses only clojure.core. Bypass JVM dependency resolution on Jolt.
timeout 60 "${JOLT:-jolt}" -Scp pilot pilot/numeric.clj > "$out/jolt"
diff -u "$out/jvm" "$out/jolt"
cat "$out/jvm"
