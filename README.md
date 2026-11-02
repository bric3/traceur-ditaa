# Traceur Ditaa

[![Maven Central](https://img.shields.io/maven-central/v/io.github.bric3.traceur/traceur-ditaa?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.bric3.traceur/traceur-ditaa)
[![Build](https://github.com/bric3/traceur-ditaa/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/bric3/traceur-ditaa/actions/workflows/ci.yml)
[![Line coverage](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbric3%2Ftraceur-ditaa%2Fbadges%2Fcoverage.json)](https://github.com/bric3/traceur-ditaa/actions/workflows/ci.yml?query=branch%3Amain)

A ditaa-compatible Java 11 renderer for PNG and vector SVG.
Independently implemented, with no third-party runtime dependencies.

Maven coordinates: `io.github.bric3.traceur:traceur-ditaa`.
Use [Central's snapshot repository](https://central.sonatype.com/repository/maven-snapshots/)
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

`render` accepts a `String` or `Reader`. Use `InputStreamReader` to choose byte decoding.
Pass raw diagram text; the caller handles PlantUML preprocessing and
`@startditaa`/`@endditaa` directives.

| Target                     | Destination    | Encoding                     |
| -------------------------- | -------------- | ---------------------------- |
| `RenderTarget.png(output)` | `OutputStream` | PNG                          |
| `RenderTarget.svg(output)` | `OutputStream` | UTF-8 SVG                    |
| `RenderTarget.svg(writer)` | `Writer`       | Caller-selected SVG encoding |

`RenderOptions.DEFAULT` uses scale `1`, shadows enabled and shared-edge separation enabled.
Renderer instances are thread-safe. The caller owns buffering, flushing and closing.

Runtime modules: `java.base` and `java.desktop`.
See [rendering contracts and limits](docs/COMPATIBILITY.md#rendering-api).

## Build

Use a Git checkout with tags, Java 25 and an installed Java 11 toolchain:

```sh
./gradlew build
```

JARs: `build/libs/`.
Development, testing and publishing: [CONTRIBUTING.md](CONTRIBUTING.md).

Licence: [MPL-2.0](LICENSE).
