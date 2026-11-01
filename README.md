# Traceur Ditaa

[![Maven Central](https://img.shields.io/maven-central/v/io.github.bric3.traceur/traceur-ditaa?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.bric3.traceur/traceur-ditaa)
[![Build](https://github.com/bric3/traceur-ditaa/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/bric3/traceur-ditaa/actions/workflows/ci.yml)

An independently implemented, MPL-2.0 Java library for rendering ditaa text as
PNG and vector SVG. It targets Java 11 and has no third-party runtime dependencies.

Maven coordinates: `io.github.bric3.traceur:traceur-ditaa`. Publication is pending.

Version 0.0.1 is experimental. Exact rendering compatibility is incomplete:
76 of 1,920 comparison cases match the reference. See the
[validation snapshot](docs/VALIDATION.md) for the measured gaps.

## Usage

```java
import io.github.bric3.traceur.ditaa.DitaaRenderer;
import io.github.bric3.traceur.ditaa.OutputFormat;
import io.github.bric3.traceur.ditaa.RenderOptions;
import java.nio.file.Files;
import java.nio.file.Path;

String diagram = String.join("\n", "+-----+", "| Box |", "+-----+");

try (var output = Files.newOutputStream(Path.of("box.svg"))) {
    var size = new DitaaRenderer().render(
            diagram, RenderOptions.DEFAULT, OutputFormat.SVG, output);
    System.out.println(size.width() + " x " + size.height());
}
```

Pass raw diagram text. The caller handles PlantUML preprocessing and
`@startditaa`/`@endditaa`, and closes the output stream. `RenderOptions` controls
scale, shadows, and shared-edge separation; defaults are `1`, `true`, and `true`.
Use `OutputFormat.PNG` for raster output. Renderer instances are thread-safe.

The library requires the JDK's `java.base` and `java.desktop` modules. There is no
public CLI or binary compatibility with ditaa's Java classes.

## Build

Build from a Git checkout with tags and Java 11 and Java 25 toolchains installed:

```sh
./gradlew build
```

Gradle runs on Java 25 and compiles with `--release 11`. `build` runs the tests on
both Java versions and writes the library, source, and Javadoc JARs to
`build/libs/`. Set `org.gradle.java.installations.paths` if Gradle cannot discover
the installed JDKs.

## Versions and CI

Versioning follows [jardiff](https://github.com/bric3/jardiff) using Javier Segovia's
[Semver plugin](https://github.com/JavierSegoviaCordoba/semver-gradle-plugin) 0.9.0.
Git tags are the version source: a clean `v0.0.1` checkout builds as `0.0.1`.
Development versions include the commit count and hash; modified checkouts append
`DIRTY`. Maven publication coordinates use `-SNAPSHOT` for development versions.

```sh
./gradlew printSemver
```

The calculated version and prefixed tag are written to `build/semver/version.txt`.
Create annotated `vMAJOR.MINOR.PATCH` tags on tested commits to cut new versions.

[CI](.github/workflows/ci.yml) runs on pull requests, pushes to `main`, and `v*`
tags. It validates the wrapper, builds and tests, and uploads JARs and test reports.
Successful pushes create or update a draft release from those same JARs.
Published releases are preserved. Publication remains a manual decision.

## Technical details

- [Rendering and comparison workflow](docs/COMPATIBILITY.md)
- [Implementation provenance and fixture licences](docs/PROVENANCE.md)
- [Recorded correctness and cold-start results](docs/VALIDATION.md)

Original source and project-authored fixtures use [MPL-2.0](LICENSE). Reference
renderers are external test tools and are excluded from release artifacts.
