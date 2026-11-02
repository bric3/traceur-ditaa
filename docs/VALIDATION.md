# Validation record

Measurements recorded on 2026-11-02. Build counts below precede the corpus-suite
migration; allocation and latency results apply to the revisions named here.

## Recorded build checks

| Measurement              | Result                                                              |
| ------------------------ | ------------------------------------------------------------------- |
| Unit tests               | 2,530 per JDK on macOS and Linux; Java 11 and Java 25               |
| Image comparisons        | 2,208 per JDK: 85 authored diagrams and seven blog diagrams         |
| Build-logic checks       | 20                                                                  |
| Renderer line coverage   | 99.37%                                                              |
| Renderer branch coverage | 97.03%                                                              |
| Coverage gate            | 99% lines, 97% branches; no exclusions                              |
| Bytecode                 | Java 11                                                             |
| Runtime modules          | `java.base`, `java.desktop`                                         |
| Snapshot JAR             | Approximately 62 KiB; excludes test libraries and reference helpers |

Tests cover geometry, text, encoding, limits, concurrency, resource ownership and
I/O failures. Linux coverage was also measured directly with JaCoCo 0.8.14 on both JDKs.

### Formatting and test tools

| Tool                | Recorded version |
| ------------------- | ---------------- |
| JSVG                | 2.2.0            |
| TableTest           | 1.2.2            |
| Prince of Space     | 2.3.0            |
| TableTest Formatter | 1.1.2            |
| Yumi Licenser       | 4.0.0            |

Formatting preserved the parsed structure and literals of 41 Java files,
TableTest cells, and the content and order of 152 comments. A forced second pass
was unchanged. Long diagrams use JUnit method sources to preserve row spacing.

## Corpus results

| Measurement            | Result                                                |
| ---------------------- | ----------------------------------------------------- |
| Unique inputs          | 740: 240 retained and 500 added                       |
| Provenance entries     | 1,346 across 522 repositories                         |
| Comparisons            | 5,920 passed: two formats and four variants per input |
| Four-slot distribution | 188, 175, 176 and 201 inputs                          |
| Runtime                | Temurin 25.0.5+8-LTS, Linux container                 |
| SVG rasteriser         | `rsvg-convert` 2.58.0                                 |
| Recorded report        | `build/linux-extended-comparison/`                    |

PNG dimensions and decoded pixels match exactly. Both SVGs use the same
rasteriser for pixel comparison. External inputs stay outside the repository;
the manifest pins revisions, extraction locations and SHA-256 values.

Regressions cover contours, shared compartments, junctions, shafts, markers,
diagonals, concave documents, text columns, shadows and wide grids.
macOS Java 11 fixture tests and their generator prime the glyph cache consistently.
Corpus reference processes and cold-start measurements start unprimed.

### Reference checksums

| JAR               | SHA-256                                                            |
| ----------------- | ------------------------------------------------------------------ |
| PlantUML 1.2026.8 | `0f77e5f769836b3dee340e207fe497c3e4c43e973d559e3c306915da9c32e34c` |
| ditaa 0.11.0      | `9418aa63ff6d89c5d2318396f59836e120e75bea7a5930c4d34aa10fe7a196a9` |

### Gradle suite migration

- All 5,920 Linux comparisons passed through `corpusTest`, with one XML result per case.
- An eight-case run restored reports and images byte-for-byte after `cleanCorpusTest`.
- Changing the runner-image input invalidated the cache and executed the tests.
- Restoring the original environment produced `corpusTest FROM-CACHE` again.

