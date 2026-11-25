# Portable vector contract, revision 1

A vector update should produce the requested contents without changing an earlier value. This pack checks that promise across slices and sequences of updates, including boundaries near powers of 32. It also checks that reductions visit elements in order. These histories can expose bugs that a single `assoc` or `subvec` example misses. See the [coverage matrix](../../docs/coverage.md) for participating runtimes and artifacts.

| Interaction | What could go wrong? |
| --- | --- |
| Slice, then replace the first or last element | An update through a slice could write to the wrong position in the backing vector. |
| Grow or shrink near sizes 32 and 1,024 | The last element, count, or traversal order could change at a size boundary. |
| Keep an earlier vector and continue editing | A later operation could change an already returned value. The test revisits every saved snapshot. |

After every operation the runner compares the vector with a sequence model, including indexed reads, prefix removal, and reduction order. Inputs use finite vectors, integer or keyword elements, valid slices, and small nonnegative indices. Empty-vector commands avoid invalid calls. The checks make no assumptions about branching factor, chunking or allocation. [Vector reference](https://clojure.org/reference/data_structures#Vectors) · [Subvector contract](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/subvec)

**Admission rule for new properties.** Record a public contract or a derivation from one, the input domain, what each participating host claims to support, the observable result and independent oracle. Each host must pass explicit boundary witnesses. Keep unsupported features visible as capability gaps; give undocumented behavior and version-specific expectations their own labeled characterization tests. If runtimes disagree, check the domain and oracle before attributing a bug. A newly observed consensus alone cannot establish portability.

PRs run fixed fixtures. Scheduled/manual runs add 100 recipes generated once with the run ID as seed by the pinned development Hegel engine (0.51.6). Jobs consume the same uploaded EDN. A pack-local deletion/size shrinker preserves the failed operation and law. The control deliberately loses a write through an offset view; it must shrink to a two-element input and replay in a new process. Run locally with:

    bash packs/vector/run.sh clojure -M
    bash packs/vector/run.sh jolt -Scp packs/vector

Set VECTOR_RECIPES to a downloaded recipe file for the same discovery batch. To replay a minimum, put it inside a one-element EDN vector and pass that file to check.clj check. Timeouts fail the worker and need separate confirmation before reporting a target defect.

A portability review example from the imported corpus: a destructuring default
that refers to a sibling binding returns 2 on Clojure 1.12.3 and fails compilation
on 1.13.0-alpha11 (unresolved symbol). Treat this as a versioned observation;
the original corpus label alone cannot establish a common requirement.
