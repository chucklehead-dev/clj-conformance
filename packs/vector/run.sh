#!/usr/bin/env bash
set -euo pipefail
# Arguments are the runtime command, including its classpath options.
work=$(mktemp -d)
trap 'rm -r "$work"' EXIT
for file in packs/vector/fixed.edn "${VECTOR_RECIPES:-}"; do
  [[ -z "$file" ]] || timeout 120 "$@" packs/vector/check.clj check "$file"
done
timeout 30 "$@" packs/vector/check.clj control > "$work/control.edn"
timeout 30 "$@" packs/vector/check.clj replay-control "$work/control.edn"
