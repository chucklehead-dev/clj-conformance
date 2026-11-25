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

## Do selected and unused entries describe the binding that ran?

Suppose an options map has two entries, `{:a 10 :b 20}`, and the binding reads one of them. If the local receives `10`, the selected map should contain `{:a 10}` and the unused map should contain `{:b 20}`. Those two maps should have no key in common, and together they should account for the input.

1. **One lookup, two summaries.** Supply the key as a constant, compute it once in a local, or put the key expression directly in the binding. Request `:select`, `:excess`, both, or neither. Distinct input values let the test tell which entry supplied the local.
2. **Observe repeated work.** Use a key function that always returns `:a`, alternates between `:a` and `:b`, or throws on its second call. The first function reveals extra calls even when the values look correct. The other two reveal whether repeated work changes the summaries or makes the form throw.
3. **Follow leaf paths through defaults.** In a nested map, selected and unused leaves may share a parent. The model predicts both nested maps from explicit lookups. An absent selected leaf gets its default in the selected map. Present nil and false leaves keep their values. Unrelated entries stay in the unused map.

On JVM alpha11 this flat form returns `[10 {:b 20} {:b 20} 3]`:

```clojure
(let [calls (atom 0)
      key! #(if (odd? (swap! calls inc)) :a :b)
      {x (key!) :select picked :excess unused} {:a 10 :b 20}]
  [x picked unused @calls])
```

Computing `k` as `(key!)` first and using `{x k ...}` gives `[10 {:a 10} {:b 20} 1]`. A nested variant binds a missing leaf to its default of 7 while its selected child map comes back empty. These are candidate defects pending confirmation of the semantics of computed binding keys. The partition column counts all differences from the model. The comparator retains exact characterized outcomes and writes reproducers for them. Other differences fail CI.

All workers use the same [37 partition recipes and expectations](conformance/partition.cljc). The main shared artifact contains 337 cases including the other property families. jank and ClojureCLR replay the partition recipes in their derivative lanes and retain per-case differences and skip counts in their own artifacts.
