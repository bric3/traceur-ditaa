# Rendering and comparison

`DiagramParser` expands a character grid, connects neighbouring line characters,
and walks directed edges to identify bounded regions. A shared `Drawing` holds
paths, fills, and labels. `PngWriter` uses Java2D and ImageIO; `SvgWriter` writes
vector paths and escaped text. Each render call owns its state.

The parser recognises nested and adjacent regions, rounded corners, dashed lines,
arrows, markers, bullets, colours, and `{d}`, `{s}`, `{io}`, `{o}`, `{mo}`, `{c}`,
and `{tr}` shape tags. Complex boundaries, scaling, text layout, and some shape
styles still differ from the references.

Tabs expand to eight-column stops; CRLF and CR become LF. Empty input produces a
blank image. Rendering rejects invalid text characters and scales, oversized
input, grids above 250,000 cells, dimensions above 32,768 pixels, and images above
25 million pixels. Write failures propagate to the caller.

## Corpus comparison

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
Corpus and benchmark tasks are opt-in and run separately from CI's build checks.
