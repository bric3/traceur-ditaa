# Rendering and comparison

`DiagramParser` expands a character grid, connects neighbouring line characters,
and walks directed edges to identify bounded regions. A shared `Drawing` holds
paths, fills, and labels. `PngWriter` uses Java2D and ImageIO; `SvgWriter` writes
vector paths and escaped text. Each render call owns its state.

The parser recognises nested and adjacent regions, rounded corners, dashed lines,
arrows, markers, bullets, colours, and `{d}`, `{s}`, `{io}`, `{o}`, `{mo}`, `{c}`,
and `{tr}` shape tags. Compatibility is verified against the pinned corpus and
the authored fixture matrix below.

Tabs expand to eight-column stops; CRLF and CR become LF. Empty input produces a
blank image. Rendering rejects invalid text characters and scales, oversized
input, grids above 250,000 cells, dimensions above 32,768 pixels, and images above
25 million pixels. Read and write failures propagate to the caller.

`DitaaRenderer.render` accepts a `String` or `Reader` and a `RenderTarget`.
Reader input is limited to 1,000,000 UTF-16 code units while reading, then parsed
before output starts. PNG targets accept an `OutputStream`; SVG targets accept
an `OutputStream` encoded as UTF-8 or a `Writer` whose encoding belongs to the
caller. Rendering never closes or flushes caller resources. The original
`OutputFormat` overload remains available.

## Corpus comparison

The normal build runs 2,530 tests on each JDK, including 2,208 exact image comparisons
from 85 project-authored diagrams and seven blog diagrams, three scales and all
shadow/separation settings. Blog sources and licensing are recorded in
[BLOG-SOURCES.md](../src/test/resources/compatibility/BLOG-SOURCES.md).
PNG assertions compare every decoded pixel. SVG assertions rasterise both vectors
in memory with JSVG 2.2.0 before comparing every pixel. These focused fixtures cover
individual features; the external corpus below checks their combinations.

TableTest expresses tabular geometry, text and version-mapping cases. A diagram
argument converter expands explicit row breaks while preserving diagram spacing.
Generated fixture matrices, concurrency and I/O checks retain JUnit 5 constructs.
JSVG and TableTest are test dependencies; the library and cold-start harness have
no third-party runtime dependencies.

`check` enforces 99% line coverage and 97% branch coverage of the whole renderer,
without exclusions. HTML and XML reports are written to `build/reports/jacoco/test/`.

Regenerate the authored reference images with Java 25 and JBang:

```sh
jbang tools/GenerateReferenceFixtures.java /path/to/plantuml-1.2026.8.jar /path/to/ditaa-0.11.0.jar
jbang tools/GenerateReferenceFixtures.java /path/to/plantuml-1.2026.8.jar /path/to/ditaa-0.11.0.jar /path/to/java11/bin/java
```

Java2D rasterisation and font metrics depend on the OS and JDK version.
Run both commands on macOS and Linux to regenerate the full fixture set.
Linux references are stored in the linux subdirectory of compatibility fixtures.
Each platform uses PNGs generated on the tested JDK and SVGs generated on Java 25.
The generator checks both reference binary checksums before writing fixtures.
PNG fixtures use the same raw-text entry point as the corpus comparison, preserving
leading blank rows and source whitespace.
On macOS Java 11, fixture generation and tests initialise the font cache with the
same ASCII text at each scale. This avoids history-dependent missing glyphs in
Java2D. Corpus comparisons and cold-start benchmarks remain unprimed.
To regenerate selected fixtures, append their comma-separated names after `current` or the Java 11
executable. The recorded environment must match; otherwise regenerate the full
matrix first.

```sh
jbang tools/GenerateReferenceFixtures.java /path/to/plantuml-1.2026.8.jar /path/to/ditaa-0.11.0.jar current box
```

The [manifest](../corpus/manifest.tsv) pins 740 unique inputs with repository,
revision, extraction location, SHA-256, and unresolved licence markers. Recreate
the external cache with Java 25 and JBang:

```sh
jbang tools/FetchPinnedCorpus.java corpus/manifest.tsv /tmp/traceur-ditaa-corpus
./gradlew corpusTest \
  -Pcorpus=/tmp/traceur-ditaa-corpus/inputs \
  -PpngReference=/path/to/plantuml-1.2026.8.jar \
  -PsvgReference=/path/to/ditaa-0.11.0-standalone.jar
```

