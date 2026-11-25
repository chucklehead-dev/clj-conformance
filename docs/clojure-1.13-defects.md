# Three JVM Clojure 1.13 defects

These examples reproduced on both 1.13.0-alpha10 and 1.13.0-alpha11. The first two also run on 1.12.3, which gives us a before-and-after comparison. The third concerns syntax introduced in 1.13. The version numbers here identify tested releases. The first affected release has not been established.

## 1. Two-argument `get-in` inlining can read the map before the path is fully evaluated

Clojure 1.13 inlines two-argument `get-in` calls when the path is written directly as a vector, such as `(get-in m [:a (change!)])`. An ordinary function call evaluates the whole vector, including `(change!)`, before looking up `:a`. The inlined code looks up `:a` first. If `change!` replaces the inner map at `:a`, the lookup continues in the old inner map and returns its old value.

```clojure
(defn read-value [literal?]
  (let [m (doto (java.util.HashMap.) (.put :a {:b :before}))
        change! (fn [] (.put m :a {:b :after}) :b)]
    (if literal?
      (get-in m [:a (change!)])
      (let [path [:a (change!)]]
        (get-in m path)))))

[(read-value true) (read-value false)]
```

**Expected** `[:after :after]`, as on 1.12.3. **Observed** `[:before :after]` on both tested alphas. Each call creates a fresh map.

Binding the path to a local, as in the second branch, makes the update happen before either lookup and gives the expected result. [Implementation at alpha11](https://github.com/clojure/clojure/blob/clojure-1.13.0-alpha11/src/clj/clojure/core.clj#L6403)

## 2. A correct result type hint can stop a loop from compiling

This loop counts down and returns a string. The hint tells the compiler the type of that result.

```clojure
(loop [i 2]
  ^String (if-let [more (pos? i)]
            (recur (dec i))
            "done"))
```

**Expected** `"done"`, as on 1.12.3. **Observed** a compile error, `Can only recur from tail position`, on both tested alphas. Removing just `^String` makes the same loop return `"done"`.

Clojure permits `recur` where control can jump straight back to the loop. The compiler propagates the hint by wrapping the macro expansion in a call to `identity`. That puts the jump inside another function's argument. **Workaround:** remove the hint from the macro call that encloses `recur`. Our alpha11 source-loading and ahead-of-time compilation fixtures reproduce the same failure with a pass-through macro. [Compiler change at alpha11](https://github.com/clojure/clojure/blob/clojure-1.13.0-alpha11/src/jvm/clojure/lang/Compiler.java#L7590)

## 3. Destructuring with `:select` or `:all` rejects a local default when the map key is `false` or `nil`

Start with a keyword key. In the binding below, `x :price` means “look up `:price` and call its value `x`.” The input is empty, so `:or {x 7}` gives `x` the value `7`. The new `:select picked` directive also binds `picked` to a map of the selected entries, using their original keys and including defaults. [Clojure guide to associative destructuring](https://clojure.org/guides/destructuring#_associative_destructuring)

```clojure
(let [{x :price, :or {x 7}, :select picked} {}]
  [x picked])
;; => [7 {:price 7}]
```

Change only the lookup key from `:price` to `false`, another valid Clojure map key.

```clojure
(let [{x false, :or {x 7}, :select picked} {}]
  [x picked])
```

**Expected:** `[7 {false 7}]`. The same local gets the same default. Only the key in the selected map changes.

**Actual on 1.13.0-alpha10 and alpha11:** macro expansion fails with `symbol x in :or does not refer to a binding`. Dropping `:select picked` and returning `x` yields `7`. Using `nil` as the lookup key or requesting `:all` also triggers the error.

The compiler finds the lookup key for `x`, then tests that key’s truthiness to decide whether `x` has a binding. Both `false` and `nil` fail that test. For this single-key example, `(let [x (get m false 7)] [x {false x}])` produces the intended result and preserves any value already present at that key. [Destructuring implementation at alpha11](https://github.com/clojure/clojure/blob/clojure-1.13.0-alpha11/src/clj/clojure/core.clj#L4687)

The JVM comparison runs these [regression properties](../packs/clojure-1.13/interactions.clj) on the baseline and current candidate. [Replay instructions](../packs/clojure-1.13/README.md#compiler-and-metadata-interaction-properties)
