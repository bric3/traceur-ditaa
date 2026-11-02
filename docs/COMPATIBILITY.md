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
25 million pixels. Write failures propagate to the caller.

## Corpus comparison

The normal build runs 1,753 tests on each JDK, including 1,512 exact image comparisons
from 63 project-authored diagrams, three scales and all shadow/separation settings.
PNG assertions compare every decoded pixel. SVG assertions rasterise both vectors
with `rsvg-convert` before comparing every pixel. These focused fixtures cover
individual features; the external corpus below checks their combinations.

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
To regenerate one fixture, append its name after `current` or the Java 11
executable. The recorded environment must match; otherwise regenerate the full
matrix first.

```sh
jbang tools/GenerateReferenceFixtures.java /path/to/plantuml-1.2026.8.jar /path/to/ditaa-0.11.0.jar current box
```

The [manifest](../corpus/manifest.tsv) pins 240 unique inputs with repository,
revision, extraction location, SHA-256, and unresolved licence markers. Recreate
the external cache with Java 25 and JBang:

```sh
jbang tools/FetchPinnedCorpus.java corpus/manifest.tsv /tmp/traceur-ditaa-corpus
./gradlew compareCorpus \
  -Pcorpus=/tmp/traceur-ditaa-corpus/inputs \
  -Poracle=/path/to/plantuml-1.2026.8.jar \
  -PsvgOracle=/path/to/ditaa-0.11.0-standalone.jar
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
and difference images appear in `build/comparison/index.html`.

Successful references are cached by input, options, format, oracle hashes, JDK,
and rasterizer. Delete `build/comparison` to refresh them after changing fonts.
`FetchCorpus.java` and `ImportSearchCorpus.java` discover additional documentation
blocks and GitHub `.ditaa` files. GitHub search caps results at 1,000; the recorded
corpus describes the searched collections.

## Cold start

```sh
./gradlew benchmarkColdStart \
  -Poracle=/path/to/plantuml-1.2026.8.jar \
  -PsvgOracle=/path/to/ditaa-0.11.0-standalone.jar
```

The benchmark interleaves 30 fresh JVMs per implementation, format, and diagram
size. Timing includes startup, layout, rendering, and encoding. The gate requires
a lower median and p95 no higher than the reference. Results, raw samples, and
environment details are written to `build/cold-start/`.

Run without competing builds. Peak RSS uses `/usr/bin/time` on macOS and Linux;
macOS sandbox restrictions can prevent access to its resource counters.
CI and release workflows require every corpus comparison to pass before publishing.
Cold-start benchmarks remain opt-in.