Install `rsvg-convert`, or supply `-Prasterizer=/path/to/rsvg-convert`.
The PNG reference runs through its public raw-text API in an isolated JVM.
Both renderers receive the same whitespace; PlantUML preprocessing is excluded.
PNG comparisons require identical dimensions and decoded RGBA pixels. Both SVGs
are rasterised by the same renderer before comparison; the replacement SVG must
also be valid XML containing vector content. Pin the OS, JDK, fonts, and rasterizer
for repeatable pixel comparisons.

Each input runs in both formats with defaults, shadows disabled, separation
disabled, and scale 0.7. Reference processes have a 15-second timeout; four cases
run concurrently. Every mismatch or error fails the task. Results, logs, overlays,
and difference images appear under `build/comparison/<slot>/<input>/`; a complete
run uses `build/comparison/0-1/all/index.html`.

CI and releases require all 5,920 comparisons, partitioned into four deterministic
SHA-256 slots. Slot indices start at zero: `-PtestSlot=1/4` runs the second of four
slots. The total is dynamic; omitting the property runs every input. Selection is
stable across manifest ordering, and every input belongs to exactly one slot.
The `traceur.test-slots` build-logic plugin writes the selected manifest, which
also limits fetching:

```sh
./gradlew selectTestSlot -PtestSlot=1/4
jbang tools/FetchPinnedCorpus.java build/test-slots/manifest.tsv /tmp/traceur-ditaa-corpus
./gradlew corpusTest -PtestSlot=1/4 \
  -Pcorpus=/tmp/traceur-ditaa-corpus/inputs \
  -PpngReference=/path/to/plantuml-1.2026.8.jar \
  -PsvgReference=/path/to/ditaa-0.11.0-standalone.jar
```

`corpusTest` is a JVM test suite in this project, using JUnit 5 dynamic tests.
Each input, variant and format has a separate result in Gradle's HTML and XML
reports under `build/reports/tests/corpusTest` and `build/test-results/corpusTest`.
It runs explicitly in the CI matrix; `build` and unit-test coverage do not run it.
Use `-PtestInput=<sha256>` with a manifest ID to rerun one diagram's eight cases;
if combined with `testSlot`, both selections apply. An empty slot reports a skipped test.
Gradle's `--tests` selects the test class or factory, rather than individual
dynamic comparisons. Gradle caches the suite results and comparison reports.
The cache inputs include the selected manifest, corpus files, reference JARs,
renderer classes, JDK release, OS, runner image, font files and rasteriser version.
CI's `setup-gradle` action restores the local build cache for each matrix slot;
`main` builds save entries that subsequent builds and pull requests can reuse.
Use `--rerun-tasks` to force execution.

## Extending the corpus

The [search inventory](../corpus/search-2026-11-02.tsv) records six GitHub code
queries for `.ditaa`, AsciiDoc, Markdown, PlantUML and Org files. Three queries
reached GitHub's 1,000-result cap. The importer found 1,474 new unique candidates
across 488 repositories. Selection retained the previous 240 inputs and added
500, taking one candidate per repository each round in repository-name order.
This is a bounded sample of the recorded searches.

```sh
jbang tools/SearchCorpus.java /tmp/traceur-discovery
jbang tools/ImportSearchCorpus.java /tmp/traceur-discovery/search.tsv /tmp/traceur-discovery
jbang tools/SelectCorpus.java /path/to/base-manifest.tsv /tmp/traceur-discovery corpus/rejected.tsv
```

The selection tool writes `selected/manifest.tsv` and `selected/inputs/` in the
external cache. Review their provenance, compare all selected inputs, then append
their metadata to the manifest. External source documents and extracted diagrams
stay outside the repository.

The [rejection list](../corpus/rejected.tsv) records one empty diagram rejected
by both references; the next candidate filled its place. Rendering mismatches
remain required cases and fail the comparison.

## Cold start

```sh
./gradlew benchmarkColdStart \
  -PpngReference=/path/to/plantuml-1.2026.8.jar \
  -PsvgReference=/path/to/ditaa-0.11.0-standalone.jar
```

The benchmark interleaves 30 fresh JVMs per implementation, format, and diagram
size. Timing includes startup, layout, rendering, and encoding. The gate requires
a lower median and p95 no higher than the reference. Results, raw samples, and
environment details are written to `build/cold-start/`.
The PNG reference uses the same public raw-text entry point as the corpus check;
the SVG reference uses ditaa's CLI. Compare revisions with the same entry points,
JDK, fonts and inputs.

Run without competing builds. Peak RSS uses `/usr/bin/time` on macOS and Linux;
macOS sandbox restrictions can prevent access to its resource counters.
CI and release workflows require every corpus comparison to pass before publishing.
Cold-start benchmarks remain opt-in.
