# Jolt native regions

Can a compiled loop observe a write through a second name for the same array?
The property adds each source element into its destination, writes the result,
and then reads the source element again. With separate arrays, that read sees
the original value. With the same array passed twice, it sees the updated value.

For example, start with source `[2.0]` and destination `[7.0]`. After one step
the source still reads `2.0` and the destination holds `9.0`. Pass the source
as both arguments and the read and the destination should both give `4.0`.

The 48 cases cover empty arrays, one-element arrays, longer arrays, positive
and negative inputs, and stopping before, inside, or at the end of the array.
Each case checks the accumulated reads and the complete contents of both
arrays against a persistent-vector model, including the untouched suffix.

The same loop body runs as ordinary JVM Clojure, as a Jolt source region, and
in an executable produced by `jolt build`. The runner verifies that Jolt
compiled a native region and compares all three transcripts. The values are
exactly representable doubles. This pack runs in the scheduled suite and in
the manually selectable `pilot` and `all` jobs on Linux x86_64.

With Jolt 0.8.30, a C compiler, Java, and an absolute path to a
Clojure tools jar containing `clojure.main`, run from the repository root:

```sh
PILOT_CLOJURE_JAR=/path/to/clojure-tools.jar bash packs/native-regions/run.sh /tmp/native-region-report
```
