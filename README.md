# Traceur Ditaa

[![Maven Central](https://img.shields.io/maven-central/v/io.github.bric3.traceur/traceur-ditaa?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.bric3.traceur/traceur-ditaa)
[![Build](https://github.com/bric3/traceur-ditaa/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/bric3/traceur-ditaa/actions/workflows/ci.yml)
[![Line coverage](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbric3%2Ftraceur-ditaa%2Fbadges%2Fcoverage.json)](https://github.com/bric3/traceur-ditaa/actions/workflows/ci.yml?query=branch%3Amain)

An independently implemented Java 11 library for rendering ditaa text as PNG
and vector SVG, with no third-party runtime dependencies. Rendering is checked
against ditaa and PlantUML reference images; compatibility covers the tested
diagrams and options.

Maven coordinates: `io.github.bric3.traceur:traceur-ditaa`. Use
[Central's snapshot repository](https://central.sonatype.com/repository/maven-snapshots/)
for `-SNAPSHOT` versions.

## Usage

```java
import io.github.bric3.traceur.ditaa.DitaaRenderer;
import io.github.bric3.traceur.ditaa.RenderOptions;
import io.github.bric3.traceur.ditaa.RenderTarget;
import java.nio.file.Files;
import java.nio.file.Path;

String diagram = String.join("\n", "+-----+", "| Box |", "+-----+");

try (var output = Files.newOutputStream(Path.of("box.svg"))) {
    var size = new DitaaRenderer().render(
            diagram, RenderOptions.DEFAULT, RenderTarget.svg(output));
    System.out.println(size.width() + " x " + size.height());
}
```

Pass raw diagram text. The caller handles PlantUML preprocessing and
`@startditaa`/`@endditaa`. Input can be a `String` or `Reader`; use an
`InputStreamReader` to choose input decoding. The caller owns buffering, flushing
and closing. `RenderOptions` controls
scale, shadows, and shared-edge separation; defaults are `1`, `true`, and `true`.
Use `RenderTarget.png(OutputStream)` for raster output. SVG targets accept an
`OutputStream` (UTF-8) or `Writer`. Renderer instances are thread-safe.

The library requires the JDK's `java.base` and `java.desktop` modules. There is no
public CLI or binary compatibility with ditaa's Java classes.

## Build

Build from a Git checkout with tags, using Java 25 and an installed Java 11 toolchain:

```sh
./gradlew build
```

JARs are written to `build/libs/`. See [CONTRIBUTING.md](CONTRIBUTING.md) for
development, testing and publishing.

Licensed under [MPL-2.0](LICENSE).
