# Which Clojure behaviors have we checked?

CI uses the latest stable JVM 1.12.x as the reference for the 1.13 candidate and retains 1.12.3 to catch changes relative to the original corpus. The [full comparison run](https://github.com/chucklehead-dev/clj-conformance/actions/runs/45548696444) includes two Jolt builds, two Babashka builds, jank, and two ClojureCLR builds. The workflow resolves rolling builds anew each run.

## Coverage by property

| Property or suite | JVM 1.12.3 / 1.12.7 | JVM 1.13 alpha11 | Jolt release / nightly | Babashka release / dev | jank lazy / eager | CLR stable / 1.13 candidate |
| --- | --- | --- | --- | --- | --- | --- |
| Vector slices and updates preserve saved values | Tested | Tested | Tested | Tested | — | Tested |
| Same lookup and binding recipes replayed across builds | Shared | Shared | Shared | Shared | — | — |
| Required bindings in those shared recipes | Skipped | Tested | Skipped | Tested | — | — |
| Change one sibling across a loop while checking the other | Shared | Shared + swarm | Shared + swarm | Shared + swarm | Fixed cases | Fixed cases |
| Nested lookup with a default for missing keys | Shared | Shared | Shared | Shared | Fixed cases | Fixed cases |
| Missing versus nil/false in merge callback arguments | Growth histories | Growth histories | — | — | Fixed cases | Fixed cases |
| Compiler hints, metadata, and source versus AOT | Tested | Known defects | — | — | — | — |
| Rename a falsey key under a destructuring summary | — | Known defect | — | — | — | — |
| Historical corpus classifications and numeric results | Both | Both | Numeric | Numeric | — | — |

**Shared** means the workers receive identical generated cases. **Skipped** means the worker rejected a feature probe and recorded the rejection. A dash means this suite supplies no result for that cell. A green job means its gates passed; the JVM candidate still reports exact known failures. jank's lazy and eager modes use the same upstream continuous binary. The CLR columns cover NuGet stable and the latest 1.13 build.

### Results from the linked run

All seven shared workers received the same 300 cases. The comparison found zero disagreements and zero unexpected model results. The 171 skips were recipes requiring 1.13 binding features.

| Builds | Matched the model | Feature skipped | Differed |
| --- | ---: | ---: | ---: |
| JVM 1.12.3, JVM 1.12.7, Jolt release, Jolt nightly (each) | 129 | 171 | 0 |
| JVM 1.13.0-alpha11, Babashka release, Babashka dev (each) | 300 | 0 | 0 |

jank passed the fixed portable checks in both JIT modes. Both CLR builds passed those checks and the vector histories. Counts above describe only the shared replay cases. [Run summary and artifacts](https://github.com/chucklehead-dev/clj-conformance/actions/runs/45548696444)

## What changed between builds?

| Example | Observations | Why it matters |
| --- | --- | --- |
| A path expression updates the map inside two-argument `get-in` | JVM 1.12.3 and 1.12.7 read the updated value. The inlined alpha11 call reads the old value. | Moving the path into a local can change the result. |
| A correct type hint encloses a tail `recur` | Both stable JVM versions compile and run the loop. Alpha11 rejects it during compilation. | Adding a hint can stop valid code from building, including during AOT. |
| `:select` asks for a summary of a defaulted `false` or `nil` key | Alpha11 rejects the binding. A keyword key works with the same default. | A summary can stop code that uses a legal map key from compiling. |
| The input and default have visible effects | JVM 1.12.3/1.12.7, jank and CLR 1.12.6 evaluate the direct input first. JVM alpha11 and CLR 1.13.0-alpha8 evaluate the default first. | On the JVM, changing that order can change which exception reaches the caller. This is tracked as an observed version difference pending contract review. |

[Minimal examples and workarounds for the first three defects](clojure-1.13-defects.md)

## Read a run

1. Open the **differential** job summary for the run. Its table counts matches, feature skips, and differences from the model for each participating worker and shared property group. The same table is saved as `coverage.md` in `shared-comparison`, alongside `comparison.edn` and any standalone `repro-*.clj` programs.
2. For JVM-only results, download `jvm-version-comparison`. The versioned interactions logs identify the property, input, expected result and observation for known and unexpected failures. The numeric and corpus diffs compare pinned to rolling stable, then rolling stable to candidate.
3. For jank, read `jank-conformance` for build identity and results from each JIT mode. For ClojureCLR, read `clr-stable-comparison` and `clr-candidate-1.13-comparison`. These separate lanes run the portable derivative cases; CLR also replays the shared vector histories in the main workflow.

## Computed keys and destructuring summaries

The shared suite now also compares the local bound by a computed key with the entries claimed by `:select` and `:excess`. Its 37 cases cover passing controls, throwing key expressions, and nested defaults. The differential job reports these cases in the **Selected / unused entries** column. jank and ClojureCLR run the same recipes in their own lanes. The 300-case table above records the earlier run, before these cases were added. [Properties and runnable example](../packs/shared/README.md#do-selected-and-unused-entries-describe-the-binding-that-ran)
