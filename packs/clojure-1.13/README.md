# Clojure 1.13 language properties

## Does legacy destructuring still work when you use the new 1.13 features?

Suppose a function accepts an options map. It has always used `:keys` to read an optional retry count and `:or` to supply a default. In 1.13 the author adds a required ID with `:keys!` and asks for a map of the selected options with `:select`. Callers should still get the same retry count. The new summary should agree with the value the function actually uses.

```clojure
(let [{:keys! [id] :keys [retries] :or {retries 3}
       :select picked :as supplied} {:id 42 :debug true}]
  [retries picked supplied])
;; => [3 {:id 42, :retries 3} {:id 42, :debug true}]
```

The pack checks these properties of **existing bindings combined with required keys and summaries**:

1. **A default applies only to an absent key.** In the example, omitting `:retries` gives both the local and the selected map a value of `3`. Supplying `:retries nil` or `:retries false` preserves that value in both places.
2. **A required key can hold a falsey value.** A required function parameter accepts a present `:id` whose value is `nil` or `false`. Omitting the key raises an error. With `:missing`, destructuring can instead collect the missing key and bind the local to `nil`. The report distinguishes that case from a present key whose value happens to be `nil`.
3. **Each summary describes the same input from a different perspective.** For the example above, `:select` includes the ID and the defaulted retry count. `:excess` would contain just `{:debug true}`. `:all` would include all three entries. The existing `:as` binding still gives the caller the original two-entry map.
4. **Renaming a local preserves which key it reads.** If a local named `id` is bound to the required key `:acct/id`, an unrelated `:id` cannot satisfy it. Another test uses the legal map keys `false` and `nil`. Asking for a summary should still allow a default for their local. Alpha11 rejects this combination. [Example and compiler error](../../docs/clojure-1.13-defects.md#3-destructuring-with-select-or-all-rejects-a-local-default-when-the-map-key-is-false-or-nil)
5. **A missing nested value is reported at the right path.** If `:card` and its child `:x` are required, an empty input produces a missing report of `{:card {:x nil}}`. A separate check binds the whole missing card with `:as`. The local should be `nil`; an earlier alpha exposed an internal Java object here.
6. **Bindings stay independent across branches and loop iterations.** If two branches each contain an `:id`, removing the left ID must preserve the right local and its missing-key report. Repeating the same binding in `let`, a function parameter, and `loop` checks that `recur` reads the new input each time.
7. **Requesting a summary should not run a default expression extra times.** The test uses a counter as the default and compares the same loop with and without `:select` and `:all`. Each summary should contain the value the local received on that iteration, and the counter should follow the same sequence in both versions of the loop.

One related test **records a change in evaluation order**. If the input expression and the default both throw, JVM 1.12.3 and 1.12.7 surface the input error. Alpha11 surfaces the default error before the input runs. Computing the input in a preceding local makes all three versions surface the input error. CI reports this as an observed version difference pending contract review.

## Do lookup and merge operations preserve which values callers see?

- **Inlining a nested lookup should preserve argument evaluation order.** A test calls two-argument `get-in` with a key expression that updates the map. The literal path, precomputed path, and Var call should all read the updated value. Alpha11’s inlined call can read the old one. A separate three-argument test checks that a present `nil` or `false` survives when the caller supplies a fallback. [Minimal inlining example](../../docs/clojure-1.13-defects.md#1-two-argument-get-in-inlining-can-read-the-map-before-the-path-is-fully-evaluated)
- **Merging one branch should preserve its neighbors.** When two nested maps share a leaf, `merge-deep` takes the later value. `merge-deep-with` gives the combining function the old and new values in that order, including when either is `nil`.
- **The construction history of a map should not change its merge result.** Build equal maps directly, by growing and deleting entries, or as sorted maps. Check that `merge-with` produces the same entries and calls its combining function once per overlapping key with the right arguments.

## Compiler and metadata interaction properties

- **An accurate result hint should leave a valid loop compilable.** A loop that counts down and returns a string should still work when its tail macro call is marked `^String`. Alpha11 rejects the hinted `recur`. For an unambiguous method call, the tests also compare the value, order of side effects, and exception data with and without a receiver hint. [Minimal hint example](../../docs/clojure-1.13-defects.md#2-a-correct-result-type-hint-can-stop-a-loop-from-compiling)
- **Metadata on code and metadata on a returned value serve different purposes.** Annotating a pass-through macro call should still evaluate its argument once and return the same object with its own metadata. Changing a collection’s user metadata should preserve equality, hash, map lookup, and set membership. Earlier values should retain their original metadata.
- **Growing and shrinking a transient map should preserve its contents.** Compare each edit against a persistent map, save snapshots, then continue editing. Check that saved values stay unchanged. The 1.13 checks also verify that converting a transient back to a persistent collection preserves its metadata.

## Source loading and ahead-of-time compilation

Running a namespace from source and running its compiled classes should yield the same values, metadata, object identity within a run, and effects in the same order. The AOT test uses fresh JVMs and loads the emitted classes with the fixture source absent. The hinted-tail fixture also checks that source loading and AOT compilation reproduce the same alpha11 compiler error.

## Run or replay a property

From the repository root, choose the script for the behavior you want to investigate. For a CI failure, use the Clojure version and seed from its JVM comparison artifact.

| Script in this directory | What it exercises |
| --- | --- |
| [check.clj](check.clj) | Fixed combinations of required, optional, missing and extra keys, plus lookup and recursive merge cases. |
| [swarm.clj](swarm.clj) | Generates nested map binding forms and inputs, then predicts each local and missing path. Seed `13011` reproduces the historical alpha10 nested-required regression. |
| [mixed.clj](mixed.clj) | Alternates map and sequence bindings, including short vectors, rest capture and `:as`, across `fn`, `let` and `loop`. |
| [siblings.clj](siblings.clj) | Changes one sibling at a time. On a value mismatch it reduces the binding and input history while preserving the failing property, side, field and context. |
| [interactions.clj](interactions.clj) | Runs the version comparisons for defaults, inlining, compiler hints, metadata and map histories. |

```sh
deps='{:deps {org.clojure/clojure {:mvn/version "1.13.0-alpha11"}}}'
clojure -Srepro -Sdeps "$deps" -M packs/clojure-1.13/check.clj
clojure -Srepro -Sdeps "$deps" -M packs/clojure-1.13/siblings.clj 13011
bash packs/clojure-1.13/aot/run.sh "$(clojure -Srepro -Sdeps "$deps" -Spath)" target/aot-local
```

The swarm, mixed and interaction scripts take a seed in the same way as the sibling script. On Jolt, run `jolt -Scp packs/clojure-1.13 packs/clojure-1.13/siblings.clj 13011 --probe` to exercise supported bindings. CI records the three reproduced alpha11 defects as known only for their specific observations on that version. [Runtime coverage](../../docs/coverage.md) · [1.13 defect brief](../../docs/clojure-1.13-defects.md) · [Development changelog](https://clojure.org/releases/devchangelog)
