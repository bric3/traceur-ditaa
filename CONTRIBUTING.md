# Contributing

## Build

Requirements:

- Git checkout with tags for version calculation.
- Java 25 to run Gradle; Java 11 for compatibility tests.
- `org.gradle.java.installations.paths` if Gradle cannot discover the JDKs.

```sh
./gradlew build
```

| Check             | Requirement                                         |
| ----------------- | --------------------------------------------------- |
| Compilation       | Java 11 bytecode                                    |
| Unit tests        | Pass on Java 11 and Java 25                         |
| Renderer coverage | At least 99% lines and 97% branches; no exclusions  |
| Formatting        | Spotless: Prince of Space, then TableTest Formatter |
| Source headers    | MPL-2.0, checked by Yumi Licenser                   |

Library, source and Javadoc JARs go to `build/libs/`.
Tool versions live in the [version catalog](gradle/libs.versions.toml).

## Formatting and tests

```sh
./gradlew spotlessApply
./gradlew applyLicenses
```

- Prefer TableTest for tabular cases; use argument converters where helpful.
- Use JUnit 5 for generated matrices, concurrency and I/O checks.
- Preserve diagram whitespace. Assert observable rendering behaviour.

## Rendering verification

| Suite           | Comparison                                            | Invocation             |
| --------------- | ----------------------------------------------------- | ---------------------- |
| Unit fixtures   | Exact PNG pixels; SVGs rasterised in memory with JSVG | `./gradlew build`      |
| External corpus | Exact PNG pixels; SVGs rasterised with `rsvg-convert` | `./gradlew corpusTest` |

The corpus suite requires fetched inputs and reference JARs. CI runs it in matrix
slots and requires every slot to pass before publishing. Fixture generation and
corpus fetching require Java 25 and JBang.

Use documentation, public binary APIs and rendered output as references.
Exclude upstream implementation sources and decompiled code.

- [Rendering, fixtures, slots and cache](docs/COMPATIBILITY.md)
- [Validation measurements](docs/VALIDATION.md)
- [Implementation provenance and licences](docs/PROVENANCE.md)

## Versioning

```sh
./gradlew printSemver
```

| Checkout                           | Version          |
| ---------------------------------- | ---------------- |
| Clean `v0.0.1` tag                 | `0.0.1`          |
| Commits after `v0.0.1`             | `0.0.2-SNAPSHOT` |
| Between `v0.0.2-rc.1` and `v0.0.2` | `0.0.2-SNAPSHOT` |

`-Psnapshot=true` forces a snapshot at a release tag.
The version is recorded in `build/semver/version.txt`, the POM, JAR filenames
and the `Implementation-Version` manifest attribute.

## CI

[CI](.github/workflows/ci.yml) builds pull requests and pushes to `main`.
After the build and corpus slots pass, `main` publishes:

- Tested snapshot JARs to Maven Central.
- Combined Java 11/25 line coverage to `badges:coverage.json`.
- Updated JARs and target commit for the newest draft release.

The README coverage badge uses Shields.io; image caching can delay updates.

### Draft releases

Create a draft with its intended tag, such as `v0.1.0`, before creating the Git tag.
CI creates the tag only in its private checkout to package the tested classes.

CI preserves the draft's title and notes, replaces its assets, and verifies the
target commit and three JAR uploads. It skips the refresh when no draft exists
or `main` has advanced past the tested commit.

### Publish a release

1. Open GitHub's **Releases > Draft a new release** and select a tested commit.
2. Set a tag: `v0.0.2-rc.1` for a prerelease, or `v0.0.2` for a final release.
3. Match the prerelease checkbox to the tag and publish.

The [release workflow](.github/workflows/release.yml) checks out the tag, builds,
runs tests and corpus slots, attaches JARs, signs the artifacts and publishes to Central.
Keep releases mutable so the workflow can attach the JARs.

## Maven Central setup

The `traceur.central-publication` settings convention configures
[Nmcp](https://gradleup.com/nmcp/) and Gradle signing.
The library build defines the Maven publication and POM.

Requirements:

- A [Central namespace](https://central.sonatype.com/publishing/namespaces)
  covering `io.github.bric3.traceur`, with SNAPSHOTs enabled.
- A [Central user token](https://central.sonatype.com/usertoken).
- A public signing key on a
  [Central-supported keyserver](https://central.sonatype.org/publish/requirements/gpg/#distributing-your-public-key).

Configure these GitHub Actions repository secrets:

| Secret                               | Value                          |
| ------------------------------------ | ------------------------------ |
| `MAVEN_CENTRAL_USERNAME`             | Central token username         |
| `MAVEN_CENTRAL_TOKEN`                | Central token password         |
| `ORG_GRADLE_PROJECT_SIGNINGKEY`      | ASCII-armoured private PGP key |
| `ORG_GRADLE_PROJECT_SIGNINGPASSWORD` | Key passphrase                 |

Snapshots can publish unsigned. Prereleases and final releases require signing;
Central publishes them after validation.

### Local publishing

Set `mavenCentralUsername`, `mavenCentralPassword`, `signingKey` and
`signingPassword` in your user `gradle.properties`, or use the corresponding
`ORG_GRADLE_PROJECT_...` environment variables.

| Operation                           | Command                                                           |
| ----------------------------------- | ----------------------------------------------------------------- |
| Stage a snapshot locally            | `./gradlew nmcpZipAggregation -Psnapshot=true`                    |
| Publish a snapshot                  | `./gradlew publishAggregationToCentralSnapshots -Psnapshot=true`  |
| Publish a clean release tag at HEAD | `./gradlew publishAggregationToCentralPortal -PreleaseTag=v0.0.2` |
