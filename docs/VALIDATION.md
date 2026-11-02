# Validation snapshot

Compatibility, coverage, allocation and cold start measured on 2026-11-02.

## Build

`build` passes 5,060 tests across Java 11 and Java 25 on macOS, plus 20
build-logic checks. Linux passes the same 2,530 tests on each JDK. Each JDK runs
2,208 exact PNG/SVG comparisons of 85 project-authored and seven blog diagrams.
The remaining checks cover geometry, text, encoding, limits, concurrency, stream
ownership and I/O failures.

Whole-renderer coverage is 99.37% of lines and 97.03% of branches on both
platforms, without exclusions. `check` requires 99% line and 97% branch coverage.
Linux coverage was also measured directly with JaCoCo 0.8.14 on both JDKs.

SVG fixture comparisons use JSVG 2.2.0. TableTest 1.2.2 expresses tabular cases;
JUnit method sources retain long diagrams whose rows the formatter cannot preserve.
Spotless uses Prince of Space 2.3.0 and TableTest Formatter 1.1.2; Yumi Licenser
4.0.0 checks source headers. Formatting preserved the parsed structure and literal
values of 41 Java files, including TableTest cells, and the content and order of
152 comments. A forced second pass was unchanged.

The library targets Java 11 bytecode and depends only on `java.base` and
`java.desktop`. Its snapshot JAR is approximately 62 KiB. Reference helpers and
third-party test libraries are absent from the runtime artifact.

## Publishing

`nmcpZipAggregation -Psnapshot=true` stages the `0.1.1-SNAPSHOT` publication
locally, including library, source and Javadoc JARs, POM, Gradle metadata and
checksums. CI publishes the tested JARs after the build and all four corpus
shards pass. Release publication uses the same compatibility gate.

## Rendering

The manifest retains the previous 240 unique inputs and adds 500 selected from
GitHub searches. Its 1,346 provenance entries cover 522 repositories. External
source documents and diagrams remain outside the repository; the manifest pins
revisions, extraction locations and SHA-256 values.

All 5,920 comparisons pass on Linux: 740 inputs, two formats and four option
variants. Every decoded PNG pixel matches. SVGs are rasterised by the same
renderer and compared pixel by pixel. CI partitions the inputs into four
SHA-256 shards containing 188, 175, 176 and 201 diagrams.

The full corpus ran with Temurin 25.0.5+8-LTS and `rsvg-convert` 2.58.0 in the
Linux test container. Its report is generated under `build/linux-extended-comparison/`.

The authored regressions cover branched and split contours, shared open
compartments, adjacent junctions, short shafts, independent markers, diagonal
endpoints, concave documents, text columns, shadow rounding and wide grids.
macOS Java 11 fixture tests prime the Java2D glyph cache consistently with their
reference generator. Corpus comparisons and cold-start measurements use fresh,
unprimed processes.

Reference JAR SHA-256 values:

- PlantUML 1.2026.8: `0f77e5f769836b3dee340e207fe497c3e4c43e973d559e3c306915da9c32e34c`.
- ditaa 0.11.0: `9418aa63ff6d89c5d2318396f59836e120e75bea7a5930c4d34aa10fe7a196a9`.

## Allocation

Java 25 thread-allocation measurements use 30 warm-up renders and 50 measured SVG
renders, writing to a null output stream. The dense blank grid contains 200,000
source cells at scale 0.2. Both revisions use the same runtime and harness;
the baseline contains the production code from `98b4c8b`. Candidate measurements use
`19bf16c`, before the final correction for disjoint branched frames. That
correction does not change the measured diagrams' rendering paths.

| SVG workload     | Before, bytes/render | After, bytes/render |
| ---------------- | -------------------- | ------------------- |
| Empty            | 6,696                | 8,208               |
| 50 text rows     | 166,618              | 168,299             |
| 1 box            | 43,475               | 45,689              |
| 10 boxes         | 519,549              | 602,276             |
| 50 boxes         | 2,637,186            | 3,115,405           |
| Dense blank grid | 5,709,210            | 966,355             |

Trimming trailing blank rows reduces dense blank-grid allocation by 83%.
Exact contour ordering and the additional boundary classification increase
allocation for ten and fifty boxes by 16% and 18%. Region bounds are cached once
per region; collections whose iteration affects painting retain their order.
These measurements describe allocation, not warm throughput or retained heap.

## Cold start

Environment: macOS 26.7.2, aarch64, Temurin 25.0.5+8-LTS. Each workload uses
30 interleaved fresh JVMs per revision. Timing includes encoding; peak RSS comes
from `/usr/bin/time -l`. Cells show before / after, with the same baseline as
the allocation measurements.

| Workload     | Format | Median ms     | p95 ms          | Peak RSS MiB  |
| ------------ | ------ | ------------- | --------------- | ------------- |
| Empty        | SVG    | 60.1 / 63.7   | 84.0 / 72.3     | 56.2 / 57.7   |
| 50 text rows | SVG    | 943.1 / 934.8 | 1833.4 / 1680.4 | 211.8 / 212.0 |
| 1 box        | SVG    | 929.8 / 933.8 | 1065.8 / 1113.3 | 211.5 / 212.4 |
| 50 boxes     | SVG    | 971.9 / 980.9 | 1069.4 / 1070.7 | 217.8 / 221.1 |
| 1 box        | PNG    | 979.7 / 977.6 | 1012.1 / 1022.5 | 217.3 / 218.3 |

Startup medians stay within 9.1 ms of the baseline. One-box SVG p95 increases
by 47.4 ms, while text-only p95 decreases by 153.1 ms. Fifty-box peak RSS
increases by 3.3 MiB. The p95 changes vary by workload.

Measurements, raw samples and the direct revision harness are stored under
`build/cold-start/extended/`. Warm throughput and retained heap were not measured.

The reference benchmark uses the same runtime and 30 interleaved fresh JVMs per
implementation. Cells show traceur-ditaa / reference.

| Boxes | Format | Median ms       | p95 ms          | Peak RSS MiB  |
| ----- | ------ | --------------- | --------------- | ------------- |
| 1     | PNG    | 963.3 / 972.1   | 1079.0 / 1077.8 | 218.4 / 222.6 |
| 1     | SVG    | 939.0 / 961.8   | 1013.6 / 1067.3 | 212.4 / 218.7 |
| 10    | PNG    | 989.1 / 1065.7  | 1071.9 / 1145.2 | 221.1 / 310.1 |
| 10    | SVG    | 1007.4 / 1052.5 | 1125.2 / 1322.5 | 214.1 / 308.6 |
| 50    | PNG    | 1026.1 / 1515.0 | 1067.8 / 1590.3 | 233.0 / 836.1 |
| 50    | SVG    | 975.0 / 1494.7  | 1051.4 / 1559.6 | 220.9 / 833.4 |

Median latency and peak RSS are lower in all six scenarios. The strict gate
fails for one-box PNG p95: 1078.96 ms versus 1077.79 ms, a 1.16 ms increase.
All other median and p95 comparisons pass. Raw reference samples and environment
details are in `build/cold-start/extended-reference/`.
