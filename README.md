# clj-conformance

We run small Clojure programs on JVM Clojure, Jolt, Babashka, jank and ClojureCLR to find changes that affect callers. For example, can adding a correct type hint make a loop stop compiling? On two Clojure 1.13 alphas, it did. [Three defects with runnable examples](docs/clojure-1.13-defects.md)

**Start with the [runtime and property matrix](docs/coverage.md)** to see which builds ran each check, which features they skipped, and where they disagree.

## Run the checks

[GitHub Actions](https://github.com/chucklehead-dev/clj-conformance/actions/workflows/conformance.yml) runs the suite daily. Choose **Run workflow → all** for the full matrix or **jvm-versions** for the pinned 1.12.3 baseline, latest stable 1.12.x, and latest 1.13 candidate. From the command line:

```sh
gh workflow run conformance.yml -f suite=all
```

For a quick local check of vector behavior, run `bash packs/vector/run.sh clojure -M` from the repository root with Clojure CLI, Bash and GNU `timeout`. To reproduce the JVM version comparison, run `bash pilot/jvm-versions.sh`. For a JVM/Jolt numeric comparison, run `bash pilot/compare.sh` with Jolt installed.

## Explore the packs

- [Clojure 1.13 properties](packs/clojure-1.13/README.md) test destructuring, lookups, compiler hints, metadata, merges and source versus AOT compilation. They include seeded replay commands and distinguish known, version-specific observations from expected behavior.
- [Portable vectors](packs/vector/README.md) test slices, updates, reductions and retained snapshots against a sequence model, including generated histories and shrinking.
- [Shared differential replay](packs/shared/README.md) generates one set of binding and lookup recipes, runs it on multiple builds, and creates small standalone reproducers for disagreements.
- [Derivative runtimes](packs/derivatives/README.md) describes the common cases used for jank and ClojureCLR.

The separate historical Jolt regression matrix requires Babashka, GNU `timeout`, and absolute `JOLT_UNFIXED` and `JOLT_FIXED` paths. Run `bb regressions/jolt/run.bb`; the catalog identifies applicable revisions. The upstream Chez corpus runner remains in the Jolt compiler tree.

## Source lineage

| Public source snapshot | Imported material |
| --- | --- |
| [casselc/jolt at 33a1bfc](https://github.com/casselc/jolt/tree/33a1bfc44010c0a3dd61ba6dce8e67303ffe0cff/test) | `test/chez/corpus.edn`, the certifier, known divergences and host profile under `test/conformance/`; certification README and SPEC under `docs/upstream/`. |
| [jolt-aspect-packs at dcf9419](https://github.com/chucklehead-dev/jolt-aspect-packs/tree/dcf941956f3b40e9538fb2401c8522ac1f2d3153) | Portable regression scripts, catalog, runner, fork coverage ledger, pending string-indexing witness, original matrix and spec-corpus design documents. |

The original EPL-2.0 license texts are in `licenses/`. Imported documents describe their source repositories and may refer to paths or dependencies only available there. Related work includes [correctness pack roadmap #53](https://github.com/chucklehead-dev/jolt-aspect-packs/issues/53), [spec corpus roadmap #69](https://github.com/chucklehead-dev/jolt-aspect-packs/issues/69), [jolt-bytes](https://github.com/casselc/jolt-bytes), and [jolt-bencode](https://github.com/casselc/jolt-bencode).