Cache verification logs: `build/review/corpus-build-cache/`.
See [suite commands and cache inputs](COMPATIBILITY.md#corpus-suite).

[CI run 41537844323](https://github.com/bric3/traceur-ditaa/actions/runs/41537844323)
populated all four slot caches. A
[different caller workflow](https://github.com/bric3/traceur-ditaa/actions/runs/41540441472)
then reused the tested classes on fresh runners with a local `v0.1.0` release-tag
fixture. Every slot restored `corpusTest FROM-CACHE` under version `0.1.0`;
each Gradle invocation took 2-4 seconds.

## Publication checks

`nmcpZipAggregation -Psnapshot=true` staged `0.1.1-SNAPSHOT` locally with library,
source and Javadoc JARs, POM, Gradle metadata and checksums.
CI publishes tested artifacts after the build and every corpus slot pass.
Releases use the same compatibility gate.

## Allocation

| Parameter        | Value                                                    |
| ---------------- | -------------------------------------------------------- |
| Runtime          | Java 25; same runtime and harness for both revisions     |
| Samples          | 30 warm-up renders, then 50 measured SVG renders         |
| Destination      | Null output stream                                       |
| Dense blank grid | 200,000 source cells, scale `0.2`                        |
| Before           | Production code from `98b4c8b`                           |
| After            | `19bf16c`, before the disjoint branched-frame correction |

The later correction does not affect these workloads' rendering paths.

| SVG workload     | Before, bytes/render | After, bytes/render |
| ---------------- | -------------------- | ------------------- |
| Empty            | 6,696                | 8,208               |
| 50 text rows     | 166,618              | 168,299             |
| 1 box            | 43,475               | 45,689              |
| 10 boxes         | 519,549              | 602,276             |
| 50 boxes         | 2,637,186            | 3,115,405           |
| Dense blank grid | 5,709,210            | 966,355             |

- Dense blank-grid allocation fell 83% after trailing blank rows were trimmed.
- Ten-box and fifty-box allocation rose 16% and 18% with exact contour ordering
  and additional boundary classification.
- Region bounds are cached per region. Paint-sensitive collections retain iteration order.

These measurements cover allocation, excluding warm throughput and retained heap.

## Cold start

| Parameter   | Value                                               |
| ----------- | --------------------------------------------------- |
| Environment | macOS 26.7.2, aarch64, Temurin 25.0.5+8-LTS         |
| Samples     | 30 interleaved fresh JVMs per workload and revision |
| Timing      | Includes encoding                                   |
| Peak RSS    | `/usr/bin/time -l`                                  |
| Revisions   | Same before/after pair as allocation                |

### Before / after

| Workload     | Format | Median ms     | p95 ms          | Peak RSS MiB  |
| ------------ | ------ | ------------- | --------------- | ------------- |
| Empty        | SVG    | 60.1 / 63.7   | 84.0 / 72.3     | 56.2 / 57.7   |
| 50 text rows | SVG    | 943.1 / 934.8 | 1833.4 / 1680.4 | 211.8 / 212.0 |
| 1 box        | SVG    | 929.8 / 933.8 | 1065.8 / 1113.3 | 211.5 / 212.4 |
| 50 boxes     | SVG    | 971.9 / 980.9 | 1069.4 / 1070.7 | 217.8 / 221.1 |
| 1 box        | PNG    | 979.7 / 977.6 | 1012.1 / 1022.5 | 217.3 / 218.3 |

- Median differences stayed within 9.1 ms of the baseline.
- One-box SVG p95 rose 47.4 ms; text-only p95 fell 153.1 ms.
- Fifty-box peak RSS rose 3.3 MiB.

Raw samples and revision harness: `build/cold-start/extended/`.
Warm throughput and retained heap were not measured.

### Traceur Ditaa / reference

Same runtime and 30 interleaved fresh JVMs per implementation.

| Boxes | Format | Median ms       | p95 ms          | Peak RSS MiB  |
| ----- | ------ | --------------- | --------------- | ------------- |
| 1     | PNG    | 963.3 / 972.1   | 1079.0 / 1077.8 | 218.4 / 222.6 |
| 1     | SVG    | 939.0 / 961.8   | 1013.6 / 1067.3 | 212.4 / 218.7 |
| 10    | PNG    | 989.1 / 1065.7  | 1071.9 / 1145.2 | 221.1 / 310.1 |
| 10    | SVG    | 1007.4 / 1052.5 | 1125.2 / 1322.5 | 214.1 / 308.6 |
| 50    | PNG    | 1026.1 / 1515.0 | 1067.8 / 1590.3 | 233.0 / 836.1 |
| 50    | SVG    | 975.0 / 1494.7  | 1051.4 / 1559.6 | 220.9 / 833.4 |

Median latency and peak RSS were lower in all six workloads.
The strict p95 gate failed for one-box PNG: 1078.96 ms versus 1077.79 ms,
a 1.16 ms increase. All other median and p95 comparisons passed.

Raw samples and environment: `build/cold-start/extended-reference/`.
