# Rendering and verification

## Rendering API

`DitaaRenderer.render` accepts a `String` or `Reader`, rendering options and a
`RenderTarget`. It returns image dimensions in pixels.

| Contract      | Behaviour                                                                             |
| ------------- | ------------------------------------------------------------------------------------- |
| Input         | Raw ditaa text; caller handles PlantUML preprocessing and directives                  |
| Byte decoding | Caller selects an `InputStreamReader` charset                                         |
| PNG output    | `RenderTarget.png(OutputStream)`                                                      |
| SVG output    | `RenderTarget.svg(OutputStream)` uses UTF-8; `svg(Writer)` uses the writer's encoding |
| Resources     | Caller controls buffering, flushing and closing                                       |
| Thread safety | Each render call owns its state                                                       |
| I/O failures  | Propagate to the caller                                                               |

The `OutputFormat` overload remains available. The library has no public CLI or
binary compatibility with ditaa's Java classes.

### Text and limits

- Tabs expand to eight-column stops; CRLF and CR become LF.
- Empty input produces a blank image.
- Input is parsed and validated before output starts.
- Invalid text characters and scales are rejected.

| Limit                 | Maximum                     |
| --------------------- | --------------------------- |
| Input length          | 1,000,000 UTF-16 code units |
| Grid size             | 250,000 cells               |
| Image width or height | 32,768 pixels               |
| Image area            | 25 million pixels           |

### Rendering model

1. `DiagramParser` expands the grid, connects neighbouring line characters and
   walks directed edges to identify bounded regions.
2. `Drawing` holds paths, fills and labels.
3. `PngWriter` uses Java2D and ImageIO; `SvgWriter` emits vector paths and escaped text.

Supported features include nested and adjacent regions, rounded corners, dashed
lines, arrows, markers, bullets, colours and shape tags:
`{d}`, `{s}`, `{io}`, `{o}`, `{mo}`, `{c}`, `{tr}`.

## Fixture tests

The fixture matrix covers 85 project-authored diagrams and seven
[blog diagrams](../src/test/resources/compatibility/BLOG-SOURCES.md), three scales
and every shadow/separation combination: 2,208 image comparisons per JDK.

| Format | Assertion                                                                 |
| ------ | ------------------------------------------------------------------------- |
| PNG    | Identical dimensions and decoded pixels                                   |
| SVG    | Both vectors rasterised in memory with JSVG, then compared pixel by pixel |

Fixtures isolate features; the external corpus checks their combinations.
`check` requires 99% line and 97% branch coverage of the renderer, without exclusions.
Reports: `build/reports/jacoco/test/`.

### Regenerate fixtures

Requirements: Java 25, JBang and the pinned PlantUML and ditaa JARs.
The generator verifies both JAR checksums before writing images.

```sh
# Java 25 references.
jbang tools/GenerateReferenceFixtures.java /path/to/plantuml-1.2026.8.jar /path/to/ditaa-0.11.0.jar

# Java 11 PNG references.
jbang tools/GenerateReferenceFixtures.java /path/to/plantuml-1.2026.8.jar /path/to/ditaa-0.11.0.jar /path/to/java11/bin/java

# One fixture; append comma-separated names to select several.
jbang tools/GenerateReferenceFixtures.java /path/to/plantuml-1.2026.8.jar /path/to/ditaa-0.11.0.jar current box
```

- Run both JDK commands on macOS and Linux for a complete regeneration.
- Linux images live in `src/test/resources/compatibility/linux/`.
- PNG references use the tested JDK; SVG references use Java 25.
- The PNG entry point preserves leading blank rows and source whitespace.
- Partial regeneration requires a matching recorded environment.

Java2D pixels and font metrics depend on the OS and JDK. On macOS Java 11, the
generator and fixture tests prime the glyph cache with the same ASCII text at
each scale to avoid missing glyphs. Corpus references and cold-start processes
start unprimed.

## Corpus suite

`corpusTest` is a JVM test suite in this project, using JUnit 5 dynamic tests.
The [manifest](../corpus/manifest.tsv) pins 740 unique inputs by repository,
revision, extraction location and SHA-256. Licence markers record unresolved rights.

Requirements: Java 25, JBang, `rsvg-convert` and the pinned reference JARs.
Use `-Prasterizer=/path/to/rsvg-convert` to select the SVG rasteriser.

### Run every input

```sh
jbang tools/FetchPinnedCorpus.java corpus/manifest.tsv /tmp/traceur-ditaa-corpus
./gradlew corpusTest \
  -Pcorpus=/tmp/traceur-ditaa-corpus/inputs \
  -PpngReference=/path/to/plantuml-1.2026.8.jar \
  -PsvgReference=/path/to/ditaa-0.11.0-standalone.jar
```

Each input runs in PNG and SVG with four variants:

