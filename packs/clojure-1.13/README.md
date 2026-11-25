# Clojure 1.13 language properties

The JVM comparison runs this pack on the latest published 1.13 candidate. The contracts below reflect alpha11. Each check uses plain data as its oracle. A failure prints the property, input, expected value and observed value to the comparison artifact. A later alpha can deliberately change a contract, so review its release notes before reporting a bug.

| Area | Property and bounded domain |
| --- | --- |
| Checked keys, defaults, and summaries | Independently vary a required key, an optional key with a default, and an unrelated key across absent, nil, false, and 7. Verify the local values, original map, and exact selector output for selection, missing keys, excess keys and augmented map. |
| Nested required forms | Vary the outer input between nil, an empty map and a present child map. Vary child and unrelated keys independently. Verify the missing path and projected subtree, including empty selections. Inspect the local bound by nested :as when the outer key is absent (CLJ-2990). |
| Function parameter destructuring | Rename a qualified required key and independently vary a same-name unqualified key. Absence must throw. Present nil and false must reach the function body. |
| Three-argument get-in | Compare literal and computed paths against explicit membership checks. Cover missing intermediate and leaf keys, nonassociative intermediate values, and nil and false defaults. |
| Recursive merge | Preserve disjoint siblings at a nested branch. At overlapping leaves, check rightmost precedence for merge-deep and left/right argument order for merge-deep-with, including present nil. |

Run locally with the candidate Clojure CLI dependencies, including its matching core.specs.alpha.

```sh
clojure -Srepro -Sdeps '{:deps {org.clojure/clojure {:mvn/version "1.13.0-alpha11"}}}' -M packs/clojure-1.13/check.clj
```

Alpha10 is a historical negative control. On nil input its nested selector returns an additional selection of an empty card. Alpha11 returns just the missing path, as its upstream regression test specifies.

## Next properties from the changelog

1. Extend the mixed binding swarm to cover qualified, symbol and string keys across binding contexts. Test rejection of invalid forms separately.
2. Exercise transform-keys with injective key renames on nested maps and mixed collection values. Check an inverse round trip and an independently specified expected tree. Define collision expectations separately if the docs guarantee them.
3. Extend the transient map histories below to hashed keys with deliberately colliding hash codes.
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

Seed 13011 exercises the older alpha10 nested-required regression. This associative swarm uses unique locals.

## Mixed map and sequence bindings

The companion runner alternates map and sequential bindings for two to five layers, with either kind at the root. Map layers check a required sibling with `:keys!` and `:missing`, a defaulted sibling with `:or`, and an explicitly bound child. Sequential layers vary the child position, rest capture, and `:as`. Each generated form runs against 32 inputs in three contexts. The loop carries the next input through `recur`; starting and ending with nil checks that the final iteration clears earlier bindings. Inputs include lists, vectors, truncated sequences, and dense and sparse maps. The oracle predicts each observed local directly from the recipe and input.

Replay with the seed from the JVM comparison artifact:

```sh
clojure -Srepro -Sdeps '{:deps {org.clojure/clojure {:mvn/version "1.13.0-alpha11"}}}' -M packs/clojure-1.13/mixed.clj 13011
```

The mixed runner generates one nested child per layer. It covers ordinary map-to-sequence bindings and checked scalar siblings. Alpha11's binding spec accepts a local symbol or a map binding as the binding inside `:req!`; a vector binding directly inside `:req!` is outside this grammar. Both swarms report replayable failures. Automated shrinking and invalid-form generation remain open.

## Sibling isolation and failure reduction

The sibling runner binds two independent branches with the same source keys to distinct locals. Each seed produces 32 bindings. Each binding runs in fn, let and loop against a 13-state history. Each transition changes one branch, covering removal of an ID, a present nil ID, a truncated vector, a nil branch, an absent branch, and restoration. A model checks both branches and the outer missing-key report. The isolation property also compares the unaffected branch's observed locals and inner missing-key report across the transition.

For a value mismatch, the runner removes input states, branches, directives and nested input data while requiring the same context, property, side and field to fail. The reduced form and ordered inputs appear in the job log alongside the original seed and trial. The reducer finds a locally irreducible case under its transformations. Compile and execution exceptions retain the original replay data. Reduction currently applies to the sibling runner.

The JVM candidate exercises every feature family. Stable and nightly Jolt probe required renamed locals and required nested map bindings with missing-key collection. Each probe must either return the expected results or explicitly reject the directive. The runner continues with ordinary mixed bindings when both probes report unsupported. To replay, run the same revision and runtime with the recorded seed:

