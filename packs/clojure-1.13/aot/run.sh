#!/usr/bin/env bash
set -euo pipefail
cp=$1
out=$2
fixture=packs/clojure-1.13/aot
mkdir -p "$out"
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
for mode in source compile classes compare; do
  case "$mode" in
    source|compile) path="$fixture/src:$cp" ;;
    classes) path="$classes:$cp" ;;
    compare) path="$cp" ;;
  esac
  java -cp "$path" clojure.main "$fixture/driver.clj" "$mode" "$out" "$classes"
done