| Variant | Settings                        |
| ------- | ------------------------------- |
| 0       | Defaults                        |
| 1       | Shadows disabled                |
| 2       | Shared-edge separation disabled |
| 3       | Scale `0.7`                     |

Both renderers receive the same raw text. PNG references use PlantUML's public
raw-text API in an isolated JVM. SVG comparisons rasterise both vectors with
`rsvg-convert`; actual SVGs must also be valid XML without embedded raster images.

- Dimensions and every decoded pixel must match.
- Four cases run concurrently; reference processes time out after 15 seconds.
- Every mismatch or process error fails its JUnit case.
- CI and releases require all 5,920 comparisons. Ordinary `build` and unit-test
  coverage do not run this suite.

### Select a slot or input

| Property               | Selection                                      |
| ---------------------- | ---------------------------------------------- |
| Omitted `testSlot`     | Every input                                    |
| `-PtestSlot=1/4`       | Second of four slots; indices start at zero    |
| `-PtestInput=<sha256>` | One manifest input, with all eight comparisons |
| Both properties        | Their intersection                             |

The `traceur.test-slots` build-logic plugin assigns each input to one slot by
SHA-256. The total is dynamic; manifest order does not affect selection.
An empty slot reports a skipped test. Gradle's `--tests` selects the class or
factory; use `testInput` for individual diagrams.

Generate a selected manifest to limit fetching:

```sh
./gradlew selectTestSlot -PtestSlot=1/4
jbang tools/FetchPinnedCorpus.java build/test-slots/manifest.tsv /tmp/traceur-ditaa-corpus
./gradlew corpusTest -PtestSlot=1/4 \
  -Pcorpus=/tmp/traceur-ditaa-corpus/inputs \
  -PpngReference=/path/to/plantuml-1.2026.8.jar \
  -PsvgReference=/path/to/ditaa-0.11.0-standalone.jar
```

### Reports and cache

| Output                                      | Location                                    |
| ------------------------------------------- | ------------------------------------------- |
| Gradle HTML                                 | `build/reports/tests/corpusTest/`           |
| JUnit XML                                   | `build/test-results/corpusTest/`            |
| Comparisons, logs, overlays and differences | `build/comparison/<index>-<total>/<input>/` |
| Complete comparison report                  | `build/comparison/0-1/all/index.html`       |

Gradle caches suite results and comparison files. Cache inputs include:

- Selected manifest, corpus files, renderer classes and reference JARs.
- JDK release, OS, architecture and runner image.
- Font files, font configuration and SVG rasteriser version.

CI's `setup-gradle` action restores the local build cache per matrix slot.
`main` saves entries that later builds and pull requests can reuse.
Use `--rerun-tasks` to force execution. Pin the rendering environment for repeatable pixels.

## Extend the corpus

The [search inventory](../corpus/search-2026-11-02.tsv) records six queries for
`.ditaa`, AsciiDoc, Markdown, PlantUML and Org files. Three queries reached
GitHub's 1,000-result cap; the corpus is a bounded sample.

| Discovery result       | Count |
| ---------------------- | ----- |
| New unique candidates  | 1,474 |
| Candidate repositories | 488   |
| Retained inputs        | 240   |
| Added inputs           | 500   |

Selection takes one candidate per repository per round, in repository-name order.

```sh
jbang tools/SearchCorpus.java /tmp/traceur-discovery
jbang tools/ImportSearchCorpus.java /tmp/traceur-discovery/search.tsv /tmp/traceur-discovery
jbang tools/SelectCorpus.java /path/to/base-manifest.tsv /tmp/traceur-discovery corpus/rejected.tsv
```

1. Review `selected/manifest.tsv` and `selected/inputs/` in the external cache.
2. Compare every selected input.
3. Append approved metadata to `corpus/manifest.tsv`.

Keep external documents and extracted diagrams outside the repository.
The [rejection list](../corpus/rejected.tsv) records one empty diagram rejected
by both references; the next candidate replaced it. Rendering mismatches remain
required test cases.

## Cold-start benchmark

```sh
./gradlew benchmarkColdStart \
  -PpngReference=/path/to/plantuml-1.2026.8.jar \
  -PsvgReference=/path/to/ditaa-0.11.0-standalone.jar
```

| Measurement | Method                                                                |
| ----------- | --------------------------------------------------------------------- |
| Samples     | 30 interleaved fresh JVMs per implementation, format and diagram size |
| Timing      | Startup, layout, rendering and encoding                               |
| Gate        | Lower median; p95 no higher than the reference                        |
| Peak RSS    | `/usr/bin/time` on macOS and Linux                                    |
| Output      | `build/cold-start/`: results, raw samples and environment             |

PNG references use the public raw-text API; SVG references use ditaa's CLI.
Keep entry points, JDK, fonts and inputs consistent across revisions.
Run without competing builds. macOS sandbox restrictions can block RSS counters.
Cold-start benchmarks are opt-in.
