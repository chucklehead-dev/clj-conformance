# Clojure 1.13 language properties

The JVM comparison runs this pack on the latest published 1.13 candidate. The contracts below reflect alpha11. Each check uses plain data as its oracle. A failure prints the property, input, expected value and observed value to the comparison artifact. A later alpha can deliberately change a contract, so review its release notes before reporting a bug.

| Area | Property and bounded domain |
| --- | --- |
| Checked keys, defaults, and summaries | Independently vary a required key, an optional key with a default, and an unrelated key across absent, nil, false, and 7. Verify the local values, original map, and exact selector output for selection, missing keys, excess keys and augmented map. |
| Nested required forms | Vary the outer input between nil, an empty map and a present child map. Vary child and unrelated keys independently. Verify the missing path and projected subtree, including empty selections. Inspect the local bound by nested :as when the outer key is absent (CLJ-2990). |
| Function parameter destructuring | Rename a qualified required key and independently vary a same-name unqualified key. Absence must throw. Present nil and false must reach the function body. |
| Inlined get-in | Compare literal and computed paths against explicit membership checks. Cover missing intermediate and leaf keys, nonassociative intermediate values, and nil and false defaults. |
| Recursive merge | Preserve disjoint siblings at a nested branch. At overlapping leaves, check rightmost precedence for merge-deep and left/right argument order for merge-deep-with, including present nil. |

Run locally with the candidate Clojure CLI dependencies, including its matching core.specs.alpha.

```sh
clojure -Srepro -Sdeps '{:deps {org.clojure/clojure {:mvn/version "1.13.0-alpha11"}}}' -M packs/clojure-1.13/check.clj
```

Alpha10 is a historical negative control. On nil input its nested selector returns an additional selection of an empty card. Alpha11 returns just the missing path, as its upstream regression test specifies.

## Next properties from the changelog

1. Generate a binding shape once and emit it in let, fn and loop. Start with equivalent keyword, string and symbol key families, qualified checked keys, and nested renaming. Compare values against a presence-based model and test rejection of invalid forms separately.
2. Exercise transform-keys with injective key renames on nested maps and mixed collection values. Check an inverse round trip and an independently specified expected tree. Define collision expectations separately if the docs guarantee them.
3. Cross the small-map representation boundary with assoc!, dissoc! and persistent!, comparing every completed history to a persistent map model. Check values and metadata where documented. Representation thresholds and speed belong in benchmarks.
4. Extend literal versus computed get-in with side-effect counters around arguments. Test the same value, evaluation count and order. Check type-hint propagation using a separate compiler fixture.
5. Check Unicode tagged literals by supplying an explicit data reader and comparing tag and payload. Keep Java constructor and interop assertions in a JVM-specific pack.

The alpha9 release dropped the experimental :defaults directive. Use the current :or and :all contracts. Cross-runtime admission requires a shared documented contract, domain and observable outcome for that target. Passing this JVM pack alone supplies evidence only for the tested candidate and inputs.

[Development changelog](https://clojure.org/releases/devchangelog) · [Upstream destructuring and selector tests at alpha11](https://github.com/clojure/clojure/blob/clojure-1.13.0-alpha11/test/clojure/test_clojure/data_structures.clj)

## Recursive destructuring swarm

Each CI run uses its GitHub run ID as a seed. Each of 64 trials picks a subset of key families, defaults, checked and renamed required bindings, and selector summaries. It compiles a function parameter from a tree up to four keys deep, then exercises 32 inputs including nil, empty maps, dense trees and sparse trees with nil branches. The model walks input paths to predict every local and the nested missing-key report. A second check adds an unrelated key carrying false and verifies that only the excess and augmented maps can acquire it. Summaries must contain ordinary data from the generator's domain.

The first mismatch prints the generated form, seed, trial, input and observations. Replay the same revision with the recorded candidate and seed:

```sh
clojure -Srepro -Sdeps '{:deps {org.clojure/clojure {:mvn/version "1.13.0-alpha11"}}}' -M packs/clojure-1.13/swarm.clj 13011
```

Seed 13011 exercises the older alpha10 nested-required regression. The swarm currently generates associative function parameter forms with unique locals. Sequence destructuring, shared locals, invalid forms, and automated shrinking are outside its coverage.
