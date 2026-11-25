# clj-conformance

Compare Clojure implementations with reference JVM Clojure and independently stated expectations. This starter includes a small live comparison, a snapshot of Jolt's JVM certification tooling, and historical fixed/unfixed Jolt regression cases.

## First run
The `jolt-nightly` job runs daily and on manual dispatch. It compares the official rolling Linux `vnightly` build with JVM Clojure 1.12.3 on the numeric pilot. Its artifact records the source commit, release and asset IDs, verified digest, executable version and comparison log.
The daily workflow also compares JVM Clojure 1.12.3 with the newest published 1.13.x (including alphas, betas and RCs). Select `jvm-versions` for that job alone or run `bash pilot/jvm-versions.sh`. It checks the actual loaded versions, compares numeric output and per-row corpus classifications, and archives both versioned reports and diffs. Equal classifications can still hide differences in returned values or exception details.

The [Clojure conformance workflow](https://github.com/chucklehead-dev/clj-conformance/actions/workflows/conformance.yml) runs the numeric pilot and the JVM corpus in separate Linux jobs daily at 06:23 UTC. Choose **Run workflow** to run both or select one suite. From the CLI use `gh workflow run conformance.yml -f suite=all` (or `pilot`, `corpus`). The corpus job uploads its EDN report when produced, including on a failed comparison. The historical regression matrix still requires an explicit pair of Jolt builds.

Install Clojure CLI, Jolt 0.8.30, Bash, and GNU `timeout`. From the repository root run:

```sh
bash pilot/compare.sh
clojure -M test/conformance/certify.clj --self-test
```

The first command runs the same small program on the JVM and Jolt, checking literal and runtime numeric paths on both against the expected values. A passing run prints `[[31 31 -1] [31 31 -1]]`. Set `CLOJURE` and `JOLT` to override executable paths. The second command tests the imported certifier's classification and argument handling.

To certify the imported trusted corpus on the JVM, run `clojure -M test/conformance/certify.clj --edn report.edn`. This evaluates Clojure source. Its profile and divergence ledger are snapshots from the source revision below. The upstream Chez corpus runner remains part of the Jolt compiler tree.

The separate historical regression matrix requires Babashka, GNU timeout, and explicit absolute `JOLT_UNFIXED` and `JOLT_FIXED` executable paths. Run `bb regressions/jolt/run.bb`. It checks previously identified pass/fail signatures against those builds. See the catalog for applicable revisions.

## Source lineage

| Public source snapshot | Imported material |
| --- | --- |
| [casselc/jolt at 33a1bfc](https://github.com/casselc/jolt/tree/33a1bfc44010c0a3dd61ba6dce8e67303ffe0cff/test) | `test/chez/corpus.edn`, the certifier, known divergences and host profile under `test/conformance/`; certification README and SPEC under `docs/upstream/`. |
| [jolt-aspect-packs at dcf9419](https://github.com/chucklehead-dev/jolt-aspect-packs/tree/dcf941956f3b40e9538fb2401c8522ac1f2d3153) | Portable regression scripts, catalog, runner, fork coverage ledger, pending string-indexing witness, original matrix and spec-corpus design documents. |

The original EPL-2.0 license texts are in `licenses/`. Imported documents describe their source repositories and can reference targets, dependencies or relative paths available only there. Source repositories retain their history. For related design and executable oracles see [correctness pack roadmap #53](https://github.com/chucklehead-dev/jolt-aspect-packs/issues/53), [spec corpus roadmap #69](https://github.com/chucklehead-dev/jolt-aspect-packs/issues/69), [jolt-bytes](https://github.com/casselc/jolt-bytes), and [jolt-bencode](https://github.com/casselc/jolt-bencode).

## Hegel integration after the upgrade

When the [jolt-hegel upgrade](https://github.com/chucklehead-dev/jolt-hegel/pull/122) and subsequent native-engine migration are qualified, pin the verified full Hegel commit and native version in a generator-only dependency root. The JVM and derivative workers can continue accepting plain fixtures without Hegel installed.

Start by generating bounded numeric expression recipes. Evaluate each in literal and runtime-value form on both workers. Shrink while preserving the operation and pair of outcomes that differ. Save the minimized standalone program, ordinary EDN input, both runtime versions and observations, plus the generator's native replay bundle. Track agreement with JVM Clojure separately from agreement with the independent expected behavior. Extend to vector views and map update histories once the numeric worker protocol is established. [Article motivating these comparisons](https://s.conao3.com/blog/2026/e4f733)

The [portable vector pack](packs/vector/README.md) defines the domain, oracle, evidence required for portable assertions, and how to reproduce discovery runs. The JVM comparison gates exact reviewed outcomes in `pilot/reviewed-1.13.0-alpha11.edn` against that version alone. The baseline JVM retains its original gate. Both per-version EDN reports retain the observed differences.
