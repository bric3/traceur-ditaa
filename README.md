# Traceur Ditaa

[![Maven Central](https://img.shields.io/maven-central/v/io.github.bric3.traceur/traceur-ditaa?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.bric3.traceur/traceur-ditaa)
[![Build](https://github.com/bric3/traceur-ditaa/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/bric3/traceur-ditaa/actions/workflows/ci.yml)

An independently implemented, MPL-2.0 Java library for rendering ditaa text as
PNG and vector SVG. It targets Java 11 and has no third-party runtime dependencies.

Maven coordinates: `io.github.bric3.traceur:traceur-ditaa`. Publication is pending.

Complete rendering compatibility is required before release. The current renderer
matches 691 of 1,920 corpus comparisons exactly. See the
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

Build from a Git checkout with tags, Java 11 and Java 25 toolchains, and
`rsvg-convert` installed:

```sh
./gradlew build
```

Gradle runs on Java 25 and compiles with `--release 11`. `build` runs the tests on
both Java versions, enforces at least 99% line and 97% branch coverage, and writes
the library, source, and Javadoc JARs to
`build/libs/`. Set `org.gradle.java.installations.paths` if Gradle cannot discover
the installed JDKs.

## Versions and CI

Git tags supply release versions. Development builds use Javier Segovia's
[Semver plugin](https://github.com/JavierSegoviaCordoba/semver-gradle-plugin) 0.9.0
to select the next patch version with `-SNAPSHOT`. A clean `v0.0.1` checkout builds
as `0.0.1`; later commits build as `0.0.2-SNAPSHOT`. Between `v0.0.2-rc.1` and
`v0.0.2`, development builds remain `0.0.2-SNAPSHOT`. The POM, JAR filenames and
`Implementation-Version` manifest entry use the same version.

```sh
./gradlew printSemver
```

Development builds write the calculated version to `build/semver/version.txt`.
[CI](.github/workflows/ci.yml) builds and tests pull requests and pushes to `main`.
Successful `main` builds publish snapshots and update one draft named
"Development preview", with tag `development-preview` and the tested JARs.

To release, open GitHub's **Releases > Draft a new release**, select the target
commit and enter `v0.0.2-rc.1` for a prerelease or `v0.0.2` for the final release.
The target commit must contain the publishing configuration.
Set the prerelease checkbox to match the tag, then click **Publish release**.
The [release workflow](.github/workflows/release.yml) checks out that exact tag,
builds and tests, attaches the JARs, signs the artifacts and publishes to Central.
Keep GitHub releases mutable so the workflow can attach JARs after publication.

## Maven Central setup

The `traceur.central-publication` settings convention in `build-logic` configures
[Nmcp](https://gradleup.com/nmcp/) 1.6.2 and Gradle signing. The library build owns
the Maven publication and POM metadata.

Before enabling publishing:

1. Create `bric3/traceur-ditaa` and add it as `origin`.
2. Confirm that your [Central namespace](https://central.sonatype.com/publishing/namespaces)
   covers `io.github.bric3.traceur`, then enable SNAPSHOTs on that namespace.
3. Generate a [Central user token](https://central.sonatype.com/usertoken) and
   configure these GitHub Actions repository secrets:

| Secret                               | Value                                              |
| ------------------------------------ | -------------------------------------------------- |
| `MAVEN_CENTRAL_USERNAME`              | Username from the Central user token                |
| `MAVEN_CENTRAL_TOKEN`                 | Password from the Central user token                |
| `ORG_GRADLE_PROJECT_SIGNINGKEY`       | ASCII-armoured private PGP signing key              |
| `ORG_GRADLE_PROJECT_SIGNINGPASSWORD`  | Passphrase for that key                             |

Publish the corresponding public key to a
[Central-supported keyserver](https://central.sonatype.org/publish/requirements/gpg/#distributing-your-public-key).
Snapshots can publish without signing credentials. Prereleases and final releases
require signing credentials. Central releases publish automatically after validation.
Push `main` and the existing `v0.0.1` tag after configuring the secrets.

For local publishing, supply the Gradle properties `mavenCentralUsername`,
`mavenCentralPassword`, `signingKey` and `signingPassword` in your user
`gradle.properties` or through `ORG_GRADLE_PROJECT_...` environment variables.

```sh
# Stage a development publication locally without uploading.
./gradlew nmcpZipAggregation -Psnapshot=true

# Publish a development snapshot.
./gradlew publishAggregationToCentralSnapshots -Psnapshot=true

# Publish an existing, clean release tag checked out at HEAD.
./gradlew publishAggregationToCentralPortal -PreleaseTag=v0.0.2
```

Consumers need the [Central snapshot repository](https://central.sonatype.com/repository/maven-snapshots/)
for development versions. Published prereleases and final releases resolve through
`mavenCentral()`.

## Technical details

- [Rendering and comparison workflow](docs/COMPATIBILITY.md)
- [Implementation provenance and fixture licences](docs/PROVENANCE.md)
- [Recorded correctness and cold-start results](docs/VALIDATION.md)

Original source and project-authored fixtures use [MPL-2.0](LICENSE). Reference
renderers are external test tools and are excluded from release artifacts.
