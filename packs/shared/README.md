# Shared differential replay

This pack generates a single set of seeded recipes and runs those same inputs on multiple runtime builds. It checks observations against an independent model as well as comparing workers. This helps distinguish a disagreement between implementations from a case where all workers agree on an unexpected result.

Sibling destructuring recipes change one branch at a time in `fn`, `let`, and `loop`; the other branch's locals and missing-key summary should stay unchanged. Histories include missing and nil branches, truncated vectors, and restoration. `get-in` recipes check both the result and ordered evaluation trace, including hits, missing keys, non-map intermediates, nil and false leaves, and computed paths. These cases use the three-argument form. The JVM interaction pack tests the separate [two-argument inlining defect](../../docs/clojure-1.13-defects.md#1-two-argument-get-in-inlining-can-read-the-map-before-the-path-is-fully-evaluated), where a path expression changes what the map contains.

The generated EDN records forms, feature requirements, inputs and predictions. Workers record build identity, observations and explicit feature rejections. The comparison artifact includes standalone `repro-*.clj` programs for disagreements; run a reproducer unchanged on the identified builds. It selects the smallest serialized witness actually run by both builds for the differing field and context. This bounded selection complements the sibling runner's reducer; consensus is not proof of a contract.

From the repository root, for example:

```sh
clojure -M packs/shared/generate.clj 13011 --library /tmp/shared.edn
bb packs/shared/worker.clj /tmp/shared.edn bb-release "$(bb --version)" /tmp/bb.edn
clojure -M packs/shared/compare.clj /tmp/shared.edn /tmp/comparison /tmp/bb.edn
```

CI saves recipes and worker reports as artifacts. See the [coverage matrix](../../docs/coverage.md) for participating builds and artifact names.