```sh
clojure -Srepro -Sdeps '{:deps {org.clojure/clojure {:mvn/version "1.13.0-alpha11"}}}' -M packs/clojure-1.13/siblings.clj 13011
jolt -Scp packs/clojure-1.13 packs/clojure-1.13/siblings.clj 13011 --probe
```

## Compiler and metadata interaction properties

The JVM lane runs `interactions.clj` on 1.12.3 and the resolved 1.13 candidate. Each version has an interactions log in the JVM comparison artifact. Replay with the logged seed:

```sh
clojure -Srepro -Sdeps '{:deps {org.clojure/clojure {:mvn/version "1.13.0-alpha11"}}}' -M packs/clojure-1.13/interactions.clj 13011
```

| Transformation | Observations |
| --- | --- |
| Change two-argument get-in between a literal path, precomputed path and Var call | A later path element mutates the source map. All ordinary argument expressions should finish before lookup begins. Record the value and how often the key expression ran. The three-argument checks elsewhere exercise the defaulted path. |
| Add an accurate String or Object hint to the result of if-let, when or a pass-through macro in a loop | The program should compile, recur twice, and return the same string. |
| Hint a local receiver for an unambiguous method, or annotate a pass-through macro invocation | Compare effect order, return value, exception data and, for the pass-through macro, reference identity and the returned vector’s existing metadata. |
| Move the destructuring input into a preceding local, independently throwing from input and default | Record which expression ran, in what order, and which exception reached the caller. Both evaluations use a single default for an absent key. |
| Add :select, :all or both to a destructuring loop | Compare default invocation counts against the same loop without summaries. Check each local and summary after transitions through absent, false and nil values. |
| Merge equal maps built directly, grown then shrunk, or built as sorted maps | Compare merge-with output and the multiset of callback arguments against an independent presence model. Overlapping nil/false values occur on both sides across trials. Tagged values identify the key for each callback. |
| Rename a source key to nil or false with a local default | Adding :select or :all should preserve the local and report the corresponding source key. Check absent, present nil, false and 4. |
| Replace or update user metadata | Equality, hash, lookup as a key and set membership stay stable. The new value has the requested metadata and the earlier value retains its own. |
| Grow, delete and regrow a map via transients | Replay the same history under nil, empty and populated metadata. Observe count, find, contains? and get after each operation. Freeze at checkpoints and resume through a fresh transient. Check retained snapshots after subsequent writes. Compare by contents, independently of representation. |

Both JVMs exercise metadata propagation through into and update-vals. The 1.13 candidate additionally checks metadata across transient-to-persistent conversion for vectors, sets and maps, including map growth across both small-map thresholds. Type-hint equivalence here covers accurate reference hints and unambiguous calls. Overload selection and primitive coercion have different semantics.

On 1.12.3 a direct destructuring input runs before its default. On alpha11 the default can run before the input. When both throw, the caller receives the default's exception and the input expression never runs. Prebinding the input leaves the 1.12.3 behavior intact on both tested versions. Alpha11 logs these exact four traces as `:observed-transition` pending contract review. For the separate loop-count control, both JVMs evaluated the default twice on initial loop entry, then once on each recur in the tested history. The property compares traces across summary configurations without requiring that implementation detail.

The oracle records the three reproduced alpha11 defects as `:known` only for their specific inputs and observed value or compiler phase, cause class and message. The intended behavior counts as a pass on every version. Other failures, including a recurrence on a later alpha, fail the job.

[Clojure metadata reference](https://clojure.org/reference/metadata) · [Development changelog](https://clojure.org/releases/devchangelog)

## Source loading and ahead-of-time compilation

The JVM job runs the same two fixture namespaces from source and from AOT-compiled classes. Source loading, compilation, and class loading each get a fresh JVM. The class-loading stage checks that the fixture source is absent from its classpath. It compares return values, metadata, reference identity within each run, and the order and count of observable effects against both the source run and an independent expected result. The hinted recur fixture also checks the precise alpha11 compile failure in both modes. A fixture that fails to compile has no class execution result. The JVM job archives each stage's EDN report.

```sh
cp=$(clojure -Srepro -Sdeps '{:deps {org.clojure/clojure {:mvn/version "1.13.0-alpha11"}}}' -Spath)
bash packs/clojure-1.13/aot/run.sh "$cp" target/aot-local
```

[Plain-language explanations and minimal examples of the three 1.13 defects](../../docs/clojure-1.13-defects.md)
