# Portable vector contract, revision 1

**Claim.** A persistent vector represents an ordered sequence. A valid half-open subvector exposes the selected elements. An update returns the specified new sequence and leaves previously retained values observable as before. Reduction visits the elements in order. See the [Clojure data structures reference](https://clojure.org/reference/data_structures#Vectors) and the public [subvec](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/subvec), assoc, pop, nth, drop and reduce docstrings.

**Domain.** Finite ordinary persistent vectors, small exact nonnegative indices, integer/keyword elements, eager pure reducers. Slices satisfy 0 ≤ start ≤ end ≤ count. Set/pop commands on an empty vector leave the trace state unchanged and make no invalid call. Sizes beside powers of 32 guide exploration; a runtime may implement these vectors with any branching factor. The expected contents are derived independently with list/sequence operations. The oracle still relies on each host's basic sequence operations and equality; cross-host agreement is additional evidence, not a proof.

**Observation.** Check count, ordered contents, indexed last-element lookup (including the empty default), reduction order, dropped prefix, and all retained snapshots after each command. The contract ignores concrete type, transient optimizations, identity, chunking, allocation, printer format and exception messages. Primitive vectors, Java interop, invalid indices and performance require separate capability or characterization packs.

**Admission rule for new properties.** Record a public contract or a derivation from one, the input domain, what each participating host claims to support, the observable result and independent oracle. Each host must pass explicit boundary witnesses. Keep unsupported features visible as capability gaps; give undocumented behavior and version-specific expectations their own labeled characterization tests. If runtimes disagree, check the domain and oracle before attributing a bug. A newly observed consensus alone cannot establish portability.

PRs run fixed fixtures. Scheduled/manual runs add 100 recipes generated once with the run ID as seed by the pinned development Hegel engine (0.51.6). Jobs consume the same uploaded EDN. A pack-local deletion/size shrinker preserves the failed operation and law. The control deliberately loses a write through an offset view; it must shrink to a two-element input and replay in a new process. Run locally with:

    bash packs/vector/run.sh clojure -M
    bash packs/vector/run.sh jolt -Scp packs/vector

Set VECTOR_RECIPES to a downloaded recipe file for the same discovery batch. To replay a minimum, put it inside a one-element EDN vector and pass that file to check.clj check. Timeouts fail the worker and need separate confirmation before reporting a target defect.
